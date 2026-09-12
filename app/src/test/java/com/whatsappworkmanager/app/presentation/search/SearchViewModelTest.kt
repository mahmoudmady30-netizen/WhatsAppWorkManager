package com.whatsappworkmanager.app.presentation.search

import androidx.test.core.app.ApplicationProvider
import com.whatsappworkmanager.app.WwmApplication
import com.whatsappworkmanager.app.domain.model.WorkMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers marking a message Important (the star toggle) also teaching Reply Detection from its
 * text — requested explicitly: starring something should make this exact wording recognized
 * as "needs a reply" going forward too, not just flip a flag on that one message.
 *
 * REAL BUG FOUND AND FIXED HERE: this class previously had no `Dispatchers.setMain(...)` setup
 * at all. `SearchViewModel.setImportant`/`markAsRead` launch via `viewModelScope.launch { }`,
 * which by default runs on `Dispatchers.Main.immediate` — under Robolectric that's backed by a
 * simulated Android main looper that, without something explicitly pumping it, never actually
 * *runs* posted work. That meant `.join()` on the returned `Job` wasn't racing the coroutine
 * (as Round 25/26's notes assumed) — it was **deadlocking**: waiting forever for a coroutine
 * that could never be scheduled to run at all, since nothing was driving `Dispatchers.Main`'s
 * queue. That's what hung the CI's "Run unit tests" step indefinitely — not a slow first-time
 * Robolectric download, as first suspected. Both pieces are needed together, not either/or:
 * `Dispatchers.setMain(UnconfinedTestDispatcher())` so the launched coroutine is actually
 * driven to run (removing the deadlock), and `.join()` so the test still genuinely waits for
 * the *complete* result — including Room's own internal executor thread-hop inside — rather
 * than assuming synchronous completion.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = WwmApplication::class)
class SearchViewModelTest {

    private lateinit var app: WwmApplication
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        runBlocking {
            app.messageRepository.deleteAll()
            app.replyPhraseRuleRepository.observeRules().first().forEach {
                app.replyPhraseRuleRepository.delete(it.id)
            }
        }
        viewModel = SearchViewModel(app, MessageFilter.ALL)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleMessage(text: String) = WorkMessage(
        groupName = "Sales Team",
        sender = "Manager",
        text = text,
        timestamp = System.currentTimeMillis()
    )

    /**
     * `viewModel.uiState.value.results` only reflects a just-made change once the ViewModel's
     * own `observeMessages()` collector (started in `init`, running on `viewModelScope`) has
     * processed the *next* emission from Room's Flow — and that emission is itself delivered
     * via Room's own invalidation-tracker executor, a real background thread that
     * `Dispatchers.setMain(UnconfinedTestDispatcher())` doesn't control (same underlying reason
     * `setImportant`/`markAsRead` need `.join()` on the Job they return, documented above; this
     * is the same class of raciness showing up on the *read* side instead of the write side).
     * Polling briefly is the robust fix that doesn't depend on knowing exactly how many
     * background hops are involved.
     */
    private suspend fun awaitResults(predicate: (List<WorkMessage>) -> Boolean): List<WorkMessage> {
        val deadline = System.currentTimeMillis() + 2000L
        while (System.currentTimeMillis() < deadline) {
            val current = viewModel.uiState.value.results
            if (predicate(current)) return current
            kotlinx.coroutines.delay(20L)
        }
        return viewModel.uiState.value.results
    }

    @Test
    fun `marking a message important adds its text as a Reply Detection phrase`() = runBlocking {
        val id = app.messageRepository.insert(sampleMessage("Please confirm the delivery time"))
        val message = app.messageRepository.observeMessages().first().first { it.id == id }

        viewModel.setImportant(message, true).join()

        val phrases = app.replyPhraseRuleRepository.getEnabledPhrasesOnce()
        assertTrue(phrases.any { it.equals("Please confirm the delivery time", ignoreCase = true) })
    }

    @Test
    fun `un-starring a message does not remove its Reply Detection phrase`() = runBlocking {
        val id = app.messageRepository.insert(sampleMessage("Please confirm the delivery time"))
        val message = app.messageRepository.observeMessages().first().first { it.id == id }

        viewModel.setImportant(message, true).join()
        viewModel.setImportant(message.copy(isImportant = true), false).join()

        val phrases = app.replyPhraseRuleRepository.getEnabledPhrasesOnce()
        assertTrue(phrases.any { it.equals("Please confirm the delivery time", ignoreCase = true) })
    }

    @Test
    fun `marking the same-text message important twice does not create a duplicate phrase`() = runBlocking {
        val id1 = app.messageRepository.insert(sampleMessage("Same wording"))
        val id2 = app.messageRepository.insert(sampleMessage("Same wording"))
        val messages = app.messageRepository.observeMessages().first()

        viewModel.setImportant(messages.first { it.id == id1 }, true).join()
        viewModel.setImportant(messages.first { it.id == id2 }, true).join()

        val phrases = app.replyPhraseRuleRepository.getEnabledPhrasesOnce()
        assertTrue(phrases.count { it.equals("Same wording", ignoreCase = true) } == 1)
    }

    @Test
    fun `un-marking important as false does not add a phrase`() = runBlocking {
        val id = app.messageRepository.insert(sampleMessage("Just checking in"))
        val message = app.messageRepository.observeMessages().first().first { it.id == id }

        viewModel.setImportant(message, false).join()

        val phrases = app.replyPhraseRuleRepository.getEnabledPhrasesOnce()
        assertFalse(phrases.any { it.equals("Just checking in", ignoreCase = true) })
    }

    @Test
    fun `the All filter shows only unread messages`() = runBlocking {
        val readId = app.messageRepository.insert(sampleMessage("Already handled"))
        val unreadId = app.messageRepository.insert(sampleMessage("Still pending"))
        app.messageRepository.markRead(readId)

        viewModel.onFilterChanged(MessageFilter.ALL)

        // Waits for the FULL expected end state — both the unread message present AND the
        // read one absent — not just one half of it. Polling for only "unreadId showed up"
        // could return the moment the insert-emissions caught up but *before* the later
        // markRead(readId) emission had also been processed, since each is a separate Flow
        // emission the ViewModel's collector catches up to one at a time; asserting readId's
        // absence right after would then race that specific emission and fail intermittently.
        val results = awaitResults { current ->
            current.any { it.id == unreadId } && current.none { it.id == readId }
        }
        assertTrue(results.any { it.id == unreadId })
        assertFalse(results.any { it.id == readId })
    }

    @Test
    fun `a message marked read no longer appears under All but does under Read`() = runBlocking {
        val id = app.messageRepository.insert(sampleMessage("Needs a look"))

        viewModel.onFilterChanged(MessageFilter.ALL)
        assertTrue(awaitResults { it.any { m -> m.id == id } }.any { it.id == id })

        viewModel.markAsRead(id).join()

        viewModel.onFilterChanged(MessageFilter.ALL)
        assertFalse(awaitResults { it.none { m -> m.id == id } }.any { it.id == id })

        viewModel.onFilterChanged(MessageFilter.READ)
        assertTrue(awaitResults { it.any { m -> m.id == id } }.any { it.id == id })
    }

    @Test
    fun `Read All marks every currently visible unread message as read`() = runBlocking {
        val id1 = app.messageRepository.insert(sampleMessage("first"))
        val id2 = app.messageRepository.insert(sampleMessage("second"))
        viewModel.onFilterChanged(MessageFilter.ALL)
        awaitResults { it.size >= 2 }

        viewModel.markAllVisibleAsRead().join()

        val messages = app.messageRepository.observeMessages().first()
        assertTrue(messages.first { it.id == id1 }.isRead)
        assertTrue(messages.first { it.id == id2 }.isRead)
    }

    @Test
    fun `Read All does not touch messages outside the currently visible filter`() = runBlocking {
        val importantId = app.messageRepository.insert(sampleMessage("important one").copy(isImportant = true))
        val id = app.messageRepository.insert(sampleMessage("id"))
        app.messageRepository.insert(sampleMessage("unrelated"))
        viewModel.onFilterChanged(MessageFilter.IMPORTANT)
        awaitResults { it.any { m -> m.id == importantId } }

        viewModel.markAllVisibleAsRead().join()

        val messages = app.messageRepository.observeMessages().first()
        assertTrue(messages.first { it.id == importantId }.isRead)
        assertFalse(messages.first { it.text == "unrelated" }.isRead)
    }

    @Test
    fun `Delete All removes every currently visible message`() = runBlocking {
        app.messageRepository.insert(sampleMessage("first"))
        app.messageRepository.insert(sampleMessage("second"))
        viewModel.onFilterChanged(MessageFilter.ALL)
        awaitResults { it.size >= 2 }

        viewModel.deleteAllVisible().join()

        val messages = app.messageRepository.observeMessages().first()
        assertTrue(messages.none { it.text == "first" || it.text == "second" })
    }

    @Test
    fun `Delete All does not touch messages outside the currently visible filter`() = runBlocking {
        val importantId = app.messageRepository.insert(sampleMessage("important one").copy(isImportant = true))
        app.messageRepository.insert(sampleMessage("unrelated"))
        viewModel.onFilterChanged(MessageFilter.IMPORTANT)
        awaitResults { it.any { m -> m.id == importantId } }

        viewModel.deleteAllVisible().join()

        val messages = app.messageRepository.observeMessages().first()
        assertTrue(messages.none { it.id == importantId })
        assertTrue(messages.any { it.text == "unrelated" })
    }
}
