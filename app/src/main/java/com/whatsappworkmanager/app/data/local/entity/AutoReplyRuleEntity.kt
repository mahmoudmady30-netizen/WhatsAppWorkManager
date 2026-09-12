package com.whatsappworkmanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Premium Auto Reply rule. A rule can target one or several configured names/numbers, or all
 * incoming messages when the target is blank. It can either use a fixed reply or ask the selected AI provider to generate a reply using the incoming message,
 * recent conversation context, tone, and [aiInstruction]. [autoSend] enables the user-requested
 * automatic send path through the explicitly enabled Accessibility Service.
 */
@Entity(tableName = "auto_reply_rules")
data class AutoReplyRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personMatch: String,
    val keyword: String?,
    val replyText: String?,
    val aiInstruction: String? = null,
    val autoSend: Boolean = false,
    val phoneNumber: String? = null,
    val enabled: Boolean = true,
    // Stored as the enum's name (e.g. "WORK") rather than adding a Room TypeConverter for a
    // 3-value enum — simpler, and the conversion lives in one place (OtherRepositoryImpls'
    // toDomain/toEntity) rather than a separate converter class.
    val tone: String = "WORK",
    val updatedAt: Long = System.currentTimeMillis(),
    val platform: String = "whatsapp"
)
