package com.whatsappworkmanager.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.WwmApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Result of pinging the currently-selected AI provider — drives the green/red/gray dot in Settings. */
enum class AiConnectionStatus { UNTESTED, TESTING, CONNECTED, FAILED }

data class SettingsUiState(
    val language: String = "system",
    val theme: String = "premium",
    val aiProvider: String = "groq",
    val aiCloudConsent: Boolean = false,
    val retentionDays: Int = 30,
    val aiConnectionStatus: AiConnectionStatus = AiConnectionStatus.UNTESTED,
    val aiConnectionError: String? = null,
    val whatsappVariant: String = "auto",
    val autoEnableNewGroups: Boolean = false,
    val autoReplyEnabled: Boolean = false,
    val autoReplyDelaySeconds: Int = 8
)

class SettingsViewModel(private val app: WwmApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        combine(
            app.settingsDataStore.language,
            app.settingsDataStore.theme,
            app.settingsDataStore.aiProvider,
            app.settingsDataStore.aiCloudConsent,
            app.settingsDataStore.retentionDays
        ) { language, theme, provider, consent, retention ->
            Quintuple(language, theme, provider, consent, retention)
        }.combine(app.settingsDataStore.whatsappVariant) { quintuple, variant ->
            quintuple to variant
        }.combine(app.settingsDataStore.autoEnableNewGroups) { pair, autoEnable ->
            Triple(pair.first, pair.second, autoEnable)
        }.combine(app.settingsDataStore.autoReplyEnabled) { triple, autoReply ->
            triple to autoReply
        }.combine(app.settingsDataStore.autoReplyDelaySeconds) { pair, delay ->
            pair to delay
        }.onEach { (pair, delay) ->
            val (triple, autoReply) = pair
            val (quintuple, variant, autoEnable) = triple
            val (language, theme, provider, consent, retention) = quintuple
            // .copy(...) here, NOT a fresh SettingsUiState(...) — a plain replacement would
            // reset aiConnectionStatus/aiConnectionError back to their defaults every time any
            // of these five preferences re-emits (e.g. mid-test, if the provider dropdown value
            // itself is what just changed), wiping out the connection-test result the user is
            // actively looking at.
            val previousProvider = _uiState.value.aiProvider
            _uiState.value = _uiState.value.copy(
                language = language,
                theme = theme,
                aiProvider = provider,
                aiCloudConsent = consent,
                retentionDays = retention,
                whatsappVariant = variant,
                autoEnableNewGroups = autoEnable,
                autoReplyEnabled = autoReply,
                autoReplyDelaySeconds = delay,
                // Switching providers invalidates any previous test result — a green light for
                // OpenAI shouldn't linger after switching to Gemini.
                aiConnectionStatus = if (provider != previousProvider) AiConnectionStatus.UNTESTED else _uiState.value.aiConnectionStatus,
                aiConnectionError = if (provider != previousProvider) null else _uiState.value.aiConnectionError
            )
        }.launchIn(viewModelScope)
    }

    fun setLanguage(value: String) {
        // Applied synchronously & immediately (see LocaleHelper) so the UI switches language
        // right away, in addition to the async DataStore write that keeps this screen's own
        // dropdown showing the right current selection.
        com.whatsappworkmanager.app.utils.LocaleHelper.setLanguage(app, value)
        viewModelScope.launch { app.settingsDataStore.setLanguage(value) }
    }
    fun setTheme(value: String) = viewModelScope.launch { app.settingsDataStore.setTheme(value) }
    fun setAiProvider(value: String) = viewModelScope.launch { app.settingsDataStore.setAiProvider(value) }
    fun setAiCloudConsent(value: Boolean) = viewModelScope.launch { app.settingsDataStore.setAiCloudConsent(value) }
    fun setRetentionDays(value: Int) = viewModelScope.launch { app.settingsDataStore.setRetentionDays(value) }
    fun setWhatsappVariant(value: String) = viewModelScope.launch { app.settingsDataStore.setWhatsappVariant(value) }
    fun setAutoEnableNewGroups(value: Boolean) = viewModelScope.launch { app.settingsDataStore.setAutoEnableNewGroups(value) }
    fun setAutoReplyEnabled(value: Boolean) = viewModelScope.launch { app.settingsDataStore.setAutoReplyEnabled(value) }
    fun setAutoReplyDelaySeconds(value: Int) = viewModelScope.launch { app.settingsDataStore.setAutoReplyDelaySeconds(value) }

    fun saveApiKey(providerId: String, key: String) {
        app.settingsDataStore.setApiKey(providerId, key)
        // A newly-saved key invalidates whatever the last test result was.
        _uiState.value = _uiState.value.copy(aiConnectionStatus = AiConnectionStatus.UNTESTED, aiConnectionError = null)
    }

    /**
     * Actually calls the selected provider (a tiny real request — `suggestReplies` on a short
     * test string) and reports whether it genuinely worked, rather than assuming a saved key
     * and a green-looking Settings screen mean the API calls succeed. This is what the
     * green/red dot next to "AI Provider" reflects. The Local provider always reports
     * connected immediately (it's on-device, nothing to fail).
     */
    fun testAiConnection() {
        if (_uiState.value.aiConnectionStatus == AiConnectionStatus.TESTING) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(aiConnectionStatus = AiConnectionStatus.TESTING, aiConnectionError = null)

            val providerId = _uiState.value.aiProvider
            if (providerId == "local") {
                _uiState.value = _uiState.value.copy(aiConnectionStatus = AiConnectionStatus.CONNECTED, aiConnectionError = null)
                return@launch
            }

            val provider = app.aiProviderFactory.resolveActiveProvider()
            if (provider == null) {
                val reason = if (!_uiState.value.aiCloudConsent) {
                    app.getString(R.string.ai_test_error_consent_needed)
                } else {
                    app.getString(R.string.ai_test_error_no_api_key_fmt, providerId)
                }
                _uiState.value = _uiState.value.copy(aiConnectionStatus = AiConnectionStatus.FAILED, aiConnectionError = reason)
                return@launch
            }

            try {
                val replies = provider.suggestReplies("Hello, just testing the connection.", "en")
                if (replies.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        aiConnectionStatus = AiConnectionStatus.FAILED,
                        aiConnectionError = app.getString(R.string.ai_test_error_empty_response)
                    )
                } else {
                    _uiState.value = _uiState.value.copy(aiConnectionStatus = AiConnectionStatus.CONNECTED, aiConnectionError = null)
                }
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    aiConnectionStatus = AiConnectionStatus.FAILED,
                    aiConnectionError = t.message ?: t::class.simpleName ?: "Connection failed"
                )
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            app.messageRepository.deleteAll()
            app.workGroupRepository.deleteAll()
            app.summaryRepository.deleteAll()
        }
    }

    class Factory(private val app: WwmApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(app) as T
    }
}

private data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
