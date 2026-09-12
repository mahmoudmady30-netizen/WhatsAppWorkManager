package com.whatsappworkmanager.app.presentation.keywords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.KeywordRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class KeywordRulesUiState(
    val isLoading: Boolean = true,
    val rules: List<KeywordRule> = emptyList()
)

class KeywordRulesViewModel(private val app: WwmApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(KeywordRulesUiState())
    val uiState: StateFlow<KeywordRulesUiState> = _uiState

    init {
        app.keywordRuleRepository.observeRules()
            .onEach { rules -> _uiState.value = KeywordRulesUiState(isLoading = false, rules = rules) }
            .launchIn(viewModelScope)
    }

    fun addRule(keyword: String, priority: Int) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            app.keywordRuleRepository.upsert(KeywordRule(keyword = keyword.trim(), priority = priority, enabled = true))
        }
    }

    fun updateRule(rule: KeywordRule, keyword: String, priority: Int) {
        if (keyword.isBlank()) return
        viewModelScope.launch {
            app.keywordRuleRepository.upsert(rule.copy(keyword = keyword.trim(), priority = priority))
        }
    }

    fun setEnabled(rule: KeywordRule, enabled: Boolean) {
        viewModelScope.launch { app.keywordRuleRepository.upsert(rule.copy(enabled = enabled)) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { app.keywordRuleRepository.delete(id) }
    }

    class Factory(private val app: WwmApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = KeywordRulesViewModel(app) as T
    }
}
