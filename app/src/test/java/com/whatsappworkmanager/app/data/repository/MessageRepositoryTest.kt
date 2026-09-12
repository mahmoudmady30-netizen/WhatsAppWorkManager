package com.whatsappworkmanager.app.data.repository

import androidx.test.core.app.ApplicationProvider
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkMessage
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

/** Covers marking a captured message read and deleting one — both surfaced from the Search screen. */
@RunWith(RobolectricTestRunner::class)
@Config(application = WwmApplication::class)
class MessageRepositoryTest {

    private lateinit var app: WwmApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // Same defensive reset as the other Robolectric test classes in this project — without
        // it, leftover messages from a previous test method (or class, depending on how the
        // test JVM forks) can make an assertion like "exactly 1 message left" fail for reasons
        // that have nothing to do with the behavior actually under test here.
        runBlocking { app.messageRepository.deleteAll() }
    }

    private fun sampleMessage(text: String) = WorkMessage(
        groupName = "Sales Team",
        sender = "Manager",
        text = text,
        timestamp = System.currentTimeMillis(),
        isImportant = false,
        importanceScore = 0,
        needsReply = false,
        isRead = false,
        isIncludedInSummary = true
    )

    @Test
    fun `a newly captured message starts unread`() = runBlocking {
        app.messageRepository.insert(sampleMessage("hello"))
        val messages = app.messageRepository.observeMessages().first()
        assertFalse(messages.first { it.text == "hello" }.isRead)
    }

    @Test
    fun `marking a message read updates only that message`() = runBlocking {
        val id1 = app.messageRepository.insert(sampleMessage("first"))
        app.messageRepository.insert(sampleMessage("second"))

        app.messageRepository.markRead(id1)

        val messages = app.messageRepository.observeMessages().first()
        assertTrue(messages.first { it.text == "first" }.isRead)
        assertFalse(messages.first { it.text == "second" }.isRead)
    }

    @Test
    fun `deleting a message removes only that one`() = runBlocking {
        val id1 = app.messageRepository.insert(sampleMessage("keep me"))
        val id2 = app.messageRepository.insert(sampleMessage("delete me"))

        app.messageRepository.delete(id2)

        val messages = app.messageRepository.observeMessages().first()
        assertEquals(1, messages.size)
        assertEquals("keep me", messages.first().text)
    }
}
