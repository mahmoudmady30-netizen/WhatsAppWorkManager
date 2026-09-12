package com.whatsappworkmanager.app.presentation.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage
import com.whatsappworkmanager.app.domain.model.MessagingPlatform
import com.whatsappworkmanager.app.worker.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ScheduledMessagesUiState(
    val isLoading: Boolean = true,
    val messages: List<ScheduledOutgoingMessage> = emptyList(),
    val whatsappVariant: String = "auto"
)

class ScheduledMessagesViewModel(private val app: WwmApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduledMessagesUiState())
    val uiState: StateFlow<ScheduledMessagesUiState> = _uiState

    init {
        app.scheduledMessageRepository.observeScheduledMessages()
            .onEach { messages -> _uiState.value = _uiState.value.copy(isLoading = false, messages = messages) }
            .launchIn(viewModelScope)
        app.settingsDataStore.whatsappVariant
            .onEach { variant -> _uiState.value = _uiState.value.copy(whatsappVariant = variant) }
            .launchIn(viewModelScope)
    }

    fun addMessage(text: String, hour: Int, minute: Int, repeatDaily: Boolean, phoneNumber: String? = null, recipientName: String? = null, platform: MessagingPlatform = MessagingPlatform.WHATSAPP) {
        viewModelScope.launch {
            val message = ScheduledOutgoingMessage(
                text = text,
                timeMinutes = hour * 60 + minute,
                repeatDaily = repeatDaily,
                phoneNumber = phoneNumber?.takeIf { it.isNotBlank() },
                recipientName = recipientName?.takeIf { it.isNotBlank() },
                platform = platform
            )
            val id = app.scheduledMessageRepository.upsert(message)
            WorkScheduler.scheduleMessageReminder(
                context = app,
                id = id,
                text = text,
                hour = hour,
                minute = minute,
                repeatDaily = repeatDaily,
                phoneNumber = message.phoneNumber,
                recipientName = message.recipientName,
                platform = message.platform
            )
        }
    }

    /** Updates an existing scheduled message in place and reschedules its WorkManager job. */
    fun updateMessage(
        id: Long,
        text: String,
        hour: Int,
        minute: Int,
        repeatDaily: Boolean,
        phoneNumber: String? = null,
        recipientName: String? = null,
        platform: MessagingPlatform = MessagingPlatform.WHATSAPP
    ) {
        viewModelScope.launch {
            val cleanedPhone = phoneNumber?.takeIf { it.isNotBlank() }
            val message = ScheduledOutgoingMessage(
                id = id,
                text = text,
                timeMinutes = hour * 60 + minute,
                repeatDaily = repeatDaily,
                phoneNumber = cleanedPhone,
                recipientName = recipientName?.takeIf { it.isNotBlank() },
                platform = platform
            )
            app.scheduledMessageRepository.upsert(message)
            com.whatsappworkmanager.app.service.ScheduledSendQueue.remove(app, id)
            // Re-scheduling with the same id replaces the existing WorkManager job (see
            // enqueueUniqueWork/enqueueUniquePeriodicWork policies in WorkScheduler), so there's
            // no separate "cancel first" step needed here.
            WorkScheduler.scheduleMessageReminder(
                context = app,
                id = id,
                text = text,
                hour = hour,
                minute = minute,
                repeatDaily = repeatDaily,
                phoneNumber = cleanedPhone,
                recipientName = message.recipientName,
                platform = message.platform
            )
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch { app.scheduledMessageRepository.delete(id) }
        com.whatsappworkmanager.app.service.ScheduledSendQueue.remove(app, id)
        WorkScheduler.cancelMessageReminder(app, id)
    }

    /** Records that this message was actually sent right now — same "handled" signal the
     *  Dashboard's own Send Now button and the reminder notification's tap action record,
     *  clearing it from the missed-count badge on the Schedule nav button. */
    fun markSent(message: ScheduledOutgoingMessage) {
        viewModelScope.launch {
            app.scheduledMessageRepository.upsert(message.copy(lastSentAt = System.currentTimeMillis()))
        }
    }

    class Factory(private val app: WwmApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScheduledMessagesViewModel(app) as T
    }
}
