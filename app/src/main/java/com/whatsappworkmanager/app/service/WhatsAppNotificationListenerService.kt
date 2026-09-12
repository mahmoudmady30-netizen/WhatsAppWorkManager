package com.whatsappworkmanager.app.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkMessage
import com.whatsappworkmanager.app.domain.model.MessagingPlatform
import com.whatsappworkmanager.app.domain.model.AutoReplyRule
import com.whatsappworkmanager.app.domain.usecase.KeywordScoring
import com.whatsappworkmanager.app.domain.usecase.MessageClassifier
import com.whatsappworkmanager.app.domain.usecase.NotificationTextParser
import com.whatsappworkmanager.app.domain.usecase.ReplyDetector
import com.whatsappworkmanager.app.utils.Constants
import com.whatsappworkmanager.app.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import androidx.core.content.ContextCompat
import kotlinx.coroutines.sync.withLock
import com.whatsappworkmanager.app.presentation.search.detectLanguage
import com.whatsappworkmanager.app.utils.IntentHelper
import java.security.MessageDigest

/**
 * Reads WhatsApp / WhatsApp Business notifications the user has explicitly granted access to,
 * via the standard NotificationListenerService API. This is the ONLY mechanism this app uses
 * to observe WhatsApp activity.
 *
 * Explicitly NOT done here, by design:
 *  - no reading of WhatsApp's own database or files
 *  - no Accessibility Service usage
 *  - no altering of read receipts / last-seen / online status
 *  - no access to WhatsApp's private database or files
 *  - no modification of read receipts / last-seen / online status
 *  - Premium Auto Send may use WhatsApp's official notification Direct Reply action when
 *    WhatsApp exposes a RemoteInput. This is the background path and does not open the chat UI.
 *    If Direct Reply is unavailable, the app falls back to the explicitly enabled Accessibility
 *    automation path, which requires a visible/unlocked WhatsApp window.
 *
 * WhatsApp's notification structure can change between versions/OEMs. Every extraction step
 * below is defensive: a missing/renamed field degrades gracefully instead of crashing.
 *
 * The persistence + classification logic lives in the top-level [captureMessage] function
 * (below, same file) rather than as a method on this class, specifically so it can be
 * exercised in a Robolectric unit test without needing a bound Service/StatusBarNotification —
 * see WhatsAppNotificationListenerServiceTest.
 */
class WhatsAppNotificationListenerService : NotificationListenerService() {

    /**
     * Keeps the latest WhatsApp Direct-Reply action per visible chat. A scheduled message may
     * fire minutes/hours after the last incoming notification; requiring a notification to still
     * be visible at the exact scheduled second was the main reason schedules degraded into a
     * reminder. The PendingIntent/RemoteInput action is kept while the notification-listener
     * process is alive, so the alarm can submit the reply without opening WhatsApp.
     */
    private data class CachedReplyAction(
        val title: String,
        val packageName: String,
        val actionIntent: android.app.PendingIntent,
        val remoteInputs: Array<RemoteInput>,
        val savedAt: Long
    )

    private fun cacheReplyAction(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        if (!isSupportedPackage(sbn.packageName)) return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?.takeIf { it.isNotBlank() } ?: return
        val action = notification.actions?.firstOrNull {
            it.actionIntent != null && !it.remoteInputs.isNullOrEmpty()
        } ?: return
        val actionIntent = action.actionIntent ?: return
        val inputs = action.remoteInputs?.copyOf() ?: return
        synchronized(cachedReplyActions) {
            cachedReplyActions[cacheKey(title, sbn.packageName)] = CachedReplyAction(
                title = title, packageName = sbn.packageName,
                actionIntent = actionIntent, remoteInputs = inputs, savedAt = System.currentTimeMillis()
            )
            while (cachedReplyActions.size > 100) {
                val oldest = cachedReplyActions.minByOrNull { it.value.savedAt }?.key ?: break
                cachedReplyActions.remove(oldest)
            }
        }
        // If a schedule fired while the listener was disconnected, the alarm left a durable
        // pending transaction. A newly posted WhatsApp notification may now provide exactly the
        // RemoteInput action required to complete it, even with the screen locked.
        serviceScope.launch { processPendingScheduledSends() }
    }


    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val textParser = NotificationTextParser()
    private val scheduledSendReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_SEND_SCHEDULED_MESSAGE) return
            val pending = goAsync()
            serviceScope.launch {
                try {
                    val id = intent.getLongExtra(EXTRA_SCHEDULED_ID, -1L)
                    val text = intent.getStringExtra(EXTRA_SCHEDULED_TEXT).orEmpty()
                    val recipient = intent.getStringExtra(EXTRA_SCHEDULED_RECIPIENT).orEmpty()
                    val phoneNumber = intent.getStringExtra(EXTRA_SCHEDULED_PHONE)
                    val platform = MessagingPlatform.fromKey(intent.getStringExtra(EXTRA_SCHEDULED_PLATFORM))
                    if (id >= 0 && text.isNotBlank()) {
                        val app = applicationContext as WwmApplication
                        val preferredVariant = app.settingsDataStore.whatsappVariant.first()
                        val preferredPackage = IntentHelper.resolveMessagingPackage(packageManager, platform)
                        val sent = sendScheduledViaActiveNotification(text, recipient, phoneNumber, preferredPackage)
                        if (sent) {
                            app.scheduledMessageRepository.observeScheduledMessages().first()
                                .firstOrNull { it.id == id }?.let { message ->
                                    app.scheduledMessageRepository.upsert(message.copy(lastSentAt = System.currentTimeMillis()))
                                }
                            NotificationHelper.cancelScheduledMessageReminder(applicationContext, id)
                            ScheduledSendQueue.remove(applicationContext, id)
                            removePendingScheduledSend(applicationContext, id)
                        }
                        // This is an ordered broadcast: report the actual result to the worker
                        // so it can retry silently instead of forcing a manual tap.
                        setResultCode(if (sent) android.app.Activity.RESULT_OK else android.app.Activity.RESULT_CANCELED)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Scheduled background send failed", t)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        try {
            handle(sbn)
        } catch (t: Throwable) {
            // Never let a malformed/unexpected notification structure crash the listener.
            Log.w(TAG, "Failed to process notification safely", t)
        }
    }

    private fun handle(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        if (!isSupportedPackage(pkg)) return
        // Cache WhatsApp's own Direct Reply action before any duplicate/status filtering.
        // This lets a later scheduled alarm reply even when the notification has disappeared.
        cacheReplyAction(sbn)

        // WhatsApp (regular and Business both) bundles multiple notifications from different
        // chats into one Android notification group and posts a "summary" notification on top
        // — the literal text Android shows for it is something like "8 messages from 4
        // chats", with the app's own name as the "sender". That's not a real message from
        // anyone; it's WhatsApp's own aggregate counter. FLAG_GROUP_SUMMARY is the official,
        // reliable way to identify this specific kind of notification (as opposed to guessing
        // from its text, which would be fragile across languages/WhatsApp versions) — skip it
        // outright, before even looking at title/text, since a summary notification's content
        // was never meant to be read as a message in the first place.
        if ((sbn.notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY != 0) return

        // "Checking for new messages", "Backing up chats", and similar status notifications
        // represent an ACTIVE, ongoing background operation — not a one-time event like a
        // real incoming message — and Android's own convention for that is
        // FLAG_ONGOING_EVENT (the same flag behind an undismissable download-progress bar).
        // This is a structural signal rather than a guess at the exact wording of the status
        // text, so it keeps working even if WhatsApp changes that phrasing, capitalizes it
        // differently, or shows it in a language the text-based check below doesn't list —
        // the text check stays too, as a second layer for any status notification that
        // *isn't* flagged ongoing, but this is the fix that doesn't depend on matching text
        // at all.
        if ((sbn.notification?.flags ?: 0) and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras: Bundle = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        if (title.isNullOrBlank() || text.isNullOrBlank()) return

        // See lastSeenTextByKey's doc — this is what actually stops the periodic catch-up
        // scan (and a listener reconnect) from re-capturing the exact same still-active,
        // unchanged notification as if it were a brand-new message every time they run, while
        // still letting a genuinely new message posted under a reused notification key
        // through.
        if (alreadyProcessed(sbn.key, "$title\n$text")) return

        // WhatsApp posts its own status notifications through the exact same channel as real
        // chat messages — "Checking for new messages", "Backing up chats", etc. — with the
        // app's own name as the title rather than a contact/group name. Without filtering
        // these out, they showed up in Search/summaries looking exactly like a real message
        // from someone named "WhatsApp Business".
        if (isWhatsAppSystemNotification(title, text)) return

        // WhatsApp group notifications typically title as "Group Name" and text as
        // "Sender: message". Direct chats title as the contact name with just the message
        // in text. We try to split "Sender: message"; if it doesn't match, sender is null
        // and the whole text is treated as the message body — never crashes either way.
        val parsed = textParser.parse(text)
        val groupName = cleanGroupTitle(title)
        val timestamp = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis()

        val app = applicationContext as WwmApplication
        val context = applicationContext
        serviceScope.launch {
            try {
                captureMessage(context, app, groupName, parsed.sender, parsed.message, timestamp, sbn)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to persist captured message", t)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(this, scheduledSendReceiver, IntentFilter(ACTION_SEND_SCHEDULED_MESSAGE), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(scheduledSendReceiver) }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
        // The genuinely robust fix for "might have missed a notification" — rather than
        // guessing at every possible reason a specific `onNotificationPosted` callback could
        // have been delayed or dropped (Doze timing, the service process being killed and
        // restarted, a brief connection gap), ask the system directly for the ground truth:
        // every notification actually active right now. `getActiveNotifications()` is a
        // standard, official NotificationListenerService API, not a workaround — it returns
        // exactly what's in the shade at this moment, muted or not, regardless of how or when
        // each one was posted. Reusing `handle()` means the existing dedup check
        // (isDuplicate) makes this scan a genuine no-op for anything already captured, so it's
        // safe to run every single time the listener (re)connects.
        performCatchUpScan()
        serviceScope.launch { processPendingScheduledSends() }
    }

    /**
     * Re-syncs against every currently active WhatsApp/Business notification — called on every
     * listener (re)connection automatically, and also from the Dashboard's Refresh button (via
     * [triggerCatchUpScan]) for an on-demand version the user can invoke themselves at any time.
     */
    private fun performCatchUpScan() {
        try {
            val active = activeNotifications ?: return
            for (sbn in active) {
                if (isSupportedPackage(sbn.packageName)) {
                    handle(sbn)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Catch-up scan failed", t)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Notification listener disconnected")
    }

    /**
     * Best-effort scheduled send using an already-posted WhatsApp notification's Direct Reply.
     * This is the only local Android path that can send without launching WhatsApp's UI. It is
     * intentionally recipient-matched; if no matching active notification exposes RemoteInput,
     * it returns false and the scheduled reminder remains available.
     */
    private fun sendScheduledViaActiveNotification(text: String, recipient: String, phoneNumber: String? = null, preferredPackage: String? = null): Boolean {
        if (text.isBlank()) return false
        val wanted = recipient.trim()
        val wantedDigits = phoneNumber?.filter { it.isDigit() }.orEmpty()

        // First use a currently active notification, which is the freshest action.
        val active = activeNotifications.orEmpty()
        val activeCandidate = active.asSequence()
            .filter { isSupportedPackage(it.packageName) }
            .filter { preferredPackage == null || it.packageName == preferredPackage }
            .filter { (it.notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY == 0 }
            .filter { sbn ->
                val title = sbn.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
                val titleDigits = title.filter { it.isDigit() }
                wanted.isBlank() ||
                    title.equals(wanted, ignoreCase = true) || title.contains(wanted, ignoreCase = true) ||
                    (wantedDigits.length >= 6 && titleDigits.contains(wantedDigits))
            }
            .firstOrNull { sbn -> sbn.notification?.actions?.any { !it.remoteInputs.isNullOrEmpty() && it.actionIntent != null } == true }
        if (activeCandidate != null && sendViaNotificationReply(applicationContext, activeCandidate, text)) return true

        // If the notification was dismissed/updated after it arrived, use the cached Direct
        // Reply action captured by onNotificationPosted. This is the key fix for scheduled send:
        // the alarm no longer requires the target notification to still be in the shade.
        val cached = synchronized(cachedReplyActions) {
            cachedReplyActions.values
                .filter { isSupportedPackage(it.packageName) }
                .filter { preferredPackage == null || it.packageName == preferredPackage }
                .filter { cached ->
                    val titleDigits = cached.title.filter { it.isDigit() }
                    wanted.isBlank() ||
                        cached.title.equals(wanted, ignoreCase = true) || cached.title.contains(wanted, ignoreCase = true) ||
                        (wantedDigits.length >= 6 && titleDigits.contains(wantedDigits))
                }
                .maxByOrNull { it.savedAt }
        } ?: return false

        return runCatching {
            val intent = Intent()
            val results = Bundle().apply { cached.remoteInputs.forEach { putCharSequence(it.resultKey, text) } }
            RemoteInput.addResultsToIntent(cached.remoteInputs, intent, results)
            cached.actionIntent.send(applicationContext, 0, intent)
            true
        }.onFailure { Log.w(TAG, "Cached WhatsApp scheduled direct reply failed", it) }
            .getOrDefault(false)
    }


    private fun processPendingScheduledSends() {
        val ids = pendingScheduledIds(applicationContext)
        if (ids.isEmpty()) return
        val app = applicationContext as WwmApplication
        serviceScope.launch {
            val schedules = runCatching { app.scheduledMessageRepository.observeScheduledMessages().first() }
                .getOrElse { emptyList() }
            for (id in ids) {
                val message = schedules.firstOrNull { it.id == id && it.enabled }
                if (message == null) {
                    removePendingScheduledSend(applicationContext, id)
                    continue
                }
                val preferredVariant = app.settingsDataStore.whatsappVariant.first()
                val globalPackage = IntentHelper.resolveMessagingPackage(applicationContext.packageManager, message.platform)
                val storedPackage = applicationContext.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
                    .getStringSet("pending_packages", emptySet()).orEmpty()
                    .firstOrNull { it.startsWith("$id|") }
                    ?.substringAfter("|")
                val preferredPackage = storedPackage ?: globalPackage
                val sent = sendScheduledViaActiveNotification(
                    message.text, message.recipientName.orEmpty(), message.phoneNumber, preferredPackage
                )
                if (sent) {
                    runCatching {
                        app.scheduledMessageRepository.upsert(
                            message.copy(lastSentAt = System.currentTimeMillis())
                        )
                    }
                    NotificationHelper.cancelScheduledMessageReminder(applicationContext, id)
                    ScheduledSendQueue.remove(applicationContext, id)
                    removePendingScheduledSend(applicationContext, id)
                }
            }
        }
    }

    companion object {
        private const val TAG = "WwmNotificationListener"
        private const val PENDING_PREFS = "scheduled_send_transactions"
        private const val PENDING_IDS = "pending_ids"
        private val cachedReplyActions = mutableMapOf<String, CachedReplyAction>()
        private fun cacheKey(title: String, packageName: String): String =
            "${packageName}:${title.trim().lowercase()}"

        fun enqueuePendingScheduledSend(context: Context, id: Long, packageName: String?) {
            val prefs = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
            val ids = prefs.getStringSet(PENDING_IDS, emptySet()).orEmpty().toMutableSet()
            ids.add(id.toString())
            val packages = prefs.getStringSet("pending_packages", emptySet()).orEmpty().toMutableSet()
            packages.removeAll { it.startsWith("$id|") }
            packageName?.let { packages.add("$id|$it") }
            prefs.edit().putStringSet(PENDING_IDS, ids).putStringSet("pending_packages", packages).apply()
        }

        private fun removePendingScheduledSend(context: Context, id: Long) {
            val prefs = context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
            val ids = prefs.getStringSet(PENDING_IDS, emptySet()).orEmpty().toMutableSet()
            ids.remove(id.toString())
            val packages = prefs.getStringSet("pending_packages", emptySet()).orEmpty().toMutableSet()
            packages.removeAll { it.startsWith("$id|") }
            prefs.edit().putStringSet(PENDING_IDS, ids).putStringSet("pending_packages", packages).apply()
        }

        private fun pendingScheduledIds(context: Context): Set<Long> =
            context.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
                .getStringSet(PENDING_IDS, emptySet()).orEmpty()
                .mapNotNull { it.toLongOrNull() }.toSet()

        /** Called by the UI automation lane after it has actually clicked WhatsApp's Send. */
        fun markScheduledSendComplete(context: Context, id: Long) {
            removePendingScheduledSend(context, id)
            ScheduledSendQueue.remove(context, id)
        }

        const val ACTION_SEND_SCHEDULED_MESSAGE = "com.whatsappworkmanager.app.ACTION_SEND_SCHEDULED_MESSAGE"
        const val EXTRA_SCHEDULED_ID = "extra_scheduled_id"
        const val EXTRA_SCHEDULED_TEXT = "extra_scheduled_text"
        const val EXTRA_SCHEDULED_RECIPIENT = "extra_scheduled_recipient"
        const val EXTRA_SCHEDULED_PHONE = "extra_scheduled_phone"
        const val EXTRA_SCHEDULED_PLATFORM = "extra_scheduled_platform"
        /**
         * Tracks the last-seen text for each StatusBarNotification `key` — the official,
         * stable Android identifier for "this is the same notification slot" (tied to
         * package/id/tag/user, not content). Pairing it with the text, rather than keying on
         * `key` alone, matters: some notifications get *updated in place* with genuinely new
         * content under the same key (an appended-message style notification, for instance),
         * and blocking on key alone would silently swallow every message after the first one
         * in that thread. What actually needs blocking is a re-scan of a notification whose
         * key *and* text both match what was already seen — that's the same notification,
         * unchanged, encountered again — which is exactly what a periodic catch-up scan (or a
         * listener reconnect) does for every notification still sitting active in the shade.
         * `postTime` alone couldn't distinguish this from a genuinely new message when missing
         * /invalid, since this app's fallback for that case is a *fresh* `currentTimeMillis()`
         * on every call — comfortably outside even a generous timestamp tolerance window.
         * Bounded and synchronized since multiple scans can run in overlapping coroutines.
         */
        private const val MAX_TRACKED_KEYS = 500
        private val lastSeenTextByKey = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, String>(16, 0.75f, false) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
                    size > MAX_TRACKED_KEYS
            }
        )

        internal fun alreadyProcessed(key: String?, text: String): Boolean {
            if (key == null) return false
            val previous = lastSeenTextByKey.put(key, text)
            return previous == text
        }

        /**
         * Forces the listener to disconnect and immediately reconnect, which re-triggers
         * [onListenerConnected] and therefore a fresh catch-up scan — an on-demand "resync
         * now" for anything that might have been missed, without needing to hold a fragile
         * reference to a live service instance (which could be null if the process was ever
         * killed and hasn't been asked to reconnect yet). `requestRebind` is itself a standard
         * static NotificationListenerService API, safe to call whether or not the service is
         * currently bound.
         */
        fun triggerCatchUpScan(context: Context) {
            try {
                requestRebind(
                    android.content.ComponentName(context, WhatsAppNotificationListenerService::class.java)
                )
            } catch (t: Throwable) {
                Log.w(TAG, "Could not request a catch-up rebind", t)
            }
        }
    }
}

/**
 * Sends a reply through WhatsApp's notification Direct Reply action when the notification
 * exposes an Android RemoteInput. This does not launch WhatsApp or require an active window, so
 * it is suitable for the user's requested screen-off/locked-phone background path. Some
 * WhatsApp/OEM notification variants intentionally expose no reply action; in that case the
 * function returns false and the caller falls back to the explicitly enabled Accessibility
 * automation path.
 */
internal fun sendViaNotificationReply(
    context: Context,
    sbn: StatusBarNotification,
    replyText: String
): Boolean {
    if (replyText.isBlank()) return false
    val notification = sbn.notification ?: return false
    val actions = notification.actions ?: return false
    val replyAction = actions.firstOrNull { action ->
        val inputs = action.remoteInputs
        !inputs.isNullOrEmpty() && action.actionIntent != null
    } ?: return false

    return runCatching {
        val intent = android.content.Intent()
        val results = Bundle().apply {
            // WhatsApp normally exposes one RemoteInput for the reply field. Populate every
            // input defensively; only the matching key will be consumed by WhatsApp.
            replyAction.remoteInputs!!.forEach { putCharSequence(it.resultKey, replyText) }
        }
        RemoteInput.addResultsToIntent(replyAction.remoteInputs, intent, results)
        replyAction.actionIntent.send(context, 0, intent)
        true
    }.onFailure {
        Log.w(CAPTURE_TAG, "Messaging notification direct reply failed; using Accessibility fallback", it)
    }.getOrDefault(false)
}

/** True for supported messaging packages (WhatsApp, WhatsApp Business, or Messenger). */
internal fun isSupportedPackage(packageName: String?): Boolean =
    packageName == Constants.WHATSAPP_PACKAGE || packageName == Constants.WHATSAPP_BUSINESS_PACKAGE || packageName == Constants.MESSENGER_PACKAGE

internal fun messagingPlatformForPackage(packageName: String?): MessagingPlatform = when (packageName) {
    Constants.MESSENGER_PACKAGE -> MessagingPlatform.MESSENGER
    Constants.WHATSAPP_BUSINESS_PACKAGE -> MessagingPlatform.WHATSAPP_BUSINESS
    else -> MessagingPlatform.WHATSAPP
}

/**
 * True for WhatsApp's own status notifications rather than an actual chat message — these use
 * the app's own name ("WhatsApp" / "WhatsApp Business") as the title, since they're not from
 * any contact or group, plus a small set of known status phrases as a second, independent
 * signal (title alone is enough in practice, but a real contact could theoretically be named
 * "WhatsApp Business" too — checking the text as well makes that false-positive far less
 * likely without needing to filter on anything more fragile like notification category, which
 * varies across WhatsApp versions).
 */
internal fun isWhatsAppSystemNotification(title: String, text: String): Boolean {
    val normalizedTitle = title.trim()
    // startsWith rather than an exact match: some devices/launchers append a badge count or
    // similar suffix to the title (e.g. "WhatsApp Business (3)"), which an exact-equals check
    // would silently miss — this still can't false-positive onto a real contact/group, since
    // no real chat is ever named literally "WhatsApp" or "WhatsApp Business" at the start.
    val titleIsAppName = normalizedTitle.startsWith("WhatsApp Business", ignoreCase = true) ||
        normalizedTitle.startsWith("WhatsApp", ignoreCase = true) ||
        normalizedTitle.equals("Messenger", ignoreCase = true)
    if (!titleIsAppName) return false

    val knownStatusPhrases = listOf(
        "checking for new messages",
        "backing up",
        "backed up",
        "turning off notifications",
        "media backed up",
        "restoring",
        "tap to update",
        "downloading update"
    )
    return knownStatusPhrases.any { text.contains(it, ignoreCase = true) }
}

/**
 * When several messages from the same group stack up, WhatsApp sometimes titles the bundled
 * notification "Group Name (N messages): Last Sender" instead of just "Group Name" — without
 * stripping that suffix, the group's own display name (used everywhere: Work Groups & Clients,
 * Search, Summary, Auto Reply matching) permanently absorbs whichever person happened to send
 * the most recent message in that specific notification, rather than staying just the group's
 * actual name. Strips the "(N message(s)): Sender" part when present; returns the title
 * unchanged for a normal single-message title or a 1:1 chat (which never has this suffix).
 */
/**
 * Strips WhatsApp's own "per-sender" suffix from a group notification's title, which shows up
 * in two different forms depending on how many messages are bundled:
 * - "GroupName (N messages): Sender" when several are bundled together
 * - "GroupName: Sender" when it's just the one new message (no count needed)
 * Both are WhatsApp's own annotation of *who* just sent a message in the group, not part of
 * the group's own name — left unstripped, the same group ends up captured under a different
 * "name" every time a different member sends the first message of a batch, which is exactly
 * the bug this fixes (see MessageCleanup.purgeGroupTitleSuffixJunk for cleaning up already-
 * captured data poisoned by it).
 *
 * The bare "GroupName: Sender" form (no message count) needs a heuristic, since a colon alone
 * doesn't distinguish WhatsApp's suffix from a group name that legitimately contains one (e.g.
 * "Team: Design") — only stripped when the text after the *last* colon actually looks like a
 * sender identifier: a phone number, or a short (≤4 words) capitalized name, rather than
 * assuming every colon is this suffix.
 */
internal fun cleanGroupTitle(title: String): String {
    val trimmed = title.trim()
    val bundledMatch = Regex("""^(.+?)\s*\(\d+\s+messages?\)\s*:.*$""").find(trimmed)
    if (bundledMatch != null) {
        return bundledMatch.groupValues[1].trim().ifBlank { trimmed }
    }
    val colonIndex = trimmed.lastIndexOf(':')
    if (colonIndex in 1 until trimmed.length - 1) {
        val prefix = trimmed.substring(0, colonIndex).trim()
        val suffix = trimmed.substring(colonIndex + 1).trim()
        if (prefix.isNotBlank() && looksLikeSenderIdentifier(suffix)) {
            return prefix
        }
    }
    return trimmed
}

private fun looksLikeSenderIdentifier(text: String): Boolean {
    if (text.isBlank()) return false
    val withoutSpaces = text.replace(" ", "")
    val digitsOnly = withoutSpaces.filter { it.isDigit() }
    // A phone number: mostly digits (allowing for a leading "+" and internal spaces/dashes).
    if (digitsOnly.length >= 6 && digitsOnly.length.toFloat() / withoutSpaces.length >= 0.6f) return true
    // A short, plausible person name: a handful of capitalized words, nothing that reads like
    // a subtitle or category (parentheses, slashes, and the like).
    val words = text.split(" ").filter { it.isNotBlank() }
    if (words.isEmpty() || words.size > 4) return false
    if (Regex("""[(){}\[\]#@/|]""").containsMatchIn(text)) return false
    return words.all { it.firstOrNull()?.isUpperCase() == true }
}

/**
 * Classifies and persists one captured message, updates the owning group's stats, and — if the
 * group is opted into Work Summary AND the message is high-priority — posts an "Important
 * Message" notification. Pulled out of the Service class so it has no dependency on
 * StatusBarNotification/Bundle and can be unit-tested directly (see
 * WhatsAppNotificationListenerServiceTest).
 *
 * Builds a *fresh* [MessageClassifier] on every call, seeded with whatever custom keyword
 * rules, important-people names, and reply phrases are currently saved — so changes made in
 * Settings (Important Keywords / Important People / Reply Detection) take effect on the very
 * next message, without needing an app restart. Messages arrive one at a time via notifications
 * (not a hot loop), so the small cost of rebuilding this per message is negligible.
 */
private const val CAPTURE_TAG = "WwmNotificationListener"

/**
 * Serializes the "check for a duplicate, then insert" sequence in [captureMessage] below.
 * Without this, two concurrent calls (the live notification listener and the periodic
 * catch-up scan can both be mid-flight at once, or a burst of several notifications can be
 * processed together) could each check "is this a duplicate?" before either had actually
 * inserted its row — both would see "no" and both would insert, producing a genuine duplicate
 * regardless of how generous the timestamp tolerance window is. A Mutex makes the whole
 * check-then-insert sequence atomic across every caller, which a wider time window alone can't
 * guarantee.
 */
private val captureMutex = kotlinx.coroutines.sync.Mutex()


/**
 * Determines whether one Premium Auto Reply rule targets the incoming WhatsApp notification.
 * Name matching is case-insensitive; phone matching ignores formatting characters. A keyword,
 * when configured, must also be present in the incoming message.
 */
internal fun matchesAutoReplyRule(
    rule: com.whatsappworkmanager.app.domain.model.AutoReplyRule,
    groupName: String,
    sender: String?,
    messageText: String
): Boolean {
    val senderDigits = sender?.filter { it.isDigit() }.orEmpty()
    val groupDigits = groupName.filter { it.isDigit() }
    // One rule can target several names: comma, semicolon, or newline separates targets.
    // A blank target is the intentional global rule: every incoming WhatsApp message is eligible.
    val targets = rule.personMatch
        .split(',', ';', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }

    val personMatches = if (targets.isEmpty()) {
        true
    } else {
        targets.any { target ->
            val personDigits = target.filter { it.isDigit() }
            val personPhoneMatch = personDigits.length >= 6 && (
                senderDigits.contains(personDigits) || groupDigits.contains(personDigits)
            )
            groupName.contains(target, ignoreCase = true) ||
                (sender != null && sender.contains(target, ignoreCase = true)) ||
                personPhoneMatch
        }
    }

    val savedPhoneDigits = rule.phoneNumber?.filter { it.isDigit() }.orEmpty()
    val savedPhoneMatch = savedPhoneDigits.length >= 6 && (
        senderDigits.contains(savedPhoneDigits) || groupDigits.contains(savedPhoneDigits)
    )
    val keywordMatches = rule.keyword.isNullOrBlank() ||
        messageText.contains(rule.keyword, ignoreCase = true)

    return (personMatches || savedPhoneMatch) && keywordMatches
}

/** Stable grouping key used only to prevent duplicate replies for the same configured client. */
private fun autoReplyTargetKey(
    rule: com.whatsappworkmanager.app.domain.model.AutoReplyRule
): String {
    val phone = rule.phoneNumber?.filter { it.isDigit() }.orEmpty()
    return if (phone.length >= 6) {
        "phone:$phone"
    } else if (rule.personMatch.isBlank()) {
        "global"
    } else {
        "person:${rule.personMatch.trim().lowercase()}"
    }
}

suspend fun captureMessage(
    context: Context,
    app: WwmApplication,
    groupName: String,
    sender: String?,
    messageText: String,
    timestamp: Long,
    sourceNotification: StatusBarNotification? = null
) {
    // Android can fire onNotificationPosted more than once for what is genuinely the same
    // notification event (WhatsApp re-posting/updating it) — without this check, each firing
    // inserted its own duplicate row, which is exactly what showed up as the same message
    // appearing twice in Search with an identical timestamp. The whole check-then-insert span
    // is held under captureMutex — not just the check — since releasing the lock in between
    // would let a second concurrent call slip its own insert in before this one's insert ever
    // happens, defeating the point; see captureMutex's doc for the full reasoning.
    val platform = messagingPlatformForPackage(sourceNotification?.packageName)

    val customKeywordRules = app.keywordRuleRepository.getEnabledOnce()
    val importantSenderNames = app.importantContactRepository.getEnabledNamesOnce()
    val customReplyPhrases = app.replyPhraseRuleRepository.getEnabledPhrasesOnce()
    val autoEnableNewGroups = app.settingsDataStore.autoEnableNewGroups.first()

    val classifier = MessageClassifier(
        keywordScoring = KeywordScoring(extraRules = customKeywordRules),
        replyDetector = ReplyDetector(customPhrases = customReplyPhrases)
    )
    val classification = classifier.classify(
        text = messageText,
        sender = sender,
        importantSenderNames = importantSenderNames
    )

    val captured = captureMutex.withLock {
        if (app.messageRepository.isDuplicate(groupName, sender, messageText, timestamp, platform)) {
            return@withLock null
        }

        // Upsert BEFORE checking which groups are enabled: with auto-enable turned on
        // (Settings → "Automatically include new groups/clients"), a brand-new group is
        // created already enabled — checking the enabled list only *after* this means the
        // very first message from that new group is correctly counted as included too, not
        // just the second message onward.
        app.workGroupRepository.upsertGroupSeen(
            name = groupName,
            timestamp = timestamp,
            isImportant = classification.isImportant,
            autoEnable = autoEnableNewGroups
        )

        // Case-insensitive on purpose: a group/contact added manually by typing its name (see
        // WorkGroupRepository.addManually) should still match even if the user's
        // capitalization differs slightly from exactly what WhatsApp puts in the notification
        // title.
        val enabledGroups = app.workGroupRepository.getEnabledGroupNames()
        val isEnabledGroup = enabledGroups.any { it.equals(groupName, ignoreCase = true) }

        val message = WorkMessage(
            groupName = groupName,
            sender = sender,
            text = messageText,
            timestamp = timestamp,
            isImportant = classification.isImportant,
            importanceScore = classification.score,
            needsReply = classification.needsReply,
            isRead = false,
            isIncludedInSummary = isEnabledGroup,
            platform = platform
        )
        val newId = app.messageRepository.insert(message)
        Triple(newId, message, isEnabledGroup)
    } ?: return
    val (newId, message, isEnabledGroup) = captured

    // Optional automatic reply pipeline. It is OFF by default and only runs for groups/clients
    // the user explicitly enabled. Generation happens here; actual WhatsApp UI interaction is
    // delegated to the user-enabled AccessibilityService, which presses Send only after the
    // queued text has been inserted into the WhatsApp composer.
    if (isEnabledGroup && classification.needsReply && app.settingsDataStore.autoReplyEnabled.first()) {
        try {
            val language = detectLanguage(messageText)
            val provider = app.aiProviderFactory.resolveActiveProvider()
            val suggestions = if (provider != null) {
                runCatching { provider.suggestReplies(messageText, language) }.getOrElse {
                    app.aiProviderFactory.localProvider().suggestReplies(messageText, language)
                }
            } else {
                app.aiProviderFactory.localProvider().suggestReplies(messageText, language)
            }
            val reply = suggestions.firstOrNull()?.trim().orEmpty()
            if (reply.isNotBlank()) {
                // Prefer WhatsApp's notification-level Direct Reply action. This is the only
                // route that can genuinely send while the phone is locked/screen-off: it does
                // not open the WhatsApp UI at all. If WhatsApp does not expose a RemoteInput
                // action on this notification, keep the persistent Accessibility fallback.
                val backgroundSent = sourceNotification?.let {
                    sendViaNotificationReply(context, it, reply)
                } == true
                val variant = app.settingsDataStore.whatsappVariant.first()
                val pkg = when (platform) {
                    MessagingPlatform.MESSENGER -> Constants.MESSENGER_PACKAGE
                    MessagingPlatform.WHATSAPP_BUSINESS -> Constants.WHATSAPP_BUSINESS_PACKAGE
                    MessagingPlatform.WHATSAPP -> when (variant) {
                        IntentHelper.WHATSAPP_VARIANT_BUSINESS -> Constants.WHATSAPP_BUSINESS_PACKAGE
                        else -> Constants.WHATSAPP_PACKAGE
                    }
                }
                val keySource = "${groupName.lowercase()}|${sender.orEmpty().lowercase()}|${messageText.trim()}"
                val key = MessageDigest.getInstance("SHA-256").digest(keySource.toByteArray()).joinToString("") { "%02x".format(it) }
                val delay = app.settingsDataStore.autoReplyDelaySeconds.first()
                if (!backgroundSent) {
                    AutoReplyQueue.enqueue(context, AutoReplyQueue.Job(
                        groupName = groupName,
                        incomingText = messageText,
                        replyText = reply,
                        packageName = pkg,
                        notBefore = System.currentTimeMillis() + delay * 1000L,
                        messageKey = key,
                        phoneNumber = null
                    ))
                }
            }
        } catch (t: Throwable) {
            Log.w(CAPTURE_TAG, "Auto reply generation failed; message remains captured normally", t)
        }
    }

    if (classification.isImportant || classification.needsReply) {
        com.whatsappworkmanager.app.utils.BadgeUpdater.refresh(app)
    }

    // Premium Auto Reply rules are evaluated independently from the general reply detector.
    // IMPORTANT: never use firstOrNull here. A user can configure several clients, and a single
    // incoming notification must be allowed to match the rule for the correct client without
    // the first configured rule blocking every rule after it.
    try {
        val activeRules = app.autoReplyRuleRepository.getEnabledOnce()
        val matchedRules = activeRules
            .filter { rule -> rule.platform == platform && matchesAutoReplyRule(rule, groupName, sender, messageText) }
            // If the same client has accidentally been configured more than once, do not send
            // multiple replies for one incoming message. Prefer the rule with a keyword (more
            // specific) and then the most recently edited rule. Different clients remain fully
            // independent and are all processed.
            .groupBy { rule -> autoReplyTargetKey(rule) }
            .values
            .mapNotNull { rules ->
                rules.maxWithOrNull(
                    compareBy<AutoReplyRule> { !it.keyword.isNullOrBlank() }
                        .thenBy { it.updatedAt }
                )
            }

        for (matchedRule in matchedRules) {
            val phone = matchedRule.phoneNumber
                ?: com.whatsappworkmanager.app.presentation.search.findSavedPhoneForName(
                    app, sender ?: groupName
                )
            val variant = app.settingsDataStore.whatsappVariant.first()
            // A blank replyText means "let the AI write it" — generated fresh for this
            // specific rule/client and incoming message.
            val aiGenerated = matchedRule.replyText.isNullOrBlank()
            val replyText = if (aiGenerated) {
                generateAutoReplyText(
                    app,
                    groupName,
                    messageText,
                    matchedRule.tone,
                    matchedRule.aiInstruction
                )
            } else {
                matchedRule.replyText.orEmpty()
            }

            if (replyText.isBlank()) continue

            if (aiGenerated) {
                // Keep a compact per-person history so Show Replies can audit exactly what the
                // AI generated for each rule/client.
                runCatching {
                    app.autoReplyReplyHistoryRepository.insert(
                        com.whatsappworkmanager.app.domain.model.AutoReplyReply(
                            ruleId = matchedRule.id,
                            personLabel = sender ?: groupName,
                            incomingText = messageText,
                            replyText = replyText
                        )
                    )
                }.onFailure {
                    Log.w(CAPTURE_TAG, "Could not save AI reply history", it)
                }
            }

            if (matchedRule.autoSend) {
                // First try WhatsApp's notification-level Direct Reply. This keeps the reply in
                // the background and can work with a locked/screen-off phone when WhatsApp
                // exposes RemoteInput for this notification. Accessibility remains the fallback.
                val backgroundSent = sourceNotification?.let {
                    sendViaNotificationReply(context, it, replyText)
                } == true

                val pkg = when (variant) {
                    IntentHelper.WHATSAPP_VARIANT_BUSINESS -> Constants.WHATSAPP_BUSINESS_PACKAGE
                    else -> Constants.WHATSAPP_PACKAGE
                }
                val keySource = "${matchedRule.id}|${groupName.lowercase()}|${sender.orEmpty().lowercase()}|${messageText.trim()}"
                val key = MessageDigest.getInstance("SHA-256")
                    .digest(keySource.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val delay = app.settingsDataStore.autoReplyDelaySeconds.first()

                if (!backgroundSent) {
                    AutoReplyQueue.enqueue(
                        context,
                        AutoReplyQueue.Job(
                            groupName = groupName,
                            incomingText = messageText,
                            replyText = replyText,
                            packageName = pkg,
                            phoneNumber = phone,
                            notBefore = System.currentTimeMillis() + delay * 1000L,
                            messageKey = key
                        )
                    )
                }
            } else {
                // Unique notification id per rule + captured message. Previously every matched
                // rule would have reused the same id, causing one client's notification to
                // replace another client's notification in Android's notification manager.
                val replyNotificationId = Constants.NOTIFICATION_ID_AUTO_REPLY_BASE +
                    ((newId * 31L + matchedRule.id) % 100000L).toInt()
                NotificationHelper.showAutoReplyReadyNotification(
                    context = context,
                    personLabel = sender ?: groupName,
                    replyText = replyText,
                    notificationId = replyNotificationId,
                    phoneNumber = phone,
                    preferredWhatsappVariant = variant,
                    platform = platform
                )
            }

            app.settingsDataStore.setLastAutoReply(replyText, sender ?: groupName, phone)
        }
    } catch (t: Throwable) {
        Log.w(CAPTURE_TAG, "Auto Reply match check failed; message remains captured normally", t)
    }

    if (isEnabledGroup && classification.isHighPriority) {
        NotificationHelper.showImportantMessageNotification(
            context = context,
            groupName = groupName,
            messageText = messageText,
            notificationId = Constants.NOTIFICATION_ID_IMPORTANT_BASE + (newId % 1000).toInt(),
            platform = platform
        )
    }
}

/**
 * The AI-written half of Auto Reply (see AutoReplyRuleEntity's doc): generates a reply from
 * [messageText] itself, using the same recent-conversation-context mechanism the Search/
 * Summary screens' "Generate Reply" already uses, so a match on an ongoing back-and-forth gets
 * a reply that accounts for it rather than reacting to one line in isolation. Falls back to
 * the local, offline provider if no cloud provider is configured or the call fails, and to a
 * plain, honest placeholder only if even that somehow fails — this always returns *something*
 * usable rather than silently producing an empty reply.
 */
private suspend fun generateAutoReplyText(
    app: WwmApplication,
    groupName: String,
    messageText: String,
    tone: com.whatsappworkmanager.app.domain.model.ReplyTone,
    instruction: String? = null
): String {
    return try {
        val language = com.whatsappworkmanager.app.presentation.search.detectLanguage(messageText)
        val conversation = com.whatsappworkmanager.app.presentation.search.fetchRecentConversationTexts(
            app, groupName, System.currentTimeMillis()
        )
        val provider = app.aiProviderFactory.resolveActiveProvider()
        val suggestions = if (provider != null) {
            try {
                // A hard timeout, not just a try/catch, is what actually matters here: a
                // network call that *hangs* rather than fails outright (exactly what happens
                // to background network access under Doze/App Standby when the device is
                // locked and this app isn't exempted from battery optimization) never throws,
                // so the catch block below would never run either — the whole capture
                // pipeline would just sit there waiting forever, and no notification would
                // ever fire, fallback included. This is very likely the real cause behind
                // "Auto Reply doesn't work when the phone is locked": not that it's broken,
                // but that it's stuck waiting on a network call Doze is silently deferring.
                kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    provider.suggestRepliesForConversation(conversation, language, tone, instruction)
                } ?: app.aiProviderFactory.localProvider().suggestReplies(messageText, language)
            } catch (e: Exception) {
                app.aiProviderFactory.localProvider().suggestReplies(messageText, language)
            }
        } else {
            app.aiProviderFactory.localProvider().suggestReplies(messageText, language)
        }
        suggestions.firstOrNull()?.takeIf { it.isNotBlank() } ?: fallbackAutoReplyText(language)
    } catch (t: Throwable) {
        Log.w(CAPTURE_TAG, "AI Auto Reply generation failed; using a plain fallback line", t)
        fallbackAutoReplyText(com.whatsappworkmanager.app.presentation.search.detectLanguage(messageText))
    }
}

private fun fallbackAutoReplyText(language: String): String =
    if (language == "ar") "تمام، وصلني، هرد عليك بعدين." else "Got it, I'll get back to you soon."

