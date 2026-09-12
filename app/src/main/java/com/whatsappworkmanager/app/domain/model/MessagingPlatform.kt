package com.whatsappworkmanager.app.domain.model

enum class MessagingPlatform(val key: String) {
    WHATSAPP("whatsapp"),
    WHATSAPP_BUSINESS("whatsapp_business"),
    MESSENGER("messenger");

    companion object {
        fun fromKey(value: String?): MessagingPlatform = entries.firstOrNull { it.key == value } ?: WHATSAPP
    }
}

fun MessagingPlatform.displayName(): String = when (this) {
    MessagingPlatform.WHATSAPP -> "WhatsApp"
    MessagingPlatform.WHATSAPP_BUSINESS -> "WhatsApp Business"
    MessagingPlatform.MESSENGER -> "Messenger"
}
