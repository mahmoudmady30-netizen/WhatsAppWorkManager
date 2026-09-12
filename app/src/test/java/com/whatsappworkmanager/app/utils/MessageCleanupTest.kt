package com.whatsappworkmanager.app.utils

import androidx.test.core.app.ApplicationProvider
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for real leftover junk: messages captured by an earlier app version,
 * before `isWhatsAppSystemNotification` existed, that stayed in the local database forever
 * since that filter only ever prevents *future* captures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = WwmApplication::class)
class MessageCleanupTest {

    private lateinit var app: WwmApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking {
            app.messageRepository.deleteAll()
            app.workGroupRepository.deleteAll()
        }
    }

    @Test
    fun `purges already-captured WhatsApp status notifications`() = runBlocking {
        app.messageRepository.insert(
            WorkMessage(groupName = "WhatsApp Business", sender = null, text = "Checking for new messages", timestamp = System.currentTimeMillis())
        )
        app.messageRepository.insert(
            WorkMessage(groupName = "WhatsApp", sender = null, text = "Backing up chats", timestamp = System.currentTimeMillis())
        )

        MessageCleanup.purgeSystemNotificationJunk(app)

        val remaining = app.messageRepository.observeMessages().first()
        assertEquals(0, remaining.size)
    }

    @Test
    fun `does not touch a real message from an actual client or group`() = runBlocking {
        app.messageRepository.insert(
            WorkMessage(groupName = "Sales Team", sender = "Manager", text = "Are you free tomorrow?", timestamp = System.currentTimeMillis())
        )
        // Coincidentally-named contact/group — not an actual WhatsApp status notification, so
        // must survive: its *text* doesn't match any known status phrase.
        app.messageRepository.insert(
            WorkMessage(groupName = "WhatsApp Business", sender = null, text = "Can you send the invoice?", timestamp = System.currentTimeMillis())
        )

        MessageCleanup.purgeSystemNotificationJunk(app)

        val remaining = app.messageRepository.observeMessages().first()
        assertEquals(2, remaining.size)
        assertTrue(remaining.any { it.text == "Are you free tomorrow?" })
        assertTrue(remaining.any { it.text == "Can you send the invoice?" })
    }

    @Test
    fun `running the purge twice is a safe no-op the second time`() = runBlocking {
        app.messageRepository.insert(
            WorkMessage(groupName = "WhatsApp", sender = null, text = "Checking for new messages", timestamp = System.currentTimeMillis())
        )

        MessageCleanup.purgeSystemNotificationJunk(app)
        MessageCleanup.purgeSystemNotificationJunk(app)

        val remaining = app.messageRepository.observeMessages().first()
        assertEquals(0, remaining.size)
    }

    @Test
    fun `purges WhatsApp's own group-summary notifications ('N messages from M chats')`() = runBlocking {
        app.messageRepository.insert(
            WorkMessage(groupName = "WA Business", sender = null, text = "8 messages from 4 chats", timestamp = System.currentTimeMillis())
        )
        app.messageRepository.insert(
            WorkMessage(groupName = "WhatsApp", sender = null, text = "1 message from 1 chat", timestamp = System.currentTimeMillis())
        )

        MessageCleanup.purgeGroupSummaryJunk(app)

        val remaining = app.messageRepository.observeMessages().first()
        assertEquals(0, remaining.size)
    }

    @Test
    fun `does not purge a real message that merely mentions a similar-looking number`() = runBlocking {
        app.messageRepository.insert(
            WorkMessage(groupName = "Sales Team", sender = "Manager", text = "Please send 8 messages from 4 chats you saved earlier", timestamp = System.currentTimeMillis())
        )

        MessageCleanup.purgeGroupSummaryJunk(app)

        // Deliberately NOT purged: the pattern only matches when the *entire* message is
        // exactly "N messages from M chats" (WhatsApp's own summary format), not any message
        // that happens to contain that phrase somewhere within otherwise real content.
        val remaining = app.messageRepository.observeMessages().first()
        assertEquals(1, remaining.size)
    }

    @Test
    fun `purgeGroupSummaryJunk run twice is a safe no-op the second time`() = runBlocking {
        app.messageRepository.insert(
            WorkMessage(groupName = "WA Business", sender = null, text = "7 messages from 3 chats", timestamp = System.currentTimeMillis())
        )

        MessageCleanup.purgeGroupSummaryJunk(app)
        MessageCleanup.purgeGroupSummaryJunk(app)

        val remaining = app.messageRepository.observeMessages().first()
        assertEquals(0, remaining.size)
    }

    @Test
    fun `purgeGroupTitleSuffixJunk repoints messages from a dirty bundled-title group name to the clean one`() = runBlocking {
        app.messageRepository.insert(
            WorkMessage(groupName = "Matajer Mirgab Team (2 messages): Ahmed Hatem", sender = null, text = "hello", timestamp = System.currentTimeMillis())
        )

        MessageCleanup.purgeGroupTitleSuffixJunk(app)

        val messages = app.messageRepository.observeMessages().first()
        assertEquals("Matajer Mirgab Team", messages.first().groupName)
    }

    @Test
    fun `purgeGroupTitleSuffixJunk merges into an already-existing clean-named work group, preserving enabled state`() = runBlocking {
        app.workGroupRepository.upsertGroupSeen("Matajer Mirgab Team", System.currentTimeMillis() - 5000, isImportant = false)
        app.workGroupRepository.setEnabled("Matajer Mirgab Team", false)
        app.workGroupRepository.upsertGroupSeen("Matajer Mirgab Team (2 messages): Ahmed Hatem", System.currentTimeMillis(), isImportant = false)
        app.workGroupRepository.setEnabled("Matajer Mirgab Team (2 messages): Ahmed Hatem", true)

        MessageCleanup.purgeGroupTitleSuffixJunk(app)

        val groups = app.workGroupRepository.observeGroups().first()
        assertEquals(1, groups.count { it.name == "Matajer Mirgab Team" })
        assertTrue(groups.first { it.name == "Matajer Mirgab Team" }.isEnabled)
        assertTrue(groups.none { it.name.contains("messages)") })
    }

    @Test
    fun `purgeGroupTitleSuffixJunk run twice is a safe no-op the second time`() = runBlocking {
        app.messageRepository.insert(
            WorkMessage(groupName = "Sales Team (3 messages): Manager", sender = null, text = "hello", timestamp = System.currentTimeMillis())
        )

        MessageCleanup.purgeGroupTitleSuffixJunk(app)
        MessageCleanup.purgeGroupTitleSuffixJunk(app)

        val messages = app.messageRepository.observeMessages().first()
        assertEquals("Sales Team", messages.first().groupName)
    }
}
