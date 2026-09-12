package com.whatsappworkmanager.app.domain.usecase

import com.whatsappworkmanager.app.domain.model.ScheduleKind
import com.whatsappworkmanager.app.domain.model.WorkSchedule

/**
 * Pure logic for deciding the current mode (Work / Break / Quiet) and whether a scheduled
 * time has "arrived" for a given minute-of-day. Kept free of Android APIs for testability;
 * WorkManager/AlarmManager wiring lives in the worker/ layer.
 */
class ScheduleLogic {

    /** Returns the [ScheduleKind] active at [nowMinutes] (0..1439), or null if none matches. */
    fun activeModeAt(nowMinutes: Int, schedules: List<WorkSchedule>): ScheduleKind? {
        val modeSchedules = schedules.filter {
            it.enabled && it.endTimeMinutes != null &&
                it.kind in listOf(ScheduleKind.WORK_MODE, ScheduleKind.BREAK_MODE, ScheduleKind.QUIET_MODE)
        }
        for (s in modeSchedules) {
            val end = s.endTimeMinutes ?: continue
            val inRange = if (s.startTimeMinutes <= end) {
                nowMinutes in s.startTimeMinutes until end
            } else {
                // overnight range, e.g. 22:00 -> 06:00
                nowMinutes >= s.startTimeMinutes || nowMinutes < end
            }
            if (inRange) return s.kind
        }
        return null
    }

    /** True if a one-shot SUMMARY schedule is due at exactly [nowMinutes] (+/- toleranceMinutes). */
    fun isSummaryDue(nowMinutes: Int, schedule: WorkSchedule, toleranceMinutes: Int = 1): Boolean {
        if (!schedule.enabled || schedule.kind != ScheduleKind.SUMMARY) return false
        val diff = kotlin.math.abs(nowMinutes - schedule.startTimeMinutes)
        return diff <= toleranceMinutes || diff >= (1440 - toleranceMinutes)
    }
}
