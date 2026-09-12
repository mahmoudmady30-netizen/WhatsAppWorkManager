package com.whatsappworkmanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupName: String,
    val sender: String?,
    val text: String,
    val timestamp: Long,
    val isImportant: Boolean,
    val importanceScore: Int,
    val needsReply: Boolean,
    val isRead: Boolean,
    val isIncludedInSummary: Boolean,
    val includedInPastSummary: Boolean = false,
    val platform: String = "whatsapp"
)
