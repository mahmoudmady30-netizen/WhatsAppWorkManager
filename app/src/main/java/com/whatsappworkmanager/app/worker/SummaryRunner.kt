package com.whatsappworkmanager.app.worker

import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkSummary
import kotlinx.coroutines.flow.first

/**
 * The actual "generate one summary" logic, shared between [SummaryWorker] (background/scheduled,
 * where WorkManager's own retry + Doze-aware timing apply) and the Dashboard's "Generate Summary
 * Now" button (which calls [runOnce] directly in the ViewModel's own coroutine scope instead of
 * going through WorkManager, so the user gets an immediate result or an immediate error — not a
 * fire-and-forget background job that might be deferred for a while under Doze with zero visible
 * feedback either way).
 *
 * Builds each summary from every message that's eligible (opted-in via its group's toggle) and
 * hasn't already been folded into a previous summary — see
 * `MessageDao.getPendingForSummary` — rather than a fixed time window since the last summary.
 * A pure time-window approach had a real bug: once any summary ran (even one covering zero
 * messages), the window advanced past that point, and a message captured before that point but
 * only made eligible afterwards (e.g. its group was enabled a bit late) could never appear in
 * any future summary. Tracking "already summarized" per message instead makes that impossible.
 */
object SummaryRunner {

    const val SIX_HOURS_MILLIS = 6 * 60 * 60 * 1000L

    suspend fun runOnce(app: WwmApplication): WorkSummary {
        val language = app.settingsDataStore.language.first().let {
            if (it == "system") defaultDeviceLanguage(app) else it
        }

        val messages = app.messageRepository.getPendingForSummary()
        // Distinguishes "genuinely nothing has happened" from "messages exist but nothing is
        // opted in yet" — the second case needs a different, actionable message (see
        // SummaryGenerator.buildLocalSummaryText) instead of a plain "no messages", which
        // looked wrong/broken next to a Dashboard that plainly shows a non-zero message count.
        val noEnabledGroupsHint = if (messages.isEmpty()) {
            val hasAnyCapturedMessages = app.messageRepository.observeMessages().first().isNotEmpty()
            val hasNoEnabledGroups = app.workGroupRepository.getEnabledGroupNames().isEmpty()
            hasAnyCapturedMessages && hasNoEnabledGroups
        } else {
            false
        }

        val periodEnd = System.currentTimeMillis()
        // Purely informational (shown as the summary's date range) — the actual message
        // selection above no longer depends on this window at all.
        val periodStart = messages.minOfOrNull { it.timestamp }
            ?: (app.summaryRepository.latest()?.periodEnd ?: (periodEnd - SIX_HOURS_MILLIS))

        val aiProvider = app.aiProviderFactory.resolveActiveProvider()
        val summary = app.summaryGenerator.generate(
            messages = messages,
            periodStart = periodStart,
            periodEnd = periodEnd,
            aiProvider = aiProvider,
            language = language,
            noEnabledGroupsHint = noEnabledGroupsHint
        )
        val savedId = app.summaryRepository.save(summary)
        // Only mark messages as "used" once the summary that covers them has actually been
        // saved — if saving somehow failed, we'd want to retry with the same messages next time
        // rather than silently losing them.
        app.messageRepository.markAllPendingAsSummarized()
        return summary.copy(id = savedId)
    }

    private fun defaultDeviceLanguage(app: WwmApplication): String {
        val locale = app.resources.configuration.locales[0]
        return if (locale.language == "ar") "ar" else "en"
    }
}
