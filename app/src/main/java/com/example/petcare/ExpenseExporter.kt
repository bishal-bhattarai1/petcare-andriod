package com.example.petcare

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes expense reports (CSV or PDF) into the public Downloads/PetCare folder via MediaStore,
 * so no storage permission is needed. Call off the main thread.
 */
class ExpenseExporter(private val context: Context) {

    enum class Format(val extension: String, val mimeType: String) {
        CSV("csv", "text/csv"),
        PDF("pdf", "application/pdf")
    }

    data class Report(
        val expenses: List<ExpenseTransaction>,
        /** Human-readable description of the active filters, e.g. "All pets · This month". */
        val scope: String
    )

    /** Saves the report and returns its content URI, or null if writing failed. */
    fun export(report: Report, format: Format): Uri? {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "PetCare_expenses_$stamp.${format.extension}")
            put(MediaStore.Downloads.MIME_TYPE, format.mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PetCare")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                when (format) {
                    Format.CSV -> writeCsv(report, out)
                    Format.PDF -> writePdf(report, out)
                }
            } ?: throw IllegalStateException("No output stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun writeCsv(report: Report, out: OutputStream) {
        val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sb = StringBuilder()
        sb.append('﻿') // BOM so Excel opens the file as UTF-8
        sb.append("Date,Pet,Category,Description,Amount\r\n")
        report.expenses.forEach { e ->
            val date = parseExpenseDate(e.date)?.let { isoDate.format(it) } ?: e.date
            sb.append(listOf(date, e.petName, e.category, e.description).joinToString(",") { csvCell(it) })
            sb.append(',').append(String.format(Locale.US, "%.2f", e.amount)).append("\r\n")
        }
        sb.append(",,,Total,").append(String.format(Locale.US, "%.2f", report.expenses.sumOf { it.amount })).append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    /** Quotes a CSV cell and neutralises leading formula characters (CSV injection). */
    private fun csvCell(value: String): String {
        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    private fun writePdf(report: Report, out: OutputStream) {
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val rowHeight = 22f

        val accent = Color.rgb(0x18, 0x41, 0x4D)
        val muted = Color.rgb(0x6B, 0x74, 0x78)
        val divider = Color.rgb(0xE1, 0xE6, 0xE7)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; textSize = 22f; typeface = Typeface.DEFAULT_BOLD }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x2B, 0x33, 0x38); textSize = 10.5f }
        val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 9.5f }
        val line = Paint().apply { color = divider; strokeWidth = 1f }
        val right = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val rightBold = Paint(bold).apply { textAlign = Paint.Align.RIGHT }

        // Column x positions: Date, Pet, Category, Description, Amount (right-aligned).
        val colDate = margin
        val colPet = margin + 80
        val colCategory = margin + 180
        val colDescription = margin + 260
        val colAmount = pageWidth - margin

        val total = report.expenses.sumOf { it.amount }
        val generated = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date())

        var pageNumber = 0
        lateinit var page: PdfDocument.Page
        var y = 0f

        fun drawTableHeader() {
            val canvas = page.canvas
            canvas.drawText("DATE", colDate, y, small)
            canvas.drawText("PET", colPet, y, small)
            canvas.drawText("CATEGORY", colCategory, y, small)
            canvas.drawText("DESCRIPTION", colDescription, y, small)
            canvas.drawText("AMOUNT", colAmount, y, Paint(small).apply { textAlign = Paint.Align.RIGHT })
            y += 8f
            canvas.drawLine(margin, y, pageWidth - margin, y, line)
            y += 16f
        }

        fun startPage() {
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = margin + 10f
            if (pageNumber == 1) {
                val canvas = page.canvas
                canvas.drawText("PetCare expense report", margin, y + 12f, title)
                y += 34f
                canvas.drawText(report.scope, margin, y, small)
                y += 14f
                canvas.drawText("Generated $generated", margin, y, small)
                y += 28f

                canvas.drawText("Total spending", margin, y, small)
                canvas.drawText("Transactions", margin + 180, y, small)
                y += 18f
                canvas.drawText(formatMoney(total), margin, y, Paint(title).apply { textSize = 18f })
                canvas.drawText(report.expenses.size.toString(), margin + 180, y, Paint(title).apply { textSize = 18f })
                y += 24f

                EXPENSE_CATEGORIES.forEach { category ->
                    val amount = report.expenses.filter { it.category.equals(category, true) }.sumOf { it.amount }
                    if (amount > 0) {
                        val percent = if (total > 0) (amount / total * 100).toInt() else 0
                        canvas.drawText("$category  ${formatMoney(amount)} ($percent%)", margin, y, body)
                        y += 15f
                    }
                }
                y += 18f
            }
            drawTableHeader()
        }

        fun finishPage() {
            page.canvas.drawText("Page $pageNumber", pageWidth - margin, pageHeight - 24f, Paint(small).apply { textAlign = Paint.Align.RIGHT })
            pdf.finishPage(page)
        }

        fun ellipsize(text: String, paint: Paint, width: Float): String {
            if (paint.measureText(text) <= width) return text
            var end = text.length
            while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > width) end--
            return text.substring(0, end) + "…"
        }

        startPage()
        if (report.expenses.isEmpty()) {
            page.canvas.drawText("No expenses for this selection.", margin, y, small)
            y += rowHeight
        }
        report.expenses.forEach { e ->
            if (y > pageHeight - margin - 40f) {
                finishPage()
                startPage()
            }
            val canvas = page.canvas
            canvas.drawText(displayDate(e.date), colDate, y, body)
            canvas.drawText(ellipsize(e.petName, body, colCategory - colPet - 8), colPet, y, body)
            canvas.drawText(e.category, colCategory, y, body)
            canvas.drawText(ellipsize(e.description, body, colAmount - colDescription - 70), colDescription, y, body)
            canvas.drawText(formatMoney(e.amount), colAmount, y, right)
            y += 8f
            canvas.drawLine(margin, y, pageWidth - margin, y, line)
            y += rowHeight - 8f
        }
        if (y > pageHeight - margin - 40f) {
            finishPage()
            startPage()
        }
        page.canvas.drawText("Total", colDescription, y + 4f, bold)
        page.canvas.drawText(formatMoney(total), colAmount, y + 4f, rightBold)
        finishPage()

        pdf.writeTo(out)
        pdf.close()
    }
}
