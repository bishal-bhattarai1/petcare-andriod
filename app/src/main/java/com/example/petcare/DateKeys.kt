package com.example.petcare

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Day keys ("yyyy-MM-dd") used to store per-day routine completions.
 * Moved out of the old AuthDatabaseHelper companion so non-database code can use them.
 */
object DateKeys {
    /** Day key for [calendar], e.g. "2026-10-04". */
    fun dateKey(calendar: Calendar): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

    fun todayKey(): String = dateKey(Calendar.getInstance())

    /** Monday and Sunday day keys of the week (Mon–Sun) containing [dateKey]. */
    fun weekRange(dateKey: String): Pair<String, String> {
        val calendar = Calendar.getInstance()
        runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey) }.getOrNull()?.let { calendar.time = it }
        val sinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        calendar.add(Calendar.DAY_OF_YEAR, -sinceMonday)
        val start = dateKey(calendar)
        calendar.add(Calendar.DAY_OF_YEAR, 6)
        return start to dateKey(calendar)
    }

    /** "2026-09-28,2026-09-30" → "Mon,Wed". */
    fun weekDayCodes(dateKeys: String): String {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayName = SimpleDateFormat("EEE", Locale.US)
        return dateKeys.split(",").mapNotNull { key ->
            runCatching { parser.parse(key.trim()) }.getOrNull()?.let(dayName::format)
        }.joinToString(",")
    }
}
