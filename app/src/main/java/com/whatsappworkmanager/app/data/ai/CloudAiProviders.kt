package com.whatsappworkmanager.app.data.ai

import com.whatsappworkmanager.app.domain.model.WorkMessage
import com.whatsappworkmanager.app.domain.repository.AiSummaryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Base class for cloud providers. The API key is supplied by the caller (read from
 * SettingsDataStore's EncryptedSharedPreferences) — never embedded here, never in
 * BuildConfig, never in strings.xml. If [apiKeyProvider] returns null/blank, [summarize]
 * throws so the caller falls back to [LocalRuleBasedAiProvider].
 */
abstract class BaseCloudAiProvider(
    protected val apiKeyProvider: () -> String?
) : AiSummaryProvider {

    override val isCloud: Boolean = true

    protected val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    protected fun requireKey(): String {
        val key = apiKeyProvider()
        if (key.isNullOrBlank()) {
            throw IllegalStateException("No API key configured for provider '$id'. Falling back to local summary.")
        }
        return key
    }

    protected fun buildPrompt(messages: List<WorkMessage>, language: String): String {
        val joined = messages.joinToString("\n") { "[${it.groupName}] ${it.sender ?: ""}: ${it.text}" }
        // Explicitly forbids markdown tables/bold-asterisk syntax — earlier prompts left the
        // model free to reach for markdown (tables, "**bold**"), which is exactly what made
        // the summary look "random"/unformatted in the app: this is a plain-text UI, not a
        // markdown renderer, so a raw "| Sender | Action |" table or literal "**" characters
        // just showed up as noisy text. A one-line takeaway first, then a few short bullet
        // lines with a light, purposeful emoji per line (not decoration for its own sake) is
        // both genuinely easier to scan on a phone screen and something the app can display
        // correctly with zero rendering.
        val instruction = if (language == "ar") {
            "لخّص رسائل الشغل دي باللهجة المصرية، باختصار شديد. الرسايل دي ممكن تكون بأي " +
                "لغة، بس لازم تكتب أنت بالعربي المصري بس من غير أي كلمة إنجليزي — حتى لو " +
                "الرسايل نفسها كانت بالإنجليزي. اكتب النتيجة كنص عادي بس — " +
                "من غير أي Markdown خالص (يعني من غير ** أو جداول بعلامة |). ابدأ بسطر واحد " +
                "قصير جدًا يقول الخلاصة الكاملة (السطر ده هو أهم حاجة، خليه واضح ومفيد لوحده). " +
                "بعد سطر فاضي، اكتب أهم النقاط كل واحدة في سطر لوحدها، وابدأ كل نقطة بإيموجي " +
                "واحد بس يعبّر عن نوعها (⚠️ لمشكلة أو شكوى، ⏰ لديدلاين أو حاجة عاجلة، 💬 لحاجة " +
                "محتاجة رد، ℹ️ لمعلومة عادية من غير أهمية). ركّز على: مين محتاج اهتمام، أي " +
                "مشاكل، أي حاجة محتاجة رد أو ديدلاين. متعيدش كتابة كل الرسائل."
        } else {
            "Summarize these work messages very concisely. These messages may be in any " +
                "language, but you must respond in English only — even if the messages " +
                "themselves are in Arabic or another language. Write the result as plain " +
                "text — no Markdown at all " +
                "(no ** asterisks, no | table syntax). Start with one short line giving the " +
                "complete takeaway on its own (this line matters most — make it clear and " +
                "useful by itself). After a blank line, list the key points, one per line, " +
                "each starting with exactly one emoji matching its kind (⚠️ for a problem or " +
                "complaint, ⏰ for a deadline or anything urgent, 💬 for something needing a " +
                "reply, ℹ️ for a plain, non-urgent note). Focus on: who needs attention, " +
                "problems or complaints, anything needing a reply or a deadline. Do not " +
                "restate every message."
        }
        return "$instruction\n\n$joined"
    }

    /** Asks for 3 short, distinctly-toned replies, one per line, numbered "1." "2." "3.". */
    protected fun buildReplyPrompt(original: String, language: String): String {
        return if (language == "ar") {
            "اقترح 3 ردود قصيرة ومختلفة في الأسلوب (مباشر، ودّي، رسمي) باللهجة المصرية على الرسالة " +
                "دي: \"$original\".\nاكتب كل رد في سطر منفصل يبدأ بـ 1. و 2. و 3. من غير أي شرح إضافي."
        } else {
            "Suggest 3 short replies with different tones (direct, friendly, formal) to this " +
                "message: \"$original\".\nWrite each on its own line starting with 1. 2. and 3. " +
                "— no extra explanation."
        }
    }

    /** Splits a numbered-list response into individual reply strings, stripping the numbering. */
    protected fun parseNumberedReplies(raw: String): List<String> {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
        val cleaned = lines.mapNotNull { line ->
            line.replace(Regex("^[0-9]+[.)\\-]\\s*"), "").trim().ifBlank { null }
        }
        return (if (cleaned.isNotEmpty()) cleaned else listOf(raw.trim())).take(3)
    }

    /**
     * The [draft] here is an instruction to *improve*, deliberately worded so the model
     * doesn't mistake it for an incoming message to respond to — see
     * [AiSummaryProvider.refineReply]'s doc for why that distinction matters.
     */
    protected fun buildRefinePrompt(draft: String, language: String): String {
        return if (language == "ar") {
            "دي مسودة رد كتبها المستخدم بنفسه على رسالة واتساب. أعد صياغتها باللهجة المصرية " +
                "بشكل احترافي ومنظم، مع الحفاظ على نفس المعنى والنية بالظبط — من غير ما تضيف " +
                "معلومات جديدة أو تغيّر القصد. رد بالنص المُحسّن بس، من غير أي شرح إضافي أو " +
                "علامات اقتباس.\n\nالمسودة: \"$draft\""
        } else {
            "The following is a draft WhatsApp reply the user wrote themselves. Rewrite it to " +
                "be professional and well-organized, keeping the exact same meaning and intent " +
                "— don't add new information or change what it's saying. Reply with only the " +
                "improved text, no explanation, no quotation marks.\n\nDraft: \"$draft\""
        }
    }

    /**
     * Same intent as [buildReplyPrompt] but given the recent back-and-forth instead of a
     * single message — see [AiSummaryProvider.suggestRepliesForConversation]. [conversation]
     * is oldest-first; the last line is what's actually being replied to, everything before it
     * is context only.
     */
    protected fun buildConversationReplyPrompt(
        conversation: List<String>,
        language: String,
        tone: com.whatsappworkmanager.app.domain.model.ReplyTone? = null,
        instruction: String? = null
    ): String {
        val transcript = conversation.mapIndexed { index, line ->
            if (index == conversation.lastIndex) "${index + 1}. $line  ← ${if (language == "ar") "الرسالة اللي محتاجة رد" else "reply to this one"}"
            else "${index + 1}. $line"
        }.joinToString("\n")
        val customInstruction = instruction?.trim()?.takeIf { it.isNotBlank() }
        val instructionLine = customInstruction?.let {
            if (language == "ar") "تعليمات المستخدم الخاصة بهذا الشخص: $it" else "User's custom instructions for this person: $it"
        }
        // Auto Reply asks for exactly one reply shaped by the chosen relationship tone,
        // rather than the ordinary 3-varied-options prompt below — the person already told
        // this rule how they want to sound with whoever it matches, so there's no "pick one
        // of three" step left for a background-fired notification to make sense.
        if (tone != null) {
            val toneDescription = when (tone) {
                com.whatsappworkmanager.app.domain.model.ReplyTone.WORK ->
                    if (language == "ar") "زميل شغل — رد محترف ومختصر ومباشر" else "a work colleague — a professional, concise, businesslike reply"
                com.whatsappworkmanager.app.domain.model.ReplyTone.FRIEND ->
                    if (language == "ar") "صديق مقرب — رد ودّي وعادي وخفيف" else "a close friend — a warm, casual, relaxed reply"
                com.whatsappworkmanager.app.domain.model.ReplyTone.FAMILY ->
                    if (language == "ar") "فرد من العيلة — رد حنين وقريب وعائلي" else "a family member — a warm, caring, familial reply"
            }
            return if (language == "ar") {
                "دي آخر رسايل جت من نفس الشخص/الجروب، بالترتيب (الأقدم الأول):\n\n$transcript\n\n" + (instructionLine?.plus("\n\n") ?: "") +
                    "الشخص ده بالنسبالك $toneDescription. اكتب رد واحد بس على آخر رسالة، بالأسلوب " +
                    "ده بالظبط، باللهجة المصرية — خد بالك من سياق الرسايل اللي قبلها عشان الرد يبقى " +
                    "مناسب ومقنع. اكتب الرد في سطر واحد يبدأ بـ 1. من غير أي شرح إضافي."
            } else {
                "These are the most recent messages from the same sender/group, in order " +
                    "(oldest first):\n\n$transcript\n\n${instructionLine?.plus("\n\n").orEmpty()}This person is $toneDescription to you. Write " +
                    "exactly one reply to the last message only, in that style — but take the " +
                    "earlier messages into account as context, so the reply is genuinely " +
                    "appropriate and convincing. Write it on one line starting with 1. — no extra " +
                    "explanation."
            }
        }
        return if (language == "ar") {
            "دي آخر رسايل جت من نفس الشخص/الجروب، بالترتيب (الأقدم الأول):\n\n$transcript\n\n" + (instructionLine?.plus("\n\n") ?: "") +
                "اقترح 3 ردود قصيرة ومختلفة في الأسلوب (مباشر، ودّي، رسمي) باللهجة المصرية على " +
                "آخر رسالة بس — بس خد بالك من سياق الرسايل اللي قبلها عشان الرد يبقى مناسب ومقنع، " +
                "مش مجرد رد عام. اكتب كل رد في سطر منفصل يبدأ بـ 1. و 2. و 3. من غير أي شرح إضافي."
        } else {
            "These are the most recent messages from the same sender/group, in order (oldest " +
                "first):\n\n$transcript\n\nSuggest 3 short replies with different tones (direct, " +
                "friendly, formal) to the LAST message only — but take the earlier messages into " +
                "account as context, so the reply is genuinely appropriate and convincing rather " +
                "than a generic response to the last line alone. Write each on its own line " +
                "starting with 1. 2. and 3. — no extra explanation."
        }
    }
}

/** OpenAI Chat Completions-style provider. Adjust model/endpoint to your account's access. */
class OpenAiProvider(apiKeyProvider: () -> String?) : BaseCloudAiProvider(apiKeyProvider) {
    override val id: String = "openai"

    private fun chatCompletion(prompt: String): String {
        val key = requireKey()
        val body = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("OpenAI request failed: ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            return json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }

    override suspend fun summarize(messages: List<WorkMessage>, language: String): String =
        withContext(Dispatchers.IO) { chatCompletion(buildPrompt(messages, language)) }

    override suspend fun suggestReplies(original: String, language: String): List<String> =
        withContext(Dispatchers.IO) { parseNumberedReplies(chatCompletion(buildReplyPrompt(original, language))) }

    override suspend fun refineReply(draft: String, language: String): String =
        withContext(Dispatchers.IO) { chatCompletion(buildRefinePrompt(draft, language)) }

    override suspend fun suggestRepliesForConversation(
        conversation: List<String>,
        language: String,
        tone: com.whatsappworkmanager.app.domain.model.ReplyTone?,
        instruction: String?
    ): List<String> =
        withContext(Dispatchers.IO) { parseNumberedReplies(chatCompletion(buildConversationReplyPrompt(conversation, language, tone, instruction))) }
}

/**
 * xAI's Grok provider. Grok's API is intentionally OpenAI-compatible (same request/response
 * JSON shape, same `/chat/completions` path, same Bearer-token auth) — just a different host
 * and model name. Double-check the model name against xAI's current docs before relying on
 * this in production; model names change more often than the API shape does.
 */
class GrokProvider(apiKeyProvider: () -> String?) : BaseCloudAiProvider(apiKeyProvider) {
    override val id: String = "grok"

    private fun chatCompletion(prompt: String): String {
        val key = requireKey()
        val body = JSONObject().apply {
            put("model", "grok-3-latest")
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        val request = Request.Builder()
            .url("https://api.x.ai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Grok request failed: ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            return json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }

    override suspend fun summarize(messages: List<WorkMessage>, language: String): String =
        withContext(Dispatchers.IO) { chatCompletion(buildPrompt(messages, language)) }

    override suspend fun suggestReplies(original: String, language: String): List<String> =
        withContext(Dispatchers.IO) { parseNumberedReplies(chatCompletion(buildReplyPrompt(original, language))) }

    override suspend fun refineReply(draft: String, language: String): String =
        withContext(Dispatchers.IO) { chatCompletion(buildRefinePrompt(draft, language)) }

    override suspend fun suggestRepliesForConversation(
        conversation: List<String>,
        language: String,
        tone: com.whatsappworkmanager.app.domain.model.ReplyTone?,
        instruction: String?
    ): List<String> =
        withContext(Dispatchers.IO) { parseNumberedReplies(chatCompletion(buildConversationReplyPrompt(conversation, language, tone, instruction))) }
}

/**
 * Groq's provider — NOT the same company as xAI's Grok above (easy to confuse; different
 * companies, different APIs, different key formats — Groq's keys start with `gsk_`). Groq
 * hosts fast inference for open models behind an OpenAI-compatible
 * `/openai/v1/chat/completions` endpoint.
 *
 * Model note: Groq deprecated its earlier Llama chat models (including the
 * `llama-3.3-70b-versatile` this used to default to — that caused real "404 model not found"
 * errors for users). Groq's current general-purpose recommendation is `openai/gpt-oss-120b`
 * (a smaller `openai/gpt-oss-20b` also exists for lighter/cheaper use). Check
 * console.groq.com/docs/models before relying on this — Groq's hosted model lineup changes
 * fairly often, faster than most providers.
 */
class GroqProvider(apiKeyProvider: () -> String?) : BaseCloudAiProvider(apiKeyProvider) {
    override val id: String = "groq"

    private fun chatCompletion(prompt: String): String {
        val key = requireKey()
        val body = JSONObject().apply {
            put("model", "openai/gpt-oss-120b")
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Groq request failed: ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            return json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        }
    }

    override suspend fun summarize(messages: List<WorkMessage>, language: String): String =
        withContext(Dispatchers.IO) { chatCompletion(buildPrompt(messages, language)) }

    override suspend fun suggestReplies(original: String, language: String): List<String> =
        withContext(Dispatchers.IO) { parseNumberedReplies(chatCompletion(buildReplyPrompt(original, language))) }

    override suspend fun refineReply(draft: String, language: String): String =
        withContext(Dispatchers.IO) { chatCompletion(buildRefinePrompt(draft, language)) }

    override suspend fun suggestRepliesForConversation(
        conversation: List<String>,
        language: String,
        tone: com.whatsappworkmanager.app.domain.model.ReplyTone?,
        instruction: String?
    ): List<String> =
        withContext(Dispatchers.IO) { parseNumberedReplies(chatCompletion(buildConversationReplyPrompt(conversation, language, tone, instruction))) }
}

/** Anthropic Messages API provider. */
class AnthropicProvider(apiKeyProvider: () -> String?) : BaseCloudAiProvider(apiKeyProvider) {
    override val id: String = "anthropic"

    private fun message(prompt: String, maxTokens: Int): String {
        val key = requireKey()
        val body = JSONObject().apply {
            put("model", "claude-3-5-haiku-latest")
            put("max_tokens", maxTokens)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Anthropic request failed: ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            return json.getJSONArray("content").getJSONObject(0).getString("text")
        }
    }

    override suspend fun summarize(messages: List<WorkMessage>, language: String): String =
        withContext(Dispatchers.IO) { message(buildPrompt(messages, language), maxTokens = 500) }

    override suspend fun suggestReplies(original: String, language: String): List<String> =
        withContext(Dispatchers.IO) {
            parseNumberedReplies(message(buildReplyPrompt(original, language), maxTokens = 250))
        }

    override suspend fun refineReply(draft: String, language: String): String =
        withContext(Dispatchers.IO) { message(buildRefinePrompt(draft, language), maxTokens = 250) }

    override suspend fun suggestRepliesForConversation(
        conversation: List<String>,
        language: String,
        tone: com.whatsappworkmanager.app.domain.model.ReplyTone?,
        instruction: String?
    ): List<String> =
        withContext(Dispatchers.IO) {
            parseNumberedReplies(message(buildConversationReplyPrompt(conversation, language, tone, instruction), maxTokens = 250))
        }
}

/** Google Gemini provider. */
class GeminiProvider(apiKeyProvider: () -> String?) : BaseCloudAiProvider(apiKeyProvider) {
    override val id: String = "gemini"

    private fun generateContent(prompt: String): String {
        val key = requireKey()
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
            )
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$key"
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Gemini request failed: ${response.code}")
            val json = JSONObject(response.body?.string().orEmpty())
            return json.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getString("text")
        }
    }

    override suspend fun summarize(messages: List<WorkMessage>, language: String): String =
        withContext(Dispatchers.IO) { generateContent(buildPrompt(messages, language)) }

    override suspend fun suggestReplies(original: String, language: String): List<String> =
        withContext(Dispatchers.IO) { parseNumberedReplies(generateContent(buildReplyPrompt(original, language))) }

    override suspend fun refineReply(draft: String, language: String): String =
        withContext(Dispatchers.IO) { generateContent(buildRefinePrompt(draft, language)) }

    override suspend fun suggestRepliesForConversation(
        conversation: List<String>,
        language: String,
        tone: com.whatsappworkmanager.app.domain.model.ReplyTone?,
        instruction: String?
    ): List<String> =
        withContext(Dispatchers.IO) { parseNumberedReplies(generateContent(buildConversationReplyPrompt(conversation, language, tone, instruction))) }
}
