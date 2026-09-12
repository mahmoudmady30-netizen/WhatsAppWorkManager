package com.whatsappworkmanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val text: String,
    val importantCount: Int,
    val needReplyCount: Int,
    val groupCount: Int,
    val totalMessages: Int,
    val isAiGenerated: Boolean,
    val isPinned: Boolean = false
)
