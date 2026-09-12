package com.whatsappworkmanager.app.presentation.scheduling

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.ScheduleKind
import com.whatsappworkmanager.app.domain.model.WorkSchedule
import com.whatsappworkmanager.app.worker.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class WorkSchedulesUiState(
    val isLoading: Boolean = true,
    val summarySchedules: List<WorkSchedule> = emptyList(),
    val modeWindows: List<WorkSchedule> = emptyList()
)

class WorkSchedulesViewModel(private val app: WwmApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkSchedulesUiState())
    val uiState: StateFlow<WorkSchedulesUiState> = _uiState

    init {
        app.scheduleRepository.observeSchedules()
            .onEach { all ->
                _uiState.value = WorkSchedulesUiState(
                    isLoading = false,
                    summarySchedules = all.filter { it.kind == ScheduleKind.SUMMARY },
                    modeWindows = all.filter { it.kind != ScheduleKind.SUMMARY }
                )
            }
            .launchIn(viewModelScope)
    }

    /** Adds (or replaces) a recurring daily summary trigger at [hour]:[minute]. */
    fun addSummarySchedule(name: String, hour: Int, minute: Int) {
        viewModelScope.launch {
            val schedule = WorkSchedule(
                name = name,
                startTimeMinutes = hour * 60 + minute,
                endTimeMinutes = null,
                enabled = true,
                kind = ScheduleKind.SUMMARY,
                repeatDaily = true
            )
            val id = app.scheduleRepository.upsert(schedule)
            WorkScheduler.scheduleDailySummary(app, id, hour, minute)
        }
    }

    /** Adds a Work / Break / Quiet window between [startHour]:[startMinute] and [endHour]:[endMinute]. */
    fun addModeWindow(
        name: String,
        kind: ScheduleKind,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        viewModelScope.launch {
            val schedule = WorkSchedule(
                name = name,
                startTimeMinutes = startHour * 60 + startMinute,
                endTimeMinutes = endHour * 60 + endMinute,
                enabled = true,
                kind = kind,
                repeatDaily = true
            )
            app.scheduleRepository.upsert(schedule)
        }
    }

    fun setEnabled(schedule: WorkSchedule, enabled: Boolean) {
        viewModelScope.launch { app.scheduleRepository.upsert(schedule.copy(enabled = enabled)) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { app.scheduleRepository.delete(id) }
    }

    class Factory(private val app: WwmApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkSchedulesViewModel(app) as T
    }
}
