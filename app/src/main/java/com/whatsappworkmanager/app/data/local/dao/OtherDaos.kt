package com.whatsappworkmanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whatsappworkmanager.app.data.local.entity.KeywordRuleEntity
import com.whatsappworkmanager.app.data.local.entity.ImportantContactEntity
import com.whatsappworkmanager.app.data.local.entity.AutoReplyRuleEntity
import com.whatsappworkmanager.app.data.local.entity.AutoReplyReplyHistoryEntity
import com.whatsappworkmanager.app.data.local.entity.ReplyPhraseRuleEntity
import com.whatsappworkmanager.app.data.local.entity.ScheduleEntity
import com.whatsappworkmanager.app.data.local.entity.ScheduledOutgoingMessageEntity
import com.whatsappworkmanager.app.data.local.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries ORDER BY isPinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): SummaryEntity?

    @Insert
    suspend fun insert(summary: SummaryEntity): Long

    @Query("UPDATE summaries SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM summaries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM summaries")
    suspend fun deleteAll()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY startTimeMinutes ASC")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ScheduleEntity): Long

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface KeywordRuleDao {
    @Query("SELECT * FROM keyword_rules ORDER BY priority DESC")
    fun observeAll(): Flow<List<KeywordRuleEntity>>

    @Query("SELECT * FROM keyword_rules WHERE enabled = 1")
    suspend fun getEnabledOnce(): List<KeywordRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: KeywordRuleEntity): Long

    @Query("DELETE FROM keyword_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ImportantContactDao {
    @Query("SELECT * FROM important_contacts ORDER BY name ASC")
    fun observeAll(): Flow<List<ImportantContactEntity>>

    @Query("SELECT name FROM important_contacts WHERE enabled = 1")
    suspend fun getEnabledNamesOnce(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ImportantContactEntity): Long

    @Query("DELETE FROM important_contacts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM important_contacts")
    suspend fun deleteAll()
}

@Dao
interface ReplyPhraseRuleDao {
    @Query("SELECT * FROM reply_phrase_rules ORDER BY phrase ASC")
    fun observeAll(): Flow<List<ReplyPhraseRuleEntity>>

    @Query("SELECT phrase FROM reply_phrase_rules WHERE enabled = 1")
    suspend fun getEnabledPhrasesOnce(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: ReplyPhraseRuleEntity): Long

    @Query("DELETE FROM reply_phrase_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AutoReplyRuleDao {
    @Query("SELECT * FROM auto_reply_rules ORDER BY personMatch ASC")
    fun observeAll(): Flow<List<AutoReplyRuleEntity>>

    @Query("SELECT * FROM auto_reply_rules WHERE enabled = 1")
    suspend fun getEnabledOnce(): List<AutoReplyRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AutoReplyRuleEntity): Long

    @Query("DELETE FROM auto_reply_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY timeMinutes ASC")
    fun observeAll(): Flow<List<ScheduledOutgoingMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ScheduledOutgoingMessageEntity): Long

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AutoReplyReplyHistoryDao {
    @Query("SELECT * FROM auto_reply_reply_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AutoReplyReplyHistoryEntity>>

    @Query("SELECT * FROM auto_reply_reply_history WHERE ruleId = :ruleId ORDER BY createdAt DESC LIMIT 50")
    fun observeForRule(ruleId: Long): Flow<List<AutoReplyReplyHistoryEntity>>

    @Insert
    suspend fun insert(history: AutoReplyReplyHistoryEntity): Long

    @Query("DELETE FROM auto_reply_reply_history WHERE ruleId = :ruleId")
    suspend fun deleteForRule(ruleId: Long)
}
