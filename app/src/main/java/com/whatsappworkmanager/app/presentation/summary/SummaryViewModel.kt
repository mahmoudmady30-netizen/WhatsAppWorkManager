package com.whatsappworkmanager.app.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SummaryUiState(
    val isLoading: Boolean = true,
    val summaries: List<WorkSummary> = emptyList()
)

class SummaryViewModel(private val app: WwmApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState

    init {
        app.summaryRepository.observeSummaries()
            .onEach { summaries ->
                // Once at least one summary has real content, hide older empty ("no messages
                // during this period") ones from the list — seeing a stale "nothing happened"
                // entry sitting right next to (or above) a summary that clearly has messages
                // read as broken/contradictory. If literally everything is still empty (a
                // brand-new setup with nothing captured yet), keep showing them so the screen
                // isn't confusingly blank instead.
                val hasRealSummary = summaries.any { it.totalMessages > 0 }
                val visible = if (hasRealSummary) summaries.filter { it.totalMessages > 0 } else summaries
                _uiState.value = SummaryUiState(isLoading = false, summaries = visible)
            }
            .launchIn(viewModelScope)
    }

    /** Pinned summaries sort to the top (see SummaryDao.observeAll's ORDER BY) — this only
     *  flips the flag; the resulting re-sort happens automatically via the Flow above. */
    fun togglePinned(summary: WorkSummary) = viewModelScope.launch {
        app.summaryRepository.setPinned(summary.id, !summary.isPinned)
    }

    fun deleteSummary(id: Long) = viewModelScope.launch {
        app.summaryRepository.delete(id)
    }

    class Factory(private val app: WwmApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SummaryViewModel(app) as T
    }
}
