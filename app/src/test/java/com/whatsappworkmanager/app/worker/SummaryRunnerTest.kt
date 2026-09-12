package com.whatsappworkmanager.app.worker

import androidx.test.core.app.ApplicationProvider
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.service.captureMessage
import kotlinx.coroutines.flow.first
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
 * Regression coverage for a real bug: a message captured *before* its group was enabled used
 * to stay permanently excluded from every future summary, even after the group was later
 * switched on — because `isIncludedInSummary` was only ever set once, at capture time. Also
 * covers the switch away from a fixed time-window ("since the last summary") to tracking each
 * message's own "already summarized" state, which was the other half of the same class of bug
 * (a message could fall outside the window of every future summary run).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = WwmApplication::class)
class SummaryRunnerTest {

    private lateinit var app: WwmApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking {
            app.messageRepository.deleteAll()
            app.workGroupRepository.deleteAll()
            app.summaryRepository.deleteAll()
            // This setting is a shared, persistent DataStore value — other test classes (e.g.
            // WhatsAppNotificationListenerServiceTest) intentionally turn it on for their own
            // tests. Without resetting it here too, a leftover "true" from a different test
            // class run earlier in the same test process makes a newly-discovered group start
            // enabled when this test expects it to start disabled.
            app.settingsDataStore.setAutoEnableNewGroups(false)
        }
    }

    @Test
    fun `a message captured before its group is enabled is retroactively included once enabled`() = runBlocking {
        // Group starts disabled (the default) — message arrives while it's still off.
        captureMessage(
            context = app,
            app = app,
            groupName = "Sales Team",
            sender = "Manager",
            messageText = "Urgent update needed",
            timestamp = System.currentTimeMillis()
        )
        val beforeEnable = app.messageRepository.observeMessages().first().first()
        assertEquals(false, beforeEnable.isIncludedInSummary)

        // User only enables the group afterwards, from Work Groups & Clients.
        app.workGroupRepository.setEnabled("Sales Team", true)
        app.messageRepository.setIncludedForGroup("Sales Team", true)

        val afterEnable = app.messageRepository.observeMessages().first().first()
        assertEquals(true, afterEnable.isIncludedInSummary)

        val summary = SummaryRunner.runOnce(app)
        assertEquals(1, summary.totalMessages)
        assertTrue(summary.text.contains("1"))
    }

    @Test
    fun `a message already included in one summary does not appear again in the next`() = runBlocking {
        app.workGroupRepository.upsertGroupSeen("Sales Team", System.currentTimeMillis(), isImportant = false)
        app.workGroupRepository.setEnabled("Sales Team", true)

        captureMessage(
            context = app,
            app = app,
            groupName = "Sales Team",
            sender = "Manager",
            messageText = "First batch message",
            timestamp = System.currentTimeMillis()
        )

        val firstSummary = SummaryRunner.runOnce(app)
        assertEquals(1, firstSummary.totalMessages)

        // Nothing new has arrived — the next summary should cover zero messages, not
        // re-include the one that's already been summarized.
        val secondSummary = SummaryRunner.runOnce(app)
        assertEquals(0, secondSummary.totalMessages)
    }

    @Test
    fun `generating an early empty summary does not block a later message from ever being counted`() = runBlocking {
        // This is the exact failure mode the old time-window approach had: an early summary
        // run (even with nothing to report) used to permanently advance the window, so a
        // message captured after that point but still somehow missed would never be picked up
        // by a *later* summary either, if the window logic had any gap. Tracking "pending"
        // messages directly instead makes the summary's own history irrelevant to whether a
        // given message eventually gets counted.
        val emptySummary = SummaryRunner.runOnce(app)
        assertEquals(0, emptySummary.totalMessages)

        app.workGroupRepository.upsertGroupSeen("Sales Team", System.currentTimeMillis(), isImportant = false)
        app.workGroupRepository.setEnabled("Sales Team", true)
        captureMessage(
            context = app,
            app = app,
            groupName = "Sales Team",
            sender = "Manager",
            messageText = "Message after the empty summary",
            timestamp = System.currentTimeMillis()
        )

        val secondSummary = SummaryRunner.runOnce(app)
        assertEquals(1, secondSummary.totalMessages)
    }

    @Test
    fun `a message captured with no groups enabled produces an actionable hint, not a plain empty message`() = runBlocking {
        // Regression test for exactly the Dashboard screenshot: "Messages Today: 2" but the
        // summary said "no messages", with no indication why — because nothing was enabled.
        captureMessage(
            context = app,
            app = app,
            groupName = "Some New Contact",
            sender = null,
            messageText = "hello",
            timestamp = System.currentTimeMillis()
        )

        val summary = SummaryRunner.runOnce(app)

        assertEquals(0, summary.totalMessages)
        assertTrue(summary.text.contains("Work Groups", ignoreCase = true) || summary.text.contains("جروب"))
    }

    @Test
    fun `a summary with genuinely zero captured messages still shows the plain empty text`() = runBlocking {
        val summary = SummaryRunner.runOnce(app)

        assertEquals(0, summary.totalMessages)
        assertFalse(summary.text.contains("Work Groups", ignoreCase = true))
    }
}
