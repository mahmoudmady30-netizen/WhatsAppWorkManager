package com.whatsappworkmanager.app.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.whatsappworkmanager.app.utils.Constants
import com.whatsappworkmanager.app.domain.model.MessagingPlatform
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Central place for scheduling background work. Scheduled messages use AlarmManager for the
 * closest possible delivery to the requested clock time (exact when Android grants exact-alarm
 * access). The alarm creates a durable send transaction; the notification listener and the
 * explicitly enabled AccessibilityService provide the two actual delivery lanes.
 */
object WorkScheduler {

    fun scheduleDailyCleanup(context: Context) {
        val request = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            Constants.WORKER_CLEANUP_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Every 15 minutes (WorkManager's minimum periodic interval), forces the notification
     * listener to reconnect and re-sync against every currently active WhatsApp notification —
     * see NotificationCatchUpWorker's doc for why this matters specifically for muted chats.
     * `KEEP` (not `UPDATE`) since there's no configuration to this job that would ever need
     * updating — call once at app startup and leave it alone.
     */
    fun scheduleNotificationCatchUp(context: Context) {
        val request = PeriodicWorkRequestBuilder<NotificationCatchUpWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            Constants.WORKER_NOTIFICATION_CATCH_UP_TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Schedules a recurring daily summary at [hour]:[minute] (24h, local time). */
    fun scheduleDailySummary(context: Context, scheduleId: Long, hour: Int, minute: Int) {
        val delay = millisUntilNext(hour, minute)
        val request = PeriodicWorkRequestBuilder<SummaryWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        // REPLACE, not UPDATE: UPDATE is specifically designed to preserve an already-running
        // periodic work's original phase/next-run time when only its input data or
        // constraints change — it does NOT actually respect a *new* initial delay on an
        // existing schedule. That's exactly why editing a schedule's time here previously had
        // no effect (it kept firing at the OLD time, or appeared to stop rescheduling
        // altogether). REPLACE genuinely cancels the old periodic work and starts fresh with
        // the new initial delay.
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "${Constants.WORKER_SUMMARY_TAG}_$scheduleId",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }

    fun runSummaryNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SummaryWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${Constants.WORKER_SUMMARY_TAG}_manual",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleMessageReminder(
        context: Context,
        id: Long,
        text: String,
        hour: Int,
        minute: Int,
        repeatDaily: Boolean = true,
        phoneNumber: String? = null,
        recipientName: String? = null,
        platform: MessagingPlatform = MessagingPlatform.WHATSAPP
    ) {
        cancelMessageReminder(context, id)
        scheduleNextMessageAlarm(context, id, text, hour, minute, phoneNumber, recipientName, repeatDaily, platform)
    }

    /** Schedules the next Premium send with AlarmManager; exact when the OS grants exact-alarm access. */
    fun scheduleNextMessageAlarm(
        context: Context,
        id: Long,
        text: String,
        hour: Int,
        minute: Int,
        phoneNumber: String?,
        recipientName: String?,
        repeatDaily: Boolean = true,
        platform: MessagingPlatform = MessagingPlatform.WHATSAPP
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, com.whatsappworkmanager.app.receiver.ScheduledMessageAlarmReceiver::class.java).apply {
            action = ACTION_SCHEDULED_MESSAGE_ALARM
            putExtra(ScheduledMessageReminderWorker.KEY_ID, id)
            putExtra(ScheduledMessageReminderWorker.KEY_TEXT, text)
            putExtra(ScheduledMessageReminderWorker.KEY_HOUR, hour)
            putExtra(ScheduledMessageReminderWorker.KEY_MINUTE, minute)
            putExtra(ScheduledMessageReminderWorker.KEY_REPEAT_DAILY, repeatDaily)
            phoneNumber?.let { putExtra(ScheduledMessageReminderWorker.KEY_PHONE_NUMBER, it) }
            recipientName?.let { putExtra(ScheduledMessageReminderWorker.KEY_RECIPIENT_NAME, it) }
            putExtra(ScheduledMessageReminderWorker.KEY_PLATFORM, platform.key)
        }
        val pending = PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = nextTriggerMillis(hour, minute)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /**
     * Cancels a previously scheduled message reminder (called when the user deletes it).
     */
    fun cancelMessageReminder(context: Context, id: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, com.whatsappworkmanager.app.receiver.ScheduledMessageAlarmReceiver::class.java).apply {
            action = ACTION_SCHEDULED_MESSAGE_ALARM
        }
        val pending = PendingIntent.getBroadcast(
            context, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
        WorkManager.getInstance(context).cancelUniqueWork("${Constants.WORKER_SCHEDULED_MESSAGE_TAG}_$id")
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long = millisUntilNext(hour, minute) + System.currentTimeMillis()

    const val ACTION_SCHEDULED_MESSAGE_ALARM = "com.whatsappworkmanager.app.ACTION_SCHEDULED_MESSAGE_ALARM"

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
