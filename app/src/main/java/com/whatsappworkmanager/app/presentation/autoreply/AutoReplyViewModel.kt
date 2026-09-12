package com.whatsappworkmanager.app.presentation.autoreply

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.AutoReplyRule
import com.whatsappworkmanager.app.domain.model.AutoReplyReply
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class AutoReplyUiState(
    val isLoading: Boolean = true,
    val rules: List<AutoReplyRule> = emptyList(),
    val replyHistory: List<AutoReplyReply> = emptyList()
)

class AutoReplyViewModel(private val app: WwmApplication) : ViewModel() {
    private val _uiState = MutableStateFlow(AutoReplyUiState())
    val uiState: StateFlow<AutoReplyUiState> = _uiState

    init {
        app.autoReplyRuleRepository.observeRules()
            .onEach { rules ->
                _uiState.value = _uiState.value.copy(isLoading = false, rules = rules)
            }
            .launchIn(viewModelScope)
        app.autoReplyReplyHistoryRepository.observeAll()
            .onEach { history ->
                _uiState.value = _uiState.value.copy(replyHistory = history)
            }
            .launchIn(viewModelScope)
    }

    fun addRule(
        personMatch: String, phoneNumber: String?, keyword: String?, replyText: String?,
        aiInstruction: String?, autoSend: Boolean,
        tone: com.whatsappworkmanager.app.domain.model.ReplyTone,
        platform: com.whatsappworkmanager.app.domain.model.MessagingPlatform
    ) {
        // Blank personMatch is a valid global rule: it applies to any incoming WhatsApp message.
        viewModelScope.launch {
            app.autoReplyRuleRepository.upsert(
                AutoReplyRule(
                    personMatch = personMatch.trim(),
                    phoneNumber = phoneNumber?.trim()?.ifBlank { null },
                    keyword = keyword?.trim()?.ifBlank { null },
                    replyText = replyText?.trim()?.ifBlank { null },
                    aiInstruction = aiInstruction?.trim()?.ifBlank { null },
                    autoSend = autoSend,
                    enabled = true,
                    tone = tone,
                    platform = platform
                )
            )
        }
    }

    fun updateRule(
        rule: AutoReplyRule, personMatch: String, phoneNumber: String?, keyword: String?,
        replyText: String?, aiInstruction: String?, autoSend: Boolean,
        tone: com.whatsappworkmanager.app.domain.model.ReplyTone,
        platform: com.whatsappworkmanager.app.domain.model.MessagingPlatform
    ) {
        // Blank personMatch is a valid global rule: it applies to any incoming WhatsApp message.
        viewModelScope.launch {
            app.autoReplyRuleRepository.upsert(
                rule.copy(
                    personMatch = personMatch.trim(),
                    phoneNumber = phoneNumber?.trim()?.ifBlank { null },
                    keyword = keyword?.trim()?.ifBlank { null },
                    replyText = replyText?.trim()?.ifBlank { null },
                    aiInstruction = aiInstruction?.trim()?.ifBlank { null },
                    autoSend = autoSend,
                    tone = tone,
                    updatedAt = System.currentTimeMillis(),
                    platform = platform
                )
            )
        }
    }

    fun setTone(rule: AutoReplyRule, tone: com.whatsappworkmanager.app.domain.model.ReplyTone) {
        viewModelScope.launch {
            app.autoReplyRuleRepository.upsert(rule.copy(tone = tone, updatedAt = System.currentTimeMillis()))
        }
    }

    fun setEnabled(rule: AutoReplyRule, enabled: Boolean) {
        viewModelScope.launch { app.autoReplyRuleRepository.upsert(rule.copy(enabled = enabled)) }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            app.autoReplyRuleRepository.delete(id)
            app.autoReplyReplyHistoryRepository.deleteForRule(id)
        }
    }

    class Factory(private val app: WwmApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AutoReplyViewModel(app) as T
    }
}
