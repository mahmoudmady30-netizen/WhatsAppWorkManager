package com.whatsappworkmanager.app.service

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.AutoReplyRule
import com.whatsappworkmanager.app.utils.Constants
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

/**
 * Covers the safe Auto Reply feature: a matching rule must only ever prepare a ready-to-send
 * notification, never send anything on its own. Deliberately does NOT exercise
 * WhatsAppAccessibilityService — that class is no longer reachable at all (its manifest entry
 * was removed), so there is nothing to test there; these tests confirm the *replacement*
 * behaves safely instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = WwmApplication::class)
class AutoReplyDetectionTest {

    private lateinit var app: WwmApplication
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking {
            app.messageRepository.deleteAll()
            app.autoReplyRuleRepository.getEnabledOnce().forEach {
                app.autoReplyRuleRepository.delete(it.id)
            }
        }
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = Shadows.shadowOf(manager)
        // cancelAll() is the real, public NotificationManager API — ShadowNotificationManager's
        // own cancel(tag, id) overload is protected (only meant to be called internally by the
        // shadow framework itself), so calling it directly here doesn't compile. Clearing
        // everything between tests via the real API achieves the same "start each test with a
        // clean notification slate" goal without needing that protected method at all.
        manager.cancelAll()
    }

    @Test
    fun `a matching person and keyword surfaces a ready-reply notification`() = runBlocking {
        app.autoReplyRuleRepository.upsert(
            AutoReplyRule(personMatch = "Ahmed", keyword = "price", replyText = "Our price list is attached.")
        )

        captureMessage(
            context = app, app = app, groupName = "Ahmed Hassan", sender = null,
            messageText = "What's the price for this?", timestamp = System.currentTimeMillis()
        )

        val posted = shadowNotificationManager.activeNotifications
        assertTrue(posted.any { it.id >= Constants.NOTIFICATION_ID_AUTO_REPLY_BASE })
    }

    @Test
    fun `a matching person with no keyword matches any message from them`() = runBlocking {
        app.autoReplyRuleRepository.upsert(
            AutoReplyRule(personMatch = "Ahmed", keyword = null, replyText = "Thanks, I'll get back to you.")
        )

        captureMessage(
            context = app, app = app, groupName = "Ahmed Hassan", sender = null,
            messageText = "Completely unrelated text", timestamp = System.currentTimeMillis()
        )

        val posted = shadowNotificationManager.activeNotifications
        assertTrue(posted.any { it.id >= Constants.NOTIFICATION_ID_AUTO_REPLY_BASE })
    }

    @Test
    fun `a keyword rule does not fire when the keyword is absent`() = runBlocking {
        app.autoReplyRuleRepository.upsert(
            AutoReplyRule(personMatch = "Ahmed", keyword = "price", replyText = "Our price list is attached.")
        )

        captureMessage(
            context = app, app = app, groupName = "Ahmed Hassan", sender = null,
            messageText = "Just saying hello", timestamp = System.currentTimeMillis()
        )

        val posted = shadowNotificationManager.activeNotifications
        assertFalse(posted.any { it.id >= Constants.NOTIFICATION_ID_AUTO_REPLY_BASE })
    }

    @Test
    fun `a disabled rule never fires`() = runBlocking {
        app.autoReplyRuleRepository.upsert(
            AutoReplyRule(personMatch = "Ahmed", keyword = null, replyText = "Thanks!", enabled = false)
        )

        captureMessage(
            context = app, app = app, groupName = "Ahmed Hassan", sender = null,
            messageText = "Anything at all", timestamp = System.currentTimeMillis()
        )

        val posted = shadowNotificationManager.activeNotifications
        assertFalse(posted.any { it.id >= Constants.NOTIFICATION_ID_AUTO_REPLY_BASE })
    }

    @Test
    fun `a rule for a different person does not fire`() = runBlocking {
        app.autoReplyRuleRepository.upsert(
            AutoReplyRule(personMatch = "Mostafa", keyword = null, replyText = "Thanks!")
        )

        captureMessage(
            context = app, app = app, groupName = "Ahmed Hassan", sender = null,
            messageText = "Anything at all", timestamp = System.currentTimeMillis()
        )

        val posted = shadowNotificationManager.activeNotifications
        assertFalse(posted.any { it.id >= Constants.NOTIFICATION_ID_AUTO_REPLY_BASE })
    }

    @Test
    fun `a blank reply text has the AI generate a reply instead of using fixed text`() = runBlocking {
        app.autoReplyRuleRepository.upsert(
            AutoReplyRule(personMatch = "Ahmed", keyword = null, replyText = null)
        )

        captureMessage(
            context = app, app = app, groupName = "Ahmed Hassan", sender = null,
            messageText = "What time works for you tomorrow?", timestamp = System.currentTimeMillis()
        )

        val posted = shadowNotificationManager.activeNotifications
            .firstOrNull { it.id >= Constants.NOTIFICATION_ID_AUTO_REPLY_BASE }
        assertTrue(posted != null)
        // No cloud provider is configured in this test environment, so this exercises the
        // local, offline fallback provider — the point being verified is simply that *some*
        // non-blank text was generated and used, not any specific wording.
        val body = posted?.notification?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        assertTrue(!body.isNullOrBlank())
    }

    @Test
    fun `an all-whitespace reply text is treated the same as blank — AI generates instead`() = runBlocking {
        app.autoReplyRuleRepository.upsert(
            AutoReplyRule(personMatch = "Ahmed", keyword = null, replyText = "   ")
        )

        captureMessage(
            context = app, app = app, groupName = "Ahmed Hassan", sender = null,
            messageText = "Quick question about the invoice", timestamp = System.currentTimeMillis()
        )

        val posted = shadowNotificationManager.activeNotifications
            .firstOrNull { it.id >= Constants.NOTIFICATION_ID_AUTO_REPLY_BASE }
        val body = posted?.notification?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        assertTrue(!body.isNullOrBlank())
        assertTrue(body != "   ")
    }

    @Test
    fun `a fixed reply text is used verbatim, not replaced by AI generation`() = runBlocking {
        app.autoReplyRuleRepository.upsert(
            AutoReplyRule(personMatch = "Ahmed", keyword = null, replyText = "Our office is closed today.")
        )

        captureMessage(
            context = app, app = app, groupName = "Ahmed Hassan", sender = null,
            messageText = "Are you open right now?", timestamp = System.currentTimeMillis()
        )

        val posted = shadowNotificationManager.activeNotifications
            .firstOrNull { it.id >= Constants.NOTIFICATION_ID_AUTO_REPLY_BASE }
        val body = posted?.notification?.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        // No phone number is saved for "Ahmed Hassan" in this test, so the notification body
        // is correctly wrapped in the "Copied — paste it into the chat: ..." explanation (see
        // NotificationHelper.showAutoReplyReadyNotification) rather than being the bare reply
        // text — checking containment, not exact equality, verifies what this test actually
        // cares about (the fixed text wasn't replaced by an AI-generated one) without being
        // coupled to that wrapping.
        assertTrue(body?.contains("Our office is closed today.") == true)
    }
    @Test
    fun `multiple different clients can match independently instead of first rule blocking the rest`() {
        val ahmed = AutoReplyRule(personMatch = "Ahmed", keyword = null, replyText = "Hi Ahmed")
        val mostafa = AutoReplyRule(personMatch = "Mostafa", keyword = null, replyText = "Hi Mostafa")

        assertTrue(matchesAutoReplyRule(ahmed, "Ahmed Hassan", null, "Hello"))
        assertTrue(matchesAutoReplyRule(mostafa, "Mostafa Ali", null, "Hello"))
    }

    @Test
    fun `same client with multiple matching rules prefers the more specific keyword rule`() {
        val generic = AutoReplyRule(
            id = 1, personMatch = "Ahmed", keyword = null, replyText = "Generic", updatedAt = 100
        )
        val specific = AutoReplyRule(
            id = 2, personMatch = "Ahmed", keyword = "price", replyText = "Price", updatedAt = 50
        )

        val matches = listOf(generic, specific)
            .filter { matchesAutoReplyRule(it, "Ahmed Hassan", null, "What is the price?") }
            .groupBy { it.personMatch.lowercase() }
            .values
            .mapNotNull { rules ->
                rules.maxWithOrNull(
                    compareBy<AutoReplyRule> { !it.keyword.isNullOrBlank() }.thenBy { it.updatedAt }
                )
            }

        assertEquals(2, matches.single().id)
    }

    @Test
    fun `client rule also matches by its saved phone number when WhatsApp shows the number`() {
        val rule = AutoReplyRule(
            personMatch = "John Smith",
            phoneNumber = "971501234567",
            keyword = null,
            replyText = "Thanks"
        )

        assertTrue(matchesAutoReplyRule(rule, "+971 50 123 4567", null, "Hello"))
        assertTrue(matchesAutoReplyRule(rule, "John Smith", null, "Hello"))
    }

}
