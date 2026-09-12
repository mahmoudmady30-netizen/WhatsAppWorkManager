package com.whatsappworkmanager.app.data.ai

import com.whatsappworkmanager.app.data.prefs.SettingsDataStore
import com.whatsappworkmanager.app.domain.repository.AiSummaryProvider
import kotlinx.coroutines.flow.first

/**
 * Resolves the currently-configured [AiSummaryProvider], honoring explicit cloud consent.
 * Returns null (meaning "use local only") if:
 *  - the selected provider is "local", or
 *  - a cloud provider is selected but the user has not given cloud-consent, or
 *  - no API key has been saved for that provider.
 * The caller (SummaryGenerator) already falls back to the local rule-based text on any
 * exception, so this is a defense-in-depth check, not the only one.
 */
class AiProviderFactory(private val settings: SettingsDataStore) {

    suspend fun resolveActiveProvider(): AiSummaryProvider? {
        val providerId = settings.aiProvider.first()
        if (providerId == "local") return null

        val consented = settings.aiCloudConsent.first()
        if (!consented) return null

        val keyProvider: () -> String? = { settings.getApiKey(providerId) }
        return when (providerId) {
            "openai" -> OpenAiProvider(keyProvider)
            "anthropic" -> AnthropicProvider(keyProvider)
            "gemini" -> GeminiProvider(keyProvider)
            "grok" -> GrokProvider(keyProvider)
            "groq" -> GroqProvider(keyProvider)
            else -> null
        }
    }

    fun localProvider(): AiSummaryProvider = LocalRuleBasedAiProvider()
}
