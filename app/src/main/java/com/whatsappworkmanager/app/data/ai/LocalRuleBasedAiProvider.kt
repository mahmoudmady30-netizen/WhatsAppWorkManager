package com.whatsappworkmanager.app.data.ai

import com.whatsappworkmanager.app.domain.model.WorkMessage
import com.whatsappworkmanager.app.domain.repository.AiSummaryProvider

/**
 * Fully on-device provider. No network, no API key, always available. Used as the default
 * provider and as the automatic fallback when a cloud provider fails or is disabled.
 */
class LocalRuleBasedAiProvider : AiSummaryProvider {

    override val id: String = "local"
    override val isCloud: Boolean = false

    override suspend fun summarize(messages: List<WorkMessage>, language: String): String {
        if (messages.isEmpty()) {
            return if (language == "ar") "لسه مفيش رسائل من WhatsApp." else "No messages yet."
        }
        val important = messages.filter { it.isImportant }
        val needReply = messages.filter { it.needsReply }
        val groups = messages.map { it.groupName }.distinct()

        return if (language == "ar") {
            buildString {
                append("عندك ${messages.size} رسالة من ${groups.size} جروبات.\n")
                if (important.isNotEmpty()) {
                    append("🔴 ${important.size} رسائل مهمة محتاجة اهتمام.\n")
                }
                if (needReply.isNotEmpty()) {
                    append("🟡 ${needReply.size} رسائل محتاجة رد.\n")
                }
                append("باقي الرسائل تحديثات عادية.")
            }
        } else {
            buildString {
                append("You have ${messages.size} messages from ${groups.size} groups.\n")
                if (important.isNotEmpty()) {
                    append("🔴 ${important.size} important messages need attention.\n")
                }
                if (needReply.isNotEmpty()) {
                    append("🟡 ${needReply.size} messages need a reply.\n")
                }
                append("The rest are routine updates.")
            }
        }
    }

    override suspend fun suggestReplies(original: String, language: String): List<String> {
        // Three safe, generic tone variants — never sent automatically.
        val looksLikeQuestion = original.contains("?") || original.contains("؟")
        return if (language == "ar") {
            listOfNotNull(
                "تمام، هراجع الموضوع وأبعتلك تحديث خلال شوية.",
                "آسف على التأخير، هرد عليك بالتفاصيل كاملة قريب.",
                if (looksLikeQuestion) "أيوه، مؤكد." else "استلمت رسالتك، هتابع معاك."
            )
        } else {
            listOfNotNull(
                "Got it — I'll look into this and send an update shortly.",
                "Apologies for the delay, I'll follow up with full details soon.",
                if (looksLikeQuestion) "Yes, confirmed." else "Received — I'll follow up with you."
            )
        }
    }
}
