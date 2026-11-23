package com.example.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class SimSlotInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val isSystemDefault: Boolean = false,
    val phoneAccountHandle: PhoneAccountHandle? = null
)

private val GENERIC_SLOT_REGEX = Regex("^(sim|slot|card|sub|subscription)\\s*\\d+$", RegexOption.IGNORE_CASE)
private val GENERIC_SIM_NAMES = setOf(
    "sim",
    "sim card",
    "device sim",
    "sim cellular",
    "phone default sim",
    "carrier sim",
    "default sim",
    "cellular",
    "synced",
    "android",
    "unknown",
    "no service",
    "emergency calls only",
    "none",
    "n/a"
)

/**
 * Extracts only the real SIM carrier/company name (e.g. "Grameenphone", "Airtel", "Robi", "Jio")
 * from a raw route label or Android Subscription/PhoneAccount text.
 * Strips slot prefixes ("SIM 1 •", "SIM 2 •", "CARD 1", etc.) and returns "" when only a
 * generic label ("SIM", "SIM 1", "SIM 2", "Device SIM", etc.) is available.
 */
fun extractSimCompanyName(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""

    val segments = trimmed
        .split("•", "|", "—", "–", " - ")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val meaningful = segments.mapNotNull { segment ->
        val cleaned = segment
            .replace(Regex("^(sim|slot|card)\\s*\\d+\\s*[:\\-•|]?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\((sim|slot|card)\\s*\\d+\\)$", RegexOption.IGNORE_CASE), "")
            .trim()
        val normalized = cleaned.lowercase().replace(Regex("\\s+"), " ")
        when {
            cleaned.isBlank() -> null
            GENERIC_SLOT_REGEX.matches(normalized) -> null
            normalized in GENERIC_SIM_NAMES -> null
            cleaned.all { it.isDigit() } -> null
            else -> cleaned
        }
    }

    return meaningful.firstOrNull().orEmpty()
}

/**
 * Snapshot of active SIM subscriptions and Telecom PhoneAccountHandles used to resolve
 * which SIM carrier/company handled a live or logged call without repeated IPC.
 */
class SimAccountLookup(
    private val activeSims: List<SimSlotInfo>,
    private val iccIdBySubId: Map<Int, String>,
    private val phoneAccountIdsInOrder: List<String>,
    private val phoneAccountLabelsById: Map<String, String>
) {
    fun resolve(
        accountId: String?,
        accountLabel: String? = null,
        fallbackSlotIndex: Int = TelecomBridge.DEFAULT_SIM_SLOT_INDEX
    ): String {
        val rawId = accountId?.trim().orEmpty()
        val rawLabel = accountLabel?.trim().orEmpty()

        // 1. Direct match by Subscription ID or ICCID
        if (rawId.isNotEmpty()) {
            activeSims.firstOrNull { sim ->
                val subIdStr = sim.subscriptionId.toString()
                val iccId = iccIdBySubId[sim.subscriptionId].orEmpty()
                rawId.equals(subIdStr, ignoreCase = true) ||
                    (iccId.isNotEmpty() && (
                        rawId.equals(iccId, ignoreCase = true) ||
                            rawId.startsWith(iccId, ignoreCase = true) ||
                            iccId.startsWith(rawId, ignoreCase = true)
                        ))
            }?.let { sim ->
                formatSimLabel(sim).takeIf { it.isNotBlank() }?.let { return it }
            }

            // 2. Match against TelecomManager.callCapablePhoneAccounts ordering
            val handleIdx = phoneAccountIdsInOrder.indexOfFirst { it.equals(rawId, ignoreCase = true) }
            if (handleIdx >= 0) {
                activeSims.getOrNull(handleIdx)?.let { sim ->
                    formatSimLabel(sim).takeIf { it.isNotBlank() }?.let { return it }
                }
            }

            // 3. Match against PhoneAccount label resolved from TelecomManager
            val mappedLabel = phoneAccountLabelsById[rawId].orEmpty()
            matchSimByText(mappedLabel)?.let { return it }

            // 4. Match explicit slot hints in accountId (e.g. "0", "1", "SIM1", "SIM 2") -> map to carrier name
            matchSimByText(rawId)?.let { return it }
        }

        // 5. Match against caller-supplied PhoneAccount label
        if (rawLabel.isNotEmpty()) {
            matchSimByText(rawLabel)?.let { return it }
        }

        // 6. Fallback to explicit selected slot or default / single active SIM company name
        if (fallbackSlotIndex >= 0) {
            activeSims.firstOrNull { it.slotIndex == fallbackSlotIndex }
                ?.let { formatSimLabel(it) }
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            activeSims.getOrNull(fallbackSlotIndex)
                ?.let { formatSimLabel(it) }
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
            return ""
        }

        if (activeSims.size == 1) {
            return formatSimLabel(activeSims.first())
        }
        activeSims.firstOrNull { it.isSystemDefault }
            ?.let { formatSimLabel(it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        activeSims.firstOrNull()
            ?.let { formatSimLabel(it) }
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return ""
    }

    private fun matchSimByText(text: String): String? {
        if (text.isBlank()) return null
        val lower = text.lowercase()
        activeSims.firstOrNull { sim ->
            sim.carrierName.isNotBlank() && lower.contains(sim.carrierName.lowercase())
        }?.let { sim ->
            formatSimLabel(sim).takeIf { it.isNotBlank() }?.let { return it }
        }

        val slotMatched = when {
            lower == "0" || lower.contains("sim 1") || lower.contains("sim1") || lower.contains("slot 1") || lower.contains("card 1") -> {
                activeSims.firstOrNull { it.slotIndex == 0 }?.let { formatSimLabel(it) }?.takeIf { it.isNotBlank() }
            }
            lower == "1" || lower.contains("sim 2") || lower.contains("sim2") || lower.contains("slot 2") || lower.contains("card 2") -> {
                activeSims.firstOrNull { it.slotIndex == 1 }?.let { formatSimLabel(it) }?.takeIf { it.isNotBlank() }
            }
            else -> null
        }
        if (slotMatched != null) return slotMatched

        return extractSimCompanyName(text).takeIf { it.isNotBlank() }
    }

    companion object {
        fun formatSimLabel(sim: SimSlotInfo): String {
            return extractSimCompanyName(sim.carrierName.ifBlank { sim.displayName })
        }
    }
}

class TelecomBridge(private val context: Context) {

    companion object {
        /** Uses the default calling SIM configured in Android Phone Settings. */
        const val DEFAULT_SIM_SLOT_INDEX: Int = -1
    }

    fun isDefaultDialer(): Boolean {
        return try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecom?.defaultDialerPackage == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun hasPhoneStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCallPhonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun createDefaultDialerIntent(): Intent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                } else {
                    null
                }
            } else {
                @Suppress("DEPRECATION")
                Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Reads real active SIM subscriptions from Android's SubscriptionManager & TelecomManager,
     * resolves each SIM's real carrier/company name, pairs its Telecom PhoneAccountHandle, and
     * accurately detects whether a default calling SIM is selected in Android Phone Settings
     * vs. "Ask every time" (no default calling SIM selected).
     */
    @SuppressLint("MissingPermission")
    fun detectActiveSims(): List<SimSlotInfo> {
        if (!hasPhoneStatePermission() && !isDefaultDialer()) {
            return emptyList()
        }
        return try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

            val defaultVoiceSubId = runCatching {
                SubscriptionManager.getDefaultVoiceSubscriptionId()
            }.getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)

            val defaultOutgoingResult = runCatching {
                telecom?.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            }
            val defaultOutgoingHandle: PhoneAccountHandle? = defaultOutgoingResult.getOrNull()
            val hasTelecomDefaultCheck = telecom != null && defaultOutgoingResult.isSuccess

            val allHandles: List<PhoneAccountHandle> = runCatching {
                telecom?.callCapablePhoneAccounts.orEmpty()
            }.getOrDefault(emptyList())

            val handleLabels = mutableMapOf<PhoneAccountHandle, String>()
            val simHandles = allHandles.filter { handle ->
                val account = runCatching { telecom?.getPhoneAccount(handle) }.getOrNull()
                val label = account?.label?.toString()?.trim().orEmpty()
                if (label.isNotEmpty()) {
                    handleLabels[handle] = label
                }
                account == null || account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
            }.ifEmpty { allHandles }

            val handleSubIds = mutableMapOf<PhoneAccountHandle, Int>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && telephonyManager != null) {
                simHandles.forEach { handle ->
                    val subId = runCatching { telephonyManager.getSubscriptionId(handle) }
                        .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                    if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        handleSubIds[handle] = subId
                    }
                }
            }

            val sortedSubs = runCatching {
                subManager?.activeSubscriptionInfoList.orEmpty()
            }.getOrDefault(emptyList()).sortedBy { it.simSlotIndex }

            // Fallback when SubscriptionManager returns empty but Telecom exposes SIM phone accounts
            if (sortedSubs.isEmpty()) {
                if (simHandles.isEmpty()) return emptyList()
                return simHandles.mapIndexed { idx, handle ->
                    val cleanCompany = extractSimCompanyName(handleLabels[handle].orEmpty())
                    val isDefault = when {
                        simHandles.size == 1 -> true
                        hasTelecomDefaultCheck -> defaultOutgoingHandle != null &&
                            (handle == defaultOutgoingHandle || handle.id == defaultOutgoingHandle.id)
                        else -> false
                    }
                    SimSlotInfo(
                        subscriptionId = handleSubIds[handle] ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                        slotIndex = idx,
                        displayName = cleanCompany,
                        carrierName = cleanCompany,
                        isSystemDefault = isDefault,
                        phoneAccountHandle = handle
                    )
                }
            }

            val usedHandles = mutableSetOf<PhoneAccountHandle>()
            val pairedEntries = sortedSubs.mapIndexed { idx, info ->
                val iccId = runCatching { info.iccId?.trim().orEmpty() }.getOrDefault("")
                val subIdStr = info.subscriptionId.toString()
                val rawSubCarrier = info.carrierName?.toString()?.trim().orEmpty()
                val rawSubDisplay = info.displayName?.toString()?.trim().orEmpty()

                val matchedHandle = simHandles.firstOrNull { h ->
                    h !in usedHandles && handleSubIds[h] == info.subscriptionId
                } ?: simHandles.firstOrNull { h ->
                    val hid = h.id?.trim().orEmpty()
                    h !in usedHandles && hid.isNotEmpty() && (
                        hid.equals(subIdStr, ignoreCase = true) ||
                            (iccId.isNotEmpty() && (
                                hid.equals(iccId, ignoreCase = true) ||
                                    hid.startsWith(iccId, ignoreCase = true) ||
                                    iccId.startsWith(hid, ignoreCase = true)
                                ))
                        )
                } ?: simHandles.firstOrNull { h ->
                    val label = handleLabels[h].orEmpty()
                    h !in usedHandles && label.isNotBlank() && (
                        (rawSubCarrier.isNotBlank() && label.contains(rawSubCarrier, ignoreCase = true)) ||
                            (rawSubDisplay.isNotBlank() && label.contains(rawSubDisplay, ignoreCase = true))
                        )
                } ?: simHandles.getOrNull(idx)?.takeIf { it !in usedHandles }
                    ?: simHandles.firstOrNull { it !in usedHandles }

                if (matchedHandle != null) {
                    usedHandles.add(matchedHandle)
                }

                val subTelephony = runCatching {
                    telephonyManager?.createForSubscriptionId(info.subscriptionId)
                }.getOrNull()

                val cleanCompany = listOf(
                    extractSimCompanyName(rawSubCarrier),
                    extractSimCompanyName(runCatching { subTelephony?.networkOperatorName.orEmpty() }.getOrDefault("")),
                    extractSimCompanyName(runCatching { subTelephony?.simOperatorName.orEmpty() }.getOrDefault("")),
                    extractSimCompanyName(matchedHandle?.let { handleLabels[it] }.orEmpty()),
                    extractSimCompanyName(rawSubDisplay)
                ).firstOrNull { it.isNotBlank() }.orEmpty()

                Triple(info, matchedHandle, cleanCompany)
            }

            val defaultHandleSubId = if (
                defaultOutgoingHandle != null &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                telephonyManager != null
            ) {
                runCatching { telephonyManager.getSubscriptionId(defaultOutgoingHandle) }
                    .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
            } else {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }

            val mapped = pairedEntries.map { (info, matchedHandle, cleanCompany) ->
                val iccId = runCatching { info.iccId?.trim().orEmpty() }.getOrDefault("")
                val matchesDefaultHandle = defaultOutgoingHandle != null && (
                    matchedHandle == defaultOutgoingHandle ||
                        (matchedHandle != null && matchedHandle.id == defaultOutgoingHandle.id) ||
                        (defaultHandleSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
                            defaultHandleSubId == info.subscriptionId) ||
                        defaultOutgoingHandle.id.equals(info.subscriptionId.toString(), ignoreCase = true) ||
                        (iccId.isNotEmpty() && (
                            defaultOutgoingHandle.id.equals(iccId, ignoreCase = true) ||
                                defaultOutgoingHandle.id.startsWith(iccId, ignoreCase = true) ||
                                iccId.startsWith(defaultOutgoingHandle.id, ignoreCase = true)
                            ))
                    )

                val isDefault = when {
                    pairedEntries.size == 1 -> true
                    hasTelecomDefaultCheck -> defaultOutgoingHandle != null && matchesDefaultHandle
                    else -> defaultVoiceSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
                        info.subscriptionId == defaultVoiceSubId
                }

                SimSlotInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    displayName = cleanCompany,
                    carrierName = cleanCompany,
                    isSystemDefault = isDefault,
                    phoneAccountHandle = matchedHandle
                )
            }

            // If Android Telecom reported a non-null default outgoing PhoneAccountHandle on a dual-SIM
            // device but OEM handle IDs didn't match above, resolve via defaultVoiceSubId or account label
            // so we still accurately reflect that a single default calling SIM is selected.
            if (mapped.size >= 2 && hasTelecomDefaultCheck && defaultOutgoingHandle != null && mapped.none { it.isSystemDefault }) {
                val defaultLabelCompany = extractSimCompanyName(
                    runCatching {
                        telecom?.getPhoneAccount(defaultOutgoingHandle)?.label?.toString().orEmpty()
                    }.getOrDefault("")
                )
                val fallbackIndex = mapped.indexOfFirst {
                    defaultVoiceSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
                        it.subscriptionId == defaultVoiceSubId
                }.takeIf { it >= 0 }
                    ?: mapped.indexOfFirst {
                        defaultLabelCompany.isNotBlank() &&
                            it.carrierName.equals(defaultLabelCompany, ignoreCase = true)
                    }.takeIf { it >= 0 }
                    ?: 0
                mapped.mapIndexed { idx, sim ->
                    if (idx == fallbackIndex) sim.copy(isSystemDefault = true) else sim
                }
            } else {
                mapped
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Builds a lookup table mapping Telecom PhoneAccount IDs / Subscription IDs / ICCIDs
     * to human-readable SIM slot labels (e.g. "SIM 1", "SIM 2", or "SIM 1 • Carrier").
     */
    @SuppressLint("MissingPermission")
    fun buildSimAccountLookup(): SimAccountLookup {
        val sims = detectActiveSims()
        val iccMap = mutableMapOf<Int, String>()
        val handleIds = mutableListOf<String>()
        val handleLabels = mutableMapOf<String, String>()

        if (hasPhoneStatePermission()) {
            runCatching {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                subManager?.activeSubscriptionInfoList.orEmpty().forEach { info ->
                    val icc = runCatching { info.iccId?.trim().orEmpty() }.getOrDefault("")
                    if (icc.isNotEmpty()) {
                        iccMap[info.subscriptionId] = icc
                    }
                }
            }
            runCatching {
                val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                val handles: List<PhoneAccountHandle> = telecom?.callCapablePhoneAccounts.orEmpty()
                handles.forEach { handle ->
                    val id = handle.id?.trim().orEmpty()
                    if (id.isNotEmpty()) {
                        handleIds.add(id)
                        val label = runCatching {
                            telecom?.getPhoneAccount(handle)?.label?.toString()?.trim().orEmpty()
                        }.getOrDefault("")
                        if (label.isNotEmpty()) {
                            handleLabels[id] = label
                        }
                    }
                }
            }
        }

        return SimAccountLookup(
            activeSims = sims,
            iccIdBySubId = iccMap,
            phoneAccountIdsInOrder = handleIds,
            phoneAccountLabelsById = handleLabels
        )
    }

    fun resolveSimLabel(
        accountId: String?,
        accountLabel: String? = null,
        fallbackSlotIndex: Int = DEFAULT_SIM_SLOT_INDEX
    ): String {
        return buildSimAccountLookup().resolve(
            accountId = accountId,
            accountLabel = accountLabel,
            fallbackSlotIndex = fallbackSlotIndex
        )
    }

    /**
     * Opens Android's system Calling Accounts / SIM settings so the user can pick their
     * Phone Default calling SIM.
     */
    fun openPhoneSimSettings(): Boolean {
        val candidates = listOf(
            Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS),
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) {
                return true
            }
        }
        return false
    }

    /**
     * Dispatches a real cellular SIM call via Android Telecom or standard ACTION_DIAL intent.
     * When preferredSim is provided, routes the call directly through that SIM's PhoneAccountHandle.
     * When preferredSlotIndex is DEFAULT_SIM_SLOT_INDEX (-1), uses the SIM selected in
     * Android Phone Settings (default outgoing voice account).
     */
    @SuppressLint("MissingPermission")
    fun placeCarrierSimCall(
        number: String,
        preferredSlotIndex: Int = DEFAULT_SIM_SLOT_INDEX,
        preferredSim: SimSlotInfo? = null
    ): Boolean {
        val sanitized = number.trim()
        if (sanitized.isEmpty()) return false
        val uri = Uri.fromParts("tel", sanitized, null)

        return try {
            if (hasCallPhonePermission()) {
                val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (telecom != null) {
                    val extras = Bundle()
                    if (hasPhoneStatePermission() || isDefaultDialer()) {
                        if (preferredSim != null) {
                            val resolvedHandle = preferredSim.phoneAccountHandle ?: run {
                                val accounts = runCatching { telecom.callCapablePhoneAccounts }.getOrNull().orEmpty()
                                accounts.firstOrNull {
                                    preferredSim.subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
                                        it.id.equals(preferredSim.subscriptionId.toString(), ignoreCase = true)
                                } ?: accounts.getOrNull(preferredSim.slotIndex)
                                    ?: accounts.firstOrNull()
                            }
                            if (resolvedHandle != null) {
                                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, resolvedHandle)
                            }
                        } else if (preferredSlotIndex == DEFAULT_SIM_SLOT_INDEX) {
                            val defaultHandle = runCatching {
                                telecom.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
                            }.getOrNull()
                            if (defaultHandle != null) {
                                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, defaultHandle)
                            }
                        } else {
                            val accounts = runCatching { telecom.callCapablePhoneAccounts }.getOrNull().orEmpty()
                            val selectedHandle = accounts.getOrNull(preferredSlotIndex) ?: accounts.firstOrNull()
                            if (selectedHandle != null) {
                                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, selectedHandle)
                            }
                        }
                    }
                    telecom.placeCall(uri, extras)
                    return true
                }
            }
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Dispatches a real GSM MMI call-forwarding code (*21*number#, *67*number#, ##002#, etc.)
     * using Uri.encode("#") so Android properly transmits the trailing hash.
     */
    fun dispatchForwardingMmiCode(mmiCode: String): Boolean {
        return try {
            val encoded = Uri.parse("tel:" + Uri.encode(mmiCode))
            val action = if (hasCallPhonePermission()) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, encoded).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens the device's default SMS messaging app pre-filled with the quick reply message
     * for the caller's number using the zero-permission ACTION_SENDTO smsto: intent.
     */
    fun openQuickReplySms(phoneNumber: String, message: String): Boolean {
        val sanitized = phoneNumber.trim()
        if (sanitized.isEmpty() || message.isBlank()) return false
        return try {
            val smsUri = Uri.parse("smsto:${Uri.encode(sanitized)}")
            val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                putExtra("sms_body", message.trim())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(smsIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun playDtmfTone(digit: Char) {
        val toneType = when (digit) {
            '0' -> ToneGenerator.TONE_DTMF_0
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        try {
            val tg = ToneGenerator(AudioManager.STREAM_DTMF, 65)
            tg.startTone(toneType, 110)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, 160)
        } catch (_: Exception) {
        }
    }
}
