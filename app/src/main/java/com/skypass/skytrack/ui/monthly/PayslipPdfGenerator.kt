package com.skypass.skytrack.ui.monthly

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.skypass.skytrack.data.remote.PayslipDto
import java.io.File
import java.io.FileOutputStream
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Payslip PDF on the company letterhead (R.drawable.letterhead as the page background, like the timesheet),
 * built from the frozen payslip returned by the backend. Same layout as the web payslip.
 */
object PayslipPdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 48f
    private const val RIGHT = PAGE_WIDTH - MARGIN_X

    private val dark = Color.rgb(15, 23, 41)
    private val muted = Color.rgb(100, 115, 140)
    private val border = Color.rgb(217, 222, 230)
    private val red = Color.rgb(199, 41, 41)
    private val green = Color.rgb(5, 120, 87)
    private val shade = Color.rgb(245, 247, 250)
    private val netShade = Color.rgb(237, 250, 242)

    /** "Rs. 1,04,000.50" — Indian digit grouping, independent of the device's number formatting. */
    fun formatAmount(amount: Double?): String {
        if (amount == null || amount.isNaN() || amount.isInfinite()) return "-"
        val fixed = java.math.BigDecimal(amount).setScale(2, java.math.RoundingMode.HALF_UP).abs().toPlainString()
        val (whole, paise) = fixed.split('.').let { it[0] to it.getOrElse(1) { "00" } }
        val grouped = if (whole.length <= 3) whole else {
            val head = whole.dropLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + whole.takeLast(3)
        }
        return "Rs. ${if (amount < 0) "-" else ""}$grouped.$paise"
    }

    private fun formatDays(days: Double?): String {
        val d = days ?: 0.0
        return if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", d).trimEnd('0').trimEnd('.')
    }

    fun generate(context: Context, payslip: PayslipDto): File {
        val salary = payslip.salary ?: error("Payslip has no figures")
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        val canvas = page.canvas

        val letterheadResId = context.resources.getIdentifier("letterhead", "drawable", context.packageName)
        if (letterheadResId != 0) {
            android.graphics.BitmapFactory.decodeResource(context.resources, letterheadResId)?.let { bitmap ->
                canvas.drawBitmap(bitmap, null, RectF(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat()), null)
            }
        }

        fun paint(size: Float, bold: Boolean = false, color: Int = dark, align: Paint.Align = Paint.Align.LEFT) = Paint().apply {
            isAntiAlias = true
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            this.color = color
            textAlign = align
        }
        val linePaint = Paint().apply { color = border; strokeWidth = 0.8f }
        fun fill(color: Int) = Paint().apply { this.color = color; style = Paint.Style.FILL }

        val monthLabel = runCatching {
            YearMonth.parse(payslip.month).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        }.getOrDefault(payslip.month)
        val issued = runCatching {
            OffsetDateTime.parse(payslip.generatedAt).atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
        }.getOrDefault("")
        val paidDays = maxOf(0.0, (salary.paidWorkingDays ?: 0.0) - (salary.totalLopDays ?: 0.0))

        // Top of content clears the letterhead's header band
        var y = if (letterheadResId != 0) 200f else 60f

        canvas.drawText(payslip.companyName ?: "Skypass Visa Services", MARGIN_X, y, paint(13f, bold = true))
        canvas.drawText("PAYSLIP", RIGHT, y, paint(16f, bold = true, color = red, align = Paint.Align.RIGHT))
        y += 18f
        canvas.drawText("Pay period: $monthLabel", MARGIN_X, y, paint(10f, color = muted))
        canvas.drawText("No. ${payslip.payslipNumber}", RIGHT, y, paint(9f, color = muted, align = Paint.Align.RIGHT))
        y += 13f
        val revision = if (payslip.version > 1) " (revision ${payslip.version})" else ""
        canvas.drawText("Issued $issued$revision", RIGHT, y, paint(9f, color = muted, align = Paint.Align.RIGHT))
        y += 16f
        canvas.drawLine(MARGIN_X, y, RIGHT, y, Paint().apply { color = dark; strokeWidth = 1f })

        // Employee details (two columns)
        val col2 = MARGIN_X + (RIGHT - MARGIN_X) / 2
        fun detail(x: Float, top: Float, label: String, value: String?) {
            canvas.drawText(label.uppercase(Locale.ENGLISH), x, top, paint(7.5f, bold = true, color = muted))
            canvas.drawText(value?.takeIf { it.isNotBlank() } ?: "-", x, top + 13f, paint(10.5f))
        }
        y += 26f
        detail(MARGIN_X, y, "Employee name", payslip.employee?.name)
        detail(col2, y, "Employee ID", payslip.employee?.employeeId)
        y += 34f
        detail(MARGIN_X, y, "Designation", payslip.employee?.designation)
        detail(col2, y, "Department", payslip.employee?.department)
        y += 34f
        detail(MARGIN_X, y, "Working days in month", formatDays(salary.workingDaysInMonth))
        detail(col2, y, "Paid days", formatDays(paidDays))

        // Earnings and deductions
        y += 40f
        val colDays = RIGHT - 150f
        canvas.drawRect(MARGIN_X, y - 14f, RIGHT, y + 6f, fill(shade))
        canvas.drawText("DESCRIPTION", MARGIN_X + 8f, y, paint(8f, bold = true, color = muted))
        canvas.drawText("DAYS", colDays, y, paint(8f, bold = true, color = muted, align = Paint.Align.RIGHT))
        canvas.drawText("AMOUNT", RIGHT - 8f, y, paint(8f, bold = true, color = muted, align = Paint.Align.RIGHT))
        y += 24f

        fun row(label: String, days: String, amount: String, color: Int = dark, bold: Boolean = false) {
            canvas.drawText(label, MARGIN_X + 8f, y, paint(10f, bold, color))
            if (days.isNotEmpty()) canvas.drawText(days, colDays, y, paint(10f, bold, color, Paint.Align.RIGHT))
            canvas.drawText(amount, RIGHT - 8f, y, paint(10f, bold, color, Paint.Align.RIGHT))
            y += 10f
            canvas.drawLine(MARGIN_X, y, RIGHT, y, linePaint)
            y += 14f
        }

        row("Monthly gross salary", "", formatAmount(salary.monthlyGross))
        if (salary.earnedGross != null && salary.earnedGross != salary.monthlyGross) {
            row("Payable for days employed", formatDays(salary.paidWorkingDays), formatAmount(salary.earnedGross))
        }
        row("Less: Absences", formatDays(salary.absenceDays), "- ${formatAmount(salary.absenceAmount)}", red)
        row("Less: Loss of pay (LOP) leave", formatDays(salary.lopLeaveDays), "- ${formatAmount(salary.lopLeaveAmount)}", red)
        row(
            "Less: Late marks (${salary.lateMarks ?: 0} marks, ${salary.lateMarksPerUnit ?: "-"} = ${formatDays(salary.lateLopDaysPerUnit)} day)",
            formatDays(salary.lateLopDays),
            "- ${formatAmount(salary.lateAmount)}",
            red
        )
        row("Total deductions", formatDays(salary.totalLopDays), "- ${formatAmount(salary.totalDeductions)}", red, bold = true)

        y += 6f
        canvas.drawRect(MARGIN_X, y - 18f, RIGHT, y + 12f, fill(netShade))
        canvas.drawText("NET PAY", MARGIN_X + 8f, y, paint(12f, bold = true, color = green))
        canvas.drawText(formatAmount(salary.netPay), RIGHT - 8f, y, paint(13f, bold = true, color = green, align = Paint.Align.RIGHT))

        y += 44f
        canvas.drawText(
            "Per-day rate: ${formatAmount(salary.perDayRate)} (monthly gross / ${formatDays(salary.workingDaysInMonth)} working days).",
            MARGIN_X, y, paint(8.5f, color = muted)
        )
        y += 12f
        canvas.drawText("This is a system-generated payslip and does not require a signature.", MARGIN_X, y, paint(8.5f, color = muted))

        document.finishPage(page)

        // Cache directory only (shared via the existing FileProvider); replaced on every open
        val dir = File(context.cacheDir, "payslips").apply { mkdirs() }
        val safeId = (payslip.employee?.employeeId ?: "employee").replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "Payslip_${safeId}_${payslip.month}_v${payslip.version}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }
}
