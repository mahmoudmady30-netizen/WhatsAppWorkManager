package com.whatsappworkmanager.app.data.repository

import com.whatsappworkmanager.app.data.local.dao.ImportantContactDao
import com.whatsappworkmanager.app.data.local.dao.AutoReplyRuleDao
import com.whatsappworkmanager.app.data.local.dao.AutoReplyReplyHistoryDao
import com.whatsappworkmanager.app.data.local.dao.KeywordRuleDao
import com.whatsappworkmanager.app.data.local.dao.ReplyPhraseRuleDao
import com.whatsappworkmanager.app.data.local.dao.ScheduleDao
import com.whatsappworkmanager.app.data.local.dao.ScheduledMessageDao
import com.whatsappworkmanager.app.data.local.dao.SummaryDao
import com.whatsappworkmanager.app.data.local.entity.ImportantContactEntity
import com.whatsappworkmanager.app.data.local.entity.AutoReplyRuleEntity
import com.whatsappworkmanager.app.data.local.entity.AutoReplyReplyHistoryEntity
import com.whatsappworkmanager.app.data.local.entity.KeywordRuleEntity
import com.whatsappworkmanager.app.data.local.entity.ReplyPhraseRuleEntity
import com.whatsappworkmanager.app.data.local.entity.ScheduleEntity
import com.whatsappworkmanager.app.data.local.entity.ScheduledOutgoingMessageEntity
import com.whatsappworkmanager.app.data.local.entity.SummaryEntity
import com.whatsappworkmanager.app.domain.model.ImportantContact
import com.whatsappworkmanager.app.domain.model.AutoReplyRule
import com.whatsappworkmanager.app.domain.model.AutoReplyReply
import com.whatsappworkmanager.app.domain.model.KeywordRule
import com.whatsappworkmanager.app.domain.model.ReplyPhraseRule
import com.whatsappworkmanager.app.domain.model.ScheduleKind
import com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage
import com.whatsappworkmanager.app.domain.model.SummaryItem
import com.whatsappworkmanager.app.domain.model.WorkSchedule
import com.whatsappworkmanager.app.domain.model.WorkSummary
import com.whatsappworkmanager.app.domain.repository.ImportantContactRepository
import com.whatsappworkmanager.app.domain.repository.AutoReplyRuleRepository
import com.whatsappworkmanager.app.domain.repository.AutoReplyReplyHistoryRepository
import com.whatsappworkmanager.app.domain.repository.KeywordRuleRepository
import com.whatsappworkmanager.app.domain.repository.ReplyPhraseRuleRepository
import com.whatsappworkmanager.app.domain.repository.ScheduleRepository
import com.whatsappworkmanager.app.domain.repository.ScheduledMessageRepository
import com.whatsappworkmanager.app.domain.repository.SummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SummaryRepositoryImpl(private val dao: SummaryDao) : SummaryRepository {
    override fun observeSummaries(): Flow<List<WorkSummary>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun save(summary: WorkSummary): Long = dao.insert(summary.toEntity())

    override suspend fun latest(): WorkSummary? = dao.latest()?.toDomain()

    override suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun deleteAll() = dao.deleteAll()
}

// Note: SummaryItem detail list is intentionally not persisted per-row (kept lightweight);
// the aggregate counts + generated text are what's stored and re-shown.
private fun SummaryEntity.toDomain() = WorkSummary(
    id = id,
    createdAt = createdAt,
    periodStart = periodStart,
    periodEnd = periodEnd,
    text = text,
    items = emptyList<SummaryItem>(),
    importantCount = importantCount,
    needReplyCount = needReplyCount,
    groupCount = groupCount,
    totalMessages = totalMessages,
    isAiGenerated = isAiGenerated,
    isPinned = isPinned
)

private fun WorkSummary.toEntity() = SummaryEntity(
    id = id,
    createdAt = createdAt,
    periodStart = periodStart,
    periodEnd = periodEnd,
    text = text,
    importantCount = importantCount,
    needReplyCount = needReplyCount,
    groupCount = groupCount,
    totalMessages = totalMessages,
    isAiGenerated = isAiGenerated,
    isPinned = isPinned
)

class ScheduleRepositoryImpl(private val dao: ScheduleDao) : ScheduleRepository {
    override fun observeSchedules(): Flow<List<WorkSchedule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(schedule: WorkSchedule): Long = dao.upsert(schedule.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

private fun ScheduleEntity.toDomain() = WorkSchedule(
    id = id,
    name = name,
    startTimeMinutes = startTimeMinutes,
    endTimeMinutes = endTimeMinutes,
    enabled = enabled,
    kind = runCatching { ScheduleKind.valueOf(kind) }.getOrDefault(ScheduleKind.SUMMARY),
    repeatDaily = repeatDaily
)

private fun WorkSchedule.toEntity() = ScheduleEntity(
    id = id,
    name = name,
    startTimeMinutes = startTimeMinutes,
    endTimeMinutes = endTimeMinutes,
    enabled = enabled,
    kind = kind.name,
    repeatDaily = repeatDaily
)

class KeywordRuleRepositoryImpl(private val dao: KeywordRuleDao) : KeywordRuleRepository {
    override fun observeRules(): Flow<List<KeywordRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getEnabledOnce(): List<KeywordRule> =
        dao.getEnabledOnce().map { it.toDomain() }

    override suspend fun upsert(rule: KeywordRule): Long = dao.upsert(rule.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

private fun KeywordRuleEntity.toDomain() = KeywordRule(id, keyword, priority, enabled)
private fun KeywordRule.toEntity() = KeywordRuleEntity(id, keyword, priority, enabled)

class ImportantContactRepositoryImpl(private val dao: ImportantContactDao) : ImportantContactRepository {
    override fun observeContacts(): Flow<List<ImportantContact>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getEnabledNamesOnce(): List<String> = dao.getEnabledNamesOnce()

    override suspend fun upsert(contact: ImportantContact): Long = dao.upsert(contact.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun deleteAll() = dao.deleteAll()
}

private fun ImportantContactEntity.toDomain() = ImportantContact(id, name, enabled, phoneNumber)
private fun ImportantContact.toEntity() = ImportantContactEntity(id, name, enabled, phoneNumber)

class ReplyPhraseRuleRepositoryImpl(private val dao: ReplyPhraseRuleDao) : ReplyPhraseRuleRepository {
    override fun observeRules(): Flow<List<ReplyPhraseRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getEnabledPhrasesOnce(): List<String> = dao.getEnabledPhrasesOnce()

    override suspend fun upsert(rule: ReplyPhraseRule): Long = dao.upsert(rule.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

private fun ReplyPhraseRuleEntity.toDomain() = ReplyPhraseRule(id, phrase, enabled)
private fun ReplyPhraseRule.toEntity() = ReplyPhraseRuleEntity(id, phrase, enabled)

class AutoReplyRuleRepositoryImpl(private val dao: AutoReplyRuleDao) : AutoReplyRuleRepository {
    override fun observeRules(): Flow<List<AutoReplyRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getEnabledOnce(): List<AutoReplyRule> = dao.getEnabledOnce().map { it.toDomain() }

    override suspend fun upsert(rule: AutoReplyRule): Long = dao.upsert(rule.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

private fun AutoReplyRuleEntity.toDomain() = AutoReplyRule(
    id, personMatch, keyword, replyText, aiInstruction, autoSend, phoneNumber, enabled,
    tone = try { com.whatsappworkmanager.app.domain.model.ReplyTone.valueOf(tone) } catch (e: IllegalArgumentException) {
        com.whatsappworkmanager.app.domain.model.ReplyTone.DEFAULT
    },
    updatedAt = updatedAt,
    platform = com.whatsappworkmanager.app.domain.model.MessagingPlatform.fromKey(platform)
)
private fun AutoReplyRule.toEntity() = AutoReplyRuleEntity(
    id = id, personMatch = personMatch, keyword = keyword, replyText = replyText,
    aiInstruction = aiInstruction, autoSend = autoSend, phoneNumber = phoneNumber,
    enabled = enabled, tone = tone.name, updatedAt = updatedAt, platform = platform.key
)


class AutoReplyReplyHistoryRepositoryImpl(private val dao: AutoReplyReplyHistoryDao) : AutoReplyReplyHistoryRepository {
    override fun observeAll(): Flow<List<AutoReplyReply>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun insert(reply: AutoReplyReply): Long = dao.insert(reply.toEntity())

    override suspend fun deleteForRule(ruleId: Long) = dao.deleteForRule(ruleId)
}

private fun AutoReplyReplyHistoryEntity.toDomain() = AutoReplyReply(
    id = id, ruleId = ruleId, personLabel = personLabel, incomingText = incomingText,
    replyText = replyText, createdAt = createdAt
)

private fun AutoReplyReply.toEntity() = AutoReplyReplyHistoryEntity(
    id = id, ruleId = ruleId, personLabel = personLabel, incomingText = incomingText,
    replyText = replyText, createdAt = createdAt
)

class ScheduledMessageRepositoryImpl(private val dao: ScheduledMessageDao) : ScheduledMessageRepository {
    override fun observeScheduledMessages(): Flow<List<ScheduledOutgoingMessage>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsert(message: ScheduledOutgoingMessage): Long = dao.upsert(message.toEntity())

    override suspend fun delete(id: Long) = dao.delete(id)
}

private fun ScheduledOutgoingMessageEntity.toDomain() =
    ScheduledOutgoingMessage(id, text, timeMinutes, repeatDaily, enabled, phoneNumber, recipientName, lastSentAt, com.whatsappworkmanager.app.domain.model.MessagingPlatform.fromKey(platform))

private fun ScheduledOutgoingMessage.toEntity() =
    ScheduledOutgoingMessageEntity(id, text, timeMinutes, repeatDaily, enabled, phoneNumber, recipientName, lastSentAt, platform.key)
