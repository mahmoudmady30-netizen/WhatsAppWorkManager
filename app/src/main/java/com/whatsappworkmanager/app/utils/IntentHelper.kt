package com.whatsappworkmanager.app.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.whatsappworkmanager.app.domain.model.displayName
import androidx.core.app.NotificationManagerCompat

/**
 * Opens WhatsApp via its public launch intent, or (when a phone number is available) a
 * specific chat pre-filled with text via WhatsApp's official click-to-chat deep link — see
 * [buildWhatsAppChatIntent]. Neither path uses Accessibility, root, or any unofficial API.
 * There is still no public, reliable way to deep-link into an arbitrary **group** chat by name
 * (groups have no phone number), so for groups this always falls back to opening WhatsApp
 * itself and letting the user take the last few taps.
 *
 * Every path here still requires the user to tap WhatsApp's own Send button — nothing here
 * sends a message without that final tap, by design. WhatsApp exposes no public way for a
 * third-party app to complete the send itself.
 */
object IntentHelper {

    /** Values for the "which WhatsApp app" preference (see Settings → WhatsApp App). */
    const val WHATSAPP_VARIANT_AUTO = "auto"
    const val WHATSAPP_VARIANT_REGULAR = "regular"
    const val WHATSAPP_VARIANT_BUSINESS = "business"

    fun openWhatsApp(context: Context, preferredVariant: String = WHATSAPP_VARIANT_AUTO) {
        val intent = buildWhatsAppChatIntent(context, phoneNumber = null, text = null, preferredVariant = preferredVariant)
        if (intent == null) {
            Toast.makeText(context, whatsAppMissingMessage(preferredVariant), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(com.whatsappworkmanager.app.R.string.toast_could_not_open_whatsapp), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens a specific WhatsApp chat with [text] pre-filled, via WhatsApp's official
     * "click-to-chat" deep link (`https://wa.me/<phone>?text=...`). [phoneNumber] must be in
     * international format with no "+", spaces, or leading zeros (e.g. "201234567890" for an
     * Egyptian number). Falls back to opening WhatsApp generally if [phoneNumber] is
     * null/blank (the target is a group, which has no phone number) or WhatsApp isn't
     * installed.
     *
     * [preferredVariant] picks between regular WhatsApp and WhatsApp Business when both are
     * installed — see the `WHATSAPP_VARIANT_*` constants and Settings → WhatsApp App. Default
     * ("auto") behaves like before: prefer regular WhatsApp, fall back to Business only if
     * regular isn't installed.
     */
    fun openWhatsAppChat(
        context: Context,
        phoneNumber: String?,
        text: String,
        preferredVariant: String = WHATSAPP_VARIANT_AUTO
    ) {
        val intent = buildWhatsAppChatIntent(context, phoneNumber, text, preferredVariant)
        if (intent == null) {
            Toast.makeText(context, whatsAppMissingMessage(preferredVariant), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Some WhatsApp builds resolve wa.me links only via the general browser intent
            // filter rather than an explicit package match; retry without an explicit package.
            try {
                context.startActivity(Intent(intent).apply { `package` = null })
            } catch (e2: Exception) {
                Toast.makeText(context, context.getString(com.whatsappworkmanager.app.R.string.toast_could_not_open_whatsapp), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Builds (without launching) the Intent that opens WhatsApp — either a specific chat with
     * [text] pre-filled if [phoneNumber] is given, or just the app itself otherwise. Exposed
     * separately from [openWhatsAppChat] so callers that need a `PendingIntent` (e.g. a
     * notification's tap action — see [NotificationHelper.showScheduledMessageReminder]) can
     * wrap this directly, without a throwaway "trampoline" Activity in between.
     *
     * Returns null only if the app selected by [preferredVariant] (or, in "auto" mode, neither
     * WhatsApp nor WhatsApp Business) is installed.
     */
    fun buildWhatsAppChatIntent(
        context: Context,
        phoneNumber: String?,
        text: String?,
        preferredVariant: String = WHATSAPP_VARIANT_AUTO
    ): Intent? {
        val pm = context.packageManager
        val pkg = resolveWhatsAppPackage(pm, preferredVariant) ?: return null

        return if (!phoneNumber.isNullOrBlank()) {
            val cleanedNumber = phoneNumber.filter { it.isDigit() }
            val encodedText = Uri.encode(text.orEmpty())
            Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanedNumber?text=$encodedText")).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            pm.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        }
    }

    /**
     * Resolves which package to target based on [preferredVariant]:
     *  - "regular" / "business": that exact app, only if it's actually installed (no silent
     *    fallback to the other one — if the user explicitly asked for Business, opening regular
     *    WhatsApp instead would be surprising, not helpful).
     *  - "auto" (default): regular WhatsApp if installed, else Business, else null — the
     *    original behavior, unchanged for anyone who hasn't set a preference.
     */
    fun resolveWhatsAppPackage(pm: PackageManager, preferredVariant: String): String? = when (preferredVariant) {
        WHATSAPP_VARIANT_REGULAR -> Constants.WHATSAPP_PACKAGE.takeIf { isInstalled(pm, it) }
        WHATSAPP_VARIANT_BUSINESS -> Constants.WHATSAPP_BUSINESS_PACKAGE.takeIf { isInstalled(pm, it) }
        else -> when {
            isInstalled(pm, Constants.WHATSAPP_PACKAGE) -> Constants.WHATSAPP_PACKAGE
            isInstalled(pm, Constants.WHATSAPP_BUSINESS_PACKAGE) -> Constants.WHATSAPP_BUSINESS_PACKAGE
            else -> null
        }
    }

    private fun whatsAppMissingMessage(preferredVariant: String): String = when (preferredVariant) {
        WHATSAPP_VARIANT_REGULAR -> "WhatsApp is not installed"
        WHATSAPP_VARIANT_BUSINESS -> "WhatsApp Business is not installed"
        else -> "WhatsApp is not installed"
    }

    /**
     * Opens Android's own system notification settings screen *for WhatsApp specifically*
     * (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`) — from there the user can set a specific
     * conversation to Android's own "Silent" tier (Settings → Notifications → Conversations,
     * or long-press a notification from that chat → the gear icon → drag importance down).
     *
     * This is the actual answer to "I muted a chat in WhatsApp and this app can no longer see
     * anything from it at all": WhatsApp's own in-app mute doesn't just silence the alert, it
     * stops posting a notification for that conversation *at all* — confirmed by checking that
     * literally nothing appears in the notification shade for a muted chat, not even silently.
     * Since this app's only legitimate way to see a message (no Accessibility Service, no
     * root) is a notification actually being posted, there is no way to capture something that
     * was never posted in the first place — that's a hard platform limitation, not a bug here.
     *
     * Android's own per-conversation "Silent" setting achieves the same practical outcome
     * (no sound, no vibration, no heads-up popup, may not even show on the lock screen) through
     * a completely different mechanism: the notification still gets posted — just marked low
     * importance — so it still reaches this app's listener normally. Practically identical
     * peace of mind, with none of WhatsApp's own mute swallowing the notification whole.
     */
    fun openNotificationSettingsForWhatsApp(context: Context, preferredVariant: String = WHATSAPP_VARIANT_AUTO): Boolean {
        val pkg = resolveWhatsAppPackage(context.packageManager, preferredVariant) ?: return false
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean = try {
        pm.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun resolveMessagingPackage(pm: PackageManager, platform: com.whatsappworkmanager.app.domain.model.MessagingPlatform): String? = when (platform) {
        com.whatsappworkmanager.app.domain.model.MessagingPlatform.MESSENGER -> Constants.MESSENGER_PACKAGE.takeIf { isInstalled(pm, it) }
        com.whatsappworkmanager.app.domain.model.MessagingPlatform.WHATSAPP -> resolveWhatsAppPackage(pm, WHATSAPP_VARIANT_REGULAR)
        com.whatsappworkmanager.app.domain.model.MessagingPlatform.WHATSAPP_BUSINESS -> resolveWhatsAppPackage(pm, WHATSAPP_VARIANT_BUSINESS)
    }

    fun openMessagingChat(context: Context, platform: com.whatsappworkmanager.app.domain.model.MessagingPlatform, phoneNumber: String?, text: String, preferredVariant: String = WHATSAPP_VARIANT_AUTO) {
        val intent = when (platform) {
            com.whatsappworkmanager.app.domain.model.MessagingPlatform.MESSENGER -> {
                val pkg = resolveMessagingPackage(context.packageManager, platform)
                pkg?.let { context.packageManager.getLaunchIntentForPackage(it)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }
            }
            com.whatsappworkmanager.app.domain.model.MessagingPlatform.WHATSAPP,
            com.whatsappworkmanager.app.domain.model.MessagingPlatform.WHATSAPP_BUSINESS ->
                buildWhatsAppChatIntent(context, phoneNumber, text, if (platform == com.whatsappworkmanager.app.domain.model.MessagingPlatform.WHATSAPP_BUSINESS) WHATSAPP_VARIANT_BUSINESS else preferredVariant)
        }
        if (intent == null) {
            Toast.makeText(context, "${platform.displayName()} is not installed", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, context.getString(com.whatsappworkmanager.app.R.string.toast_could_not_open_whatsapp), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens the system Contacts app's "add new contact" screen, pre-filled with [name] and
     * [phoneNumber] — the user still taps Save themselves in their own Contacts app. No
     * `WRITE_CONTACTS` permission is requested or needed for this: `ACTION_INSERT` hands the
     * work off to the Contacts app entirely, the same permission-free pattern already used for
     * *picking* a contact (see `PickFromContactsButton`) — this is the write-side equivalent.
     */
    fun openAddToPhoneContacts(context: Context, name: String, phoneNumber: String?) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
            putExtra(android.provider.ContactsContract.Intents.Insert.NAME, name)
            if (!phoneNumber.isNullOrBlank()) {
                putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, phoneNumber)
            }
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(com.whatsappworkmanager.app.R.string.toast_could_not_open_contacts), Toast.LENGTH_SHORT).show()
        }
    }

    /** Whether the user has granted this app Notification Access (system Settings toggle). */
    fun isNotificationAccessEnabled(context: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return context.packageName in enabledPackages
    }

    /**
     * Whether this app is currently exempted from battery optimization (Doze/App Standby).
     * Relevant specifically for muted WhatsApp chats: Android is far more willing to defer or
     * throttle a background app's work — including how promptly this app's
     * NotificationListenerService actually gets to process an incoming notification — for
     * *silent* notifications (no sound/heads-up, exactly what a muted chat produces) than for
     * ones that visibly alert the user. A capture that's merely delayed by Doze can look
     * identical to one that never happened at all from the user's point of view. Being
     * exempted removes that specific throttling.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return true
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Whether this app currently has READ_CONTACTS — requested lazily (never upfront) only
     *  when the user opts into auto-filling a phone number by name; see ContactsLookup.kt. */
    fun isReadContactsGranted(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** Opens the system screen where the user can grant Notification Access. */
    fun openNotificationAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Opens the system list of apps exempted from battery optimization (Doze/App Standby),
     * so the user can add this app themselves. Deliberately uses
     * `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (a plain list screen) rather than
     * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (a direct one-tap request), because the
     * latter requires the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` manifest permission — which
     * Google Play reviews strictly and which this project deliberately avoids requesting, in
     * keeping with its "only genuinely required permissions" rule. The user takes one extra
     * tap (finding the app in the list) instead.
     *
     * Without this, scheduled summaries and message reminders (via WorkManager) can be
     * delayed for hours under Doze/App Standby — this is an Android OS-level battery-saving
     * behavior, not a bug in this app's scheduling code.
     */
    /** Opens Android's Accessibility settings so the user can explicitly enable the WhatsApp UI automation bridge. */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, not ACTION_IGNORE_BATTERY_OPTIMIZATION_
        // SETTINGS — the latter opens the generic all-apps list, leaving the user to scroll
        // down and find/select this app themselves; the former prompts a direct system
        // dialog ("Allow [App] to ignore battery optimizations?") for this app specifically,
        // no navigation needed at all.
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Some OEM builds don't ship this dialog; fall back to the generic all-apps list,
            // and if even that's missing, the app's own settings page one level further down.
            try {
                val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                listIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(listIntent)
            } catch (e2: Exception) {
                val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }
}
