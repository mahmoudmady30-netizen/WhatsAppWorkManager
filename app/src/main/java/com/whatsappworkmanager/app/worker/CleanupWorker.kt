package com.whatsappworkmanager.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.whatsappworkmanager.app.WwmApplication
import kotlinx.coroutines.flow.first

/**
 * Purges messages older than the user's configured retention window (7 / 30 / 90 days).
 * Keeps the local database bounded instead of growing forever.
 */
class CleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as WwmApplication
        return try {
            val retentionDays = app.settingsDataStore.retentionDays.first()
            val cutoff = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L)
            app.messageRepository.purgeOlderThan(cutoff)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
