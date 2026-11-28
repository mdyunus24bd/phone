package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import com.example.data.local.ContactEntity
import com.example.data.local.VoxLineDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ResolvedCallerInfo(
    val displayName: String,
    val photoUri: String = "",
    val matchedContact: ContactEntity? = null
)

/**
 * Resolves caller contact names and photos reliably across international/national phone number
 * formats (e.g., E.164 "+8801712345678" vs local "01712-345678" or "(555) 123-4567") by combining
 * Android's native ContactsContract.PhoneLookup index with normalized Room contact matching.
 */
object CallerContactResolver {

    fun cleanNumber(rawNumber: String): String {
        return rawNumber
            .trim()
            .removePrefix("tel:")
            .removePrefix("TEL:")
            .trim()
    }

    fun isUsableNumber(rawNumber: String): Boolean {
        val cleaned = cleanNumber(rawNumber)
        if (cleaned.isBlank()) return false
        if (cleaned.equals("Unknown", ignoreCase = true)) return false
        if (cleaned.equals("Private", ignoreCase = true)) return false
        return cleaned.any { it.isDigit() }
    }

    fun isLikelyPhoneNumber(candidate: String, referenceNumber: String): Boolean {
        val trimmed = candidate.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.equals("Unknown", ignoreCase = true)) return true
        if (trimmed.none { it.isLetter() } && phoneNumbersMatch(trimmed, referenceNumber)) {
            return true
        }
        return false
    }

    /**
     * Compares two phone numbers tolerantly across formatting, country codes, and trunk zeros.
     */
    fun phoneNumbersMatch(a: String, b: String): Boolean {
        val cleanA = cleanNumber(a)
        val cleanB = cleanNumber(b)
        if (cleanA.isBlank() || cleanB.isBlank()) return false
        if (cleanA.equals(cleanB, ignoreCase = true)) return true

        val digitsA = cleanA.filter { it.isDigit() }
        val digitsB = cleanB.filter { it.isDigit() }
        if (digitsA.isEmpty() || digitsB.isEmpty()) return false
        if (digitsA == digitsB) return true

        @Suppress("DEPRECATION")
        if (runCatching { PhoneNumberUtils.compare(cleanA, cleanB) }.getOrDefault(false)) {
            return true
        }

        val strippedA = digitsA.trimStart('0')
        val strippedB = digitsB.trimStart('0')
        if (strippedA.length >= 7 && strippedA == strippedB) {
            return true
        }

        if (strippedA.length >= 8 && strippedB.length >= 8 &&
            (strippedA.endsWith(strippedB) || strippedB.endsWith(strippedA))
        ) {
            return true
        }

        // Handles common saved typos like "+880 01712345678" vs carrier "+8801712345678"
        if (digitsA.length >= 10 && digitsB.length >= 10 &&
            digitsA.takeLast(10) == digitsB.takeLast(10)
        ) {
            return true
        }

        return false
    }

    fun findMatchingContact(rawNumber: String, contacts: List<ContactEntity>): ContactEntity? {
        if (!isUsableNumber(rawNumber) || contacts.isEmpty()) return null
        val cleaned = cleanNumber(rawNumber)
        val targetDigits = cleaned.filter { it.isDigit() }

        // Pass 1: exact string or exact digits match
        contacts.firstOrNull { contact ->
            contact.displayName.isNotBlank() && (
                contact.phoneNumber.trim() == cleaned ||
                    (targetDigits.isNotEmpty() && contact.phoneNumber.filter { it.isDigit() } == targetDigits)
                )
        }?.let { return it }

        // Pass 2: normalized international / trunk-prefix match
        return contacts.firstOrNull { contact ->
            contact.displayName.isNotBlank() && phoneNumbersMatch(cleaned, contact.phoneNumber)
        }
    }

    /**
     * Queries Android's system ContactsContract.PhoneLookup directly. Works immediately even if
     * the app was cold-started by an incoming call before Room sync finished, and matches
     * secondary numbers stored on multi-number device contacts.
     */
    fun lookupSystemContact(context: Context, rawNumber: String): ResolvedCallerInfo? {
        if (!isUsableNumber(rawNumber)) return null
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactsPermission) return null

        val cleaned = cleanNumber(rawNumber)
        val digits = cleaned.filter { it.isDigit() }
        val candidates = buildList {
            add(cleaned)
            if (cleaned.startsWith("00") && cleaned.length > 4) {
                add("+" + cleaned.removePrefix("00"))
            }
            if (digits.length > 10) {
                val last10 = digits.takeLast(10)
                add(last10)
                add("0$last10")
            }
        }.distinct()

        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        )

        for (candidate in candidates) {
            val result = runCatching {
                val lookupUri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(candidate)
                )
                context.contentResolver.query(
                    lookupUri,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        val photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                        val thumbIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                        val photo = if (photoIdx >= 0) cursor.getString(photoIdx)?.trim().orEmpty() else ""
                        val thumb = if (thumbIdx >= 0) cursor.getString(thumbIdx)?.trim().orEmpty() else ""
                        if (name.isNotBlank()) {
                            ResolvedCallerInfo(
                                displayName = name,
                                photoUri = photo.ifBlank { thumb }
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            }.getOrNull()

            if (result != null) return result
        }
        return null
    }

    /**
     * Full IO resolution combining live Android ContactsContract.PhoneLookup and VoxLine's local
     * Room contact database.
     */
    suspend fun resolve(
        context: Context,
        rawNumber: String,
        roomContacts: List<ContactEntity>? = null
    ): ResolvedCallerInfo? = withContext(Dispatchers.IO) {
        if (!isUsableNumber(rawNumber)) return@withContext null
        val cleaned = cleanNumber(rawNumber)

        val systemMatch = lookupSystemContact(context, cleaned)
        val localSnapshot = roomContacts ?: runCatching {
            VoxLineDatabase.getInstance(context.applicationContext).contactDao().getAllSnapshot()
        }.getOrDefault(emptyList())
        val roomMatch = findMatchingContact(cleaned, localSnapshot)

        val resolvedName = systemMatch?.displayName?.ifBlank { roomMatch?.displayName.orEmpty() }
            ?: roomMatch?.displayName.orEmpty()
        val resolvedPhoto = systemMatch?.photoUri?.ifBlank { roomMatch?.photoUri.orEmpty() }
            ?: roomMatch?.photoUri.orEmpty()

        if (resolvedName.isBlank() && roomMatch == null) {
            return@withContext null
        }

        ResolvedCallerInfo(
            displayName = resolvedName,
            photoUri = resolvedPhoto,
            matchedContact = roomMatch
        )
    }
}
