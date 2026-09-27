package com.example.petcare

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * When a routine is due. Shared by every screen and by reminders so they always agree.
 *  - Daily: every day.
 *  - Weekly: on the chosen weekdays ("Mon,Wed,Fri" in weekDays).
 *  - Monthly: once a month on a day of the month (the number in weekDays, e.g. "15");
 *    in shorter months a 29–31 routine falls on the last day.
 */
object TaskSchedule {

    fun isDueOn(task: CareTask, day: Calendar): Boolean = when {
        task.repeatType.equals("Weekly", ignoreCase = true) -> {
            val days = task.weekDays.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            days.isEmpty() || days.any { it.equals(SimpleDateFormat("EEE", Locale.US).format(day.time), ignoreCase = true) }
        }
        task.repeatType.equals("Monthly", ignoreCase = true) -> {
            val wanted = monthDay(task) ?: 1
            day.get(Calendar.DAY_OF_MONTH) == minOf(wanted, day.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        else -> true
    }

    fun isDueToday(task: CareTask): Boolean = isDueOn(task, Calendar.getInstance())

    fun monthDay(task: CareTask): Int? = task.weekDays.trim().toIntOrNull()?.takeIf { it in 1..31 }

    /** Short description for lists, e.g. "Daily", "Weekly · Mon, Thu", "Monthly · day 15". */
    fun label(task: CareTask): String = when {
        task.repeatType.equals("Weekly", ignoreCase = true) ->
            task.weekDays.takeIf { it.isNotBlank() }?.let { "Weekly · ${it.replace(",", ", ")}" } ?: "Weekly"
        task.repeatType.equals("Monthly", ignoreCase = true) ->
            monthDay(task)?.let { "Monthly · day $it" } ?: "Monthly"
        else -> "Daily"
    }

    /** The next moment this routine is due at its scheduled time, after [now]; null if it has no time. */
    fun nextOccurrence(task: CareTask, now: Calendar = Calendar.getInstance()): Calendar? {
        val minutes = CompletionRules.minutesOfDay(task.scheduledTime) ?: return null
        val candidate = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Monthly routines can be up to ~31 days away; look a little further to be safe.
        repeat(MAX_LOOKAHEAD_DAYS) {
            if (candidate.after(now) && isDueOn(task, candidate)) return candidate
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    private const val MAX_LOOKAHEAD_DAYS = 63
}
