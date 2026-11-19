package com.example.telecom

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight Accessibility Service that enables Android 10+ AudioPolicyService
 * to share call audio input (VOICE_RECOGNITION) during active MODE_IN_CALL sessions
 * instead of silencing recordings to all zeros.
 *
 * Strictly privacy-safe: canRetrieveWindowContent is false and no UI/window events are read.
 */
class CallRecordingAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally no-op: no screen content or user input is ever inspected.
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onUnbind(intent: Intent?): Boolean {
        _isConnected.value = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _isConnected.value = false
        super.onDestroy()
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        /**
         * Returns true if this accessibility helper is currently connected or enabled
         * in Android Settings -> Accessibility.
         */
        fun isServiceEnabled(context: Context): Boolean {
            if (_isConnected.value) return true
            return runCatching {
                val expected = ComponentName(context, CallRecordingAccessibilityService::class.java)
                val flattened = expected.flattenToString()
                val shortFlattened = expected.flattenToShortString()
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()
                enabledServices
                    .split(':')
                    .any { entry ->
                        entry.equals(flattened, ignoreCase = true) ||
                            entry.equals(shortFlattened, ignoreCase = true)
                    }
            }.getOrDefault(false)
        }

        /**
         * Opens Android's Accessibility Settings so the user can enable VoxLine Call Audio Helper.
         */
        fun openAccessibilitySettings(context: Context): Boolean {
            return runCatching {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    }
}
