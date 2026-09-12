package com.whatsappworkmanager.app.utils

import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.service.isWhatsAppSystemNotification
import kotlinx.coroutines.flow.first

/**
 * `isWhatsAppSystemNotification` (in WhatsAppNotificationListenerService) prevents WhatsApp's
 * own status notifications — "Checking for new messages", "Backing up chats", etc. — from
 * being captured as real messages, but it was added *after* some had already been captured by
 * earlier app versions, sitting permanently in the local database until removed. This runs
 * once at every app startup, cheap and safe to repeat: it only ever looks at the small set of
 * messages whose group name is literally "WhatsApp" or "WhatsApp Business" (see
 * `MessageDao.getCandidateSystemNotificationJunk`), so it's a no-op once everything's already
 * been cleaned up.
 */
object MessageCleanup {
    suspend fun purgeSystemNotificationJunk(app: WwmApplication) {
        val candidates = app.messageRepository.getCandidateSystemNotificationJunk()
        for (message in candidates) {
            if (isWhatsAppSystemNotification(message.groupName, message.text)) {
                app.messageRepository.delete(message.id)
            }
        }
    }

    /**
     * Same idea, for a different piece of WhatsApp-generated junk: when several notifications
     * from different chats stack up, WhatsApp (regular and Business both) posts a "group
     * summary" notification whose text reads something like "8 messages from 4 chats" — not a
     * real message from anyone. The live capture path now skips these outright via
     * `Notification.FLAG_GROUP_SUMMARY` (a flag that isn't itself stored once something's
     * already in the database), so this text-pattern match is specifically for cleaning up
     * ones captured by an earlier app version, before that flag check existed.
     */
    suspend fun purgeGroupSummaryJunk(app: WwmApplication) {
        val groupSummaryPattern = Regex("""^\d+\s+messages?\s+from\s+\d+\s+chats?$""", RegexOption.IGNORE_CASE)
        val allMessages = app.messageRepository.observeMessages().first()
        for (message in allMessages) {
            if (groupSummaryPattern.matches(message.text.trim())) {
                app.messageRepository.delete(message.id)
            }
        }
    }

    /**
     * Cleans up group names already poisoned by the bundled-notification title bug fixed in
     * this version — see `cleanGroupTitle`'s doc in WhatsAppNotificationListenerService for
     * what the bug was. Repoints every already-captured message from a "dirty" group name to
     * its clean form, then does the same for the Work Groups & Clients tracking entries —
     * merging into an already-existing clean-named entry (summing message/important counts,
     * keeping the more recent last-activity time, staying enabled if either was) rather than
     * leaving two separate cards for what's really one group.
     */
    suspend fun purgeGroupTitleSuffixJunk(app: WwmApplication) {
        val allMessages = app.messageRepository.observeMessages().first()
        val dirtyMessageGroupNames = allMessages.map { it.groupName }.distinct()
            .filter { com.whatsappworkmanager.app.service.cleanGroupTitle(it) != it }
        for (dirtyName in dirtyMessageGroupNames) {
            val cleanName = com.whatsappworkmanager.app.service.cleanGroupTitle(dirtyName)
            app.messageRepository.renameGroupName(dirtyName, cleanName)
        }

        // Work Groups can contain manually tracked entries even when no message was ever
        // captured for them. Therefore this pass must inspect the groups independently of the
        // message table; otherwise a dirty tracking entry with an existing clean counterpart
        // would survive the cleanup (the exact regression covered by MessageCleanupTest).
        val allGroups = app.workGroupRepository.observeGroups().first()
        val dirtyGroups = allGroups.filter {
            com.whatsappworkmanager.app.service.cleanGroupTitle(it.name) != it.name
        }
        for (group in dirtyGroups) {
            val cleanName = com.whatsappworkmanager.app.service.cleanGroupTitle(group.name)
            app.workGroupRepository.mergeGroupName(group.name, cleanName)
        }
    }
}
