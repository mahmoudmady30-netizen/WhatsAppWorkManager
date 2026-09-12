package com.whatsappworkmanager.app.data.repository

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
 * Covers the "add a group/client by name before their first message" feature — important for
 * fully-muted threads that might never post a notification otherwise (see WorkGroupsScreen).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = WwmApplication::class)
class WorkGroupRepositoryTest {

    private lateinit var app: WwmApplication

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // Explicit reset: several tests below reuse the same group name (e.g. "Sales Team") —
        // without wiping state here, whichever test happens to run first "claims" that name,
        // and every other test touching it fails with stale data left over from a previous
        // test method in this same class. This guarantees each test starts from a clean slate
        // regardless of run order.
        runBlocking {
            app.workGroupRepository.deleteAll()
            app.messageRepository.deleteAll()
            // Cross-class leak guard — see SummaryRunnerTest for why this matters even though
            // no test in *this* file touches the setting directly.
            app.settingsDataStore.setAutoEnableNewGroups(false)
        }
    }

    @Test
    fun `adding a group manually creates it already enabled with zero messages`() = runBlocking {
        val added = app.workGroupRepository.addManually("Sales Team")
        assertTrue(added)

        val groups = app.workGroupRepository.observeGroups().first()
        val salesTeam = groups.first { it.name == "Sales Team" }
        assertTrue(salesTeam.isEnabled)
        assertEquals(0, salesTeam.messageCount)
    }

    @Test
    fun `adding the same name twice does nothing the second time`() = runBlocking {
        assertTrue(app.workGroupRepository.addManually("Ahmed Hassan"))
        assertFalse(app.workGroupRepository.addManually("Ahmed Hassan"))

        val groups = app.workGroupRepository.observeGroups().first()
        assertEquals(1, groups.count { it.name.equals("Ahmed Hassan", ignoreCase = true) })
    }

    @Test
    fun `adding a name is case-insensitive against an existing entry`() = runBlocking {
        assertTrue(app.workGroupRepository.addManually("Branch Managers"))
        assertFalse(app.workGroupRepository.addManually("branch managers"))
    }

    @Test
    fun `a manually-added muted group is included once its first message arrives, even with different case`() = runBlocking {
        app.workGroupRepository.addManually("Sales Team")

        // WhatsApp's notification title happens to arrive in different case than what the
        // user typed — this must still match the enabled group, not create a duplicate.
        captureMessage(
            context = app,
            app = app,
            groupName = "sales team",
            sender = "Manager",
            messageText = "Target update needed",
            timestamp = System.currentTimeMillis()
        )

        val groups = app.workGroupRepository.observeGroups().first()
        assertEquals(1, groups.count { it.name.equals("Sales Team", ignoreCase = true) })
        val salesTeam = groups.first { it.name.equals("Sales Team", ignoreCase = true) }
        assertEquals(1, salesTeam.messageCount)

        val messages = app.messageRepository.observeMessages().first()
        val captured = messages.first { it.text == "Target update needed" }
        assertTrue(captured.isIncludedInSummary)
    }

    @Test
    fun `deleting a group removes it from the list`() = runBlocking {
        app.workGroupRepository.upsertGroupSeen("Sales Team", System.currentTimeMillis(), isImportant = false)

        app.workGroupRepository.delete("Sales Team")

        val groups = app.workGroupRepository.observeGroups().first()
        assertTrue(groups.none { it.name.equals("Sales Team", ignoreCase = true) })
    }

    @Test
    fun `deleting a group does not remove its already-captured messages`() = runBlocking {
        captureMessage(
            context = app, app = app, groupName = "Sales Team", sender = "Manager",
            messageText = "Please confirm the order", timestamp = System.currentTimeMillis()
        )

        app.workGroupRepository.delete("Sales Team")

        val messages = app.messageRepository.observeMessages().first()
        assertTrue(messages.any { it.text == "Please confirm the order" })
    }

    @Test
    fun `deleting a group is case-insensitive`() = runBlocking {
        app.workGroupRepository.upsertGroupSeen("Sales Team", System.currentTimeMillis(), isImportant = false)

        app.workGroupRepository.delete("sales team")

        val groups = app.workGroupRepository.observeGroups().first()
        assertTrue(groups.none { it.name.equals("Sales Team", ignoreCase = true) })
    }

    @Test
    fun `deleting one group does not affect a different group`() = runBlocking {
        app.workGroupRepository.upsertGroupSeen("Sales Team", System.currentTimeMillis(), isImportant = false)
        app.workGroupRepository.upsertGroupSeen("Support Team", System.currentTimeMillis(), isImportant = false)

        app.workGroupRepository.delete("Sales Team")

        val groups = app.workGroupRepository.observeGroups().first()
        assertTrue(groups.any { it.name.equals("Support Team", ignoreCase = true) })
        assertTrue(groups.none { it.name.equals("Sales Team", ignoreCase = true) })
    }
}
