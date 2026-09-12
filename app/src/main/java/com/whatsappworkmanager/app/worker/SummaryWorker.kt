package com.whatsappworkmanager.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.utils.NotificationHelper

/**
 * Generates a Work Summary covering "since the last summary" (or the last 6 hours if there
 * is none yet), saves it, and posts a summary notification. Triggered periodically via
 * WorkManager (see Summary Schedule in Settings). The Dashboard's "Generate Summary Now"
 * button does NOT go through this Worker — it calls [SummaryRunner] directly for an immediate
 * result instead of a background job that WorkManager might defer.
 */
class SummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as WwmApplication
        return try {
            val summary = SummaryRunner.runOnce(app)

            val title = "📊 " + applicationContext.getString(com.whatsappworkmanager.app.R.string.summary_title)
            val body = buildString {
                append(summary.text.lineSequence().firstOrNull().orEmpty())
                append("\n\n")
                append("${summary.groupCount} groups · ${summary.totalMessages} messages · ${summary.importantCount} important")
            }
            NotificationHelper.showSummaryNotification(applicationContext, title, body, summary.id)

            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }
}
