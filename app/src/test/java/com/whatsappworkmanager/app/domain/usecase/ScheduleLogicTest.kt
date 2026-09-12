package com.whatsappworkmanager.app.domain.usecase

import com.whatsappworkmanager.app.domain.model.ScheduleKind
import com.whatsappworkmanager.app.domain.model.WorkSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleLogicTest {

    private val logic = ScheduleLogic()

    @Test
    fun `simple same-day range detects work mode`() {
        val schedules = listOf(
            WorkSchedule(name = "Morning Work", startTimeMinutes = 8 * 60, endTimeMinutes = 12 * 60, kind = ScheduleKind.WORK_MODE)
        )
        assertEquals(ScheduleKind.WORK_MODE, logic.activeModeAt(9 * 60, schedules))
        assertNull(logic.activeModeAt(13 * 60, schedules))
    }

    @Test
    fun `overnight quiet mode wraps past midnight`() {
        val schedules = listOf(
            WorkSchedule(name = "Night Quiet", startTimeMinutes = 22 * 60, endTimeMinutes = 6 * 60, kind = ScheduleKind.QUIET_MODE)
        )
        assertEquals(ScheduleKind.QUIET_MODE, logic.activeModeAt(23 * 60, schedules))
        assertEquals(ScheduleKind.QUIET_MODE, logic.activeModeAt(2 * 60, schedules))
        assertNull(logic.activeModeAt(10 * 60, schedules))
    }

    @Test
    fun `disabled schedule is ignored`() {
        val schedules = listOf(
            WorkSchedule(name = "Break", startTimeMinutes = 12 * 60, endTimeMinutes = 13 * 60, kind = ScheduleKind.BREAK_MODE, enabled = false)
        )
        assertNull(logic.activeModeAt(12 * 60 + 30, schedules))
    }

    @Test
    fun `summary schedule due within tolerance`() {
        val schedule = WorkSchedule(name = "Morning Summary", startTimeMinutes = 8 * 60, kind = ScheduleKind.SUMMARY)
        assertTrue(logic.isSummaryDue(8 * 60, schedule))
        assertTrue(logic.isSummaryDue(8 * 60 + 1, schedule))
        assertEquals(false, logic.isSummaryDue(8 * 60 + 5, schedule))
    }
}
