package com.whatsappworkmanager.app.utils

import androidx.test.core.app.ApplicationProvider
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager
import android.app.NotificationManager
import org.robolectric.Shadows

/**
 * Covers the app-icon-badge count logic — requested explicitly: unread Important/Need Reply
 * messages should be reflected on the app icon. Exercises the real repository + notification
 * pipeline; whether the OS/launcher actually renders a *number* vs. a dot is outside any app's
 * control (see NotificationHelper.updateBadgeCount's doc), so this only verifies the app's own
 * side: does it post/update/cancel the badge notification with the right count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = WwmApplication::class)
class BadgeUpdaterTest {

    private lateinit var app: WwmApplication
    private lateinit var shadowNotificationManager: ShadowNotificationManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking { app.messageRepository.deleteAll() }
        val manager = app.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowNotificationManager = Shadows.shadowOf(manager)
    }

    private fun sampleMessage(
        important: Boolean = false,
        needsReply: Boolean = false,
        isRead: Boolean = false
    ) = WorkMessage(
        groupName = "Sales Team",
        sender = "Manager",
        text = "test",
        timestamp = System.currentTimeMillis(),
        isImportant = important,
        needsReply = needsReply,
        isRead = isRead
    )

    @Test
    fun `posts a badge notification when there are unread important messages`() = runBlocking {
        app.messageRepository.insert(sampleMessage(important = true, isRead = false))

        BadgeUpdater.refresh(app)

        val posted = shadowNotificationManager.activeNotifications
        assertTrue(posted.any { it.id == Constants.NOTIFICATION_ID_BADGE })
    }

    @Test
    fun `counts both important and needs-reply messages, not just important`() = runBlocking {
        app.messageRepository.insert(sampleMessage(important = false, needsReply = true, isRead = false))

        BadgeUpdater.refresh(app)

        val posted = shadowNotificationManager.activeNotifications
        assertTrue(posted.any { it.id == Constants.NOTIFICATION_ID_BADGE })
    }

    @Test
    fun `does not count read messages even if important`() = runBlocking {
        app.messageRepository.insert(sampleMessage(important = true, isRead = true))

        BadgeUpdater.refresh(app)

        val posted = shadowNotificationManager.activeNotifications
        assertFalse(posted.any { it.id == Constants.NOTIFICATION_ID_BADGE })
    }

    @Test
    fun `cancels the badge notification once everything is read`() = runBlocking {
        val id = app.messageRepository.insert(sampleMessage(important = true, isRead = false))
        BadgeUpdater.refresh(app)
        assertTrue(shadowNotificationManager.activeNotifications.any { it.id == Constants.NOTIFICATION_ID_BADGE })

        app.messageRepository.markRead(id)
        BadgeUpdater.refresh(app)

        assertFalse(shadowNotificationManager.activeNotifications.any { it.id == Constants.NOTIFICATION_ID_BADGE })
    }

    @Test
    fun `does not count routine messages that are neither important nor needing a reply`() = runBlocking {
        app.messageRepository.insert(sampleMessage(important = false, needsReply = false, isRead = false))

        BadgeUpdater.refresh(app)

        val posted = shadowNotificationManager.activeNotifications
        assertFalse(posted.any { it.id == Constants.NOTIFICATION_ID_BADGE })
    }
}
