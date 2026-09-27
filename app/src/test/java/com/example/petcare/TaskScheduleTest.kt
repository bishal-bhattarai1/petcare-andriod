package com.example.petcare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskScheduleTest {

    private fun task(repeat: String, days: String = "", time: String = "08:00 AM") = CareTask(
        id = 1, petId = 1, petName = "Max", description = "Routine", category = "Feeding",
        repeatType = repeat, scheduledTime = time, isCompleted = false, weekDays = days, reminderEnabled = true
    )

    private fun day(year: Int, month: Int, dayOfMonth: Int, hour: Int = 0, minute: Int = 0): Calendar =
        Calendar.getInstance().apply {
            clear()
            set(year, month, dayOfMonth, hour, minute, 0)
        }

    @Test
    fun weekly_isDueOnlyOnChosenDays() {
        val t = task("Weekly", "Mon,Thu")
        assertTrue(TaskSchedule.isDueOn(t, day(2026, Calendar.SEPTEMBER, 28))) // Monday
        assertFalse(TaskSchedule.isDueOn(t, day(2026, Calendar.SEPTEMBER, 29))) // Tuesday
    }

    @Test
    fun monthly_fallsOnLastDayInShortMonths() {
        val t = task("Monthly", "31")
        assertTrue(TaskSchedule.isDueOn(t, day(2026, Calendar.FEBRUARY, 28)))
        assertFalse(TaskSchedule.isDueOn(t, day(2026, Calendar.FEBRUARY, 27)))
        assertTrue(TaskSchedule.isDueOn(t, day(2026, Calendar.MARCH, 31)))
    }

    @Test
    fun nextOccurrence_daily_movesToTomorrowOnceTimeHasPassed() {
        val next = TaskSchedule.nextOccurrence(task("Daily"), day(2026, Calendar.SEPTEMBER, 26, 9, 0))!!
        assertEquals(27, next.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, next.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun nextOccurrence_monthly_findsNextMonth() {
        val next = TaskSchedule.nextOccurrence(task("Monthly", "15"), day(2026, Calendar.SEPTEMBER, 26, 9, 0))!!
        assertEquals(Calendar.OCTOBER, next.get(Calendar.MONTH))
        assertEquals(15, next.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun nextOccurrence_isNullWithoutATime() {
        assertNull(TaskSchedule.nextOccurrence(task("Daily", time = ""), day(2026, Calendar.SEPTEMBER, 26)))
    }

    @Test
    fun label_describesRepeat() {
        assertEquals("Monthly · day 15", TaskSchedule.label(task("Monthly", "15")))
        assertEquals("Weekly · Mon, Thu", TaskSchedule.label(task("Weekly", "Mon,Thu")))
        assertEquals("Daily", TaskSchedule.label(task("Daily")))
    }
}
