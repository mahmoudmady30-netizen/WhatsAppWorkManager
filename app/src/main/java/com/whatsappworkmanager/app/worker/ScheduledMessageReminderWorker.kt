package com.whatsappworkmanager.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.utils.Constants
import com.whatsappworkmanager.app.utils.NotificationHelper
import com.whatsappworkmanager.app.service.WhatsAppNotificationListenerService
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume

/**
 * Fires at a user-scheduled time. Premium first attempts a true background send through an
 * already-posted WhatsApp notification's official Android Direct Reply/RemoteInput action, so
 * no WhatsApp screen needs to be opened. If that action is temporarily unavailable, the job
 * retries in the background instead of asking the user to press Send.
 */
class ScheduledMessageReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val text = inputData.getString(KEY_TEXT).orEmpty()
        val id = inputData.getLong(KEY_ID, -1L)
        val recipient = inputData.getString(KEY_RECIPIENT_NAME).orEmpty()
        val phoneNumber = inputData.getString(KEY_PHONE_NUMBER)
        val platform = inputData.getString(KEY_PLATFORM).orEmpty()
        if (id < 0 || text.isBlank()) return Result.failure()

        // Premium send path: ask the connected NotificationListenerService to use WhatsApp's
        // own notification Direct Reply action. The ordered broadcast gives this worker a real
        // success/failure result instead of showing a reminder immediately and hoping the
        // listener wins a race. This path does not launch WhatsApp and works with the screen off
        // or locked whenever WhatsApp exposes RemoteInput for the target notification.
        val request = Intent(WhatsAppNotificationListenerService.ACTION_SEND_SCHEDULED_MESSAGE).apply {
            setPackage(applicationContext.packageName)
            putExtra(WhatsAppNotificationListenerService.EXTRA_SCHEDULED_ID, id)
            putExtra(WhatsAppNotificationListenerService.EXTRA_SCHEDULED_TEXT, text)
            putExtra(WhatsAppNotificationListenerService.EXTRA_SCHEDULED_RECIPIENT, recipient)
            putExtra(WhatsAppNotificationListenerService.EXTRA_SCHEDULED_PHONE, phoneNumber)
            putExtra(WhatsAppNotificationListenerService.EXTRA_SCHEDULED_PLATFORM, platform)
        }

        val sent = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val resultReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (!continuation.isCompleted) continuation.resume(resultCode == android.app.Activity.RESULT_OK) {}
                }
            }
            applicationContext.sendOrderedBroadcast(
                request, null, resultReceiver, null, android.app.Activity.RESULT_CANCELED, null, null
            )
            continuation.invokeOnCancellation { /* ordered broadcast will simply finish */ }
        }

        if (sent) {
            NotificationHelper.cancelScheduledMessageReminder(
                applicationContext, id
            )
            return Result.success()
        }

        // Do not ask the user to press Send. Retry in the background so a temporarily disconnected
        // notification listener / WhatsApp notification can recover without manual intervention.
        // WorkManager applies backoff and will stop retrying if the schedule is cancelled.
        return Result.retry()
    }

    companion object {
        const val KEY_TEXT = "key_text"
        const val KEY_ID = "key_id"
        const val KEY_PHONE_NUMBER = "key_phone_number"
        const val KEY_RECIPIENT_NAME = "key_recipient_name"
        const val KEY_PLATFORM = "platform"
        const val KEY_REPEAT_DAILY = "key_repeat_daily"
        const val KEY_HOUR = "key_hour"
        const val KEY_MINUTE = "key_minute"
    }
}
