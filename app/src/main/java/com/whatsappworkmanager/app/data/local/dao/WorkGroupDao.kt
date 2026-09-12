package com.whatsappworkmanager.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.whatsappworkmanager.app.data.local.entity.WorkGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkGroupDao {

    @Query("SELECT * FROM work_groups ORDER BY lastMessageTime DESC")
    fun observeAll(): Flow<List<WorkGroupEntity>>

    @Query("SELECT * FROM work_groups WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): WorkGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: WorkGroupEntity): Long

    @Query("UPDATE work_groups SET isEnabled = :enabled WHERE name = :name COLLATE NOCASE")
    suspend fun setEnabled(name: String, enabled: Boolean)

    @Query("SELECT name FROM work_groups WHERE isEnabled = 1")
    suspend fun getEnabledNames(): List<String>

    @Query("DELETE FROM work_groups WHERE name = :name COLLATE NOCASE")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM work_groups")
    suspend fun deleteAll()
}
