package com.example.petcare

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * When a care routine may be marked done. Used by every screen that completes tasks so the
 * rules are identical everywhere:
 *  - Done is final: a completed routine can't be reopened.
 *  - A routine can only be completed once its scheduled time has passed (a 2:00 PM walk
 *    unlocks at 2:00 PM). Routines without a time can be completed any time that day.
 *  - Future days can't be completed.
 */
object CompletionRules {

    /** Why [task] can't be completed on [dateKey] right now, or null if it can. */
    fun blockReason(
        task: CareTask,
        dateKey: String = AuthDatabaseHelper.todayKey(),
        now: Calendar = Calendar.getInstance()
    ): String? {
        val today = AuthDatabaseHelper.dateKey(now)
        return when {
            task.isCompleted -> "Already done. Completed routines can't be undone."
            dateKey > today -> "You can't complete routines for a future day."
            dateKey == today && !isTimeReached(task.scheduledTime, now) ->
                "Available from ${task.scheduledTime.trim()}. You can mark it done once it's time."
            else -> null
        }
    }

    fun canComplete(task: CareTask, dateKey: String = AuthDatabaseHelper.todayKey()): Boolean =
        blockReason(task, dateKey) == null

    /** True when [time] ("hh:mm a") is now or earlier today, or when no time is set. */
    fun isTimeReached(time: String, now: Calendar = Calendar.getInstance()): Boolean {
        val scheduled = minutesOfDay(time) ?: return true
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE) >= scheduled
    }

    /** Weekly routines: a day can be ticked once it has arrived this week (Mon–Sun), never unticked. */
    fun weekDayBlockReason(day: String, task: CareTask, now: Calendar = Calendar.getInstance()): String? {
        val dayIndex = WEEK_DAYS.indexOf(day)
        val todayIndex = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday = 0 … Sunday = 6
        return when {
            dayIndex > todayIndex -> "You can't tick ${ChecklistAdapter.fullDayName(day)} before it arrives."
            dayIndex == todayIndex && !isTimeReached(task.scheduledTime, now) ->
                "Available from ${task.scheduledTime.trim()} today."
            else -> null
        }
    }

    /** Parses stored times like "02:00 PM" into minutes after midnight. */
    fun minutesOfDay(time: String): Int? {
        if (time.isBlank()) return null
        return try {
            val parsed = SimpleDateFormat("hh:mm a", Locale.US).parse(time.trim()) ?: return null
            Calendar.getInstance().apply { this.time = parsed }
                .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        } catch (_: Exception) {
            null
        }
    }

    private val WEEK_DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
}
