package com.whatsappworkmanager.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_groups")
data class WorkGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isEnabled: Boolean,
    val messageCount: Int,
    val importantCount: Int,
    val lastMessageTime: Long
)
