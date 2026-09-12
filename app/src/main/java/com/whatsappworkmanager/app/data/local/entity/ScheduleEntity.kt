package com.whatsappworkmanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startTimeMinutes: Int,
    val endTimeMinutes: Int?,
    val enabled: Boolean,
    val kind: String, // ScheduleKind name
    val repeatDaily: Boolean
)

@Entity(tableName = "keyword_rules")
data class KeywordRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val priority: Int,
    val enabled: Boolean
)

@Entity(tableName = "important_contacts")
data class ImportantContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean,
    val phoneNumber: String? = null
)

@Entity(tableName = "reply_phrase_rules")
data class ReplyPhraseRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phrase: String,
    val enabled: Boolean
)

@Entity(tableName = "scheduled_messages")
data class ScheduledOutgoingMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val timeMinutes: Int,
    val repeatDaily: Boolean,
    val enabled: Boolean,
    val phoneNumber: String? = null,
    val recipientName: String? = null,
    val lastSentAt: Long? = null,
    val platform: String = "whatsapp"
)
