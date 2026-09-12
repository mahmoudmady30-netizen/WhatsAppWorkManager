package com.whatsappworkmanager.app.presentation.replyphrases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.ReplyPhraseRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ReplyPhrasesUiState(
    val isLoading: Boolean = true,
    val rules: List<ReplyPhraseRule> = emptyList()
)

class ReplyPhrasesViewModel(private val app: WwmApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(ReplyPhrasesUiState())
    val uiState: StateFlow<ReplyPhrasesUiState> = _uiState

    init {
        app.replyPhraseRuleRepository.observeRules()
            .onEach { rules -> _uiState.value = ReplyPhrasesUiState(isLoading = false, rules = rules) }
            .launchIn(viewModelScope)
    }

    fun addPhrase(phrase: String) {
        if (phrase.isBlank()) return
        viewModelScope.launch {
            app.replyPhraseRuleRepository.upsert(ReplyPhraseRule(phrase = phrase.trim(), enabled = true))
        }
    }

    fun setEnabled(rule: ReplyPhraseRule, enabled: Boolean) {
        viewModelScope.launch { app.replyPhraseRuleRepository.upsert(rule.copy(enabled = enabled)) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { app.replyPhraseRuleRepository.delete(id) }
    }

    class Factory(private val app: WwmApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReplyPhrasesViewModel(app) as T
    }
}
