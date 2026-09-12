package com.whatsappworkmanager.app.domain.usecase

import com.whatsappworkmanager.app.domain.model.WorkMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryGeneratorTest {

    private val generator = SummaryGenerator(KeywordScoring())

    private fun message(
        group: String,
        text: String,
        important: Boolean = false,
        needsReply: Boolean = false,
        included: Boolean = true
    ) = WorkMessage(
        groupName = group,
        sender = "Sender",
        text = text,
        timestamp = System.currentTimeMillis(),
        isImportant = important,
        importanceScore = if (important) 9 else 0,
        needsReply = needsReply,
        isRead = false,
        isIncludedInSummary = included
    )

    @Test
    fun `local fallback summarizes counts correctly`() = runBlocking {
        val messages = listOf(
            message("Sales Team", "Target update needed", important = true, needsReply = true),
            message("Sales Team", "All good here"),
            message("Branch Managers", "Complaint from a customer", important = true)
        )
        val summary = generator.generate(
            messages = messages,
            periodStart = 0L,
            periodEnd = 1000L,
            aiProvider = null,
            language = "en"
        )
        assertEquals(3, summary.totalMessages)
        assertEquals(2, summary.importantCount)
        assertEquals(1, summary.needReplyCount)
        assertEquals(2, summary.groupCount)
        assertTrue(summary.text.isNotBlank())
        assertEquals(false, summary.isAiGenerated)
    }

    @Test
    fun `excluded messages are not counted`() = runBlocking {
        val messages = listOf(
            message("Friends", "party tonight?", included = false),
            message("Sales Team", "target report", important = true)
        )
        val summary = generator.generate(messages, 0L, 1000L, null, "en")
        assertEquals(1, summary.totalMessages)
        assertEquals(1, summary.groupCount)
    }

    @Test
    fun `empty period produces a graceful empty summary`() = runBlocking {
        val summary = generator.generate(emptyList(), 0L, 1000L, null, "ar")
        assertEquals(0, summary.totalMessages)
        assertTrue(summary.text.isNotBlank())
    }

    @Test
    fun `AI provider is never called when there are zero messages to summarize`() = runBlocking {
        // Regression test: sending an empty message list to a general-purpose chat model
        // produced a confusing conversational reply ("sure, please paste the messages...")
        // instead of a proper "no messages" summary — the fix is to never call the AI at all
        // when there's nothing to summarize.
        var summarizeWasCalled = false
        val fakeAiProvider = object : com.whatsappworkmanager.app.domain.repository.AiSummaryProvider {
            override val id: String = "fake"
            override val isCloud: Boolean = true
            override suspend fun summarize(messages: List<WorkMessage>, language: String): String {
                summarizeWasCalled = true
                return "This should never be returned for an empty message list."
            }
            override suspend fun suggestReplies(original: String, language: String): List<String> = emptyList()
        }

        val summary = generator.generate(
            messages = emptyList(),
            periodStart = 0L,
            periodEnd = 1000L,
            aiProvider = fakeAiProvider,
            language = "en"
        )

        assertFalse(summarizeWasCalled)
        assertFalse(summary.isAiGenerated)
        assertEquals("No messages during this period.", summary.text)
    }

    @Test
    fun `AI provider is still called normally when there are messages`() = runBlocking {
        var summarizeWasCalled = false
        val fakeAiProvider = object : com.whatsappworkmanager.app.domain.repository.AiSummaryProvider {
            override val id: String = "fake"
            override val isCloud: Boolean = true
            override suspend fun summarize(messages: List<WorkMessage>, language: String): String {
                summarizeWasCalled = true
                return "AI summary text"
            }
            override suspend fun suggestReplies(original: String, language: String): List<String> = emptyList()
        }

        val summary = generator.generate(
            messages = listOf(message("Sales Team", "target report", important = true)),
            periodStart = 0L,
            periodEnd = 1000L,
            aiProvider = fakeAiProvider,
            language = "en"
        )

        assertTrue(summarizeWasCalled)
        assertTrue(summary.isAiGenerated)
        assertEquals("AI summary text", summary.text)
    }
}
