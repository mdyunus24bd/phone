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
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimSlotInfo(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val isSystemDefault: Boolean = false
)

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
     * Reads real active SIM subscriptions from Android's SubscriptionManager and marks
     * whichever SIM is set as the system default voice SIM in Android Phone Settings.
     */
    @SuppressLint("MissingPermission")
    fun detectActiveSims(): List<SimSlotInfo> {
        if (!hasPhoneStatePermission()) {
            return emptyList()
        }
        return try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val defaultVoiceSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
            val list = subManager?.activeSubscriptionInfoList.orEmpty()
            list.sortedBy { it.simSlotIndex }.map { info ->
                val slotLabel = "SIM ${info.simSlotIndex + 1}"
                val rawCarrier = info.carrierName?.toString()?.trim().orEmpty()
                val cleanCarrier = if (
                    rawCarrier.isBlank() ||
                    rawCarrier.equals("T-Mobile", ignoreCase = true) ||
                    rawCarrier.equals("Android", ignoreCase = true)
                ) {
                    ""
                } else {
                    rawCarrier
                }
                SimSlotInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    displayName = slotLabel,
                    carrierName = cleanCarrier,
                    isSystemDefault = defaultVoiceSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID &&
                        info.subscriptionId == defaultVoiceSubId
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
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
     * When preferredSlotIndex is DEFAULT_SIM_SLOT_INDEX (-1), uses the SIM selected in
     * Android Phone Settings (default outgoing voice account).
     */
    @SuppressLint("MissingPermission")
    fun placeCarrierSimCall(
        number: String,
        preferredSlotIndex: Int = DEFAULT_SIM_SLOT_INDEX
    ): Boolean {
        val sanitized = number.trim()
        if (sanitized.isEmpty()) return false
        val uri = Uri.fromParts("tel", sanitized, null)

        return try {
            if (hasCallPhonePermission()) {
                val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (telecom != null) {
                    val extras = Bundle()
                    if (hasPhoneStatePermission()) {
                        if (preferredSlotIndex == DEFAULT_SIM_SLOT_INDEX) {
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
