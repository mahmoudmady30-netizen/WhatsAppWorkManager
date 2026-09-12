package com.whatsappworkmanager.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.whatsappworkmanager.app.service.WhatsAppNotificationListenerService

/**
 * Every 15 minutes (WorkManager's minimum periodic interval), forces the notification listener
 * to disconnect and reconnect — which re-triggers a full catch-up scan against every currently
 * active WhatsApp notification (see WhatsAppNotificationListenerService.performCatchUpScan).
 * This is the automated half of the same mechanism the Dashboard's Refresh button triggers
 * on-demand: rather than only ever reacting to `onNotificationPosted` events and hoping none
 * are ever missed or delayed, this periodically asks the system directly for the ground truth
 * of what's actually there right now — a genuine reconciliation, safe to run repeatedly since
 * the existing per-message dedup check makes re-scanning something already captured a no-op.
 */
class NotificationCatchUpWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WhatsAppNotificationListenerService.triggerCatchUpScan(applicationContext)
        return Result.success()
    }
}
