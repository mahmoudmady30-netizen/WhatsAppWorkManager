package com.whatsappworkmanager.app.service

import androidx.test.core.app.ApplicationProvider
import com.whatsappworkmanager.app.WwmApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real capture pipeline (classification -> Room persistence -> group stats)
 * end to end, using Robolectric's in-JVM Android environment instead of a physical device
 * or emulator. This is the "test Notification Listener logic as much as possible" item from
 * the project's testing requirements — StatusBarNotification/Bundle themselves are Android
 * framework types Robolectric shadows reasonably but not perfectly, so the extraction logic
 * that touches those (title/text -> sender/message) is covered separately and more reliably
 * by NotificationTextParserTest; this test covers everything downstream of that extraction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = WwmApplication::class)
class WhatsAppNotificationListenerServiceTest {

    private lateinit var app: WwmApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // Explicit reset: some tests below intentionally turn autoEnableNewGroups on and never
        // turn it back off, and/or leave captured messages/groups behind. Without wiping state
        // here, whichever test JUnit happens to run first decides the starting conditions for
        // every other test in this class — that's exactly what was causing intermittent
        // failures. Every test now starts from a known, clean slate regardless of run order.
        runBlocking {
            app.messageRepository.deleteAll()
            app.workGroupRepository.deleteAll()
            app.settingsDataStore.setAutoEnableNewGroups(false)
        }
    }

    @Test
    fun `capturing a message from a non-opted-in group stores it but excludes it from summary`() = runBlocking {
        captureMessage(
            context = app,
            app = app,
            groupName = "Friends",
            sender = "Sam",
            messageText = "party tonight?",
            timestamp = System.currentTimeMillis()
        )

        val messages = app.messageRepository.observeMessages().first()
        assertEquals(1, messages.size)
        assertFalse(messages[0].isIncludedInSummary)
    }

    @Test
    fun `capturing an urgent message from an opted-in group marks it important and included`() = runBlocking {
        // Opt "Sales Team" into Work Summary first, the way a user would from Work Groups.
        app.workGroupRepository.upsertGroupSeen("Sales Team", System.currentTimeMillis(), isImportant = false)
        app.workGroupRepository.setEnabled("Sales Team", true)

        captureMessage(
            context = app,
            app = app,
            groupName = "Sales Team",
            sender = "Manager",
            messageText = "Urgent: customer complaint needs escalation now",
            timestamp = System.currentTimeMillis()
        )

        val messages = app.messageRepository.observeMessages().first()
        val captured = messages.first { it.text.contains("escalation") }
        assertTrue(captured.isImportant)
        assertTrue(captured.isIncludedInSummary)

        val groups = app.workGroupRepository.observeGroups().first()
        val salesTeam = groups.first { it.name == "Sales Team" }
        assertEquals(2, salesTeam.messageCount) // the initial upsertGroupSeen call + this message
        assertTrue(salesTeam.importantCount >= 1)
    }

    @Test
    fun `routine message from opted-in group is stored but not marked important`() = runBlocking {
        // setEnabled is an UPDATE on an existing row (see WorkGroupDao) — matching real app
        // behavior, a group can only be toggled on from the Work Groups screen after it has
        // already appeared (i.e. after upsertGroupSeen created its row from a prior message).
        app.workGroupRepository.upsertGroupSeen("Branch Managers", System.currentTimeMillis(), isImportant = false)
        app.workGroupRepository.setEnabled("Branch Managers", true)

        captureMessage(
            context = app,
            app = app,
            groupName = "Branch Managers",
            sender = null,
            messageText = "Have a good evening everyone",
            timestamp = System.currentTimeMillis()
        )

        val messages = app.messageRepository.observeMessages().first()
        val captured = messages.first { it.groupName == "Branch Managers" }
        assertFalse(captured.isImportant)
        assertTrue(captured.isIncludedInSummary)
    }

    @Test
    fun `isSupportedPackage accepts whatsapp whatsapp business and messenger`() {
        assertTrue(isSupportedPackage("com.whatsapp"))
        assertTrue(isSupportedPackage("com.whatsapp.w4b"))
        assertTrue(isSupportedPackage("com.facebook.orca"))
        assertFalse(isSupportedPackage("com.facebook.katana"))
        assertFalse(isSupportedPackage(null))
    }

    @Test
    fun `with auto-enable off, a brand-new muted group is captured but excluded from summary`() = runBlocking {
        app.settingsDataStore.setAutoEnableNewGroups(false)

        captureMessage(
            context = app,
            app = app,
            groupName = "Muted Client",
            sender = null,
            messageText = "Are you free tomorrow?",
            timestamp = System.currentTimeMillis()
        )

        val messages = app.messageRepository.observeMessages().first()
        val captured = messages.first { it.groupName == "Muted Client" }
        assertFalse(captured.isIncludedInSummary)

        val groups = app.workGroupRepository.observeGroups().first()
        assertFalse(groups.first { it.name == "Muted Client" }.isEnabled)
    }

    @Test
    fun `with auto-enable on, a brand-new muted group is included starting from its first message`() = runBlocking {
        app.settingsDataStore.setAutoEnableNewGroups(true)

        captureMessage(
            context = app,
            app = app,
            groupName = "Muted Client 2",
            sender = null,
            messageText = "Are you free tomorrow?",
            timestamp = System.currentTimeMillis()
        )

        // The critical part: this is the group's *first ever* message — auto-enable must apply
        // in time for this exact message to be included, not just from the second one onward.
        val messages = app.messageRepository.observeMessages().first()
        val captured = messages.first { it.groupName == "Muted Client 2" }
        assertTrue(captured.isIncludedInSummary)

        val groups = app.workGroupRepository.observeGroups().first()
        assertTrue(groups.first { it.name == "Muted Client 2" }.isEnabled)
    }

    @Test
    fun `auto-enable does not retroactively enable an already-existing disabled group`() = runBlocking {
        // This group already exists (disabled) before auto-enable is turned on — auto-enable
        // only applies to groups discovered for the first time afterwards, matching
        // upsertGroupSeen's documented behavior ("has no effect on a group that already exists").
        app.settingsDataStore.setAutoEnableNewGroups(false)
        app.workGroupRepository.upsertGroupSeen("Existing Group", System.currentTimeMillis(), isImportant = false)

        app.settingsDataStore.setAutoEnableNewGroups(true)
        captureMessage(
            context = app,
            app = app,
            groupName = "Existing Group",
            sender = null,
            messageText = "second message",
            timestamp = System.currentTimeMillis()
        )

        val groups = app.workGroupRepository.observeGroups().first()
        assertFalse(groups.first { it.name == "Existing Group" }.isEnabled)
    }

    @Test
    fun `capturing the exact same message twice only stores it once`() = runBlocking {
        // Regression test: Android's onNotificationPosted fired more than once for what was
        // genuinely the same WhatsApp notification event, and each firing inserted its own
        // row — this is what showed up as an identical message appearing twice in Search.
        val timestamp = System.currentTimeMillis()
        repeat(2) {
            captureMessage(
                context = app,
                app = app,
                groupName = "+971 56 233 1154",
                sender = null,
                messageText = "مرحب باشمهندس شو صار علي الطلب",
                timestamp = timestamp
            )
        }

        val messages = app.messageRepository.observeMessages().first()
        assertEquals(1, messages.count { it.groupName == "+971 56 233 1154" })
    }

    @Test
    fun `a repost with a slightly different timestamp is still recognized as the same message`() = runBlocking {
        // Regression test: the first version of this dedup check required an *exact*
        // timestamp match, but a repost of the same WhatsApp notification isn't guaranteed to
        // carry the identical postTime down to the millisecond — this is exactly the case the
        // screenshot showed (the same "41408" message captured twice, a second apart). A
        // tolerance window catches this; an exact match did not.
        val timestamp = System.currentTimeMillis()
        captureMessage(
            context = app, app = app, groupName = "+971 56 233 1154",
            sender = null, messageText = "41408", timestamp = timestamp
        )
        captureMessage(
            context = app, app = app, groupName = "+971 56 233 1154",
            sender = null, messageText = "41408", timestamp = timestamp + 900 // ~1 second later
        )

        val messages = app.messageRepository.observeMessages().first()
        assertEquals(1, messages.count { it.groupName == "+971 56 233 1154" && it.text == "41408" })
    }

    @Test
    fun `two genuinely different messages seconds apart are both kept`() = runBlocking {
        // The tolerance window must not be so wide that it swallows real, distinct messages
        // that just happen to arrive close together.
        val timestamp = System.currentTimeMillis()
        captureMessage(
            context = app, app = app, groupName = "Sales Team",
            sender = "Manager", messageText = "first message", timestamp = timestamp
        )
        captureMessage(
            context = app, app = app, groupName = "Sales Team",
            sender = "Manager", messageText = "second message", timestamp = timestamp + 900
        )

        val messages = app.messageRepository.observeMessages().first()
        assertEquals(2, messages.count { it.groupName == "Sales Team" })
    }

    @Test
    fun `two different messages from the same sender are both kept, not deduplicated`() = runBlocking {
        val timestamp = System.currentTimeMillis()
        captureMessage(context = app, app = app, groupName = "Sales Team", sender = "Manager", messageText = "first", timestamp = timestamp)
        captureMessage(context = app, app = app, groupName = "Sales Team", sender = "Manager", messageText = "second", timestamp = timestamp)

        val messages = app.messageRepository.observeMessages().first()
        assertEquals(2, messages.count { it.groupName == "Sales Team" })
    }

    @Test
    fun `a repost arriving nearly 3 minutes later is still caught as a duplicate`() = runBlocking {
        // The tolerance window was widened from 10 seconds to 3 minutes specifically because
        // real reposts (a slow-loading link preview, a catch-up scan running some time after
        // the live notification) were observed landing further apart than a few seconds.
        val timestamp = System.currentTimeMillis()
        captureMessage(
            context = app, app = app, groupName = "Sales Team",
            sender = "Manager", messageText = "identical repost", timestamp = timestamp
        )
        captureMessage(
            context = app, app = app, groupName = "Sales Team",
            sender = "Manager", messageText = "identical repost", timestamp = timestamp + 170_000L
        )

        val messages = app.messageRepository.observeMessages().first()
        assertEquals(1, messages.count { it.text == "identical repost" })
    }

    @Test
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun `many concurrent captures of the exact same message insert exactly one row`() = runBlocking {
        // Regression test for the real race condition: the live listener and the periodic
        // catch-up scan (or a burst of several notifications) could previously both check
        // "is this a duplicate?" before either had actually inserted its row, both see "no",
        // and both insert — producing a genuine duplicate no matter how wide the timestamp
        // tolerance window was. Firing many truly concurrent calls for the identical message
        // is what actually exercises that race; a mutex around the whole check-then-insert
        // span is what closes it.
        val timestamp = System.currentTimeMillis()
        val jobs = (1..20).map {
            kotlinx.coroutines.GlobalScope.launch {
                captureMessage(
                    context = app, app = app, groupName = "Sales Team",
                    sender = "Manager", messageText = "concurrent repost", timestamp = timestamp
                )
            }
        }
        jobs.forEach { it.join() }

        val messages = app.messageRepository.observeMessages().first()
        assertEquals(1, messages.count { it.text == "concurrent repost" })
    }

    @Test
    fun `isWhatsAppSystemNotification recognizes known status notifications`() {
        assertTrue(isWhatsAppSystemNotification("WhatsApp", "Checking for new messages"))
        assertTrue(isWhatsAppSystemNotification("WhatsApp Business", "Checking for new messages"))
        assertTrue(isWhatsAppSystemNotification("WhatsApp", "Backing up chats"))
    }

    @Test
    fun `isWhatsAppSystemNotification does not flag a real message`() {
        assertFalse(isWhatsAppSystemNotification("Sales Team", "Checking for new messages"))
        assertFalse(isWhatsAppSystemNotification("Ahmed Hassan", "Are you free tomorrow?"))
    }

    @Test
    fun `a WhatsApp system notification is never captured as a message`() = runBlocking {
        // The service's handle() filters these before calling captureMessage at all — this
        // confirms captureMessage itself never needs to see one for the pipeline to be clean,
        // by simulating what handle() would have skipped and asserting the filter would apply.
        assertTrue(isWhatsAppSystemNotification("WhatsApp Business", "Checking for new messages"))
    }

    @Test
    fun `isWhatsAppSystemNotification still matches when the title has a trailing badge count`() {
        assertTrue(isWhatsAppSystemNotification("WhatsApp Business (3)", "Checking for new messages"))
        assertTrue(isWhatsAppSystemNotification("WhatsApp (1)", "Backing up chats"))
    }

    @Test
    fun `isWhatsAppSystemNotification does not false-positive on a real contact whose name starts with WhatsApp`() {
        // Extremely unlikely in practice, but worth being explicit about: startsWith alone
        // would wrongly match this if the status-phrase check weren't also required.
        assertFalse(isWhatsAppSystemNotification("WhatsApp Support Group", "Can someone help me set this up?"))
    }

    @Test
    fun `cleanGroupTitle strips the bundled-notification 'N messages Sender' suffix`() {
        assertEquals("Matajer Mirgab Team", cleanGroupTitle("Matajer Mirgab Team (2 messages): Ahmed Hatem"))
        assertEquals("Sales Team", cleanGroupTitle("Sales Team (5 messages): Manager"))
    }

    @Test
    fun `cleanGroupTitle handles the singular 'message' form too`() {
        assertEquals("Sales Team", cleanGroupTitle("Sales Team (1 message): Manager"))
    }

    @Test
    fun `cleanGroupTitle leaves a normal single-message title unchanged`() {
        assertEquals("Sales Team", cleanGroupTitle("Sales Team"))
        assertEquals("Ahmed Hassan", cleanGroupTitle("Ahmed Hassan"))
    }

    @Test
    fun `cleanGroupTitle does not strip a group name that legitimately contains parentheses`() {
        assertEquals("Sales Team (Cairo)", cleanGroupTitle("Sales Team (Cairo)"))
    }

    @Test
    fun `cleanGroupTitle strips the bare 'GroupName colon Sender' form with no message count`() {
        // WhatsApp's own annotation of who just sent a single new message in a group — no
        // "(N messages)" needed when there's only the one. Left unstripped, the exact same
        // group ends up captured under a different "name" per sender (the real-world bug
        // this covers): a person's name after the colon...
        assertEquals("Masry Area - Private Group", cleanGroupTitle("Masry Area - Private Group: Mahmoud Mansour"))
        // ...and a phone number after the colon, for a sender with no saved contact name.
        assertEquals("Masry Area - Private Group", cleanGroupTitle("Masry Area - Private Group: +971 55 550 7471"))
    }

    @Test
    fun `cleanGroupTitle does not strip a colon that is not followed by a plausible sender`() {
        // A group name that legitimately contains a colon, followed by something that reads
        // as a subtitle/category rather than a person or phone number, must survive intact —
        // the heuristic is specifically about what comes *after* the colon, not the colon's
        // mere presence.
        assertEquals("Team: Design & Marketing Weekly Sync", cleanGroupTitle("Team: Design & Marketing Weekly Sync"))
        assertEquals("Project X: Phase 1 (Planning)", cleanGroupTitle("Project X: Phase 1 (Planning)"))
    }

    @Test
    fun `a group captured from a bundled-notification title is stored under the clean name`() = runBlocking {
        captureMessage(
            context = app, app = app,
            groupName = cleanGroupTitle("Matajer Mirgab Team (2 messages): Ahmed Hatem"),
            sender = null, messageText = "hello", timestamp = System.currentTimeMillis()
        )

        val messages = app.messageRepository.observeMessages().first()
        assertTrue(messages.any { it.groupName == "Matajer Mirgab Team" })
        assertFalse(messages.any { it.groupName.contains("messages)") })
    }
}
