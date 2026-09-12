package com.whatsappworkmanager.app.presentation.workgroups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkGroupInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class WorkGroupsUiState(
    val isLoading: Boolean = true,
    val groups: List<WorkGroupInfo> = emptyList(),
    // Feedback for the manual "Add" dialog — e.g. "already exists" — shown then cleared.
    val addResultMessage: String? = null
)

class WorkGroupsViewModel(private val app: WwmApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkGroupsUiState())
    val uiState: StateFlow<WorkGroupsUiState> = _uiState

    init {
        combine(
            app.workGroupRepository.observeGroups(),
            app.messageRepository.observeMessages()
        ) { groups, messages -> groups to messages }
            .onEach { (groups, messages) ->
                // Defensive UI-level de-duplication for databases created by older builds that
                // could race two notification callbacks before the upsert was serialized.
                // New writes are also serialized in WorkGroupRepositoryImpl, but merging here
                // makes an upgrade immediately clean on screen without deleting any history.
                val merged = groups.groupBy { it.name.trim().lowercase() }.values.map { bucket ->
                    val first = bucket.maxByOrNull { it.lastMessageTime } ?: bucket.first()
                    first.copy(
                        messageCount = bucket.sumOf { it.messageCount },
                        importantCount = bucket.sumOf { it.importantCount },
                        isEnabled = bucket.any { it.isEnabled },
                        lastMessageTime = bucket.maxOf { it.lastMessageTime },
                        platforms = messages.filter { it.groupName.trim().equals(first.name.trim(), ignoreCase = true) }
                            .map { it.platform }.toSet()
                    )
                }.sortedByDescending { it.lastMessageTime }
                _uiState.value = _uiState.value.copy(isLoading = false, groups = merged)
            }
            .launchIn(viewModelScope)
    }

    fun setGroupEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            app.workGroupRepository.setEnabled(name, enabled)
            // Retroactively include/exclude this group's *existing* messages too — without
            // this, a message captured before you got around to flipping the switch on would
            // stay permanently excluded from every future summary, even though the group is
            // now enabled.
            app.messageRepository.setIncludedForGroup(name, enabled)
        }
    }

    /** Removes a group/client entirely from this list — see WorkGroupRepository.delete's doc
     *  for what this does and doesn't touch. Returns the launched Job so tests (and, if ever
     *  needed, calling code) can wait for the deletion to genuinely complete. */
    fun deleteGroup(name: String) = viewModelScope.launch {
        app.workGroupRepository.delete(name)
    }

    /**
     * Registers a group or contact by name before any message from them has arrived — for
     * fully-muted threads that might never post a single notification otherwise. Already
     * enabled on creation, so it starts being tracked (and included in summaries) from the
     * very next message WhatsApp posts for that name, silent or not.
     */
    fun addManually(name: String) {
        viewModelScope.launch {
            val added = app.workGroupRepository.addManually(name)
            _uiState.value = _uiState.value.copy(
                addResultMessage = if (added) {
                    "Added \"${name.trim()}\" — it'll start showing up from the next message."
                } else {
                    "\"${name.trim()}\" is already in the list below."
                }
            )
        }
    }

    fun dismissAddResultMessage() {
        _uiState.value = _uiState.value.copy(addResultMessage = null)
    }

    class Factory(private val app: WwmApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkGroupsViewModel(app) as T
    }
}
