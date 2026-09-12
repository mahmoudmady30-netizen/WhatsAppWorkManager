package com.whatsappworkmanager.app.utils

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression coverage: a scheduled message or Auto Reply match with no specific phone number
 * (a group target, or nobody saved for that person) used to open WhatsApp generally with
 * nothing copied to the clipboard first — the message text was simply lost, since there was
 * nothing to paste once WhatsApp opened. Both notification paths now copy the text in that
 * case, matching the same fallback already used by the Search/Summary reply flow.
 */
@RunWith(RobolectricTestRunner::class)
class NotificationHelperTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun clipboardText(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        return if (clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
    }

    @Test
    fun `scheduled reminder with no phone number copies the text to the clipboard`() {
        NotificationHelper.showScheduledMessageReminder(
            context = context,
            text = "Happy Monday everyone!",
            notificationId = 1,
            messageId = 1L,
            phoneNumber = null
        )
        assertEquals("Happy Monday everyone!", clipboardText())
    }

    @Test
    fun `scheduled reminder with a phone number does not need to touch the clipboard`() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("sentinel", "unrelated"))

        NotificationHelper.showScheduledMessageReminder(
            context = context,
            text = "Happy Monday everyone!",
            notificationId = 2,
            messageId = 2L,
            phoneNumber = "201234567890"
        )

        // Clipboard is left exactly as it was — a direct chat deep link doesn't need it.
        assertEquals("unrelated", clipboardText())
    }

    @Test
    fun `auto reply ready with no phone number copies the reply text to the clipboard`() {
        NotificationHelper.showAutoReplyReadyNotification(
            context = context,
            personLabel = "Ahmed",
            replyText = "Thanks, I'll get back to you shortly.",
            notificationId = 3,
            phoneNumber = null
        )
        assertEquals("Thanks, I'll get back to you shortly.", clipboardText())
    }
}
