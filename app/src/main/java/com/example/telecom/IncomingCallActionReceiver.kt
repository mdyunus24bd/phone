package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.MainActivity

/**
 * Handles Answer, Decline, and Hang Up actions tapped directly from the over-display
 * CallStyle incoming/ongoing call banner.
 */
class IncomingCallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER_CALL = "com.example.telecom.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE_CALL = "com.example.telecom.ACTION_DECLINE_CALL"
        const val ACTION_HANGUP_CALL = "com.example.telecom.ACTION_HANGUP_CALL"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_ANSWER_CALL -> {
                ActiveTelecomCallBus.answer()
                val openCallUi = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra(IncomingCallNotificationManager.EXTRA_OPEN_ACTIVE_CALL, true)
                }
                runCatching { context.startActivity(openCallUi) }
            }

            ACTION_DECLINE_CALL -> {
                ActiveTelecomCallBus.reject()
                IncomingCallNotificationManager.cancelCallNotification(context)
            }

            ACTION_HANGUP_CALL -> {
                ActiveTelecomCallBus.disconnect()
                IncomingCallNotificationManager.cancelCallNotification(context)
            }
        }
    }
}
