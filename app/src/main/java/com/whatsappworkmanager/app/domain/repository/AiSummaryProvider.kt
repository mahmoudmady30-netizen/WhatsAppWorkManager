package com.whatsappworkmanager.app.domain.repository

import com.whatsappworkmanager.app.domain.model.WorkMessage

/**
 * Abstraction over any summarization backend (local rule-based, or a cloud AI provider).
 * Swapping providers must never require changes outside the data/ai package.
 *
 * IMPORTANT: no implementation of this interface may hold a hardcoded API key. Cloud
 * implementations read their key from a user-provided, encrypted local setting (see
 * SettingsDataStore) — never from source code, strings.xml, or BuildConfig.
 */
interface AiSummaryProvider {

    /** Stable id shown in Settings, e.g. "local", "openai", "anthropic", "gemini". */
    val id: String

    /** Whether this provider requires network access (cloud) or runs fully on-device. */
    val isCloud: Boolean

    /**
     * Produces a short, natural-language summary (Egyptian Arabic dialect when [language] == "ar")
     * of the given messages. Implementations should throw on failure so the caller can fall back
     * to [LocalRuleBasedAiProvider].
     */
    suspend fun summarize(messages: List<WorkMessage>, language: String): String

    /**
     * Generates short suggested replies to a single message — multiple tone variants (e.g.
     * direct acknowledgement, ask-for-more-time, confirm-with-detail) so the user can pick
     * whichever fits, rather than being stuck with one generic reply. Implementations should
     * return 2-3 short, distinct options; if only one can be produced, a single-item list is
     * fine.
     */
    suspend fun suggestReplies(original: String, language: String): List<String>

    /**
     * Takes the user's OWN rough draft — something *they* wrote, in the "write your own reply"
     * field — and returns a polished, professionally-worded version in the same language and
     * with the same intent. This is fundamentally different from [suggestReplies]: the draft
     * here is treated as an instruction to improve, never as an incoming message to respond to
     * (an implementation that reused [suggestReplies]'s prompt on this input would produce a
     * reply *to* the user's draft, which is the wrong behavior entirely).
     *
     * Default implementation returns [draft] unchanged — a safe, honest fallback for any
     * provider (like the local, fully-offline one) with no real language model behind it to
     * actually do the polishing.
     */
    suspend fun refineReply(draft: String, language: String): String = draft

    /**
     * Like [suggestReplies], but given the recent conversation history from the same sender/
     * group instead of just the single latest message — [conversation] is ordered oldest
     * first, with the last entry being the message to actually reply to. Lets the AI produce a
     * reply that accounts for what's already been said (e.g. a question asked two messages
     * ago that the latest message is following up on), rather than reacting to the last line
     * in isolation, which can miss context and read as generic or even slightly off.
     *
     * Default implementation falls back to [suggestReplies] on just the last message — a safe
     * behavior for any provider that doesn't need a dedicated context-aware prompt (the local,
     * fully-offline provider uses this default, since its rule-based replies don't meaningfully
     * benefit from more context anyway).
     */
    suspend fun suggestRepliesForConversation(
        conversation: List<String>,
        language: String,
        tone: com.whatsappworkmanager.app.domain.model.ReplyTone? = null,
        instruction: String? = null
    ): List<String> =
        suggestReplies(conversation.lastOrNull().orEmpty(), language)
}
