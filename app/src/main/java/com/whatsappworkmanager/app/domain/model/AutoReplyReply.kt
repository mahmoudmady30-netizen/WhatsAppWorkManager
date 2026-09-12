package com.whatsappworkmanager.app.domain.model

data class AutoReplyReply(
    val id: Long = 0,
    val ruleId: Long,
    val personLabel: String,
    val incomingText: String,
    val replyText: String,
    val createdAt: Long = System.currentTimeMillis()
)
