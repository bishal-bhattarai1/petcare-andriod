package com.example.petcare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordImporterTest {

    @Test
    fun ics_readsEventsWithDatesTimesAndLocation() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:Rabies booster
            DTSTART:20261012T093000
            LOCATION:City Vet Clinic
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Annual check-up
            DTSTART;VALUE=DATE:20270115
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = RecordImporter.parse(ics)

        assertEquals(0, result.skipped)
        assertEquals(
            listOf(
                ImportedRecord("Vaccination", "12/10/2026", "Rabies booster · 09:30 AM · City Vet Clinic"),
                ImportedRecord("Check-up", "15/01/2027", "Annual check-up")
            ),
            result.records
        )
    }

    @Test
    fun ics_unfoldsLongLinesAndUnescapesText() {
        val ics = "BEGIN:VEVENT\r\nSUMMARY:Deworming\r\nDTSTART:20261101\r\nDESCRIPTION:Give 1 tablet\\, with food\r\n  after breakfast\r\nEND:VEVENT"

        val record = RecordImporter.parse(ics).records.single()

        assertEquals("Medication", record.type)
        assertEquals("01/11/2026", record.date)
        assertEquals("Deworming · Give 1 tablet, with food after breakfast", record.notes)
    }

    @Test
    fun ics_skipsEventsWithoutAValidDate() {
        val ics = "BEGIN:VEVENT\nSUMMARY:No date\nEND:VEVENT\nBEGIN:VEVENT\nSUMMARY:Bad\nDTSTART:2026-99-99\nEND:VEVENT"

        val result = RecordImporter.parse(ics)

        assertTrue(result.records.isEmpty())
        assertEquals(2, result.skipped)
    }

    @Test
    fun csv_withHeader_mapsColumnsByName() {
        val csv = """
            notes,date,type
            "Rabies, 1 year",2026-10-12,Vaccination
            Weight 12kg,15/01/2027,check-up
        """.trimIndent()

        val result = RecordImporter.parse(csv)

        assertEquals(
            listOf(
                ImportedRecord("Vaccination", "12/10/2026", "Rabies, 1 year"),
                ImportedRecord("Check-up", "15/01/2027", "Weight 12kg")
            ),
            result.records
        )
    }

    @Test
    fun csv_withoutHeader_usesDateTypeNotesOrder_andSkipsBadRows() {
        val csv = "12/10/2026,Rabies booster,City Vet\nnot a date,Other,x\n\n1/2/2027,Spay surgery,"

        val result = RecordImporter.parse(csv)

        assertEquals(1, result.skipped)
        assertEquals(
            listOf(
                ImportedRecord("Vaccination", "12/10/2026", "Rabies booster · City Vet"),
                ImportedRecord("Surgery", "01/02/2027", "Spay surgery")
            ),
            result.records
        )
    }

    @Test
    fun splitCsvLine_handlesQuotesAndEscapedQuotes() {
        assertEquals(listOf("a", "b, c", "say \"hi\"", ""), RecordImporter.splitCsvLine("a,\"b, c\",\"say \"\"hi\"\"\","))
    }

    @Test
    fun typeFor_recognisesCommonVetWording() {
        assertEquals("Vaccination", RecordImporter.typeFor("DHPP shot"))
        assertEquals("Check-up", RecordImporter.typeFor("Wellness exam"))
        assertEquals("Medication", RecordImporter.typeFor("Flea treatment"))
        assertEquals("Surgery", RecordImporter.typeFor("Neuter"))
        assertEquals("Other", RecordImporter.typeFor("Grooming"))
    }
}
