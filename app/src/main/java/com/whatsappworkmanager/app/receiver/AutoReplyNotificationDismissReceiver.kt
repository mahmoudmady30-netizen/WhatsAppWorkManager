package com.whatsappworkmanager.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/** Handles the explicit Delete action and the notification's swipe-away callback. */
class AutoReplyNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId >= 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "extra_auto_reply_notification_id"
    }
}
