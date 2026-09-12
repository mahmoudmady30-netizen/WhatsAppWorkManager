package com.whatsappworkmanager.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.whatsappworkmanager.app.service.WhatsAppNotificationListenerService
import com.whatsappworkmanager.app.service.ScheduledSendQueue
import com.whatsappworkmanager.app.worker.ScheduledMessageReminderWorker
import com.whatsappworkmanager.app.worker.WorkScheduler
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.utils.IntentHelper
import com.whatsappworkmanager.app.domain.model.MessagingPlatform
import kotlinx.coroutines.flow.first

/**
 * Persistent alarm bridge for Premium scheduled messages.
 *
 * The alarm never treats the notification as the send itself. It records the schedule as a
 * pending-send transaction, then asks the NotificationListenerService to reconnect and execute
 * the transaction against WhatsApp's own Direct Reply action. This survives app-process death and
 * does not depend on WorkManager being able to start a service at exactly the scheduled second.
 */
class ScheduledMessageAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WorkScheduler.ACTION_SCHEDULED_MESSAGE_ALARM) return

        val id = intent.getLongExtra(ScheduledMessageReminderWorker.KEY_ID, -1L)
        if (id < 0) return

        val appContext = context.applicationContext
        // Two independent local delivery lanes are queued at the exact alarm boundary:
        // 1) WhatsApp's Direct Reply RemoteInput (works even with the screen locked when WhatsApp
        //    exposes it), and 2) a durable Accessibility fallback for devices where the target
        //    notification has no RemoteInput. The second lane waits safely until WhatsApp UI is
        //    available and never marks the message sent until the Send control is clicked.
        val preferredVariant = kotlinx.coroutines.runBlocking {
            (appContext as? WwmApplication)?.settingsDataStore?.whatsappVariant?.first()
                ?: IntentHelper.WHATSAPP_VARIANT_AUTO
        }
        val platform = MessagingPlatform.fromKey(intent.getStringExtra(ScheduledMessageReminderWorker.KEY_PLATFORM))
        val targetPackage = when (platform) {
            MessagingPlatform.MESSENGER -> com.whatsappworkmanager.app.utils.IntentHelper.resolveMessagingPackage(appContext.packageManager, platform)
            else -> IntentHelper.resolveWhatsAppPackage(appContext.packageManager, preferredVariant)
        }
        WhatsAppNotificationListenerService.enqueuePendingScheduledSend(appContext, id, targetPackage)
        ScheduledSendQueue.enqueue(
            appContext,
            ScheduledSendQueue.Job(
                id = id,
                text = intent.getStringExtra(ScheduledMessageReminderWorker.KEY_TEXT).orEmpty(),
                recipientName = intent.getStringExtra(ScheduledMessageReminderWorker.KEY_RECIPIENT_NAME),
                phoneNumber = intent.getStringExtra(ScheduledMessageReminderWorker.KEY_PHONE_NUMBER),
                packageName = targetPackage,
                platform = platform.key
            )
        )
        WhatsAppNotificationListenerService.triggerCatchUpScan(appContext)

        // Recurring alarms are independent of the current send attempt. A pending transaction
        // remains stored until it is actually acknowledged as sent.
        if (intent.getBooleanExtra(ScheduledMessageReminderWorker.KEY_REPEAT_DAILY, false)) {
            WorkScheduler.scheduleNextMessageAlarm(
                context = appContext,
                id = id,
                text = intent.getStringExtra(ScheduledMessageReminderWorker.KEY_TEXT).orEmpty(),
                hour = intent.getIntExtra(ScheduledMessageReminderWorker.KEY_HOUR, 0),
                minute = intent.getIntExtra(ScheduledMessageReminderWorker.KEY_MINUTE, 0),
                phoneNumber = intent.getStringExtra(ScheduledMessageReminderWorker.KEY_PHONE_NUMBER),
                recipientName = intent.getStringExtra(ScheduledMessageReminderWorker.KEY_RECIPIENT_NAME),
                platform = platform
            )
        }
    }
}
