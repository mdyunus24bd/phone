package com.example.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import com.example.data.local.CallContactInfo
import com.example.data.local.CallDirection
import com.example.data.local.CallForwardingRuleEntity
import com.example.data.local.CallHistoryWithContact
import com.example.data.local.CallLogEntity
import com.example.data.local.CallRecordingEntity
import com.example.data.local.CallType
import com.example.data.local.CallTypeSummary
import com.example.data.local.ContactEntity
import com.example.data.local.SpamRuleEntity
import com.example.data.local.VoxLineDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.VoxLineSettings
import com.example.security.CryptoVault
import com.example.telecom.SpamAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class SyncBundleSummary(
    val contactsCount: Int,
    val callLogsCount: Int,
    val spamRulesCount: Int,
    val checksumShort: String
)

data class GoogleContactAccount(
    val accountName: String,
    val accountType: String = GOOGLE_ACCOUNT_TYPE
) {
    companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

data class GoogleAccountSaveResult(
    val savedToGoogleAccount: Boolean,
    val accountName: String,
    val deviceContactId: Long = 0L,
    val openedSystemFallback: Boolean = false,
    val message: String
)

class VoxLineRepository(
    private val context: Context,
    private val db: VoxLineDatabase,
    val cryptoVault: CryptoVault,
    val prefs: UserPreferencesRepository
) {
    val contactsFlow: Flow<List<ContactEntity>> = db.contactDao().observeAll()
    val callLogsFlow: Flow<List<CallLogEntity>> = db.callHistoryDao().observeAll()
    val callHistoryWithContactsFlow: Flow<List<CallHistoryWithContact>> =
        db.callHistoryDao().observeAllWithContact()
    val callTypeSummariesFlow: Flow<List<CallTypeSummary>> =
        db.callHistoryDao().observeSummaryByCallType()
    val forwardingRulesFlow: Flow<List<CallForwardingRuleEntity>> = db.forwardingRuleDao().observeAll()
    val spamRulesFlow: Flow<List<SpamRuleEntity>> = db.spamRuleDao().observeAll()
    val recordingsFlow: Flow<List<CallRecordingEntity>> = db.callRecordingDao().observeAll()
    val settingsFlow: Flow<VoxLineSettings> = prefs.settingsFlow

    suspend fun ensureDefaultForwardingTemplates() {
        if (db.forwardingRuleDao().count() == 0) {
            db.forwardingRuleDao().upsertAll(
                listOf(
                    CallForwardingRuleEntity(
                        conditionKey = "ALWAYS",
                        title = "Always Forward",
                        activatePrefix = "*21*",
                        deactivateCode = "#21#"
                    ),
                    CallForwardingRuleEntity(
                        conditionKey = "BUSY",
                        title = "When Busy",
                        activatePrefix = "*67*",
                        deactivateCode = "#67#"
                    ),
                    CallForwardingRuleEntity(
                        conditionKey = "UNANSWERED",
                        title = "When Unanswered",
                        activatePrefix = "*61*",
                        deactivateCode = "#61#"
                    ),
                    CallForwardingRuleEntity(
                        conditionKey = "UNREACHABLE",
                        title = "When Unreachable",
                        activatePrefix = "*62*",
                        deactivateCode = "#62#"
                    )
                )
            )
        }
    }

    suspend fun upsertContact(
        id: Long,
        displayName: String,
        phoneNumber: String,
        label: String,
        favorite: Boolean,
        plainNotes: String,
        avatarColorIndex: Int,
        photoUri: String = "",
        googleAccountName: String = "",
        deviceContactId: Long = 0L
    ): ContactEntity {
        val encrypted = if (plainNotes.isNotBlank()) cryptoVault.encryptString(plainNotes) else ""
        val cleanName = displayName.trim()
        val cleanPhone = phoneNumber.trim()
        val cleanLabel = label.ifBlank { "Mobile" }
        val entity = ContactEntity(
            id = id,
            displayName = cleanName,
            phoneNumber = cleanPhone,
            label = cleanLabel,
            favorite = favorite,
            encryptedNotes = encrypted,
            photoUri = photoUri.trim(),
            googleAccountName = googleAccountName.trim(),
            deviceContactId = deviceContactId,
            avatarColorIndex = avatarColorIndex,
            updatedAt = System.currentTimeMillis()
        )
        val savedId = db.contactDao().upsert(entity)
        val resolvedId = if (id > 0L) id else savedId
        val savedEntity = entity.copy(id = resolvedId)
        backfillCallLogsForContact(savedEntity)
        return savedEntity
    }

    /**
     * Links existing recent call history entries to a newly saved or updated contact
     * so Recents immediately display the contact's name, label, and photo.
     */
    private suspend fun backfillCallLogsForContact(contact: ContactEntity) = withContext(Dispatchers.IO) {
        val targetDigits = contact.phoneNumber.filter { it.isDigit() }
        if (targetDigits.isEmpty()) return@withContext
        val allLogs = db.callHistoryDao().getAllSnapshot()
        allLogs.forEach { log ->
            val logDigits = log.phoneNumber.filter { it.isDigit() }
            val phoneMatch = logDigits.isNotEmpty() && (
                logDigits == targetDigits ||
                    (targetDigits.length >= 7 && logDigits.endsWith(targetDigits)) ||
                    (logDigits.length >= 7 && targetDigits.endsWith(logDigits))
                )
            if (phoneMatch) {
                db.callHistoryDao().update(
                    log.copy(
                        displayName = contact.displayName,
                        contactInfo = CallContactInfo(
                            contactId = contact.id,
                            name = contact.displayName,
                            phoneNumber = contact.phoneNumber.ifBlank { log.phoneNumber },
                            label = contact.label
                        )
                    )
                )
            }
        }
    }

    /**
     * Discovers Google Accounts configured for Contacts sync on this Android device
     * via ContactsContract (requires only READ_CONTACTS, no GET_ACCOUNTS permission).
     */
    @SuppressLint("Range")
    suspend fun detectGoogleAccounts(): List<GoogleContactAccount> = withContext(Dispatchers.IO) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return@withContext emptyList()

        val found = linkedSetOf<String>()

        // Source 1: ContactsContract.Settings (includes Google accounts even before contacts exist)
        runCatching {
            context.contentResolver.query(
                ContactsContract.Settings.CONTENT_URI,
                arrayOf(ContactsContract.Settings.ACCOUNT_NAME, ContactsContract.Settings.ACCOUNT_TYPE),
                "${ContactsContract.Settings.ACCOUNT_TYPE} = ?",
                arrayOf(GoogleContactAccount.GOOGLE_ACCOUNT_TYPE),
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.Settings.ACCOUNT_NAME)
                if (nameIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val acc = cursor.getString(nameIdx)?.trim().orEmpty()
                        if (acc.isNotBlank()) found.add(acc)
                    }
                }
            }
        }

        // Source 2: ContactsContract.RawContacts (all Google accounts owning at least one contact)
        runCatching {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE),
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
                arrayOf(GoogleContactAccount.GOOGLE_ACCOUNT_TYPE),
                null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                if (nameIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val acc = cursor.getString(nameIdx)?.trim().orEmpty()
                        if (acc.isNotBlank()) found.add(acc)
                    }
                }
            }
        }

        found.map { GoogleContactAccount(accountName = it) }
    }

    /**
     * Copies a picked photo URI (from Android Photo Picker) into app-private storage,
     * downscaling to a crisp 512px square-bounded JPEG so it persists across restarts.
     */
    suspend fun persistContactPhoto(sourceUriString: String): String = withContext(Dispatchers.IO) {
        val trimmed = sourceUriString.trim()
        if (trimmed.isEmpty()) return@withContext ""
        if (trimmed.startsWith("file://")) return@withContext trimmed

        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return@withContext trimmed
        return@withContext try {
            val bitmap = decodeScaledBitmap(parsed, maxDim = 512) ?: return@withContext trimmed
            val dir = File(context.filesDir, "contact_photos").apply { mkdirs() }
            val outFile = File(dir, "contact_${UUID.randomUUID()}.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, fos)
                fos.flush()
            }
            bitmap.recycle()
            Uri.fromFile(outFile).toString()
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun decodeScaledBitmap(uri: Uri, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDim * 2 || bounds.outHeight / sample > maxDim * 2) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        val maxSide = maxOf(decoded.width, decoded.height)
        if (maxSide <= maxDim) return decoded
        val scale = maxDim.toFloat() / maxSide.toFloat()
        val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
        val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
        if (scaled != decoded) decoded.recycle()
        return scaled
    }

    private fun loadContactPhotoJpegBytes(photoUriString: String): ByteArray? {
        if (photoUriString.isBlank()) return null
        return try {
            val uri = Uri.parse(photoUriString)
            val bitmap = decodeScaledBitmap(uri, maxDim = 360) ?: return null
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            bitmap.recycle()
            baos.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Writes a contact (including Name, SIM Number, Favorite flag, and Photo)
     * directly into the user's selected Google Account via Android ContactsContract
     * so Android Google Contacts Sync uploads it to their Google Account.
     */
    suspend fun saveContactToGoogleAccount(
        displayName: String,
        phoneNumber: String,
        favorite: Boolean,
        photoUri: String,
        googleAccountName: String
    ): GoogleAccountSaveResult = withContext(Dispatchers.IO) {
        val hasWrite = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasWrite) {
            return@withContext GoogleAccountSaveResult(
                savedToGoogleAccount = false,
                accountName = googleAccountName,
                message = "Contacts write permission required to save directly to Google Account."
            )
        }
        if (googleAccountName.isBlank()) {
            val opened = openSystemGoogleContactsInsert(displayName, phoneNumber)
            return@withContext GoogleAccountSaveResult(
                savedToGoogleAccount = false,
                accountName = "",
                openedSystemFallback = opened,
                message = if (opened) {
                    "Opened Android Contacts to finish saving to your Google Account."
                } else {
                    "No Google Account detected on device. Saved locally in VoxLine."
                }
            )
        }

        return@withContext try {
            val ops = ArrayList<ContentProviderOperation>()
            val rawContactInsertIndex = ops.size

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, GoogleContactAccount.GOOGLE_ACCOUNT_TYPE)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, googleAccountName)
                    .withValue(ContactsContract.RawContacts.STARRED, if (favorite) 1 else 0)
                    .build()
            )

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(
                        ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                        displayName.trim()
                    )
                    .build()
            )

            if (phoneNumber.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber.trim())
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                        )
                        .build()
                )
            }

            val photoBytes = loadContactPhotoJpegBytes(photoUri)
            if (photoBytes != null && photoBytes.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                        .build()
                )
            }

            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val rawUri = results.firstOrNull()?.uri
            val rawId = rawUri?.let { runCatching { ContentUris.parseId(it) }.getOrDefault(0L) } ?: 0L
            val contactId = resolveContactIdFromRawId(rawId)

            GoogleAccountSaveResult(
                savedToGoogleAccount = true,
                accountName = googleAccountName,
                deviceContactId = contactId,
                message = "Saved to Google Account ($googleAccountName)"
            )
        } catch (e: Exception) {
            GoogleAccountSaveResult(
                savedToGoogleAccount = false,
                accountName = googleAccountName,
                message = "Saved in VoxLine (Google sync error: ${e.localizedMessage ?: "unknown"})"
            )
        }
    }

    private fun resolveContactIdFromRawId(rawContactId: Long): Long {
        if (rawContactId <= 0L) return 0L
        return runCatching {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                "${ContactsContract.RawContacts._ID} = ?",
                arrayOf(rawContactId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    /**
     * Zero-permission fallback launching Android's native Contacts / Google Contacts
     * insertion screen pre-filled with the caller's details.
     */
    fun openSystemGoogleContactsInsert(
        displayName: String,
        phoneNumber: String
    ): Boolean {
        return runCatching {
            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                if (displayName.isNotBlank()) {
                    putExtra(ContactsContract.Intents.Insert.NAME, displayName.trim())
                }
                if (phoneNumber.isNotBlank()) {
                    putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber.trim())
                    putExtra(
                        ContactsContract.Intents.Insert.PHONE_TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Opens Android Settings -> Add Google Account so users can sign in to a Google Account
     * on devices that do not have one configured yet.
     */
    fun openAddGoogleAccountSettings(): Boolean {
        val googleIntent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
            putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf(GoogleContactAccount.GOOGLE_ACCOUNT_TYPE))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(googleIntent) }.isSuccess) return true
        val fallback = Intent(Settings.ACTION_SYNC_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(fallback) }.isSuccess
    }

    fun decryptNote(encryptedNotes: String): String = cryptoVault.decryptString(encryptedNotes)

    suspend fun toggleFavorite(contact: ContactEntity) {
        db.contactDao().upsert(contact.copy(favorite = !contact.favorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteContact(contact: ContactEntity) = db.contactDao().delete(contact)

    suspend fun findContactByAddress(address: String): ContactEntity? = db.contactDao().findByAddress(address)

    fun observeCallLogsByType(callType: CallType): Flow<List<CallLogEntity>> =
        db.callHistoryDao().observeByCallType(callType)

    fun searchCallHistory(query: String): Flow<List<CallLogEntity>> =
        db.callHistoryDao().searchHistory(query)

    suspend fun insertCallLog(entry: CallLogEntity): Long = db.callHistoryDao().insert(entry)

    suspend fun updateCallLog(entry: CallLogEntity) = db.callHistoryDao().update(entry)

    suspend fun updateCallLogNote(id: Long, note: String) =
        db.callHistoryDao().updateQuickNote(id, note.trim())

    suspend fun deleteCallLog(id: Long) = db.callHistoryDao().deleteById(id)

    suspend fun insertRecording(recording: CallRecordingEntity): Long = db.callRecordingDao().insert(recording)

    suspend fun getRecordingById(id: Long): CallRecordingEntity? = db.callRecordingDao().getById(id)

    suspend fun deleteRecording(recording: CallRecordingEntity) = db.callRecordingDao().delete(recording)

    suspend fun updateForwardingRule(rule: CallForwardingRuleEntity) {
        db.forwardingRuleDao().upsert(rule.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun disableAllForwardingRules() {
        db.forwardingRuleDao().disableAll(System.currentTimeMillis())
    }

    suspend fun addSpamRule(pattern: String, label: String, riskScore: Int, autoBlock: Boolean) {
        db.spamRuleDao().upsert(
            SpamRuleEntity(
                pattern = pattern.trim(),
                label = label.trim().ifBlank { "Blocked Caller" },
                riskScore = riskScore.coerceIn(50, 100),
                autoBlock = autoBlock
            )
        )
    }

    suspend fun deleteSpamRule(rule: SpamRuleEntity) = db.spamRuleDao().delete(rule)

    suspend fun incrementSpamRuleMatch(id: Long) = db.spamRuleDao().incrementMatches(id)

    /**
     * Adds [phoneNumber] to the auto-reject Blocked Numbers list if it is not already blocked.
     * Returns true if added, or false if blank or already matched by an existing auto-block rule.
     */
    suspend fun blockNumber(phoneNumber: String, label: String): Boolean {
        val clean = phoneNumber.trim()
        if (clean.isEmpty()) return false
        val existingRules = db.spamRuleDao().getAllSnapshot()
        if (SpamAnalyzer.findBlockingRule(clean, existingRules) != null) {
            return false
        }
        db.spamRuleDao().upsert(
            SpamRuleEntity(
                pattern = clean,
                label = label.trim().ifBlank { "Blocked Number" },
                riskScore = 100,
                autoBlock = true
            )
        )
        return true
    }

    suspend fun clearAllBlockedNumbers() = db.spamRuleDao().deleteAllAutoBlocked()

    /**
     * Auto-imports and synchronizes real contacts from the Android device's ContactsContract
     * provider (phone numbers, favorites, labels, and photos).
     */
    @SuppressLint("Range")
    suspend fun importDeviceContacts(): Result<Int> = withContext(Dispatchers.IO) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return@withContext Result.failure(SecurityException("READ_CONTACTS permission required"))
        }
        return@withContext try {
            val existingByDigits = db.contactDao().getAllSnapshot()
                .mapNotNull { contact ->
                    val digits = contact.phoneNumber.filter { it.isDigit() }
                    if (digits.isNotEmpty()) digits to contact else null
                }
                .toMap()
                .toMutableMap()

            // Detect Google Account ownership per Android CONTACT_ID
            val googleAccountByContactId = mutableMapOf<Long, String>()
            runCatching {
                context.contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(
                        ContactsContract.RawContacts.CONTACT_ID,
                        ContactsContract.RawContacts.ACCOUNT_NAME,
                        ContactsContract.RawContacts.ACCOUNT_TYPE
                    ),
                    "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
                    arrayOf(GoogleContactAccount.GOOGLE_ACCOUNT_TYPE),
                    null
                )?.use { rawCursor ->
                    val cidIdx = rawCursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
                    val accIdx = rawCursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
                    if (cidIdx >= 0 && accIdx >= 0) {
                        while (rawCursor.moveToNext()) {
                            val cid = rawCursor.getLong(cidIdx)
                            val accName = rawCursor.getString(accIdx)?.trim().orEmpty()
                            if (cid > 0L && accName.isNotBlank() && !googleAccountByContactId.containsKey(cid)) {
                                googleAccountByContactId[cid] = accName
                            }
                        }
                    }
                }
            }

            val toUpsert = mutableListOf<ContactEntity>()
            val seenDigitsInPass = mutableSetOf<String>()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.STARRED,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val contactIdIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val starredIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                val photoUriIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val thumbUriIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                while (cursor.moveToNext()) {
                    val contactId = if (contactIdIdx >= 0) cursor.getLong(contactIdIdx) else 0L
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val num = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    val isStarred = if (starredIdx >= 0) cursor.getInt(starredIdx) == 1 else false
                    val rawPhoto = if (photoUriIdx >= 0) cursor.getString(photoUriIdx)?.trim().orEmpty() else ""
                    val rawThumb = if (thumbUriIdx >= 0) cursor.getString(thumbUriIdx)?.trim().orEmpty() else ""
                    val devicePhoto = rawPhoto.ifBlank { rawThumb }
                    val digits = num.filter { it.isDigit() }
                    if (name.isEmpty() || digits.isEmpty() || !seenDigitsInPass.add(digits)) continue

                    val googleAcc = googleAccountByContactId[contactId].orEmpty()
                    val existing = existingByDigits[digits]
                    if (existing == null) {
                        val entity = ContactEntity(
                            displayName = name,
                            phoneNumber = num,
                            label = if (googleAcc.isNotBlank()) "Google" else "Device",
                            favorite = isStarred,
                            photoUri = devicePhoto,
                            googleAccountName = googleAcc,
                            deviceContactId = contactId,
                            avatarColorIndex = kotlin.math.abs(name.hashCode()) % 5,
                            updatedAt = System.currentTimeMillis()
                        )
                        toUpsert.add(entity)
                        existingByDigits[digits] = entity
                    } else {
                        val resolvedPhoto = existing.photoUri.ifBlank { devicePhoto }
                        val resolvedGoogle = existing.googleAccountName.ifBlank { googleAcc }
                        val resolvedDeviceId = if (existing.deviceContactId > 0L) existing.deviceContactId else contactId
                        val shouldUpdateName = (existing.label == "Device" || existing.label == "Google") &&
                            existing.displayName != name
                        val needsMetaSync = resolvedPhoto != existing.photoUri ||
                            resolvedGoogle != existing.googleAccountName ||
                            resolvedDeviceId != existing.deviceContactId
                        if (shouldUpdateName || needsMetaSync) {
                            toUpsert.add(
                                existing.copy(
                                    displayName = if (shouldUpdateName) name else existing.displayName,
                                    photoUri = resolvedPhoto,
                                    googleAccountName = resolvedGoogle,
                                    deviceContactId = resolvedDeviceId,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }
            if (toUpsert.isNotEmpty()) {
                db.contactDao().upsertAll(toUpsert)
            }
            Result.success(toUpsert.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Auto-imports and synchronizes recent call logs from Android's system CallLog.Calls
     * content provider, resolving caller names against imported contacts.
     */
    @SuppressLint("MissingPermission")
    suspend fun syncSystemCallLogs(
        deviceTag: String,
        maxItems: Int = 500
    ): Result<Int> = withContext(Dispatchers.IO) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return@withContext Result.failure(SecurityException("READ_CALL_LOG permission required"))
        }
        return@withContext try {
            val existingByKey = db.callHistoryDao().getAllSnapshot()
                .associateBy { "${it.phoneNumber}:${it.timestamp}" }
            val existingKeys = existingByKey.keys.toMutableSet()

            val contactsByDigits = db.contactDao().getAllSnapshot()
                .mapNotNull { c ->
                    val d = c.phoneNumber.filter { it.isDigit() }
                    if (d.isNotEmpty()) d to c else null
                }
                .toMap()

            val simLookup = com.example.telecom.TelecomBridge(context).buildSimAccountLookup()
            val imported = mutableListOf<CallLogEntity>()
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.PHONE_ACCOUNT_ID
            )
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val accountIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                var scanned = 0
                while (cursor.moveToNext() && scanned < maxItems) {
                    scanned++
                    val number = if (numIdx >= 0) cursor.getString(numIdx)?.trim().orEmpty() else ""
                    val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx)?.trim().orEmpty() else ""
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
                    val duration = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                    val phoneAccountId = if (accountIdx >= 0) cursor.getString(accountIdx)?.trim().orEmpty() else ""
                    val resolvedSimLabel = simLookup.resolve(accountId = phoneAccountId)
                    val key = "$number:$date"
                    val existing = existingByKey[key]
                    if (existing != null) {
                        val cleanedExisting = com.example.telecom.extractSimCompanyName(existing.routeLabel)
                        val nextRouteLabel = resolvedSimLabel.ifBlank { cleanedExisting }
                        if (existing.routeLabel != nextRouteLabel) {
                            db.callHistoryDao().updateRouteLabel(existing.id, nextRouteLabel)
                        }
                    } else if (number.isNotEmpty() && existingKeys.add(key)) {
                        val dir = when (type) {
                            CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
                            CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
                            CallLog.Calls.MISSED_TYPE -> CallDirection.MISSED
                            CallLog.Calls.BLOCKED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallDirection.BLOCKED
                            else -> CallDirection.INCOMING
                        }
                        val digits = number.filter { it.isDigit() }
                        val matchedContact = contactsByDigits[digits]
                            ?: db.contactDao().findByAddress(number)
                        val resolvedName = matchedContact?.displayName?.ifBlank { cachedName } ?: cachedName
                        imported.add(
                            CallLogEntity(
                                phoneNumber = number,
                                displayName = resolvedName,
                                callType = CallType.SIM,
                                routeLabel = resolvedSimLabel,
                                direction = dir,
                                timestamp = date,
                                durationSec = duration,
                                contactInfo = CallContactInfo(
                                    contactId = matchedContact?.id,
                                    name = resolvedName,
                                    phoneNumber = number,
                                    label = matchedContact?.label.orEmpty()
                                ),
                                codec = "Cellular",
                                deviceTag = deviceTag
                            )
                        )
                    }
                }
            }
            if (imported.isNotEmpty()) {
                db.callHistoryDao().insertAll(imported)
            }
            Result.success(imported.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports Contacts, Call Logs, and Spam Rules into a portable
     * AES-256-GCM encrypted cross-device sync bundle via Storage Access Framework.
     */
    suspend fun exportEncryptedSyncBundle(
        targetUri: Uri,
        passphrase: String,
        deviceName: String
    ): Result<SyncBundleSummary> = withContext(Dispatchers.IO) {
        return@withContext try {
            require(passphrase.length >= 6) { "Passphrase must be at least 6 characters." }
            val contacts = db.contactDao().getAllSnapshot()
            val logs = db.callHistoryDao().getAllSnapshot()
            val spamRules = db.spamRuleDao().getAllSnapshot()

            val root = JSONObject().apply {
                put("version", 3)
                put("exportedAt", System.currentTimeMillis())
                put("sourceDevice", deviceName)
                put("contacts", JSONArray().apply {
                    contacts.forEach { c ->
                        put(JSONObject().apply {
                            put("displayName", c.displayName)
                            put("phoneNumber", c.phoneNumber)
                            put("label", c.label)
                            put("favorite", c.favorite)
                            put("notes", cryptoVault.decryptString(c.encryptedNotes))
                            put("googleAccountName", c.googleAccountName)
                            put("avatarColorIndex", c.avatarColorIndex)
                        })
                    }
                })
                put("callLogs", JSONArray().apply {
                    logs.forEach { l ->
                        put(JSONObject().apply {
                            put("phoneNumber", l.phoneNumber)
                            put("displayName", l.resolvedContactName)
                            put("contactLabel", l.contactInfo.label)
                            put("networkType", l.callType.name)
                            put("routeLabel", l.routeLabel)
                            put("direction", l.direction.name)
                            put("timestamp", l.timestamp)
                            put("durationSec", l.durationSec)
                            put("mosScore", l.mosScore.toDouble())
                            put("avgLatencyMs", l.avgLatencyMs)
                            put("avgJitterMs", l.avgJitterMs)
                            put("packetLossPct", l.packetLossPct.toDouble())
                            put("codec", l.codec)
                            put("encryptedTransport", l.encryptedTransport)
                            put("spamScore", l.spamScore)
                            put("spamReason", l.spamReason)
                            put("deviceTag", l.deviceTag.ifBlank { deviceName })
                            put("quickNote", l.quickNote)
                        })
                    }
                })
                put("spamRules", JSONArray().apply {
                    spamRules.forEach { s ->
                        put(JSONObject().apply {
                            put("pattern", s.pattern)
                            put("label", s.label)
                            put("riskScore", s.riskScore)
                            put("autoBlock", s.autoBlock)
                        })
                    }
                })
            }

            val encryptedText = cryptoVault.encryptPortableBundle(root.toString(), passphrase)
            val checksum = cryptoVault.sha256Hex(encryptedText.toByteArray(Charsets.UTF_8)).take(12).uppercase()
            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.write(encryptedText.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@withContext Result.failure(IllegalStateException("Unable to open export file"))

            prefs.recordSyncCompletion(System.currentTimeMillis(), checksum)
            Result.success(
                SyncBundleSummary(
                    contactsCount = contacts.size,
                    callLogsCount = logs.size,
                    spamRulesCount = spamRules.size,
                    checksumShort = checksum
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Decrypts and merges a portable AES-256-GCM encrypted sync bundle from another device.
     */
    suspend fun importEncryptedSyncBundle(
        sourceUri: Uri,
        passphrase: String
    ): Result<SyncBundleSummary> = withContext(Dispatchers.IO) {
        return@withContext try {
            val raw = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use {
                it.readText()
            } ?: return@withContext Result.failure(IllegalStateException("Unable to read sync file"))

            val jsonText = cryptoVault.decryptPortableBundle(raw.trim(), passphrase)
            val root = JSONObject(jsonText)

            val existingContacts = db.contactDao().getAllSnapshot()
                .map { "${it.displayName}|${it.phoneNumber}" }.toMutableSet()
            val newContacts = mutableListOf<ContactEntity>()
            val cArr = root.optJSONArray("contacts") ?: JSONArray()
            for (i in 0 until cArr.length()) {
                val o = cArr.getJSONObject(i)
                val key = "${o.optString("displayName")}|${o.optString("phoneNumber")}"
                if (existingContacts.add(key)) {
                    val plainNote = o.optString("notes", "")
                    newContacts.add(
                        ContactEntity(
                            displayName = o.optString("displayName"),
                            phoneNumber = o.optString("phoneNumber"),
                            label = o.optString("label", "Synced"),
                            favorite = o.optBoolean("favorite", false),
                            encryptedNotes = if (plainNote.isNotBlank()) cryptoVault.encryptString(plainNote) else "",
                            googleAccountName = o.optString("googleAccountName", ""),
                            avatarColorIndex = o.optInt("avatarColorIndex", 0)
                        )
                    )
                }
            }
            if (newContacts.isNotEmpty()) db.contactDao().upsertAll(newContacts)

            val existingLogs = db.callHistoryDao().getAllSnapshot()
                .map { "${it.phoneNumber}:${it.timestamp}" }.toMutableSet()
            val newLogs = mutableListOf<CallLogEntity>()
            val lArr = root.optJSONArray("callLogs") ?: JSONArray()
            for (i in 0 until lArr.length()) {
                val o = lArr.getJSONObject(i)
                val ts = o.optLong("timestamp", System.currentTimeMillis())
                val num = o.optString("phoneNumber")
                val name = o.optString("displayName")
                if (existingLogs.add("$num:$ts")) {
                    newLogs.add(
                        CallLogEntity(
                            phoneNumber = num,
                            displayName = name,
                            callType = CallType.SIM,
                            routeLabel = com.example.telecom.extractSimCompanyName(o.optString("routeLabel", "")),
                            direction = CallDirection.fromRaw(o.optString("direction", "OUTGOING")),
                            timestamp = ts,
                            durationSec = o.optLong("durationSec", 0L),
                            contactInfo = CallContactInfo(
                                name = name,
                                phoneNumber = num,
                                label = o.optString("contactLabel", "")
                            ),
                            mosScore = o.optDouble("mosScore", 0.0).toFloat(),
                            avgLatencyMs = o.optInt("avgLatencyMs", 0),
                            avgJitterMs = o.optInt("avgJitterMs", 0),
                            packetLossPct = o.optDouble("packetLossPct", 0.0).toFloat(),
                            codec = o.optString("codec", ""),
                            encryptedTransport = o.optBoolean("encryptedTransport", false),
                            spamScore = o.optInt("spamScore", 0),
                            spamReason = o.optString("spamReason", ""),
                            deviceTag = o.optString("deviceTag", "Synced Device"),
                            quickNote = o.optString("quickNote", "")
                        )
                    )
                }
            }
            if (newLogs.isNotEmpty()) db.callHistoryDao().insertAll(newLogs)

            val existingSpam = db.spamRuleDao().getAllSnapshot().map { it.pattern }.toMutableSet()
            val newSpam = mutableListOf<SpamRuleEntity>()
            val sArr = root.optJSONArray("spamRules") ?: JSONArray()
            for (i in 0 until sArr.length()) {
                val o = sArr.getJSONObject(i)
                val pat = o.optString("pattern")
                if (pat.isNotBlank() && existingSpam.add(pat)) {
                    newSpam.add(
                        SpamRuleEntity(
                            pattern = pat,
                            label = o.optString("label", "Synced Rule"),
                            riskScore = o.optInt("riskScore", 90),
                            autoBlock = o.optBoolean("autoBlock", true)
                        )
                    )
                }
            }
            if (newSpam.isNotEmpty()) db.spamRuleDao().upsertAll(newSpam)

            val checksum = cryptoVault.sha256Hex(raw.toByteArray(Charsets.UTF_8)).take(12).uppercase()
            prefs.recordSyncCompletion(System.currentTimeMillis(), checksum)

            Result.success(
                SyncBundleSummary(
                    contactsCount = newContacts.size,
                    callLogsCount = newLogs.size,
                    spamRulesCount = newSpam.size,
                    checksumShort = checksum
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun wipeAllLocalData() {
        db.callHistoryDao().clearAll()
        db.contactDao().clearAll()
        db.spamRuleDao().clearAll()
        db.callRecordingDao().clearAll()
        db.forwardingRuleDao().disableAll(System.currentTimeMillis())
    }
}
