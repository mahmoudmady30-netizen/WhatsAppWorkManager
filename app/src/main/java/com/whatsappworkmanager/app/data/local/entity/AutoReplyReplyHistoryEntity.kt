package com.whatsappworkmanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "auto_reply_reply_history",
    indices = [Index(value = ["ruleId", "createdAt"])]
)
data class AutoReplyReplyHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val personLabel: String,
    val incomingText: String,
    val replyText: String,
    val createdAt: Long = System.currentTimeMillis()
)
