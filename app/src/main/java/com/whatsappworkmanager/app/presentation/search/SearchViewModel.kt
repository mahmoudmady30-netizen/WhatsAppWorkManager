package com.whatsappworkmanager.app.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class MessageFilter { ALL, IMPORTANT, NEED_REPLY, UNREAD, READ, TODAY, YESTERDAY, THIS_WEEK }

data class SearchUiState(
    val query: String = "",
    val filter: MessageFilter = MessageFilter.ALL,
    val results: List<WorkMessage> = emptyList(),
    val isLoading: Boolean = false
)

class SearchViewModel(private val app: WwmApplication, initialFilter: MessageFilter) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState(filter = initialFilter))
    val uiState: StateFlow<SearchUiState> = _uiState

    private var allMessagesCache: List<WorkMessage> = emptyList()

    init {
        viewModelScope.launch {
            app.messageRepository.observeMessages().collect { messages ->
                allMessagesCache = messages
                applyFilters()
            }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        applyFilters()
    }

    fun onFilterChanged(filter: MessageFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
        applyFilters()
    }

    fun markAsRead(id: Long) = viewModelScope.launch {
        app.messageRepository.markRead(id)
        com.whatsappworkmanager.app.utils.BadgeUpdater.refresh(app)
    }

    /** Marks every message currently visible under the active filter as read — used by the
     *  "Read All" action, shown specifically on the Today filter (see SearchScreen). Returns
     *  the launched Job so tests can `.join()` it, same reasoning as [setImportant]. */
    fun markAllVisibleAsRead() = viewModelScope.launch {
        val toMark = _uiState.value.results.filter { !it.isRead }
        toMark.forEach { app.messageRepository.markRead(it.id) }
        if (toMark.isNotEmpty()) {
            com.whatsappworkmanager.app.utils.BadgeUpdater.refresh(app)
        }
    }

    /** Deletes every message currently visible under the active filter — same "whatever's in
     *  the current filtered results" scope as [markAllVisibleAsRead], paired with "Delete
     *  All" next to "Read All" on the Today filter. Returns the launched Job so tests (and the
     *  confirmation dialog, if it ever needs to wait) can be sure the deletion actually
     *  finished. */
    fun deleteAllVisible() = viewModelScope.launch {
        val toDelete = _uiState.value.results
        toDelete.forEach { app.messageRepository.delete(it.id) }
        if (toDelete.isNotEmpty()) {
            com.whatsappworkmanager.app.utils.BadgeUpdater.refresh(app)
        }
    }

    // Returns the launched Job (harmless for the app's own callers, which don't use it) so
    // tests can `.join()` it and know for certain the work has actually finished — including
    // Room's own suspend DAO calls inside, which hop onto Room's internal query executor
    // thread regardless of which dispatcher this coroutine itself is running on, so merely
    // controlling `Dispatchers.Main` in a test isn't enough to guarantee completion order.
    fun setImportant(message: WorkMessage, important: Boolean) = viewModelScope.launch {
        app.messageRepository.setImportant(message.id, important)
        com.whatsappworkmanager.app.utils.BadgeUpdater.refresh(app)
        // Marking something Important also teaches Reply Detection from it: add the
        // message's own text as a phrase (if it isn't already one), so this exact wording
        // is recognized as "needs a reply" going forward too — requested explicitly, and
        // only added (never removed) when un-starring, so it doesn't silently delete a
        // phrase the user may have already been relying on.
        if (important && message.text.isNotBlank()) {
            val existingPhrases = app.replyPhraseRuleRepository.getEnabledPhrasesOnce()
            val alreadyExists = existingPhrases.any { it.equals(message.text, ignoreCase = true) }
            if (!alreadyExists) {
                app.replyPhraseRuleRepository.upsert(
                    com.whatsappworkmanager.app.domain.model.ReplyPhraseRule(phrase = message.text, enabled = true)
                )
            }
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch { app.messageRepository.delete(id) }
    }

    private fun applyFilters() {
        val query = _uiState.value.query.lowercase()
        val filter = _uiState.value.filter
        val now = System.currentTimeMillis()
        val dayMillis = 24 * 60 * 60 * 1000L

        var filtered = allMessagesCache
        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.text.lowercase().contains(query) ||
                    it.groupName.lowercase().contains(query) ||
                    (it.sender?.lowercase()?.contains(query) == true)
            }
        }
        filtered = when (filter) {
            // Requested explicitly: "All" now means "everything that still needs a look" —
            // i.e. unread — rather than literally every message regardless of read state.
            // Once something is marked read (individually, or via "Read All"), it drops out
            // of this view and shows up under the Read filter instead, keeping this one
            // naturally down to what's actually pending.
            MessageFilter.ALL -> filtered.filter { !it.isRead }
            MessageFilter.IMPORTANT -> filtered.filter { it.isImportant }
            MessageFilter.NEED_REPLY -> filtered.filter { it.needsReply }
            MessageFilter.UNREAD -> filtered.filter { !it.isRead }
            MessageFilter.READ -> filtered.filter { it.isRead }
            MessageFilter.TODAY -> filtered.filter { now - it.timestamp < dayMillis }
            MessageFilter.YESTERDAY -> filtered.filter { (now - it.timestamp) in dayMillis..(2 * dayMillis) }
            MessageFilter.THIS_WEEK -> filtered.filter { now - it.timestamp < 7 * dayMillis }
        }
        _uiState.value = _uiState.value.copy(results = filtered)
    }

    class Factory(private val app: WwmApplication, private val initialFilter: MessageFilter) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(app, initialFilter) as T
    }
}
