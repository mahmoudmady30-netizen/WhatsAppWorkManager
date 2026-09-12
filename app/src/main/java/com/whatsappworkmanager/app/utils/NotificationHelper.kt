package com.whatsappworkmanager.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.whatsappworkmanager.app.R
import com.whatsappworkmanager.app.presentation.MainActivity
import com.whatsappworkmanager.app.domain.model.displayName
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent

object NotificationHelper {

    private fun deletePendingIntent(context: Context, notificationId: Int): PendingIntent {
        val intent = Intent(context, com.whatsappworkmanager.app.receiver.NotificationDismissReceiver::class.java).apply {
            putExtra(com.whatsappworkmanager.app.receiver.NotificationDismissReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context, notificationId + 700000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)

        val summary = NotificationChannel(
            Constants.CHANNEL_WORK_SUMMARY,
            context.getString(R.string.channel_work_summary),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val important = NotificationChannel(
            Constants.CHANNEL_IMPORTANT_MESSAGES,
            context.getString(R.string.channel_important_messages),
            NotificationManager.IMPORTANCE_HIGH
        )
        val system = NotificationChannel(
            Constants.CHANNEL_SYSTEM,
            context.getString(R.string.channel_system),
            NotificationManager.IMPORTANCE_LOW
        )
        val scheduledReminders = NotificationChannel(
            Constants.CHANNEL_SCHEDULED_REMINDERS,
            context.getString(R.string.channel_scheduled_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
        }
        manager.createNotificationChannels(listOf(summary, important, system, scheduledReminders))
    }

    fun showSummaryNotification(context: Context, title: String, body: String, summaryId: Long) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(Constants.EXTRA_SUMMARY_ID, summaryId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, summaryId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_WORK_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent(context, Constants.NOTIFICATION_ID_SUMMARY))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(Constants.NOTIFICATION_ID_SUMMARY, notification)
    }

    fun showImportantMessageNotification(
        context: Context,
        groupName: String,
        messageText: String,
        notificationId: Int,
        platform: com.whatsappworkmanager.app.domain.model.MessagingPlatform = com.whatsappworkmanager.app.domain.model.MessagingPlatform.WHATSAPP
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_IMPORTANT_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.stat_important) + " · " + platform.displayName() + ": $groupName")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deletePendingIntent(context, notificationId))
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Fires at the user's chosen time. Tapping the notification opens WhatsApp directly on
     * the intended chat (pre-filled with [text]) via the official click-to-chat deep link when
     * [phoneNumber] is known, or opens WhatsApp generally for a group target — either way, one
     * final tap on WhatsApp's own Send button is still required; this app never sends the
     * message itself. If WhatsApp isn't installed, tapping falls back to opening this app.
     *
     * [preferredWhatsappVariant] — see Settings → WhatsApp App / IntentHelper.WHATSAPP_VARIANT_*
     * — picks regular WhatsApp vs. WhatsApp Business when both are installed, instead of
     * always silently preferring regular WhatsApp.
     */
    fun showScheduledMessageReminder(
        context: Context,
        text: String,
        notificationId: Int,
        messageId: Long,
        phoneNumber: String? = null,
        preferredWhatsappVariant: String = IntentHelper.WHATSAPP_VARIANT_AUTO,
        platform: com.whatsappworkmanager.app.domain.model.MessagingPlatform = com.whatsappworkmanager.app.domain.model.MessagingPlatform.WHATSAPP
    ) {
        val whatsAppIntent = if (platform == com.whatsappworkmanager.app.domain.model.MessagingPlatform.MESSENGER) null else IntentHelper.buildWhatsAppChatIntent(context, phoneNumber, text, preferredWhatsappVariant)
        // No specific phone number (a group target, left blank on purpose per the add/edit
        // screen's own hint) means WhatsApp's click-to-chat deep link can't pre-fill anything
        // — buildWhatsAppChatIntent falls back to just opening WhatsApp generally in that
        // case. Previously that was the ENTIRE fallback: no clipboard copy at all, unlike the
        // identical situation in the Search/Summary reply flow — so there was nothing to
        // paste once WhatsApp opened, and the message text was simply lost. Copying it here
        // too closes that gap.
        val copiedToClipboard = phoneNumber.isNullOrBlank()
        if (copiedToClipboard) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.scheduled_reminder_title), text))
        }
        val forwardIntent = whatsAppIntent ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // Routed through ScheduledMessageTapReceiver rather than opening forwardIntent
        // directly — see that receiver's doc — so tapping this notification also counts as
        // "handled" for the missed-count badge, the same as the Dashboard/Scheduled Messages
        // screens' own Send buttons.
        val tapIntent = Intent(context, com.whatsappworkmanager.app.receiver.ScheduledMessageTapReceiver::class.java).apply {
            putExtra(com.whatsappworkmanager.app.receiver.ScheduledMessageTapReceiver.EXTRA_MESSAGE_ID, messageId)
            putExtra(com.whatsappworkmanager.app.receiver.ScheduledMessageTapReceiver.EXTRA_FORWARD_INTENT, forwardIntent)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, notificationId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = Intent(context, com.whatsappworkmanager.app.receiver.ScheduledMessageNotificationDismissReceiver::class.java).apply {
            putExtra(com.whatsappworkmanager.app.receiver.ScheduledMessageNotificationDismissReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 300000, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val displayText = if (copiedToClipboard) {
            context.getString(R.string.scheduled_reminder_copied_body_fmt, text)
        } else {
            text
        }
        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_SCHEDULED_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.scheduled_reminder_title) + " · " + platform.displayName())
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(android.R.drawable.ic_menu_delete, context.getString(R.string.notification_delete), dismissPendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 250, 150, 250))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Surfaces a safe, ready-to-send Auto Reply match (see AutoReplyRuleEntity's doc for why
     * this only ever prepares a reply rather than sending it): tapping the notification opens
     * the exact WhatsApp chat with [replyText] already typed in via the same official
     * click-to-chat deep link used elsewhere in this app — the user still taps Send
     * themselves. Falls back to opening WhatsApp generally (text copied to the clipboard by
     * the caller, same as the Search screen's reply flow) when no phone number is known for
     * this person.
     */

    fun cancelScheduledMessageReminder(context: Context, messageId: Long) {
        NotificationManagerCompat.from(context).cancel(Constants.NOTIFICATION_ID_SCHEDULED_MESSAGE_BASE + messageId.toInt())
    }

    fun showAutoReplyReadyNotification(
        context: Context,
        personLabel: String,
        replyText: String,
        notificationId: Int,
        phoneNumber: String? = null,
        preferredWhatsappVariant: String = IntentHelper.WHATSAPP_VARIANT_AUTO,
        platform: com.whatsappworkmanager.app.domain.model.MessagingPlatform = com.whatsappworkmanager.app.domain.model.MessagingPlatform.WHATSAPP
    ) {
        val whatsAppIntent = if (platform == com.whatsappworkmanager.app.domain.model.MessagingPlatform.MESSENGER) null else IntentHelper.buildWhatsAppChatIntent(context, phoneNumber, replyText, preferredWhatsappVariant)
        // Same fallback gap as showScheduledMessageReminder: no phone number known for this
        // person means the click-to-chat deep link can't pre-fill anything, and
        // buildWhatsAppChatIntent falls back to just opening WhatsApp generally — with nothing
        // copied to paste once it does, unless this closes that gap explicitly.
        val copiedToClipboard = phoneNumber.isNullOrBlank()
        if (copiedToClipboard) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.auto_reply_title), replyText))
        }
        val tapIntent = whatsAppIntent ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val displayText = if (copiedToClipboard) {
            context.getString(R.string.scheduled_reminder_copied_body_fmt, replyText)
        } else {
            replyText
        }
        val dismissIntent = Intent(context, com.whatsappworkmanager.app.receiver.AutoReplyNotificationDismissReceiver::class.java).apply {
            putExtra(com.whatsappworkmanager.app.receiver.AutoReplyNotificationDismissReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, notificationId + 200000, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_IMPORTANT_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(context.getString(R.string.auto_reply_ready_title_fmt, personLabel))
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(android.R.drawable.ic_menu_delete, context.getString(R.string.auto_reply_notification_delete), dismissPendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Keeps the app icon's badge count roughly in sync with unread Important/Need Reply
     * messages — call [BadgeUpdater.refresh] after anything that changes that count (a new
     * message captured, one marked read, importance toggled).
     *
     * Honest limitation, worth stating plainly: Android's app icon badge is largely an OS/
     * launcher decision, not something an app fully controls. `setNumber()` below is the
     * correct, standard way to *request* a numbered badge, and manufacturer launchers that
     * support numbered badges (Samsung's One UI, and others) generally honor it. Stock
     * Android's own Pixel Launcher deliberately shows only a small dot, never a number — a
     * conscious Google design decision, not a bug here or a missing permission. This still
     * gives the best result achievable for everyone: a real number where the launcher supports
     * it, a dot everywhere else, both driven off one live, auto-updating notification instead
     * of a stale one.
     */
    fun updateBadgeCount(context: Context, count: Int) {
        if (count <= 0) {
            NotificationManagerCompat.from(context).cancel(Constants.NOTIFICATION_ID_BADGE)
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, Constants.NOTIFICATION_ID_BADGE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, Constants.CHANNEL_IMPORTANT_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.badge_notification_title_fmt, count))
            .setContentText(context.getString(R.string.badge_notification_body))
            .setNumber(count)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()
        NotificationManagerCompat.from(context).notify(Constants.NOTIFICATION_ID_BADGE, notification)
    }
}
