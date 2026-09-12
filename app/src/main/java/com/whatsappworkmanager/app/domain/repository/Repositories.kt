package com.whatsappworkmanager.app.domain.repository

import com.whatsappworkmanager.app.domain.model.KeywordRule
import com.whatsappworkmanager.app.domain.model.ImportantContact
import com.whatsappworkmanager.app.domain.model.AutoReplyRule
import com.whatsappworkmanager.app.domain.model.AutoReplyReply
import com.whatsappworkmanager.app.domain.model.ReplyPhraseRule
import com.whatsappworkmanager.app.domain.model.ScheduledOutgoingMessage
import com.whatsappworkmanager.app.domain.model.WorkGroupInfo
import com.whatsappworkmanager.app.domain.model.WorkMessage
import com.whatsappworkmanager.app.domain.model.WorkSchedule
import com.whatsappworkmanager.app.domain.model.WorkSummary
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeMessages(): Flow<List<WorkMessage>>
    fun observeMessagesForGroup(groupName: String): Flow<List<WorkMessage>>
    suspend fun insert(message: WorkMessage): Long
    suspend fun isDuplicate(groupName: String, sender: String?, text: String, timestamp: Long, platform: com.whatsappworkmanager.app.domain.model.MessagingPlatform = com.whatsappworkmanager.app.domain.model.MessagingPlatform.WHATSAPP): Boolean
    suspend fun markRead(id: Long)
    suspend fun setImportant(id: Long, important: Boolean)
    suspend fun delete(id: Long)
    suspend fun search(query: String): List<WorkMessage>
    suspend fun deleteAll()
    suspend fun purgeOlderThan(cutoffTimestamp: Long)
    suspend fun countToday(): Int
    /** Every message whose group name is exactly "WhatsApp" or "WhatsApp Business" — the
     *  candidate pool for MessageCleanup's one-time startup purge of already-captured system
     *  notifications (see that class). Deliberately broader than the real filter, since the
     *  precise text-matching happens in Kotlin once these are fetched. */
    suspend fun getCandidateSystemNotificationJunk(): List<WorkMessage>
    /** Everything eligible for a summary that hasn't been included in one yet — see
     *  MessageDao.getPendingForSummary for why this replaced a fixed time window. */
    suspend fun getPendingForSummary(): List<WorkMessage>
    suspend fun markAllPendingAsSummarized()
    /** Retroactively includes/excludes an existing group's messages from future summaries —
     *  call this alongside WorkGroupRepository.setEnabled so a group toggled on *after* some
     *  of its messages already arrived doesn't leave those messages stuck excluded forever. */
    suspend fun setIncludedForGroup(groupName: String, included: Boolean)
    /** Used by the group-title cleanup — see MessageDao.renameGroupName's doc. */
    suspend fun renameGroupName(oldName: String, newName: String)
}

interface WorkGroupRepository {
    fun observeGroups(): Flow<List<WorkGroupInfo>>
    /**
     * [autoEnable] — when true and this is the *first* time [name] is seen, it's created
     * already opted into Work Summary (isEnabled = true) instead of the default off. Has no
     * effect on a group that already exists; use [setEnabled] to change an existing one. See
     * SettingsDataStore.autoEnableNewGroups.
     */
    suspend fun upsertGroupSeen(name: String, timestamp: Long, isImportant: Boolean, autoEnable: Boolean = false)
    suspend fun setEnabled(name: String, enabled: Boolean)
    /** Merges an existing dirty group entry into a clean name, preserving counts, activity and enabled state. */
    suspend fun mergeGroupName(oldName: String, newName: String)
    suspend fun getEnabledGroupNames(): List<String>
    /** Removes a group/client entirely from this list (and from being tracked for future
     *  Work Summary inclusion). Does not touch already-captured messages from that name —
     *  those remain visible in Search/history as before; only this tracking entry goes away.
     *  If a new message later arrives from the same name, it's re-created fresh (disabled by
     *  default, same as any first-time-seen name). */
    suspend fun delete(name: String)
    suspend fun deleteAll()
    /**
     * Registers a group or 1:1 contact by name *before* any message from them has ever been
     * captured, already enabled — so a completely muted thread (one that never posts a single
     * notification) can still be opted in ahead of time, exactly the way you'd tell a real
     * secretary "keep an eye out for messages from X" without waiting for the first one to
     * arrive. Returns false if a group with that exact name already exists (nothing changes;
     * the caller should just enable the existing one instead).
     */
    suspend fun addManually(name: String): Boolean
}

interface SummaryRepository {
    fun observeSummaries(): Flow<List<WorkSummary>>
    suspend fun save(summary: WorkSummary): Long
    suspend fun latest(): WorkSummary?
    suspend fun setPinned(id: Long, pinned: Boolean)
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}

interface ScheduleRepository {
    fun observeSchedules(): Flow<List<WorkSchedule>>
    suspend fun upsert(schedule: WorkSchedule): Long
    suspend fun delete(id: Long)
}

interface KeywordRuleRepository {
    fun observeRules(): Flow<List<KeywordRule>>
    suspend fun getEnabledOnce(): List<KeywordRule>
    suspend fun upsert(rule: KeywordRule): Long
    suspend fun delete(id: Long)
}

interface ImportantContactRepository {
    fun observeContacts(): Flow<List<ImportantContact>>
    suspend fun getEnabledNamesOnce(): List<String>
    suspend fun upsert(contact: ImportantContact): Long
    suspend fun delete(id: Long)
    suspend fun deleteAll()
}

interface ReplyPhraseRuleRepository {
    fun observeRules(): Flow<List<ReplyPhraseRule>>
    suspend fun getEnabledPhrasesOnce(): List<String>
    suspend fun upsert(rule: ReplyPhraseRule): Long
    suspend fun delete(id: Long)
}

interface AutoReplyRuleRepository {
    fun observeRules(): Flow<List<AutoReplyRule>>
    suspend fun getEnabledOnce(): List<AutoReplyRule>
    suspend fun upsert(rule: AutoReplyRule): Long
    suspend fun delete(id: Long)
}


interface AutoReplyReplyHistoryRepository {
    fun observeAll(): Flow<List<AutoReplyReply>>
    suspend fun insert(reply: AutoReplyReply): Long
    suspend fun deleteForRule(ruleId: Long)
}

interface ScheduledMessageRepository {
    fun observeScheduledMessages(): Flow<List<ScheduledOutgoingMessage>>
    suspend fun upsert(message: ScheduledOutgoingMessage): Long
    suspend fun delete(id: Long)
}
