package com.example.telecom

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.MainActivity

/**
 * Manages high-priority CallStyle full-screen incoming call notifications so incoming SIM calls
 * wake the display, appear over the lock screen, and pop up as an actionable heads-up banner
 * over any running app on Android 8–15+.
 */
object IncomingCallNotificationManager {

    const val CHANNEL_INCOMING_CALL = "voxline_incoming_call_v2"
    const val CHANNEL_ONGOING_CALL = "voxline_ongoing_call_v1"
    const val NOTIFICATION_ID_CALL = 4102

    const val EXTRA_OPEN_ACTIVE_CALL = "extra_open_active_call"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (nm.getNotificationChannel(CHANNEL_INCOMING_CALL) == null) {
            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING_CALL,
                "Incoming Calls (Full-Screen)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows incoming calls over the display and lock screen with Answer and Decline actions"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                setBypassDnd(true)
            }
            nm.createNotificationChannel(incomingChannel)
        }

        if (nm.getNotificationChannel(CHANNEL_ONGOING_CALL) == null) {
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_CALL,
                "Ongoing Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Displays active call status with quick return and hang up controls"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(ongoingChannel)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.canUseFullScreenIntent() ?: true
        } else {
            true
        }
    }

    fun createFullScreenIntentSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun showIncomingCall(
        context: Context,
        phoneNumber: String,
        callerName: String
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val displayTitle = callerName.ifBlank { phoneNumber.ifBlank { "Unknown Caller" } }
        val countryName = PhoneCountryResolver.resolveCountryName(context, phoneNumber)
        val subtitle = buildString {
            append("Incoming call")
            if (callerName.isNotBlank() && phoneNumber.isNotBlank() && callerName != phoneNumber) {
                append(" • $phoneNumber")
            }
            if (countryName.isNotBlank()) {
                append(" • $countryName")
            }
        }

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_OPEN_ACTIVE_CALL, true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            4101,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerPendingIntent = PendingIntent.getBroadcast(
            context,
            4102,
            Intent(context, IncomingCallActionReceiver::class.java).apply {
                action = IncomingCallActionReceiver.ACTION_ANSWER_CALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            4103,
            Intent(context, IncomingCallActionReceiver::class.java).apply {
                action = IncomingCallActionReceiver.ACTION_DECLINE_CALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerPerson = Person.Builder()
            .setName(displayTitle)
            .setImportant(true)
            .build()

        val callStyleNotification = NotificationCompat.Builder(context, CHANNEL_INCOMING_CALL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(displayTitle)
            .setContentText(subtitle)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    callerPerson,
                    declinePendingIntent,
                    answerPendingIntent
                )
            )
            .build()

        val posted = runCatching { nm.notify(NOTIFICATION_ID_CALL, callStyleNotification) }.isSuccess
        if (!posted) {
            val fallbackNotification = NotificationCompat.Builder(context, CHANNEL_INCOMING_CALL)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle(displayTitle)
                .setContentText(subtitle)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
                .addAction(android.R.drawable.sym_action_call, "Answer", answerPendingIntent)
                .build()
            runCatching { nm.notify(NOTIFICATION_ID_CALL, fallbackNotification) }
        }
    }

    fun showOngoingCall(
        context: Context,
        phoneNumber: String,
        callerName: String,
        isHolding: Boolean
    ) {
        if (!hasNotificationPermission(context)) return
        ensureChannels(context)

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val displayTitle = callerName.ifBlank { phoneNumber.ifBlank { "Active Call" } }
        val countryName = PhoneCountryResolver.resolveCountryName(context, phoneNumber)
        val subtitle = buildString {
            append(if (isHolding) "Call on hold" else "Ongoing call")
            if (countryName.isNotBlank()) append(" • $countryName")
        }

        val openCallIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_OPEN_ACTIVE_CALL, true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            4104,
            openCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangUpPendingIntent = PendingIntent.getBroadcast(
            context,
            4105,
            Intent(context, IncomingCallActionReceiver::class.java).apply {
                action = IncomingCallActionReceiver.ACTION_HANGUP_CALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use a standard ongoing call notification rather than CallStyle.forOngoingCall
        // because Android 14+ requires CallStyle notifications without fullScreenIntent
        // to be posted by a foreground service.
        val notification = NotificationCompat.Builder(context, CHANNEL_ONGOING_CALL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(displayTitle)
            .setContentText(subtitle)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setUsesChronometer(!isHolding)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hang Up", hangUpPendingIntent)
            .build()

        runCatching { nm.notify(NOTIFICATION_ID_CALL, notification) }
    }

    fun cancelCallNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(NOTIFICATION_ID_CALL)
    }
}
