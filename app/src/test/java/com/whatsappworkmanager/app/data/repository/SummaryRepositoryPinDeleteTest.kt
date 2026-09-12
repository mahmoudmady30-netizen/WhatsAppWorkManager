package com.whatsappworkmanager.app.data.repository

import androidx.test.core.app.ApplicationProvider
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkSummary
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
 * Covers the pin/delete capabilities added to Summary — requested explicitly: number the
 * cards, let the user delete one, and pin the ones they want to keep front and center.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = WwmApplication::class)
class SummaryRepositoryPinDeleteTest {

    private lateinit var app: WwmApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        runBlocking { app.summaryRepository.deleteAll() }
    }

    private fun sample(text: String, createdAt: Long) = WorkSummary(
        createdAt = createdAt,
        periodStart = createdAt - 1000,
        periodEnd = createdAt,
        text = text,
        importantCount = 0,
        needReplyCount = 0,
        groupCount = 1,
        totalMessages = 1,
        isAiGenerated = false
    )

    @Test
    fun `a pinned summary sorts before more recent unpinned ones`() = runBlocking {
        val olderId = app.summaryRepository.save(sample("older", 1000L))
        app.summaryRepository.save(sample("newer", 2000L))

        app.summaryRepository.setPinned(olderId, true)

        val summaries = app.summaryRepository.observeSummaries().first()
        assertEquals(olderId, summaries.first().id)
        assertTrue(summaries.first().isPinned)
    }

    @Test
    fun `unpinning returns a summary to normal chronological order`() = runBlocking {
        val olderId = app.summaryRepository.save(sample("older", 1000L))
        val newerId = app.summaryRepository.save(sample("newer", 2000L))
        app.summaryRepository.setPinned(olderId, true)

        app.summaryRepository.setPinned(olderId, false)

        val summaries = app.summaryRepository.observeSummaries().first()
        assertEquals(newerId, summaries.first().id)
        assertFalse(summaries.first { it.id == olderId }.isPinned)
    }

    @Test
    fun `deleting a summary removes only that one`() = runBlocking {
        val keepId = app.summaryRepository.save(sample("keep this one", 1000L))
        val deleteId = app.summaryRepository.save(sample("delete this one", 2000L))

        app.summaryRepository.delete(deleteId)

        val summaries = app.summaryRepository.observeSummaries().first()
        assertEquals(1, summaries.size)
        assertEquals(keepId, summaries.first().id)
    }
}
