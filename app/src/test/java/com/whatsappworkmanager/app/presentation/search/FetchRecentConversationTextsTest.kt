package com.whatsappworkmanager.app.presentation.search

import androidx.test.core.app.ApplicationProvider
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers gathering conversation context for a reply — requested explicitly: when several
 * messages have arrived from the same client/group, the reply should be generated from the
 * recent back-and-forth, not just the single latest line in isolation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = WwmApplication::class)
class FetchRecentConversationTextsTest {

    private lateinit var app: WwmApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking { app.messageRepository.deleteAll() }
    }

    private suspend fun insert(groupName: String, text: String, timestamp: Long) {
        app.messageRepository.insert(
            WorkMessage(groupName = groupName, sender = null, text = text, timestamp = timestamp)
        )
    }

    @Test
    fun `returns messages oldest first, ending with the one being replied to`() = runBlocking {
        val base = System.currentTimeMillis()
        insert("Client A", "first message", base)
        insert("Client A", "second message", base + 1000)
        insert("Client A", "third message", base + 2000)

        val conversation = fetchRecentConversationTexts(app, "Client A", uptoTimestamp = base + 2000)

        assertEquals(listOf("first message", "second message", "third message"), conversation)
    }

    @Test
    fun `excludes messages that arrived after the one being replied to`() = runBlocking {
        val base = System.currentTimeMillis()
        insert("Client A", "earlier message", base)
        insert("Client A", "the one being replied to", base + 1000)
        insert("Client A", "arrived later, should not be included as context", base + 2000)

        val conversation = fetchRecentConversationTexts(app, "Client A", uptoTimestamp = base + 1000)

        assertEquals(listOf("earlier message", "the one being replied to"), conversation)
    }

    @Test
    fun `caps the context at maxCount, keeping only the most recent ones`() = runBlocking {
        val base = System.currentTimeMillis()
        for (i in 1..8) {
            insert("Client A", "message $i", base + i * 1000L)
        }

        val conversation = fetchRecentConversationTexts(app, "Client A", uptoTimestamp = base + 8000, maxCount = 3)

        assertEquals(listOf("message 6", "message 7", "message 8"), conversation)
    }

    @Test
    fun `does not mix in messages from a different group`() = runBlocking {
        val base = System.currentTimeMillis()
        insert("Client A", "from client A", base)
        insert("Client B", "from client B", base + 500)

        val conversation = fetchRecentConversationTexts(app, "Client A", uptoTimestamp = base + 500)

        assertEquals(listOf("from client A"), conversation)
    }
}
