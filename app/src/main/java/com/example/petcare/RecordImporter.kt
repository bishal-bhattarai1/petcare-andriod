package com.example.petcare

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** A health record read from an imported file, not yet saved. Date is "dd/MM/yyyy". */
data class ImportedRecord(val type: String, val date: String, val notes: String)

data class ImportResult(val records: List<ImportedRecord>, val skipped: Int)

/**
 * Reads vaccination schedules and appointments from files other apps and vets produce:
 *  - iCalendar (.ics): what clinic booking systems, Google/Outlook calendars and email invites use.
 *  - CSV: a simple spreadsheet with columns date, type, notes (header optional).
 * Pure Kotlin (no Android APIs) so it's covered by unit tests.
 */
object RecordImporter {

    val TYPES = listOf("Vaccination", "Check-up", "Medication", "Surgery", "Other")

    fun parse(text: String): ImportResult =
        if (text.contains("BEGIN:VCALENDAR", ignoreCase = true) || text.contains("BEGIN:VEVENT", ignoreCase = true)) {
            parseIcs(text)
        } else {
            parseCsv(text)
        }

    // region iCalendar

    fun parseIcs(text: String): ImportResult {
        // Lines longer than 75 chars are "folded": continuation lines start with a space or tab.
        val lines = text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split("\n")
        val records = mutableListOf<ImportedRecord>()
        var skipped = 0
        var event: MutableMap<String, Pair<String, String>>? = null // NAME -> (params, value)

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.equals("BEGIN:VEVENT", ignoreCase = true) -> event = mutableMapOf()
                trimmed.equals("END:VEVENT", ignoreCase = true) -> {
                    val record = event?.let { eventToRecord(it) }
                    if (record != null) records += record else skipped++
                    event = null
                }
                event != null && ':' in trimmed -> {
                    val key = trimmed.substringBefore(':')
                    val name = key.substringBefore(';').uppercase(Locale.US)
                    if (name !in event) event[name] = key.substringAfter(';', "") to trimmed.substringAfter(':')
                }
            }
        }
        return ImportResult(records, skipped)
    }

    private fun eventToRecord(event: Map<String, Pair<String, String>>): ImportedRecord? {
        val (params, rawStart) = event["DTSTART"] ?: return null
        val (date, time) = parseIcsDateTime(rawStart.trim(), params) ?: return null
        val summary = unescape(event["SUMMARY"]?.second.orEmpty())
        val location = unescape(event["LOCATION"]?.second.orEmpty())
        val description = unescape(event["DESCRIPTION"]?.second.orEmpty())
        val type = typeFor("$summary $description")
        val notes = listOf(
            summary.takeIf { !it.equals(type, ignoreCase = true) },
            time,
            location,
            description
        ).filter { !it.isNullOrBlank() }.joinToString(" · ")
        return ImportedRecord(type, date, notes.take(MAX_NOTES))
    }

    /** DTSTART forms: 20261012 (all-day), 20261012T093000 (local), 20261012T093000Z (UTC). */
    private fun parseIcsDateTime(value: String, params: String): Pair<String, String?>? {
        return try {
            if (value.length == 8 || params.contains("VALUE=DATE", ignoreCase = true) && !value.contains('T')) {
                val d = strict("yyyyMMdd").parse(value.take(8)) ?: return null
                storedDate.format(d) to null
            } else {
                val isUtc = value.endsWith("Z")
                val parser = strict("yyyyMMdd'T'HHmmss").apply {
                    if (isUtc) timeZone = TimeZone.getTimeZone("UTC")
                }
                val d: Date = parser.parse(value.removeSuffix("Z").take(15)) ?: return null
                storedDate.format(d) to timeFormat.format(d)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun unescape(value: String) = value
        .replace("\\n", " ").replace("\\N", " ")
        .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
        .trim()

    // endregion

    // region CSV

    fun parseCsv(text: String): ImportResult {
        val rows = text.removePrefix("﻿").replace("\r\n", "\n").split("\n")
            .map { it.trim() }.filter { it.isNotEmpty() }.map { splitCsvLine(it) }
        if (rows.isEmpty()) return ImportResult(emptyList(), 0)

        // Header is optional; if present, find columns by name.
        val header = rows.first().map { it.trim().lowercase(Locale.US) }
        val hasHeader = header.any { it == "date" || it.contains("date") }
        fun column(vararg names: String, default: Int) =
            if (hasHeader) header.indexOfFirst { h -> names.any { h.contains(it) } } else default
        val dateCol = column("date", default = 0)
        val typeCol = column("type", "category", "title", "summary", "event", default = 1)
        val notesCol = column("note", "description", "detail", "comment", default = 2)

        val records = mutableListOf<ImportedRecord>()
        var skipped = 0
        rows.drop(if (hasHeader) 1 else 0).forEach { cells ->
            val date = cells.getOrNull(dateCol)?.let { parseCsvDate(it.trim()) }
            if (date == null) {
                skipped++
                return@forEach
            }
            val rawType = cells.getOrNull(typeCol).orEmpty().trim()
            val rawNotes = cells.getOrNull(notesCol).orEmpty().trim()
            val known = TYPES.firstOrNull { it.equals(rawType, ignoreCase = true) }
            val type = known ?: typeFor("$rawType $rawNotes")
            val notes = listOf(rawType.takeIf { known == null && !it.equals(type, ignoreCase = true) }, rawNotes)
                .filter { !it.isNullOrBlank() }.joinToString(" · ")
            records += ImportedRecord(type, date, notes.take(MAX_NOTES))
        }
        return ImportResult(records, skipped)
    }

    /** Splits one CSV line, honouring quotes ("a, b" stays one cell; "" is an escaped quote). */
    fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && line.getOrNull(i + 1) == '"' -> { current.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { cells += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells
    }

    private fun parseCsvDate(value: String): String? {
        for (pattern in listOf("yyyy-MM-dd", "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "dd.MM.yyyy")) {
            try {
                val d = strict(pattern).parse(value) ?: continue
                return storedDate.format(d)
            } catch (_: Exception) {
                // try the next pattern
            }
        }
        return null
    }

    // endregion

    /** Maps free text ("Rabies booster", "Annual exam"…) to one of [TYPES]. */
    fun typeFor(text: String): String {
        val t = text.lowercase(Locale.US)
        return when {
            listOf("vaccin", "rabies", "booster", "dhpp", "parvo", "distemper", "bordetella", "shot").any { it in t } -> "Vaccination"
            listOf("check", "exam", "consult", "visit", "wellness").any { it in t } -> "Check-up"
            listOf("surg", "spay", "neuter", "operation", "dental cleaning").any { it in t } -> "Surgery"
            listOf("medic", "deworm", "tablet", "pill", "dose", "flea", "tick").any { it in t } -> "Medication"
            else -> "Other"
        }
    }

    private fun strict(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
    private val storedDate get() = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    private val timeFormat get() = SimpleDateFormat("hh:mm a", Locale.US)

    private const val MAX_NOTES = 300
}
