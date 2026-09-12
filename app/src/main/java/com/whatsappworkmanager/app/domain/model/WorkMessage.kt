package com.whatsappworkmanager.app.domain.model

/**
 * Domain-level representation of a captured WhatsApp message.
 * Decoupled from the Room entity so presentation/domain never depend on persistence details.
 */
data class WorkMessage(
    val id: Long = 0,
    val groupName: String,
    val sender: String?,
    val text: String,
    val timestamp: Long,
    val isImportant: Boolean = false,
    val importanceScore: Int = 0,
    val needsReply: Boolean = false,
    val isRead: Boolean = false,
    val isIncludedInSummary: Boolean = true,
    // Whether this message has already been folded into a generated summary. Summaries are
    // built from "eligible (isIncludedInSummary) messages not yet summarized" rather than a
    // fixed time window — a pure time-window approach meant a message could permanently miss
    // every future summary if, say, an earlier (even empty) summary run had already advanced
    // the window past that message's timestamp. This flag makes that impossible: a message
    // stays eligible until it's actually been included in a real summary.
    val includedInPastSummary: Boolean = false,
    val platform: MessagingPlatform = MessagingPlatform.WHATSAPP
)
