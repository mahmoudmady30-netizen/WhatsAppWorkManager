package com.whatsappworkmanager.app.domain.model

enum class ScheduleKind { SUMMARY, WORK_MODE, BREAK_MODE, QUIET_MODE }

data class WorkSchedule(
    val id: Long = 0,
    val name: String,
    val startTimeMinutes: Int, // minutes from midnight, local time
    val endTimeMinutes: Int? = null, // null for one-shot summary triggers
    val enabled: Boolean = true,
    val kind: ScheduleKind = ScheduleKind.SUMMARY,
    val repeatDaily: Boolean = true
)

data class KeywordRule(
    val id: Long = 0,
    val keyword: String,
    val priority: Int, // scoring weight
    val enabled: Boolean = true
)

/**
 * A sender whose messages should always be treated as important, regardless of keyword
 * score — e.g. your manager, a key client. Matching is case-insensitive substring match
 * against the notification's extracted sender name (see NotificationTextParser).
 */
data class ImportantContact(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    // Optional — lets this contact be picked directly when scheduling a 1:1 message, instead
    // of typing their number again. WhatsApp notifications never expose a phone number, so
    // this has to be entered once by the user (e.g. from the Important People screen).
    val phoneNumber: String? = null
)

/**
 * A user-added phrase that marks a message as needing a reply, on top of the built-in
 * defaults in ReplyDetector.
 */
data class ReplyPhraseRule(
    val id: Long = 0,
    val phrase: String,
    val enabled: Boolean = true
)

/**
 * A safe, one-tap "ready reply" rule — see AutoReplyRuleEntity's doc for the full reasoning.
 * [keyword] null/blank means any message from the configured targets qualifies; a blank [personMatch] means any sender. [replyText] null/blank
 * means the AI writes a fresh reply from the actual message each time, rather than always
 * using the same fixed text.
 */
/**
 * How the AI should sound when it writes a reply for this rule's person — chosen once per
 * person when adding/editing the rule, rather than guessed automatically, since tone is
 * fundamentally about the *relationship*, something no message content alone reliably reveals.
 * Purely a prompt-shaping hint for AI-generated replies; has no effect at all when [AutoReplyRule.replyText]
 * is a fixed, pre-written string, since there's nothing left for the AI to write.
 */
enum class ReplyTone {
    WORK, FRIEND, FAMILY;

    companion object {
        val DEFAULT = WORK
    }
}

data class AutoReplyRule(
    val id: Long = 0,
    val personMatch: String,
    val keyword: String? = null,
    val replyText: String? = null,
    val aiInstruction: String? = null,
    val autoSend: Boolean = false,
    val phoneNumber: String? = null,
    val enabled: Boolean = true,
    val tone: ReplyTone = ReplyTone.DEFAULT,
    // Set on creation and refreshed on every save (including a plain tone-only change) — the
    // timestamp shown on each card, so it always reflects "when this rule last changed," not
    // just when it was first added.
    val updatedAt: Long = System.currentTimeMillis(),
    val platform: MessagingPlatform = MessagingPlatform.WHATSAPP
)

data class ScheduledOutgoingMessage(
    val id: Long = 0,
    val text: String,
    val timeMinutes: Int,
    val repeatDaily: Boolean,
    val enabled: Boolean = true,
    // Optional: WhatsApp phone number in international format (e.g. "201234567890", no "+" or
    // spaces). When set, the reminder opens that exact chat pre-filled with `text` via
    // WhatsApp's official click-to-chat deep link. When null (e.g. the target is a group,
    // which has no phone number), the reminder falls back to opening WhatsApp generally.
    val phoneNumber: String? = null,
    // The display name shown alongside the number in the Scheduled Messages list — captured
    // whenever a name is actually known (picked from Important People or the phone's
    // Contacts), purely for display; the reminder itself still only ever needs [phoneNumber]
    // to open the right chat. Null for a manually-typed number, or a group-target message
    // (phoneNumber also null in that case).
    val recipientName: String? = null,
    // Set whenever this message is actually sent — from the Dashboard's "Send Now" shortcut,
    // the Scheduled Messages screen's own Send button, or the reminder notification's tap
    // action. Used to detect a "missed" occurrence: a scheduled time that has passed with
    // this still null or still older than that occurrence, surfaced as a badge count on the
    // Schedule nav button. Not reset on a repeating message's *next* occurrence — only ever
    // compared against "the most recent occurrence that's already passed," so a repeating
    // message correctly starts being trackable as missed again once its next scheduled time
    // rolls past without a fresh send.
    val lastSentAt: Long? = null,
    val platform: MessagingPlatform = MessagingPlatform.WHATSAPP
)
