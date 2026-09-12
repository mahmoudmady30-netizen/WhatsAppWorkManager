package com.whatsappworkmanager.app.domain.usecase

import com.whatsappworkmanager.app.domain.model.SummaryItem
import com.whatsappworkmanager.app.domain.model.SummaryTier
import com.whatsappworkmanager.app.domain.model.WorkMessage
import com.whatsappworkmanager.app.domain.model.WorkSummary
import com.whatsappworkmanager.app.domain.repository.AiSummaryProvider

/**
 * Builds a [WorkSummary] out of a batch of messages. Tries the active [AiSummaryProvider] first
 * (if one is configured and cloud usage was consented to), and always has a fully local,
 * rule-based fallback so the feature works offline / when AI fails.
 */
class SummaryGenerator(
    private val keywordScoring: KeywordScoring
) {

    suspend fun generate(
        messages: List<WorkMessage>,
        periodStart: Long,
        periodEnd: Long,
        aiProvider: AiSummaryProvider?,
        language: String,
        // True when there ARE captured messages sitting in the database, but zero groups/
        // clients are currently enabled for Work Summary — i.e. the empty result isn't "you
        // have no WhatsApp activity", it's "you haven't turned anything on yet". Distinguishing
        // these two cases is the whole point of this parameter: the generic "no messages" text
        // was confusing people who could plainly see message counts elsewhere in the app.
        noEnabledGroupsHint: Boolean = false
    ): WorkSummary {
        val included = messages.filter { it.isIncludedInSummary }
        val items = buildItems(included)
        val importantCount = included.count { it.isImportant }
        val needReplyCount = included.count { it.needsReply }
        val whatsappCount = included.count { it.platform != com.whatsappworkmanager.app.domain.model.MessagingPlatform.MESSENGER }
        val messengerCount = included.count { it.platform == com.whatsappworkmanager.app.domain.model.MessagingPlatform.MESSENGER }
        val groupCount = included.map { it.groupName }.distinct().size

        // Never call the AI provider with zero messages — a "summarize this" prompt with no
        // actual content just makes a general-purpose chat model respond conversationally
        // ("sure, please paste the messages..."), which is confusing and also burns an API
        // call for nothing. The local text already says exactly the right thing for this case.
        val text = if (included.isEmpty()) {
            buildLocalSummaryText(included, language, noEnabledGroupsHint)
        } else {
            try {
                aiProvider?.summarize(included, language) ?: buildLocalSummaryText(included, language, noEnabledGroupsHint)
            } catch (e: Exception) {
                buildLocalSummaryText(included, language, noEnabledGroupsHint)
            }
        }

        return WorkSummary(
            createdAt = System.currentTimeMillis(),
            periodStart = periodStart,
            periodEnd = periodEnd,
            text = text,
            items = items,
            importantCount = importantCount,
            needReplyCount = needReplyCount,
            groupCount = groupCount,
            totalMessages = included.size,
            isAiGenerated = aiProvider != null && included.isNotEmpty()
        )
    }

    private fun buildItems(messages: List<WorkMessage>): List<SummaryItem> {
        return messages.map { msg ->
            val tier = when {
                msg.importanceScore >= KeywordScoring.HIGH_PRIORITY_THRESHOLD -> SummaryTier.IMPORTANT
                msg.needsReply -> SummaryTier.FOLLOW_UP
                else -> SummaryTier.GENERAL
            }
            SummaryItem(
                groupName = msg.groupName,
                text = msg.text,
                tier = tier,
                platform = msg.platform
            )
        }.sortedBy { tierOrder(it.tier) }
    }

    private fun tierOrder(tier: SummaryTier) = when (tier) {
        SummaryTier.IMPORTANT -> 0
        SummaryTier.FOLLOW_UP -> 1
        SummaryTier.GENERAL -> 2
    }

    /** Fully offline, deterministic summary — no AI required. */
    private fun buildLocalSummaryText(messages: List<WorkMessage>, language: String, noEnabledGroupsHint: Boolean): String {
        if (messages.isEmpty()) {
            if (noEnabledGroupsHint) {
                return if (language == "ar") {
                    "في رسائل وصلت فعلاً، بس مفيش أي جروب أو عميل مفعّل للتلخيص لسه. " +
                        "روح Work Groups & Clients وفعّل اللي يهمك."
                } else {
                    "Messages have arrived, but no groups/clients are enabled for summaries " +
                        "yet. Go to Work Groups & Clients and turn on the ones you care about."
                }
            }
            return if (language == "ar") "لسه مفيش رسائل خلال الفترة دي." else "No messages during this period."
        }
        val groupCounts = messages.groupingBy { it.groupName }.eachCount()
        val topGroup = groupCounts.maxByOrNull { it.value }?.key ?: ""
        val importantCount = messages.count { it.isImportant }
        val needReplyCount = messages.count { it.needsReply }
        val whatsappCount = messages.count { it.platform != com.whatsappworkmanager.app.domain.model.MessagingPlatform.MESSENGER }
        val messengerCount = messages.count { it.platform == com.whatsappworkmanager.app.domain.model.MessagingPlatform.MESSENGER }

        return if (language == "ar") {
            buildString {
                append("خلال الفترة دي:\n\n")
                append("${groupCounts.size} جروبات نشطة.\n")
                append("${messages.size} رسالة جديدة.\n")
                append("$importantCount رسائل مهمة.\n")
                append("$needReplyCount رسائل تحتاج رد.\n")
                append("واتساب: $whatsappCount · ماسنجر: $messengerCount\n\n")
                append("أكثر جروب نشاطًا: $topGroup")
            }
        } else {
            buildString {
                append("During this period:\n\n")
                append("${groupCounts.size} active groups.\n")
                append("${messages.size} new messages.\n")
                append("$importantCount important messages.\n")
                append("$needReplyCount messages need a reply.\n")
                append("WhatsApp: $whatsappCount · Messenger: $messengerCount\n\n")
                append("Most active group: $topGroup")
            }
        }
    }
}
