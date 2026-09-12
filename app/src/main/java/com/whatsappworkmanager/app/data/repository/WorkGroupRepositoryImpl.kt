package com.whatsappworkmanager.app.data.repository

import com.whatsappworkmanager.app.data.local.dao.WorkGroupDao
import com.whatsappworkmanager.app.data.local.entity.WorkGroupEntity
import com.whatsappworkmanager.app.domain.model.WorkGroupInfo
import com.whatsappworkmanager.app.domain.repository.WorkGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WorkGroupRepositoryImpl(private val dao: WorkGroupDao) : WorkGroupRepository {

    // Serialize read-modify-write operations so concurrent WhatsApp notifications
    // cannot create duplicate groups or lose message counts.
    private val writeMutex = Mutex()

    override fun observeGroups(): Flow<List<WorkGroupInfo>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsertGroupSeen(name: String, timestamp: Long, isImportant: Boolean, autoEnable: Boolean) {
        writeMutex.withLock {
        val existing = dao.getByName(name)
        val updated = existing?.copy(
            messageCount = existing.messageCount + 1,
            importantCount = existing.importantCount + if (isImportant) 1 else 0,
            lastMessageTime = timestamp
        ) ?: WorkGroupEntity(
            name = name,
            isEnabled = autoEnable, // off by default, unless the user turned on auto-enable
            messageCount = 1,
            importantCount = if (isImportant) 1 else 0,
            lastMessageTime = timestamp
        )
            dao.upsert(updated)
        }
    }

    override suspend fun setEnabled(name: String, enabled: Boolean) = dao.setEnabled(name, enabled)

    override suspend fun mergeGroupName(oldName: String, newName: String) {
        if (oldName.equals(newName, ignoreCase = true)) return
        writeMutex.withLock {
            val oldGroup = dao.getByName(oldName) ?: return
            val newGroup = dao.getByName(newName)
            if (newGroup == null) {
                dao.upsert(oldGroup.copy(name = newName))
            } else {
                dao.upsert(
                    newGroup.copy(
                        messageCount = newGroup.messageCount + oldGroup.messageCount,
                        importantCount = newGroup.importantCount + oldGroup.importantCount,
                        lastMessageTime = maxOf(newGroup.lastMessageTime, oldGroup.lastMessageTime),
                        isEnabled = newGroup.isEnabled || oldGroup.isEnabled
                    )
                )
            }
            dao.deleteByName(oldName)
        }
    }

    override suspend fun getEnabledGroupNames(): List<String> = dao.getEnabledNames()

    override suspend fun delete(name: String) = dao.deleteByName(name)

    override suspend fun addManually(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val existing = dao.getByName(trimmed)
        if (existing != null) return false
        dao.upsert(
            WorkGroupEntity(
                name = trimmed,
                isEnabled = true,
                messageCount = 0,
                importantCount = 0,
                lastMessageTime = 0L
            )
        )
        return true
    }

    override suspend fun deleteAll() = dao.deleteAll()
}

private fun WorkGroupEntity.toDomain() = WorkGroupInfo(
    id = id,
    name = name,
    isEnabled = isEnabled,
    messageCount = messageCount,
    importantCount = importantCount,
    lastMessageTime = lastMessageTime
)
