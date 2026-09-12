package com.whatsappworkmanager.app.domain.model

data class WorkGroupInfo(
    val id: Long = 0,
    val name: String,
    val isEnabled: Boolean = false,
    val messageCount: Int = 0,
    val importantCount: Int = 0,
    val lastMessageTime: Long = 0L,
    val platforms: Set<MessagingPlatform> = emptySet()
)
