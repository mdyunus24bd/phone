package com.example.data.preferences

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.voxLineDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voxline_settings"
)

enum class AutoRecordMode(val label: String, val description: String) {
    ALL_CALLS("All Calls", "Automatically record every SIM call"),
    SPECIFIC_NUMBERS("Specific Numbers", "Record only phone numbers in your allowlist")
}

enum class KeypadLayoutStyle(val label: String, val subtitle: String) {
    CIRCULAR_MODERN("Modern Circular", "Sculpted round tonal dial keys"),
    COMPACT_TACTILE("Compact Grid", "Condensed grid with extra contact space"),
    MINIMAL_CLEAN("Minimal Clean", "Borderless floating digits with hairline dividers"),
    ACCENT_RING("Accent Ring", "Crisp outlined circular rings with accent glow"),
    SOFT_CAPSULE("Soft Capsule", "Wide ergonomic pill-shaped keys"),
    CLASSIC_BOARD("Classic Phone", "Framed tactile 3x4 phone keypad board"),
    ONE_HANDED_RIGHT("One-Handed (Right)", "Thumb-shifted right-hand ergonomic pad"),
    ONE_HANDED_LEFT("One-Handed (Left)", "Thumb-shifted left-hand ergonomic pad")
}

enum class AppFontSizeOption(val label: String, val scale: Float, val percentLabel: String) {
    SMALL("Small", 0.88f, "88%"),
    NORMAL("Normal", 1.0f, "100%"),
    LARGE("Large", 1.15f, "115%"),
    EXTRA_LARGE("Extra Large", 1.30f, "130%")
}

enum class DialerSizeOption(val label: String, val subtitle: String, val scale: Float) {
    SMALL("Compact", "Smaller keys with extra space for recent calls", 0.84f),
    MEDIUM("Normal", "Balanced everyday keypad size", 1.0f),
    LARGE("Large", "Bigger keys for comfortable dialing", 1.14f),
    EXTRA_LARGE("Extra Large", "Maximum key size for easy tapping", 1.26f)
}

enum class CallForwardingCondition(val label: String, val mmiPrefix: String, val deactivateCode: String) {
    ALWAYS("Always Forward", "*21*", "##21#"),
    BUSY("When Busy", "*67*", "##67#"),
    UNANSWERED("When Unanswered", "*61*", "##61#"),
    UNREACHABLE("When Unreachable", "*62*", "##62#")
}

data class CallForwardingPreferences(
    val enabled: Boolean = false,
    val forwardingNumber: String = "",
    val condition: CallForwardingCondition = CallForwardingCondition.ALWAYS,
    val updatedAt: Long = 0L
)

data class RingtonePreferences(
    val simRingtoneUri: String = "builtin:sim_classic",
    val simRingtoneTitle: String = "Classic Horizon",
    val vibrateOnSimRing: Boolean = true
)

val DefaultQuickReplyTemplates: List<String> = listOf(
    "Can't talk right now. I'll call you back soon.",
    "I'm in a meeting. Please text me if it's urgent.",
    "On my way! I'll call you in a few minutes.",
    "Sorry I missed your call. What's up?"
)

data class VoxLineSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val accentPalette: AccentPalette = AccentPalette.EMERALD,
    val highContrast: Boolean = false,
    val fontSizeOption: AppFontSizeOption = AppFontSizeOption.NORMAL,
    val dialerSizeOption: DialerSizeOption = DialerSizeOption.MEDIUM,
    val autoRecordCalls: Boolean = false,
    val autoRecordMode: AutoRecordMode = AutoRecordMode.ALL_CALLS,
    val autoRecordNumbers: Set<String> = emptySet(),
    val keypadLayout: KeypadLayoutStyle = KeypadLayoutStyle.CIRCULAR_MODERN,
    val callForwarding: CallForwardingPreferences = CallForwardingPreferences(),
    val ringtones: RingtonePreferences = RingtonePreferences(),
    val quickReplyTemplates: List<String> = DefaultQuickReplyTemplates,
    val spamShieldEnabled: Boolean = true,
    val autoBlockHighRiskSpam: Boolean = true,
    val screenPrivacySecure: Boolean = false,
    val dtmfTonesEnabled: Boolean = true,
    val deviceSyncName: String = Build.MODEL ?: "Android Device",
    val lastSyncTimestamp: Long = 0L,
    val lastSyncChecksum: String = ""
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
        val HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        val FONT_SIZE_OPTION = stringPreferencesKey("font_size_option")
        val DIALER_SIZE_OPTION = stringPreferencesKey("dialer_size_option")
        val AUTO_RECORD = booleanPreferencesKey("auto_record")
        val AUTO_RECORD_MODE = stringPreferencesKey("auto_record_mode")
        val AUTO_RECORD_NUMBERS = stringSetPreferencesKey("auto_record_numbers")
        val KEYPAD_LAYOUT = stringPreferencesKey("keypad_layout")
        val CALL_FORWARDING_ENABLED = booleanPreferencesKey("call_forwarding_enabled")
        val CALL_FORWARDING_NUMBER = stringPreferencesKey("call_forwarding_number")
        val CALL_FORWARDING_CONDITION = stringPreferencesKey("call_forwarding_condition")
        val CALL_FORWARDING_UPDATED_AT = longPreferencesKey("call_forwarding_updated_at")
        val SIM_RINGTONE_URI = stringPreferencesKey("sim_ringtone_uri")
        val SIM_RINGTONE_TITLE = stringPreferencesKey("sim_ringtone_title")
        val VIBRATE_SIM_RING = booleanPreferencesKey("vibrate_sim_ring")
        val QUICK_REPLY_TEMPLATES = stringPreferencesKey("quick_reply_templates_json")
        val SPAM_SHIELD = booleanPreferencesKey("spam_shield")
        val AUTO_BLOCK_SPAM = booleanPreferencesKey("auto_block_spam")
        val SCREEN_PRIVACY = booleanPreferencesKey("screen_privacy")
        val DTMF_TONES = booleanPreferencesKey("dtmf_tones")
        val DEVICE_SYNC_NAME = stringPreferencesKey("device_sync_name")
        val LAST_SYNC_TS = longPreferencesKey("last_sync_ts")
        val LAST_SYNC_HASH = stringPreferencesKey("last_sync_hash")
    }

    val callForwardingFlow: Flow<CallForwardingPreferences> = context.voxLineDataStore.data.map { prefs ->
        CallForwardingPreferences(
            enabled = prefs[Keys.CALL_FORWARDING_ENABLED] ?: false,
            forwardingNumber = prefs[Keys.CALL_FORWARDING_NUMBER] ?: "",
            condition = prefs[Keys.CALL_FORWARDING_CONDITION]?.let {
                runCatching { CallForwardingCondition.valueOf(it) }.getOrNull()
            } ?: CallForwardingCondition.ALWAYS,
            updatedAt = prefs[Keys.CALL_FORWARDING_UPDATED_AT] ?: 0L
        )
    }

    val settingsFlow: Flow<VoxLineSettings> = context.voxLineDataStore.data.map { prefs ->
        VoxLineSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.LIGHT,
            accentPalette = prefs[Keys.ACCENT_PALETTE]?.let { runCatching { AccentPalette.valueOf(it) }.getOrNull() }
                ?: AccentPalette.EMERALD,
            highContrast = prefs[Keys.HIGH_CONTRAST] ?: false,
            fontSizeOption = prefs[Keys.FONT_SIZE_OPTION]?.let {
                runCatching { AppFontSizeOption.valueOf(it) }.getOrNull()
            } ?: AppFontSizeOption.NORMAL,
            dialerSizeOption = prefs[Keys.DIALER_SIZE_OPTION]?.let {
                runCatching { DialerSizeOption.valueOf(it) }.getOrNull()
            } ?: DialerSizeOption.MEDIUM,
            autoRecordCalls = prefs[Keys.AUTO_RECORD] ?: false,
            autoRecordMode = prefs[Keys.AUTO_RECORD_MODE]?.let {
                runCatching { AutoRecordMode.valueOf(it) }.getOrNull()
            } ?: AutoRecordMode.ALL_CALLS,
            autoRecordNumbers = prefs[Keys.AUTO_RECORD_NUMBERS] ?: emptySet(),
            keypadLayout = prefs[Keys.KEYPAD_LAYOUT]?.let {
                runCatching { KeypadLayoutStyle.valueOf(it) }.getOrNull()
            } ?: KeypadLayoutStyle.CIRCULAR_MODERN,
            callForwarding = CallForwardingPreferences(
                enabled = prefs[Keys.CALL_FORWARDING_ENABLED] ?: false,
                forwardingNumber = prefs[Keys.CALL_FORWARDING_NUMBER] ?: "",
                condition = prefs[Keys.CALL_FORWARDING_CONDITION]?.let {
                    runCatching { CallForwardingCondition.valueOf(it) }.getOrNull()
                } ?: CallForwardingCondition.ALWAYS,
                updatedAt = prefs[Keys.CALL_FORWARDING_UPDATED_AT] ?: 0L
            ),
            ringtones = RingtonePreferences(
                simRingtoneUri = prefs[Keys.SIM_RINGTONE_URI] ?: "builtin:sim_classic",
                simRingtoneTitle = prefs[Keys.SIM_RINGTONE_TITLE] ?: "Classic Horizon",
                vibrateOnSimRing = prefs[Keys.VIBRATE_SIM_RING] ?: true
            ),
            quickReplyTemplates = decodeQuickReplyTemplates(prefs[Keys.QUICK_REPLY_TEMPLATES]),
            spamShieldEnabled = prefs[Keys.SPAM_SHIELD] ?: true,
            autoBlockHighRiskSpam = prefs[Keys.AUTO_BLOCK_SPAM] ?: true,
            screenPrivacySecure = prefs[Keys.SCREEN_PRIVACY] ?: false,
            dtmfTonesEnabled = prefs[Keys.DTMF_TONES] ?: true,
            deviceSyncName = prefs[Keys.DEVICE_SYNC_NAME] ?: (Build.MODEL ?: "Android Device"),
            lastSyncTimestamp = prefs[Keys.LAST_SYNC_TS] ?: 0L,
            lastSyncChecksum = prefs[Keys.LAST_SYNC_HASH] ?: ""
        )
    }

    private fun decodeQuickReplyTemplates(rawJson: String?): List<String> {
        if (rawJson.isNullOrBlank()) return DefaultQuickReplyTemplates
        return runCatching {
            val arr = JSONArray(rawJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotEmpty()) add(item)
                }
            }.ifEmpty { DefaultQuickReplyTemplates }
        }.getOrElse { DefaultQuickReplyTemplates }
    }

    private fun encodeQuickReplyTemplates(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.voxLineDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAccentPalette(palette: AccentPalette) {
        context.voxLineDataStore.edit { it[Keys.ACCENT_PALETTE] = palette.name }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.voxLineDataStore.edit { it[Keys.HIGH_CONTRAST] = enabled }
    }

    suspend fun setFontSizeOption(option: AppFontSizeOption) {
        context.voxLineDataStore.edit { it[Keys.FONT_SIZE_OPTION] = option.name }
    }

    suspend fun setDialerSizeOption(option: DialerSizeOption) {
        context.voxLineDataStore.edit { it[Keys.DIALER_SIZE_OPTION] = option.name }
    }

    suspend fun setAutoRecordCalls(enabled: Boolean) {
        context.voxLineDataStore.edit { it[Keys.AUTO_RECORD] = enabled }
    }

    suspend fun setAutoRecordMode(mode: AutoRecordMode) {
        context.voxLineDataStore.edit { it[Keys.AUTO_RECORD_MODE] = mode.name }
    }

    suspend fun addAutoRecordNumber(number: String) {
        val cleaned = number.trim()
        if (cleaned.isEmpty()) return
        context.voxLineDataStore.edit { prefs ->
            val current = prefs[Keys.AUTO_RECORD_NUMBERS]?.toMutableSet() ?: mutableSetOf()
            current.add(cleaned)
            prefs[Keys.AUTO_RECORD_NUMBERS] = current
        }
    }

    suspend fun removeAutoRecordNumber(number: String) {
        context.voxLineDataStore.edit { prefs ->
            val current = prefs[Keys.AUTO_RECORD_NUMBERS]?.toMutableSet() ?: mutableSetOf()
            current.remove(number)
            prefs[Keys.AUTO_RECORD_NUMBERS] = current
        }
    }

    suspend fun clearAutoRecordNumbers() {
        context.voxLineDataStore.edit { it[Keys.AUTO_RECORD_NUMBERS] = emptySet() }
    }

    suspend fun setKeypadLayout(layout: KeypadLayoutStyle) {
        context.voxLineDataStore.edit { it[Keys.KEYPAD_LAYOUT] = layout.name }
    }

    suspend fun setCallForwardingEnabled(enabled: Boolean) {
        context.voxLineDataStore.edit {
            it[Keys.CALL_FORWARDING_ENABLED] = enabled
            it[Keys.CALL_FORWARDING_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun saveCallForwardingSettings(
        enabled: Boolean,
        forwardingNumber: String,
        condition: CallForwardingCondition = CallForwardingCondition.ALWAYS
    ) {
        context.voxLineDataStore.edit {
            it[Keys.CALL_FORWARDING_ENABLED] = enabled
            it[Keys.CALL_FORWARDING_NUMBER] = forwardingNumber.trim()
            it[Keys.CALL_FORWARDING_CONDITION] = condition.name
            it[Keys.CALL_FORWARDING_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun clearCallForwardingSettings() {
        context.voxLineDataStore.edit {
            it[Keys.CALL_FORWARDING_ENABLED] = false
            it[Keys.CALL_FORWARDING_NUMBER] = ""
            it[Keys.CALL_FORWARDING_CONDITION] = CallForwardingCondition.ALWAYS.name
            it[Keys.CALL_FORWARDING_UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun getRingtonePreferencesSnapshot(): RingtonePreferences {
        val prefs = context.voxLineDataStore.data.first()
        return RingtonePreferences(
            simRingtoneUri = prefs[Keys.SIM_RINGTONE_URI] ?: "builtin:sim_classic",
            simRingtoneTitle = prefs[Keys.SIM_RINGTONE_TITLE] ?: "Classic Horizon",
            vibrateOnSimRing = prefs[Keys.VIBRATE_SIM_RING] ?: true
        )
    }

    suspend fun setSimRingtone(uri: String, title: String) {
        context.voxLineDataStore.edit {
            it[Keys.SIM_RINGTONE_URI] = uri
            it[Keys.SIM_RINGTONE_TITLE] = title.ifBlank { "SIM Ringtone" }
        }
    }

    suspend fun setVibrateOnSimRing(enabled: Boolean) {
        context.voxLineDataStore.edit { it[Keys.VIBRATE_SIM_RING] = enabled }
    }

    suspend fun resetRingtonesToDefault() {
        context.voxLineDataStore.edit {
            it[Keys.SIM_RINGTONE_URI] = "builtin:sim_classic"
            it[Keys.SIM_RINGTONE_TITLE] = "Classic Horizon"
            it[Keys.VIBRATE_SIM_RING] = true
        }
    }

    suspend fun addQuickReplyTemplate(message: String) {
        val cleaned = message.trim()
        if (cleaned.isEmpty()) return
        context.voxLineDataStore.edit { prefs ->
            val current = decodeQuickReplyTemplates(prefs[Keys.QUICK_REPLY_TEMPLATES]).toMutableList()
            if (current.none { it.equals(cleaned, ignoreCase = true) }) {
                current.add(cleaned)
                prefs[Keys.QUICK_REPLY_TEMPLATES] = encodeQuickReplyTemplates(current)
            }
        }
    }

    suspend fun removeQuickReplyTemplate(message: String) {
        context.voxLineDataStore.edit { prefs ->
            val current = decodeQuickReplyTemplates(prefs[Keys.QUICK_REPLY_TEMPLATES]).toMutableList()
            current.removeAll { it == message }
            if (current.isEmpty()) {
                prefs.remove(Keys.QUICK_REPLY_TEMPLATES)
            } else {
                prefs[Keys.QUICK_REPLY_TEMPLATES] = encodeQuickReplyTemplates(current)
            }
        }
    }

    suspend fun resetQuickReplyTemplates() {
        context.voxLineDataStore.edit { prefs ->
            prefs[Keys.QUICK_REPLY_TEMPLATES] = encodeQuickReplyTemplates(DefaultQuickReplyTemplates)
        }
    }

    suspend fun setSpamShieldEnabled(enabled: Boolean) {
        context.voxLineDataStore.edit { it[Keys.SPAM_SHIELD] = enabled }
    }

    suspend fun setAutoBlockHighRiskSpam(enabled: Boolean) {
        context.voxLineDataStore.edit { it[Keys.AUTO_BLOCK_SPAM] = enabled }
    }

    suspend fun setScreenPrivacySecure(enabled: Boolean) {
        context.voxLineDataStore.edit { it[Keys.SCREEN_PRIVACY] = enabled }
    }

    suspend fun setDtmfTonesEnabled(enabled: Boolean) {
        context.voxLineDataStore.edit { it[Keys.DTMF_TONES] = enabled }
    }

    suspend fun recordSyncCompletion(timestamp: Long, checksum: String) {
        context.voxLineDataStore.edit {
            it[Keys.LAST_SYNC_TS] = timestamp
            it[Keys.LAST_SYNC_HASH] = checksum
        }
    }
}
