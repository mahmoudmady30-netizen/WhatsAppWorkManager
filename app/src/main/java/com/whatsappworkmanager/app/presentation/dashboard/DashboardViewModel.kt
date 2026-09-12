package com.whatsappworkmanager.app.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkSummary
import com.whatsappworkmanager.app.worker.SummaryRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** One entry in the Dashboard's notification ticker — a recently-captured message, tagged
 *  with which stat card it belongs to so the ticker can say "New in <category>". */
data class RecentActivityItem(
    val category: RecentActivityCategory,
    val personLabel: String,
    val timestamp: Long,
    // How many of the recent messages this single ticker entry actually represents — a
    // sender appearing 3 times in the raw recent-messages list collapses to one entry with
    // messageCount = 3, rather than showing (and cycling through) the same name three times
    // in a row.
    val messageCount: Int = 1,
    val platform: com.whatsappworkmanager.app.domain.model.MessagingPlatform = com.whatsappworkmanager.app.domain.model.MessagingPlatform.WHATSAPP
)

enum class RecentActivityCategory { IMPORTANT, NEED_REPLY, NORMAL }

data class DashboardUiState(
    val isLoading: Boolean = true,
    val messagesToday: Int = 0,
    val workGroupsCount: Int = 0,
    val importantCount: Int = 0,
    val needReplyCount: Int = 0,
    // Distinct sender/group names behind that count, most-recent first — shown in small text
    // outside the Need Reply card itself, rather than making someone tap through to Search
    // just to see who's actually waiting on a reply.
    val needReplyNames: List<String> = emptyList(),
    val lastSummary: WorkSummary? = null,
    // The single soonest-to-fire enabled scheduled message, if any — surfaced on the
    // Dashboard so it's visible without a trip to the Scheduled Messages screen, alongside a
    // "Send Now" shortcut for firing it early.
    val nextScheduledMessage: com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage? = null,
    // How many enabled messages currently fall within the "coming up soon" window — shown as
    // a small "+N" next to the single one actually displayed, so a second one waiting isn't
    // silently hidden.
    val upcomingScheduledQueueSize: Int = 0,
    // Scheduled messages whose time already passed without ever being sent — the badge count
    // on the Schedule nav button.
    val missedScheduledCount: Int = 0,
    val lastAutoReply: com.whatsappworkmanager.app.data.prefs.SettingsDataStore.LastAutoReply? = null,
    val whatsappVariant: String = "auto",
    // The most recent handful of captured messages, tagged by category, for the ticker strip
    // above the Summary card — most recent first. Capped to a small number since this is a
    // glance-at ticker, not a substitute for Search.
    val recentActivity: List<RecentActivityItem> = emptyList(),
    val dismissedDashboardNotifications: Set<String> = emptySet(),
    val dismissedDashboardScheduled: Set<String> = emptySet(),
    val notificationAccessEnabled: Boolean = true,
    // Immediate feedback for "Generate Summary Now" — this call no longer goes through
    // WorkManager, so the result (or error) is known right away instead of being a
    // fire-and-forget background job with no visible outcome.
    val isGeneratingSummary: Boolean = false,
    val summaryGenerationResult: String? = null
)

class DashboardViewModel(private val app: WwmApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState

    init {
        observeData()
        refreshCounts()
        app.settingsDataStore.lastAutoReply.onEach { ready ->
            _uiState.value = _uiState.value.copy(lastAutoReply = ready)
        }.launchIn(viewModelScope)
        app.settingsDataStore.whatsappVariant.onEach { variant ->
            _uiState.value = _uiState.value.copy(whatsappVariant = variant)
        }.launchIn(viewModelScope)
        app.settingsDataStore.dismissedDashboardNotifications
            .distinctUntilChanged()
            .onEach { keys -> _uiState.value = _uiState.value.copy(dismissedDashboardNotifications = keys) }
            .launchIn(viewModelScope)
        app.settingsDataStore.dismissedDashboardScheduled
            .distinctUntilChanged()
            .onEach { keys -> _uiState.value = _uiState.value.copy(dismissedDashboardScheduled = keys) }
            .launchIn(viewModelScope)
    }

    private fun observeData() {
        combine(
            app.workGroupRepository.observeGroups(),
            app.messageRepository.observeMessages(),
            app.summaryRepository.observeSummaries(),
            app.scheduledMessageRepository.observeScheduledMessages()
        ) { groups, messages, summaries, scheduledMessages ->
            Data(groups, messages, summaries, scheduledMessages)
        }.onEach { (groups, messages, summaries, scheduledMessages) ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                workGroupsCount = groups.count { it.isEnabled },
                importantCount = messages.count { it.isImportant },
                needReplyCount = messages.count { it.needsReply && !it.isRead },
                needReplyNames = messages
                    .filter { it.needsReply && !it.isRead }
                    .sortedByDescending { it.timestamp }
                    .map { it.sender ?: it.groupName }
                    .distinct(),
                // Prefer the most recent summary that actually has content — otherwise
                // generating a fresh, genuinely-empty summary right after a real one would
                // make this card show "no messages" even though a real summary exists just
                // below it in the Summary screen. Falls back to the newest overall (even if
                // empty) only when nothing has ever had content yet.
                lastSummary = summaries.firstOrNull { it.totalMessages > 0 } ?: summaries.firstOrNull(),
                nextScheduledMessage = run {
                    val now = System.currentTimeMillis()
                    scheduledMessages
                        .filter { it.enabled }
                        // Only within the "coming up soon" window — requested explicitly, so
                        // this card isn't showing something that's still hours away.
                        .filter { nextOccurrenceMillis(it) - now <= UPCOMING_WINDOW_MILLIS }
                        .filter { scheduledDashboardDismissKey(it, nextOccurrenceMillis(it)) !in _uiState.value.dismissedDashboardScheduled }
                        // Soonest first; a tie broken by whichever was added first (lowest id)
                        // rather than leaving the order to chance.
                        .sortedWith(compareBy({ nextOccurrenceMillis(it) }, { it.id }))
                        .firstOrNull()
                },
                upcomingScheduledQueueSize = run {
                    val now = System.currentTimeMillis()
                    scheduledMessages.count {
                        it.enabled &&
                            nextOccurrenceMillis(it) - now <= UPCOMING_WINDOW_MILLIS &&
                            scheduledDashboardDismissKey(it, nextOccurrenceMillis(it)) !in _uiState.value.dismissedDashboardScheduled
                    }
                },
                missedScheduledCount = run {
                    val now = java.util.Calendar.getInstance()
                    scheduledMessages.count { isMissedOccurrence(it, now) }
                },
                recentActivity = run {
                    val recentMessages = messages.sortedByDescending { it.timestamp }.take(30)
                    // Grouped by sender/group so the same person doesn't show up (and get
                    // cycled through) multiple times in a row — one entry per person, with
                    // messageCount reflecting how many of their recent messages it stands
                    // for, keeping their most severe category (Important beats Need Reply
                    // beats Normal) and their single most recent timestamp for ordering.
                    recentMessages
                        .groupBy { (it.sender ?: it.groupName) to it.platform }
                        .map { (personAndPlatform, personMessages) ->
                            val personLabel = personAndPlatform.first
                            val platform = personAndPlatform.second
                            val category = when {
                                personMessages.any { it.isImportant } -> RecentActivityCategory.IMPORTANT
                                personMessages.any { it.needsReply } -> RecentActivityCategory.NEED_REPLY
                                else -> RecentActivityCategory.NORMAL
                            }
                            RecentActivityItem(
                                category = category,
                                personLabel = personLabel,
                                timestamp = personMessages.maxOf { it.timestamp },
                                messageCount = personMessages.size,
                                platform = platform
                            )
                        }
                        .sortedByDescending { it.timestamp }
                        .take(8)
                        .filter { it.dashboardDismissKey() !in _uiState.value.dismissedDashboardNotifications }
                }
            )
        }.launchIn(viewModelScope)
    }

    private data class Data(
        val groups: List<com.whatsappworkmanager.app.domain.model.WorkGroupInfo>,
        val messages: List<com.whatsappworkmanager.app.domain.model.WorkMessage>,
        val summaries: List<WorkSummary>,
        val scheduledMessages: List<com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage>
    )

    /**
     * The next real-world timestamp this message will fire at, from right now — today's
     * occurrence of its time-of-day if that hasn't passed yet; otherwise tomorrow's (for a
     * repeating message) or effectively "never" (for a one-time message whose time already
     * passed today — [Long.MAX_VALUE] so it naturally sorts last and never wins
     * `minByOrNull`, since WorkManager itself has already fired and will not fire it again).
     */
    private fun nextOccurrenceMillis(message: com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage): Long {
        val now = java.util.Calendar.getInstance()
        val candidate = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, message.timeMinutes / 60)
            set(java.util.Calendar.MINUTE, message.timeMinutes % 60)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (candidate.timeInMillis > now.timeInMillis) return candidate.timeInMillis
        if (message.repeatDaily) {
            candidate.add(java.util.Calendar.DAY_OF_MONTH, 1)
            return candidate.timeInMillis
        }
        return Long.MAX_VALUE
    }

    /**
     * The most recent occurrence of this message's time-of-day that has already happened, or
     * null if it simply hasn't fired yet (a one-time message whose time is still ahead).
     * Deliberately distinct from [nextOccurrenceMillis], which looks *forward*; this looks
     * *backward*, to answer "was there one to send, that already came and went."
     */
    private fun mostRecentPassedOccurrenceMillis(
        message: com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage,
        now: java.util.Calendar
    ): Long? {
        val todayOccurrence = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, message.timeMinutes / 60)
            set(java.util.Calendar.MINUTE, message.timeMinutes % 60)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return when {
            todayOccurrence.timeInMillis <= now.timeInMillis -> todayOccurrence.timeInMillis
            message.repeatDaily -> (todayOccurrence.clone() as java.util.Calendar)
                .apply { add(java.util.Calendar.DAY_OF_MONTH, -1) }.timeInMillis
            else -> null
        }
    }

    /**
     * True when this message had an occurrence that already passed without ever being sent —
     * counted toward the badge on the Schedule nav button. [ScheduledOutgoingMessage.lastSentAt]
     * is set from three places that all count equally as "handled": the Dashboard's own
     * "Send Now" button, the Scheduled Messages screen's Send button, and the reminder
     * notification's own tap action.
     */
    private fun isMissedOccurrence(
        message: com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage,
        now: java.util.Calendar
    ): Boolean {
        if (!message.enabled) return false
        val passed = mostRecentPassedOccurrenceMillis(message, now) ?: return false
        return message.lastSentAt == null || message.lastSentAt < passed
    }

    companion object {
        private const val UPCOMING_WINDOW_MILLIS = 30 * 60 * 1000L
    }

    private fun RecentActivityItem.dashboardDismissKey(): String =
        "message:${category.name}:${personLabel}:${timestamp}"

    private fun scheduledDashboardDismissKey(
        message: com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage,
        occurrenceMillis: Long
    ): String = "scheduled:${message.id}:$occurrenceMillis"

    fun dismissDashboardNotification(item: RecentActivityItem) {
        viewModelScope.launch {
            app.settingsDataStore.dismissDashboardNotification(item.dashboardDismissKey())
        }
    }

    fun dismissDashboardScheduled(message: com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage) {
        viewModelScope.launch {
            val occurrence = nextOccurrenceMillis(message)
            if (occurrence != Long.MAX_VALUE) {
                app.settingsDataStore.dismissDashboardScheduled(scheduledDashboardDismissKey(message, occurrence))
            }
        }
    }

    private fun refreshCounts() {
        viewModelScope.launch {
            val today = app.messageRepository.countToday()
            _uiState.value = _uiState.value.copy(messagesToday = today)
        }
    }

    /** Re-pulls everything that isn't already live via Flow (e.g. today's count) — used by the
     *  Dashboard's Refresh button, since WhatsApp notifications can lag behind Doze/battery
     *  restrictions and the user may want to force a re-check rather than wait. */
    fun refresh() {
        refreshCounts()
    }

    fun generateSummaryNow() {
        if (_uiState.value.isGeneratingSummary) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingSummary = true, summaryGenerationResult = null)
            try {
                val summary = SummaryRunner.runOnce(app)
                _uiState.value = _uiState.value.copy(
                    isGeneratingSummary = false,
                    summaryGenerationResult = app.getString(R.string.summary_ready_fmt, summary.totalMessages, summary.importantCount)
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingSummary = false,
                    summaryGenerationResult = app.getString(R.string.summary_generation_failed)
                )
            }
        }
    }

    fun dismissSummaryGenerationResult() {
        _uiState.value = _uiState.value.copy(summaryGenerationResult = null)
    }

    fun setNotificationAccessStatus(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notificationAccessEnabled = enabled)
    }

    /** Called once the ready reply has actually been sent (or the person dismisses it) from
     *  the Dashboard's own quick-send card, so a handled reply doesn't linger there stale. */
    fun clearLastAutoReply() {
        viewModelScope.launch { app.settingsDataStore.clearLastAutoReply() }
    }

    /** Records that this scheduled message was actually sent right now — called from the
     *  Dashboard's own "Send Now" button (the Scheduled Messages screen's Send button and the
     *  reminder notification's tap action call the same repository method directly). Clears
     *  it from the missed-count badge. */
    fun markScheduledMessageSent(message: com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage) {
        viewModelScope.launch {
            app.scheduledMessageRepository.upsert(message.copy(lastSentAt = System.currentTimeMillis()))
        }
    }

    class Factory(private val app: WwmApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(app) as T
    }
}
