package com.example.telecom

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LinkedAppBrand(val displayName: String, val primaryColorArgb: Long) {
    WHATSAPP("WhatsApp", 0xFF25D366),
    WHATSAPP_BUSINESS("WhatsApp Business", 0xFF128C7E),
    IMO("imo", 0xFF1D9BF0),
    TELEGRAM("Telegram", 0xFF2AABEE),
    VIBER("Viber", 0xFF7360F2),
    SIGNAL("Signal", 0xFF3A76F0),
    GOOGLE_MEET("Google Meet", 0xFF00AC47),
    MESSENGER("Messenger", 0xFF0084FF),
    OTHER("App Account", 0xFF64748B)
}

enum class LinkedActionKind {
    MESSAGE,
    VOICE_CALL,
    VIDEO_CALL,
    PROFILE
}

data class LinkedAccountAction(
    val id: String,
    val dataId: Long,
    val appName: String,
    val packageName: String,
    val brand: LinkedAppBrand,
    val actionLabel: String,
    val actionKind: LinkedActionKind,
    val mimeType: String,
    val fallbackUri: String? = null
)

data class LinkedAccountGroup(
    val appName: String,
    val packageName: String,
    val brand: LinkedAppBrand,
    val isSyncedFromContacts: Boolean,
    val actions: List<LinkedAccountAction>
)

/**
 * Resolves real third-party linked messaging and calling accounts (WhatsApp, imo, Telegram,
 * Viber, Signal, Google Meet, Messenger, etc.) for a phone number / contact by reading
 * Android's ContactsContract.Data table and checking installed messaging apps declared in <queries>.
 */
object ContactLinkedAccountsResolver {

    private val STANDARD_CONTACT_MIMES = setOf(
        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Identity.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE,
        ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE
    )

    private data class KnownAppSpec(
        val packageName: String,
        val brand: LinkedAppBrand,
        val buildUri: (digits: String, e164: String) -> String?
    )

    private val KNOWN_APPS = listOf(
        KnownAppSpec(
            packageName = "com.whatsapp",
            brand = LinkedAppBrand.WHATSAPP,
            buildUri = { digits, _ -> if (digits.isNotEmpty()) "https://api.whatsapp.com/send?phone=$digits" else null }
        ),
        KnownAppSpec(
            packageName = "com.whatsapp.w4b",
            brand = LinkedAppBrand.WHATSAPP_BUSINESS,
            buildUri = { digits, _ -> if (digits.isNotEmpty()) "https://api.whatsapp.com/send?phone=$digits" else null }
        ),
        KnownAppSpec(
            packageName = "com.imo.android.imoim",
            brand = LinkedAppBrand.IMO,
            buildUri = { _, e164 -> if (e164.isNotEmpty()) "tel:$e164" else null }
        ),
        KnownAppSpec(
            packageName = "com.imo.android.imoimhd",
            brand = LinkedAppBrand.IMO,
            buildUri = { _, e164 -> if (e164.isNotEmpty()) "tel:$e164" else null }
        ),
        KnownAppSpec(
            packageName = "com.imo.android.imoimlite",
            brand = LinkedAppBrand.IMO,
            buildUri = { _, e164 -> if (e164.isNotEmpty()) "tel:$e164" else null }
        ),
        KnownAppSpec(
            packageName = "org.telegram.messenger",
            brand = LinkedAppBrand.TELEGRAM,
            buildUri = { digits, _ -> if (digits.isNotEmpty()) "https://t.me/+$digits" else null }
        ),
        KnownAppSpec(
            packageName = "org.telegram.messenger.web",
            brand = LinkedAppBrand.TELEGRAM,
            buildUri = { digits, _ -> if (digits.isNotEmpty()) "https://t.me/+$digits" else null }
        ),
        KnownAppSpec(
            packageName = "org.thunderdog.challegram",
            brand = LinkedAppBrand.TELEGRAM,
            buildUri = { digits, _ -> if (digits.isNotEmpty()) "https://t.me/+$digits" else null }
        ),
        KnownAppSpec(
            packageName = "com.viber.voip",
            brand = LinkedAppBrand.VIBER,
            buildUri = { digits, _ -> if (digits.isNotEmpty()) "viber://contact?number=%2B$digits" else null }
        ),
        KnownAppSpec(
            packageName = "org.thoughtcrime.securesms",
            brand = LinkedAppBrand.SIGNAL,
            buildUri = { digits, _ -> if (digits.isNotEmpty()) "https://signal.me/#p/+$digits" else null }
        )
    )

    suspend fun resolveLinkedAccounts(
        context: Context,
        phoneNumber: String,
        deviceContactId: Long = 0L
    ): List<LinkedAccountGroup> = withContext(Dispatchers.IO) {
        val cleanPhone = phoneNumber.trim()
        val digitsOnly = cleanPhone.filter { it.isDigit() }
        val e164Number = if (cleanPhone.startsWith("+")) {
            "+$digitsOnly"
        } else {
            digitsOnly
        }

        val syncedActionsByApp = linkedMapOf<String, MutableList<LinkedAccountAction>>()

        val hasReadContacts = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasReadContacts) {
            val contactIds = mutableSetOf<Long>()
            if (deviceContactId > 0L) {
                contactIds.add(deviceContactId)
            }
            if (cleanPhone.isNotBlank()) {
                lookupContactIdsByPhone(context, cleanPhone, contactIds)
            }

            if (contactIds.isNotEmpty()) {
                queryContactProviderAccounts(
                    context = context,
                    contactIds = contactIds,
                    cleanPhone = cleanPhone,
                    digitsOnly = digitsOnly,
                    sink = syncedActionsByApp
                )
            }
        }

        val groups = mutableListOf<LinkedAccountGroup>()
        val coveredBrands = mutableSetOf<LinkedAppBrand>()

        syncedActionsByApp.forEach { (_, actions) ->
            val first = actions.firstOrNull() ?: return@forEach
            val deduped = actions.distinctBy { "${it.actionKind}:${it.mimeType}" }
                .sortedBy { actionSortOrder(it.actionKind) }
            coveredBrands.add(first.brand)
            groups.add(
                LinkedAccountGroup(
                    appName = first.appName,
                    packageName = first.packageName,
                    brand = first.brand,
                    isSyncedFromContacts = true,
                    actions = deduped
                )
            )
        }

        // Also detect installed apps (e.g. WhatsApp, imo, Telegram, Viber, Signal) for unsaved/unsynced numbers
        if (digitsOnly.isNotEmpty()) {
            for (spec in KNOWN_APPS) {
                if (spec.brand in coveredBrands) continue
                if (!isPackageInstalled(context, spec.packageName)) continue
                coveredBrands.add(spec.brand)
                val uri = spec.buildUri(digitsOnly, e164Number)
                val appLabel = resolveInstalledAppName(context, spec.packageName, spec.brand.displayName)
                groups.add(
                    LinkedAccountGroup(
                        appName = appLabel,
                        packageName = spec.packageName,
                        brand = spec.brand,
                        isSyncedFromContacts = false,
                        actions = listOf(
                            LinkedAccountAction(
                                id = "installed_${spec.packageName}_msg",
                                dataId = 0L,
                                appName = appLabel,
                                packageName = spec.packageName,
                                brand = spec.brand,
                                actionLabel = "Open in $appLabel",
                                actionKind = LinkedActionKind.MESSAGE,
                                mimeType = "",
                                fallbackUri = uri
                            )
                        )
                    )
                )
            }
        }

        groups.sortedWith(
            compareByDescending<LinkedAccountGroup> { it.isSyncedFromContacts }
                .thenBy { brandPriority(it.brand) }
                .thenBy { it.appName.lowercase() }
        )
    }

    private fun lookupContactIdsByPhone(
        context: Context,
        phoneNumber: String,
        outIds: MutableSet<Long>
    ) {
        runCatching {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null,
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                while (cursor.moveToNext()) {
                    if (idIdx >= 0) {
                        val id = cursor.getLong(idIdx)
                        if (id > 0L) outIds.add(id)
                    }
                }
            }
        }
    }

    private fun queryContactProviderAccounts(
        context: Context,
        contactIds: Set<Long>,
        cleanPhone: String,
        digitsOnly: String,
        sink: MutableMap<String, MutableList<LinkedAccountAction>>
    ) {
        val placeholders = contactIds.joinToString(",") { "?" }
        val args = contactIds.map { it.toString() }.toTypedArray()
        val projection = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )

        runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                projection,
                "${ContactsContract.Data.CONTACT_ID} IN ($placeholders)",
                args,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data._ID)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val data2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
                val data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                val accTypeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)

                while (cursor.moveToNext()) {
                    val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx)?.trim().orEmpty() else ""
                    if (mime.isEmpty() || mime in STANDARD_CONTACT_MIMES) continue

                    val dataId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val data1 = if (data1Idx >= 0) cursor.getString(data1Idx)?.trim().orEmpty() else ""
                    val data2 = if (data2Idx >= 0) cursor.getString(data2Idx)?.trim().orEmpty() else ""
                    val data3 = if (data3Idx >= 0) cursor.getString(data3Idx)?.trim().orEmpty() else ""
                    val accountType = if (accTypeIdx >= 0) cursor.getString(accTypeIdx)?.trim().orEmpty() else ""

                    val parsed = classifyContactDataRow(
                        context = context,
                        dataId = dataId,
                        mimeType = mime,
                        accountType = accountType,
                        data1 = data1,
                        data2 = data2,
                        data3 = data3,
                        cleanPhone = cleanPhone,
                        digitsOnly = digitsOnly
                    ) ?: continue

                    val groupKey = "${parsed.brand.name}:${parsed.packageName}"
                    sink.getOrPut(groupKey) { mutableListOf() }.add(parsed)
                }
            }
        }
    }

    private fun classifyContactDataRow(
        context: Context,
        dataId: Long,
        mimeType: String,
        accountType: String,
        data1: String,
        data2: String,
        data3: String,
        cleanPhone: String,
        digitsOnly: String
    ): LinkedAccountAction? {
        val combined = "$mimeType $accountType $data2 $data3".lowercase()

        val (brand, pkg, fallback) = when {
            combined.contains("com.whatsapp.w4b") -> Triple(
                LinkedAppBrand.WHATSAPP_BUSINESS,
                "com.whatsapp.w4b",
                if (digitsOnly.isNotEmpty()) "https://api.whatsapp.com/send?phone=$digitsOnly" else null
            )
            combined.contains("whatsapp") -> Triple(
                LinkedAppBrand.WHATSAPP,
                "com.whatsapp",
                if (digitsOnly.isNotEmpty()) "https://api.whatsapp.com/send?phone=$digitsOnly" else null
            )
            combined.contains("com.imo.android.imoimhd") -> Triple(
                LinkedAppBrand.IMO,
                "com.imo.android.imoimhd",
                null
            )
            combined.contains("com.imo.android.imoimlite") -> Triple(
                LinkedAppBrand.IMO,
                "com.imo.android.imoimlite",
                null
            )
            combined.contains("imo.android") || combined.contains("imoim") || accountType.equals("com.imo.android.imoim", ignoreCase = true) -> Triple(
                LinkedAppBrand.IMO,
                "com.imo.android.imoim",
                null
            )
            combined.contains("telegram") || combined.contains("challegram") -> Triple(
                LinkedAppBrand.TELEGRAM,
                if (accountType.contains("challegram", ignoreCase = true)) "org.thunderdog.challegram" else "org.telegram.messenger",
                if (digitsOnly.isNotEmpty()) "https://t.me/+$digitsOnly" else null
            )
            combined.contains("viber") -> Triple(
                LinkedAppBrand.VIBER,
                "com.viber.voip",
                if (digitsOnly.isNotEmpty()) "viber://contact?number=%2B$digitsOnly" else null
            )
            combined.contains("securesms") || combined.contains("signal") -> Triple(
                LinkedAppBrand.SIGNAL,
                "org.thoughtcrime.securesms",
                if (digitsOnly.isNotEmpty()) "https://signal.me/#p/+$digitsOnly" else null
            )
            combined.contains("tachyon") || combined.contains("meetings") -> Triple(
                LinkedAppBrand.GOOGLE_MEET,
                "com.google.android.apps.tachyon",
                null
            )
            combined.contains("facebook.orca") || combined.contains("messenger") -> Triple(
                LinkedAppBrand.MESSENGER,
                "com.facebook.orca",
                null
            )
            else -> {
                // Ignore system/OEM sync rows that are not third-party messaging/calling apps
                val accLower = accountType.lowercase()
                if (accLower.isEmpty() ||
                    accLower.startsWith("com.google") ||
                    accLower.startsWith("com.android") ||
                    accLower.contains("samsung") ||
                    accLower.contains("xiaomi") ||
                    accLower.contains("miui") ||
                    accLower.contains("sim")
                ) {
                    return null
                }
                val label = resolveInstalledAppName(context, accountType, data2.ifBlank { accountType.substringAfterLast('.') })
                Triple(LinkedAppBrand.OTHER, accountType, null).also {
                    if (label.isBlank()) return null
                }
            }
        }

        val actionKind = when {
            combined.contains("video") -> LinkedActionKind.VIDEO_CALL
            combined.contains("voip") || combined.contains("voice") || combined.contains("audio") || combined.contains("call") -> LinkedActionKind.VOICE_CALL
            combined.contains("message") || combined.contains("chat") || combined.contains("profile") || combined.contains("im") -> LinkedActionKind.MESSAGE
            else -> LinkedActionKind.PROFILE
        }

        val appDisplayName = if (brand == LinkedAppBrand.OTHER) {
            resolveInstalledAppName(context, pkg, data2.ifBlank { pkg.substringAfterLast('.') })
        } else {
            brand.displayName
        }

        val rawActionLabel = data3.ifBlank {
            when (actionKind) {
                LinkedActionKind.MESSAGE -> "Message ${cleanPhone.ifBlank { data1 }}".trim()
                LinkedActionKind.VOICE_CALL -> "Voice call"
                LinkedActionKind.VIDEO_CALL -> "Video call"
                LinkedActionKind.PROFILE -> "Open in $appDisplayName"
            }
        }

        return LinkedAccountAction(
            id = "data_${dataId}_${actionKind.name}",
            dataId = dataId,
            appName = appDisplayName,
            packageName = pkg,
            brand = brand,
            actionLabel = rawActionLabel,
            actionKind = actionKind,
            mimeType = mimeType,
            fallbackUri = fallback
        )
    }

    fun launchLinkedAccountAction(context: Context, action: LinkedAccountAction): Boolean {
        // 1. Primary path: official ContactsContract.Data deep-link (works natively for WhatsApp, imo, Telegram, Viber, etc.)
        if (action.dataId > 0L && action.mimeType.isNotBlank()) {
            val dataUri = ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, action.dataId)
            val dataIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(dataUri, action.mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(dataIntent) }.isSuccess) {
                return true
            }
        }

        // 2. Direct deep-link URI targeted to the specific package
        if (!action.fallbackUri.isNullOrBlank()) {
            val uri = Uri.parse(action.fallbackUri)
            if (action.packageName.isNotBlank()) {
                val pkgIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(action.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (runCatching { context.startActivity(pkgIntent) }.isSuccess) {
                    return true
                }
            }
            val genericIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(genericIntent) }.isSuccess) {
                return true
            }
        }

        // 3. Fallback: launch the app directly
        if (action.packageName.isNotBlank()) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(action.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (runCatching { context.startActivity(launchIntent) }.isSuccess) {
                    return true
                }
            }
        }

        return false
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
    }

    private fun resolveInstalledAppName(context: Context, packageName: String, fallback: String): String {
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString().trim().ifBlank { fallback }
        }.getOrDefault(fallback)
    }

    private fun brandPriority(brand: LinkedAppBrand): Int = when (brand) {
        LinkedAppBrand.WHATSAPP -> 0
        LinkedAppBrand.WHATSAPP_BUSINESS -> 1
        LinkedAppBrand.IMO -> 2
        LinkedAppBrand.TELEGRAM -> 3
        LinkedAppBrand.VIBER -> 4
        LinkedAppBrand.SIGNAL -> 5
        LinkedAppBrand.GOOGLE_MEET -> 6
        LinkedAppBrand.MESSENGER -> 7
        LinkedAppBrand.OTHER -> 8
    }

    private fun actionSortOrder(kind: LinkedActionKind): Int = when (kind) {
        LinkedActionKind.MESSAGE -> 0
        LinkedActionKind.VOICE_CALL -> 1
        LinkedActionKind.VIDEO_CALL -> 2
        LinkedActionKind.PROFILE -> 3
    }
}
