package com.example.telecom

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Microphone-typed Foreground Service that keeps background call audio capture
 * alive when the user navigates away from the active call screen or locks the device.
 *
 * Exposes [startAndAwaitReady] so [CallRecordingManager] waits until Android has actually
 * promoted the microphone FGS before opening AudioRecord (preventing Android 11+ FGS
 * startup race conditions that silently zero-fill captured audio).
 */
class CallRecordingForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                _isForegroundActive.value = false
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val hasRecordPerm = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasRecordPerm) {
                    _isForegroundActive.value = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                val callerLabel = intent?.getStringExtra(EXTRA_CALLER_LABEL).orEmpty()
                val networkType = intent?.getStringExtra(EXTRA_NETWORK_TYPE).orEmpty().ifBlank { "SIM" }
                ensureNotificationChannel()
                val notification = buildRecordingNotification(callerLabel, networkType)
                try {
                    val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        0
                    }
                    ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
                    _isForegroundActive.value = true
                } catch (_: SecurityException) {
                    _isForegroundActive.value = false
                    stopSelf()
                    return START_NOT_STICKY
                } catch (_: IllegalStateException) {
                    _isForegroundActive.value = false
                    stopSelf()
                    return START_NOT_STICKY
                }
                return START_NOT_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        _isForegroundActive.value = false
        stopForegroundCompat()
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.recording_channel_description)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildRecordingNotification(callerLabel: String, networkType: String): Notification {
        val openCallIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val detailText = if (callerLabel.isNotBlank()) {
            "Recording $networkType call with $callerLabel"
        } else {
            "Recording $networkType call to App Internal Storage"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(detailText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "voxline_call_recording"
        private const val NOTIFICATION_ID = 4107
        private const val ACTION_START = "com.example.telecom.action.START_CALL_RECORDING_FGS"
        private const val ACTION_STOP = "com.example.telecom.action.STOP_CALL_RECORDING_FGS"
        private const val EXTRA_CALLER_LABEL = "extra_caller_label"
        private const val EXTRA_NETWORK_TYPE = "extra_network_type"

        private val _isForegroundActive = MutableStateFlow(false)
        val isForegroundActive: StateFlow<Boolean> = _isForegroundActive.asStateFlow()

        fun start(context: Context, callerLabel: String, networkType: String) {
            val intent = Intent(context, CallRecordingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CALLER_LABEL, callerLabel)
                putExtra(EXTRA_NETWORK_TYPE, networkType)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        /**
         * Starts the microphone foreground service and suspends until [ServiceCompat.startForeground]
         * completes (or [timeoutMs] elapses), preventing Android 11+ silent capture races.
         */
        suspend fun startAndAwaitReady(
            context: Context,
            callerLabel: String,
            networkType: String,
            timeoutMs: Long = 1500L
        ): Boolean {
            if (_isForegroundActive.value) return true
            start(context, callerLabel, networkType)
            return withTimeoutOrNull(timeoutMs) {
                isForegroundActive.first { it }
            } == true
        }

        fun stop(context: Context) {
            _isForegroundActive.value = false
            val intent = Intent(context, CallRecordingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.stopService(intent)
            }
        }
    }
}
