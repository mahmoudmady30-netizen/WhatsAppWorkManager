package com.whatsappworkmanager.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whatsappworkmanager.app.WwmApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Sits between a scheduled-message reminder notification and the actual WhatsApp/MainActivity
 * intent it opens — tapping the notification lands here first, which records the message as
 * "sent" (clearing it from the missed-count badge on the Schedule nav button) and then
 * immediately forwards to the real destination, so from the person's point of view tapping
 * the notification does exactly what it always did; the bookkeeping is invisible.
 *
 * Uses `goAsync()` since BroadcastReceiver.onReceive isn't itself suspending — the receiver
 * update runs on a short-lived coroutine, with the forwarding intent launched immediately
 * (not blocked on that write finishing) so there's no perceptible delay opening WhatsApp.
 */
class ScheduledMessageTapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        val forwardIntent = intent.getParcelableExtra<Intent>(EXTRA_FORWARD_INTENT)

        if (forwardIntent != null) {
            forwardIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(forwardIntent)
        }

        if (messageId >= 0) {
            val pendingResult = goAsync()
            val app = context.applicationContext as WwmApplication
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val message = app.scheduledMessageRepository.observeScheduledMessages()
                        .first()
                        .firstOrNull { it.id == messageId }
                    if (message != null) {
                        app.scheduledMessageRepository.upsert(message.copy(lastSentAt = System.currentTimeMillis()))
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_FORWARD_INTENT = "extra_forward_intent"
    }
}
