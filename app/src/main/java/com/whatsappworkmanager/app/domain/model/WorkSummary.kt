package com.whatsappworkmanager.app.domain.model

enum class SummaryTier { IMPORTANT, FOLLOW_UP, GENERAL }

data class SummaryItem(
    val groupName: String,
    val text: String,
    val tier: SummaryTier,
    val suggestedAction: String? = null,
    val platform: MessagingPlatform = MessagingPlatform.WHATSAPP
)

data class WorkSummary(
    val id: Long = 0,
    val createdAt: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val text: String,
    val items: List<SummaryItem> = emptyList(),
    val importantCount: Int,
    val needReplyCount: Int,
    val groupCount: Int,
    val totalMessages: Int,
    val isAiGenerated: Boolean,
    val isPinned: Boolean = false
)
