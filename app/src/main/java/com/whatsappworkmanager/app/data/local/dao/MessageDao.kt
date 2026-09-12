package com.whatsappworkmanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.whatsappworkmanager.app.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE groupName = :groupName ORDER BY timestamp DESC")
    fun observeForGroup(groupName: String): Flow<List<MessageEntity>>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    /**
     * Whether a near-identical message (same group, sender, text, and within [toleranceMillis]
     * of the given timestamp) has already been captured — Android's
     * `NotificationListenerService.onNotificationPosted` can fire more than once for what is
     * genuinely the same notification event (e.g. WhatsApp re-posting or updating it, or a rich
     * preview/media attachment finishing after the initial post), and without this check each
     * firing inserted its own duplicate row.
     *
     * Deliberately a *tolerance window*, not an exact timestamp match: a repost/update of the
     * same notification isn't guaranteed to carry the exact same `postTime` down to the
     * millisecond (and this app falls back to `System.currentTimeMillis()` — a fresh value
     * every call — when `postTime` is ever missing/invalid), so an exact-match check let real
     * duplicates straight through undetected. The window was widened from an initial 10 seconds
     * to 3 minutes after duplicates were still observed with the shorter window — a repost tied
     * to a slow-loading link preview or a catch-up scan running some time after the live
     * notification can genuinely land further apart than a few seconds. 3 minutes is still
     * short enough that two genuinely different messages with coincidentally identical text
     * arriving that close together stays a rare edge case, while comfortably covering realistic
     * repost/catch-up delays for what is really the same underlying notification.
     */
    @Query(
        """
        SELECT COUNT(*) FROM messages
        WHERE groupName = :groupName
          AND text = :text
          AND ABS(timestamp - :timestamp) < :toleranceMillis
          AND platform = :platform
          AND (sender = :sender OR (sender IS NULL AND :sender IS NULL))
        """
    )
    suspend fun countDuplicates(
        groupName: String,
        sender: String?,
        text: String,
        timestamp: Long,
        platform: String,
        toleranceMillis: Long = 180_000L
    ): Int

    @Query("UPDATE messages SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: Long)

    /** Manual override — lets the user flag/unflag a specific message as Important directly,
     *  independent of keyword rules or whether its sender is a saved Important Person. */
    @Query("UPDATE messages SET isImportant = :important WHERE id = :id")
    suspend fun setImportant(id: Long, important: Boolean)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * One-off, over-broad match used only for [com.whatsappworkmanager.app.utils.MessageCleanup]'s
     * startup purge of already-captured system-notification junk — see that class for why this
     * exists (the real filter, `isWhatsAppSystemNotification`, was added after some junk had
     * already been captured, and it only prevents *future* captures, not existing rows).
     */
    @Query(
        """
        SELECT * FROM messages
        WHERE groupName LIKE 'WhatsApp%' COLLATE NOCASE
        """
    )
    suspend fun getCandidateSystemNotificationJunk(): List<MessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE text LIKE '%' || :query || '%'
           OR sender LIKE '%' || :query || '%'
           OR groupName LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        """
    )
    suspend fun search(query: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE timestamp BETWEEN :start AND :end AND isIncludedInSummary = 1 ORDER BY timestamp ASC")
    suspend fun getForPeriod(start: Long, end: Long): List<MessageEntity>

    /**
     * Every message that's opted into Work Summary and hasn't been folded into a generated
     * summary yet — used instead of a fixed time window, so a message can never permanently
     * miss every future summary just because an earlier summary run's window happened to
     * already pass it by. Includes messages from a group that was only enabled *after* they
     * were captured too (see WorkGroupRepositoryImpl.setEnabled, which retroactively flips
     * isIncludedInSummary for that group's existing messages).
     */
    @Query("SELECT * FROM messages WHERE isIncludedInSummary = 1 AND includedInPastSummary = 0 ORDER BY timestamp ASC")
    suspend fun getPendingForSummary(): List<MessageEntity>

    @Query("UPDATE messages SET includedInPastSummary = 1 WHERE isIncludedInSummary = 1 AND includedInPastSummary = 0")
    suspend fun markAllPendingAsSummarized()

    /** Retroactively opts a group's *existing* messages in/out of future summaries — called
     *  when the user toggles a group on/off in Work Groups & Clients, so messages captured
     *  before that toggle aren't permanently stuck excluded. */
    @Query("UPDATE messages SET isIncludedInSummary = :included WHERE groupName = :groupName COLLATE NOCASE")
    suspend fun setIncludedForGroup(groupName: String, included: Boolean)

    /** Used by the group-title cleanup: repoints every message already captured under a
     *  "dirty" bundled-notification title (see cleanGroupTitle's doc) to the clean group name. */
    @Query("UPDATE messages SET groupName = :newName WHERE groupName = :oldName")
    suspend fun renameGroupName(oldName: String, newName: String)

    @Query("SELECT COUNT(*) FROM messages WHERE timestamp >= :startOfDay")
    suspend fun countSince(startOfDay: Long): Int

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("DELETE FROM messages WHERE timestamp < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)
}
