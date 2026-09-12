package com.whatsappworkmanager.app.data.repository

import com.whatsappworkmanager.app.data.local.dao.MessageDao
import com.whatsappworkmanager.app.data.local.entity.MessageEntity
import com.whatsappworkmanager.app.domain.model.WorkMessage
import com.whatsappworkmanager.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class MessageRepositoryImpl(private val dao: MessageDao) : MessageRepository {

    override fun observeMessages(): Flow<List<WorkMessage>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeMessagesForGroup(groupName: String): Flow<List<WorkMessage>> =
        dao.observeForGroup(groupName).map { list -> list.map { it.toDomain() } }

    override suspend fun insert(message: WorkMessage): Long = dao.insert(message.toEntity())

    override suspend fun isDuplicate(groupName: String, sender: String?, text: String, timestamp: Long, platform: com.whatsappworkmanager.app.domain.model.MessagingPlatform): Boolean =
        dao.countDuplicates(groupName, sender, text, timestamp, platform.key) > 0

    override suspend fun markRead(id: Long) = dao.markRead(id)

    override suspend fun setImportant(id: Long, important: Boolean) = dao.setImportant(id, important)

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun search(query: String): List<WorkMessage> =
        dao.search(query).map { it.toDomain() }

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun purgeOlderThan(cutoffTimestamp: Long) = dao.purgeOlderThan(cutoffTimestamp)

    override suspend fun countToday(): Int {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return dao.countSince(startOfDay)
    }

    override suspend fun getCandidateSystemNotificationJunk(): List<WorkMessage> =
        dao.getCandidateSystemNotificationJunk().map { it.toDomain() }

    override suspend fun getPendingForSummary(): List<WorkMessage> =
        dao.getPendingForSummary().map { it.toDomain() }

    override suspend fun markAllPendingAsSummarized() = dao.markAllPendingAsSummarized()

    override suspend fun setIncludedForGroup(groupName: String, included: Boolean) =
        dao.setIncludedForGroup(groupName, included)

    override suspend fun renameGroupName(oldName: String, newName: String) =
        dao.renameGroupName(oldName, newName)

    @Deprecated("Replaced by getPendingForSummary() — kept only in case anything external still calls it.")
    suspend fun getForPeriod(start: Long, end: Long): List<WorkMessage> =
        dao.getForPeriod(start, end).map { it.toDomain() }
}

private fun MessageEntity.toDomain() = WorkMessage(
    id = id,
    groupName = groupName,
    sender = sender,
    text = text,
    timestamp = timestamp,
    isImportant = isImportant,
    importanceScore = importanceScore,
    needsReply = needsReply,
    isRead = isRead,
    isIncludedInSummary = isIncludedInSummary,
    includedInPastSummary = includedInPastSummary,
    platform = com.whatsappworkmanager.app.domain.model.MessagingPlatform.fromKey(platform)
)

private fun WorkMessage.toEntity() = MessageEntity(
    id = id,
    groupName = groupName,
    sender = sender,
    text = text,
    timestamp = timestamp,
    isImportant = isImportant,
    importanceScore = importanceScore,
    needsReply = needsReply,
    isRead = isRead,
    isIncludedInSummary = isIncludedInSummary,
    includedInPastSummary = includedInPastSummary,
    platform = platform.key
)
