package com.whatsappworkmanager.app.utils

import com.whatsappworkmanager.app.WwmApplication
import kotlinx.coroutines.flow.first

/**
 * Keeps the app icon's badge in sync with unread Important/Need Reply messages. Call
 * [refresh] after anything that changes that count: a new message captured, one marked read,
 * or importance toggled. See [NotificationHelper.updateBadgeCount] for what "in sync" actually
 * means in practice (an honest note on Android's own limits here — not every launcher shows a
 * number).
 */
object BadgeUpdater {
    suspend fun refresh(app: WwmApplication) {
        val messages = app.messageRepository.observeMessages().first()
        val count = messages.count { (it.isImportant || it.needsReply) && !it.isRead }
        NotificationHelper.updateBadgeCount(app, count)
    }
}
