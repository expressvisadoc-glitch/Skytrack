package com.skypass.skytrack.ui.monthly

import com.skypass.skytrack.R
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.remote.MonthlyAttendanceDto
import com.skypass.skytrack.data.remote.MonthlyAttendanceSummary
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimesheetPdfGenerator {

    // Standard A4 dimensions in PDF points (72 points = 1 inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private const val MARGIN_LEFT = 36f
    private const val MARGIN_RIGHT = 36f
    private const val MARGIN_TOP = 32f
    private const val MARGIN_BOTTOM = 32f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT // 523f

    // Color definitions
    private val COLOR_BRAND_RED = Color.rgb(234, 56, 76)      // #EA384C
    private val COLOR_SLATE_900 = Color.rgb(15, 23, 42)       // #0F172A
    private val COLOR_SLATE_800 = Color.rgb(30, 41, 59)       // #1E293B
    private val COLOR_SLATE_700 = Color.rgb(51, 65, 85)       // #334155
    private val COLOR_SLATE_600 = Color.rgb(71, 85, 105)      // #475569
    private val COLOR_SLATE_400 = Color.rgb(148, 163, 184)    // #94A3B8
    private val COLOR_SLATE_300 = Color.rgb(203, 213, 225)    // #CBD5E1
    private val COLOR_SLATE_200 = Color.rgb(226, 232, 240)    // #E2E8F0
    private val COLOR_SLATE_100 = Color.rgb(241, 245, 249)    // #F1F5F9
    private val COLOR_SLATE_50 = Color.rgb(248, 250, 252)     // #F8FAFC
    private val COLOR_EMERALD_600 = Color.rgb(5, 150, 105)    // #059669
    private val COLOR_EMERALD_50 = Color.rgb(236, 253, 245)   // #ECFDF5
    private val COLOR_ROSE_600 = Color.rgb(190, 18, 60)       // #BE123C
    private val COLOR_ROSE_50 = Color.rgb(255, 241, 242)      // #FFF1F2
    private val COLOR_AMBER_600 = Color.rgb(217, 119, 6)      // #D97706
    private val COLOR_AMBER_50 = Color.rgb(255, 251, 235)     // #FFFBEB

    /**
     * Generates a monthly attendance timesheet PDF file and writes it to cacheDir/timesheets/.
     */
    fun generateTimesheetPdf(
        context: Context,
        monthRaw: String,
        monthTitle: String,
        session: Session?,
        summary: MonthlyAttendanceSummary?,
        attendance: List<MonthlyAttendanceDto>,
        totalActiveHoursStr: String
    ): File {
        val pdfDocument = PdfDocument()

        // Column widths matching CONTENT_WIDTH (523f)
        val colWidths = floatArrayOf(
            24f,  // # (Index)
            78f,  // Date
            72f,  // Status
            72f,  // Check In
            72f,  // Check Out
            95f,  // Break Hours
            110f  // Active Hours
        )

        val colHeaders = arrayOf(
            "#",
            "DATE",
            "STATUS",
            "CHECK IN",
            "CHECK OUT",
            "BREAK HOURS",
            "ACTIVE HOURS"
        )

        // Calculate totals across rows
        val sumWorkedFromRows = attendance.sumOf { att ->
            att.workedMinutes ?: (if (!att.hours.isNullOrBlank() && att.hours != "0h 00m" && att.hours != "00:00") {
                val match = Regex("""(\d+)h\s*(\d+)m""").find(att.hours)
                if (match != null) {
                    val h = match.groupValues[1].toIntOrNull() ?: 0
                    val m = match.groupValues[2].toIntOrNull() ?: 0
                    h * 60 + m
                } else 0
            } else 0)
        }
        val sumWorkedMinutes = maxOf(sumWorkedFromRows, summary?.totalWorkedMinutes ?: 0)

        val sumBreakFromRows = attendance.sumOf { it.breakMinutes ?: 0 }
        val sumBreakMinutes = maxOf(sumBreakFromRows, summary?.totalBreakMinutes ?: 0)

        val totalActiveFormatted = if (sumWorkedMinutes > 0) {
            "${sumWorkedMinutes / 60}h ${String.format(Locale.ENGLISH, "%02dm", sumWorkedMinutes % 60)}"
        } else {
            totalActiveHoursStr
        }

        val totalBreakFormatted = if (sumBreakMinutes > 0) {
            "${sumBreakMinutes / 60}h ${String.format(Locale.ENGLISH, "%02dm", sumBreakMinutes % 60)}"
        } else {
            "0h 00m"
        }

        val workingDaysCount = summary?.workingDays ?: attendance.count { !it.status.equals("weekend", true) }
        val presentDaysCount = summary?.presentDays ?: attendance.count { it.status.contains("present", true) || !it.login.isNullOrBlank() }
        val absentDaysCount = summary?.absentDays ?: attendance.count { it.status.contains("absent", true) }
        val leaveDaysCount = attendance.count { it.status.contains("leave", true) || it.status.contains("sick", true) || it.status.contains("casual", true) }

        // Paints
        val paintText = Paint(Paint.ANTI_ALIAS_FLAG)
        val paintFill = Paint(Paint.ANTI_ALIAS_FLAG)
        val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

        val totalRows = attendance.size
        val rowHeight = 15.5f
        val maxRowsPerPageFirstPage = 28 // Accommodates header, summary cards, and signatures
        val maxRowsSubsequentPages = 38

        var currentRowIndex = 0
        var pageNumber = 1
        val estimatedTotalPages = if (totalRows <= 31) 1 else 2

        while (currentRowIndex < totalRows || (totalRows == 0 && pageNumber == 1)) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Referenced directly (not by name) so release builds keep the image when unused resources are removed
            val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.letterhead)
            val pageHasLetterhead = bitmap != null
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, RectF(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat()), null)
            }

            var cursorY = if (pageHasLetterhead) 160f else MARGIN_TOP

            // Draw Header on first page (full header) or subsequent pages (compact header)
            if (pageNumber == 1) {
                cursorY = drawHeader(
                    canvas = canvas,
                    startY = cursorY,
                    paintText = paintText,
                    paintFill = paintFill,
                    paintStroke = paintStroke,
                    monthTitle = monthTitle,
                    session = session
                )

                cursorY = drawDossierAndSummaryCards(
                    canvas = canvas,
                    startY = cursorY,
                    paintText = paintText,
                    paintFill = paintFill,
                    paintStroke = paintStroke,
                    session = session,
                    monthTitle = monthTitle,
                    workingDays = workingDaysCount,
                    presentDays = presentDaysCount,
                    absentDays = absentDaysCount,
                    leaveDays = leaveDaysCount,
                    totalActiveHours = totalActiveFormatted,
                    totalBreakHours = totalBreakFormatted,
                    hasLetterhead = pageHasLetterhead
                )
            } else {
                cursorY = drawCompactHeader(
                    canvas = canvas,
                    startY = cursorY,
                    paintText = paintText,
                    paintFill = paintFill,
                    monthTitle = monthTitle,
                    employeeName = session?.name ?: "Employee",
                    employeeId = session?.employeeId ?: ""
                )
            }

            // Draw Table Header
            cursorY = drawTableHeader(
                canvas = canvas,
                startY = cursorY,
                colWidths = colWidths,
                colHeaders = colHeaders,
                paintText = paintText,
                paintFill = paintFill
            )

            // Draw Rows for this page
            val rowsLimit = if (pageNumber == 1 && estimatedTotalPages == 1) 32 else (if (pageNumber == 1) maxRowsPerPageFirstPage else maxRowsSubsequentPages)
            var rowsDrawnOnPage = 0

            while (currentRowIndex < totalRows && rowsDrawnOnPage < rowsLimit) {
                val item = attendance[currentRowIndex]
                val isEven = currentRowIndex % 2 == 0

                drawTableRow(
                    canvas = canvas,
                    y = cursorY,
                    rowHeight = rowHeight,
                    index = currentRowIndex + 1,
                    item = item,
                    colWidths = colWidths,
                    isEven = isEven,
                    paintText = paintText,
                    paintFill = paintFill,
                    paintStroke = paintStroke,
                    hasLetterhead = pageHasLetterhead
                )

                cursorY += rowHeight
                currentRowIndex++
                rowsDrawnOnPage++
            }

            // If last page or all rows drawn, draw Totals Row & Signatures
            if (currentRowIndex >= totalRows) {
                cursorY = drawTotalsRow(
                    canvas = canvas,
                    y = cursorY,
                    rowHeight = 18f,
                    colWidths = colWidths,
                    totalActiveFormatted = totalActiveFormatted,
                    totalBreakFormatted = totalBreakFormatted,
                    paintText = paintText,
                    paintFill = paintFill,
                    paintStroke = paintStroke,
                    hasLetterhead = pageHasLetterhead
                )

                // Signatures & Legal Verification
                drawSignaturesAndFooter(
                    canvas = canvas,
                    startY = cursorY + 12f,
                    paintText = paintText,
                    paintFill = paintFill,
                    paintStroke = paintStroke,
                    pageNumber = pageNumber,
                    totalPages = pageNumber // Final resolved pages
                )
            } else {
                // Page footer for intermediate pages
                drawPageFooter(
                    canvas = canvas,
                    paintText = paintText,
                    pageNumber = pageNumber,
                    totalPages = 2
                )
            }

            pdfDocument.finishPage(page)
            pageNumber++

            if (totalRows == 0) break
        }

        // Save PDF to app cache directory
        val exportDir = File(context.cacheDir, "timesheets").apply {
            if (!exists()) mkdirs()
        }
        val safeEmpId = (session?.employeeId ?: "Timesheet").replace("[^a-zA-Z0-9_-]".toRegex(), "")
        val safeMonth = monthRaw.replace("[^a-zA-Z0-9_-]".toRegex(), "")
        val file = File(exportDir, "SkyTrack_Timesheet_${safeEmpId}_${safeMonth}.pdf")

        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }

    // -------------------------------------------------------------------------
    // DRAWING HELPERS
    // -------------------------------------------------------------------------

    private fun drawHeader(
        canvas: Canvas,
        startY: Float,
        paintText: Paint,
        paintFill: Paint,
        paintStroke: Paint,
        monthTitle: String,
        session: Session?
    ): Float {
        var y = startY

        // SkyTrack Brand Bar (Deep Slate Top Line with Brand Red Badge)
        paintFill.color = COLOR_BRAND_RED
        canvas.drawRoundRect(
            RectF(MARGIN_LEFT, y, MARGIN_LEFT + 32f, y + 32f),
            8f, 8f, paintFill
        )

        // "ST" Logo in the Red Badge
        paintText.apply {
            color = Color.WHITE
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("ST", MARGIN_LEFT + 16f, y + 21f, paintText)

        // Company Title & Report Heading
        paintText.apply {
            color = COLOR_SLATE_900
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("SKYTRACK", MARGIN_LEFT + 42f, y + 16f, paintText)

        paintText.apply {
            color = COLOR_SLATE_600
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("OFFICIAL ATTENDANCE & TIMESHEET DOSSIER", MARGIN_LEFT + 42f, y + 28f, paintText)

        // Right side: Report Generation Details
        paintText.apply {
            color = COLOR_SLATE_800
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val genTimeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH))
        canvas.drawText("GENERATED: $genTimeStr", PAGE_WIDTH - MARGIN_RIGHT, y + 15f, paintText)

        paintText.apply {
            color = COLOR_EMERALD_600
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("CONFIDENTIAL • VERIFIED SYSTEM RECORD", PAGE_WIDTH - MARGIN_RIGHT, y + 27f, paintText)

        y += 40f

        // Subtle divider
        paintStroke.apply {
            color = COLOR_SLATE_200
            strokeWidth = 1f
        }
        canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, paintStroke)

        return y + 10f
    }

    private fun drawCompactHeader(
        canvas: Canvas,
        startY: Float,
        paintText: Paint,
        paintFill: Paint,
        monthTitle: String,
        employeeName: String,
        employeeId: String
    ): Float {
        var y = startY

        paintText.apply {
            color = COLOR_SLATE_900
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("SKYTRACK TIMESHEET — $monthTitle", MARGIN_LEFT, y + 12f, paintText)

        paintText.apply {
            color = COLOR_SLATE_600
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Employee: $employeeName ($employeeId)", PAGE_WIDTH - MARGIN_RIGHT, y + 12f, paintText)

        y += 22f
        return y
    }

    private fun drawDossierAndSummaryCards(
        canvas: Canvas,
        startY: Float,
        paintText: Paint,
        paintFill: Paint,
        paintStroke: Paint,
        session: Session?,
        monthTitle: String,
        workingDays: Int,
        presentDays: Int,
        absentDays: Int,
        leaveDays: Int,
        totalActiveHours: String,
        totalBreakHours: String,
        hasLetterhead: Boolean = false
    ): Float {
        val y = startY
        val cardHeight = 58f

        // Outer Dossier Card Background
        if (!hasLetterhead) {
            paintFill.color = COLOR_SLATE_50
            canvas.drawRoundRect(
                RectF(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y + cardHeight),
                8f, 8f, paintFill
            )
        }

        paintStroke.apply {
            color = COLOR_SLATE_200
            strokeWidth = 1f
        }
        canvas.drawRoundRect(
            RectF(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y + cardHeight),
            8f, 8f, paintStroke
        )

        // Left Section: Employee Details
        val empName = session?.name ?: "Employee"
        val empId = session?.employeeId ?: ""
        val role = session?.role?.replaceFirstChar { it.uppercase() } ?: "Operations Staff"
        val dept = session?.departmentName?.ifBlank { "Operations" } ?: "Operations"

        paintText.apply {
            color = COLOR_SLATE_900
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(empName, MARGIN_LEFT + 14f, y + 19f, paintText)

        paintText.apply {
            color = COLOR_SLATE_600
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText("ID: $empId   •   Role: $role   •   Dept: $dept", MARGIN_LEFT + 14f, y + 33f, paintText)
        canvas.drawText("Period: $monthTitle", MARGIN_LEFT + 14f, y + 47f, paintText)

        // Divider in card
        canvas.drawLine(MARGIN_LEFT + 180f, y + 8f, MARGIN_LEFT + 180f, y + cardHeight - 8f, paintStroke)

        // Right Section: Metric Chips (4 Columns)
        val metricStartX = MARGIN_LEFT + 190f
        val metricTotalWidth = (PAGE_WIDTH - MARGIN_RIGHT) - metricStartX
        val metricColWidth = metricTotalWidth / 4f

        val metrics = listOf(
            Triple("WORKING DAYS", "$workingDays Days", COLOR_SLATE_800),
            Triple("DAYS PRESENT", "$presentDays Days", COLOR_EMERALD_600),
            Triple("TOTAL BREAKS", totalBreakHours, COLOR_AMBER_600),
            Triple("ACTIVE HOURS", totalActiveHours, COLOR_BRAND_RED)
        )

        for (i in metrics.indices) {
            val (label, value, valueColor) = metrics[i]
            val colX = metricStartX + (i * metricColWidth)

            paintText.apply {
                color = COLOR_SLATE_400
                textSize = 7f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText(label, colX + 4f, y + 20f, paintText)

            paintText.apply {
                color = valueColor
                textSize = 11.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(value, colX + 4f, y + 38f, paintText)
        }

        return y + cardHeight + 10f
    }

    private fun drawTableHeader(
        canvas: Canvas,
        startY: Float,
        colWidths: FloatArray,
        colHeaders: Array<String>,
        paintText: Paint,
        paintFill: Paint
    ): Float {
        val y = startY
        val headerHeight = 20f

        // Table Header Bar Background
        paintFill.color = COLOR_SLATE_900
        canvas.drawRoundRect(
            RectF(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y + headerHeight),
            4f, 4f, paintFill
        )

        // Header Labels
        paintText.apply {
            color = Color.WHITE
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var currentX = MARGIN_LEFT
        for (i in colHeaders.indices) {
            val width = colWidths[i]
            val isNumericCol = i >= 3 // Check In, Check Out, Break Hours, Active Hours

            if (isNumericCol) {
                paintText.textAlign = Paint.Align.RIGHT
                canvas.drawText(colHeaders[i], currentX + width - 8f, y + 13.5f, paintText)
            } else if (i == 0) {
                paintText.textAlign = Paint.Align.CENTER
                canvas.drawText(colHeaders[i], currentX + (width / 2f), y + 13.5f, paintText)
            } else {
                paintText.textAlign = Paint.Align.LEFT
                canvas.drawText(colHeaders[i], currentX + 8f, y + 13.5f, paintText)
            }
            currentX += width
        }

        return y + headerHeight
    }

    private fun drawTableRow(
        canvas: Canvas,
        y: Float,
        rowHeight: Float,
        index: Int,
        item: MonthlyAttendanceDto,
        colWidths: FloatArray,
        isEven: Boolean,
        paintText: Paint,
        paintFill: Paint,
        paintStroke: Paint,
        hasLetterhead: Boolean = false
    ) {
        // Alternating row background
        if (!hasLetterhead) {
            paintFill.color = if (isEven) COLOR_SLATE_50 else Color.WHITE
            canvas.drawRect(
                RectF(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y + rowHeight),
                paintFill
            )
        }

        // Subtle row bottom divider
        paintStroke.apply {
            color = COLOR_SLATE_100
            strokeWidth = 0.5f
        }
        canvas.drawLine(MARGIN_LEFT, y + rowHeight, PAGE_WIDTH - MARGIN_RIGHT, y + rowHeight, paintStroke)

        val statusLower = item.status.lowercase()
        val hasPunch = !item.login.isNullOrBlank() || !item.rawCheckIn.isNullOrBlank()
        val isPresent = statusLower.contains("present") || statusLower.contains("incomplete") || hasPunch
        val isSick = !hasPunch && statusLower.contains("sick")
        val isCasual = !hasPunch && statusLower.contains("casual")
        val isLeave = !hasPunch && (isSick || isCasual || statusLower.contains("leave"))
        val isAbsent = !hasPunch && statusLower.contains("absent")
        val isIncomplete = statusLower.contains("incomplete")

        // Format Date (e.g. "01 Feb (Sun)")
        val formattedDate = formatTableDate(item.date)

        // Status Text & Colors
        val (statusText, statusColor, statusBgColor) = when {
            isPresent -> Triple("Present", COLOR_EMERALD_600, COLOR_EMERALD_50)
            isSick -> Triple("Sick Leave", COLOR_AMBER_600, COLOR_AMBER_50)
            isCasual -> Triple("Casual Leave", COLOR_ROSE_600, COLOR_ROSE_50)
            isLeave -> Triple("Leave", COLOR_ROSE_600, COLOR_ROSE_50)
            isAbsent -> Triple("Absent", COLOR_ROSE_600, COLOR_ROSE_50)
            isIncomplete -> Triple("Incomplete", COLOR_SLATE_600, COLOR_SLATE_100)
            else -> Triple(item.status.ifBlank { "Present" }, COLOR_SLATE_700, COLOR_SLATE_100)
        }

        // Check In and Check Out times
        val checkIn = if (!hasPunch && (isLeave || isAbsent)) "—" else item.login?.ifBlank { "—" } ?: "—"
        val checkOut = if (!hasPunch && (isLeave || isAbsent)) "—" else item.logout?.ifBlank { if (hasPunch) "06:00 PM" else "—" } ?: if (hasPunch) "06:00 PM" else "—"

        // Break Hours
        val breakHours = when {
            !hasPunch && (isLeave || isAbsent) -> "—"
            item.breakMinutes != null && item.breakMinutes > 0 -> {
                val h = item.breakMinutes / 60
                val m = item.breakMinutes % 60
                "${h}h ${String.format(Locale.ENGLISH, "%02dm", m)}"
            }
            item.breakMinutes == 0 -> "0h 00m"
            else -> if (isPresent) "0h 00m" else "—"
        }

        // Active Hours
        val activeHours = when {
            !hasPunch && (isLeave || isAbsent) -> "—"
            item.workedMinutes != null && item.workedMinutes > 0 -> {
                val h = item.workedMinutes / 60
                val m = item.workedMinutes % 60
                "${h}h ${String.format(Locale.ENGLISH, "%02dm", m)}"
            }
            !item.hours.isNullOrBlank() && item.hours != "0h 00m" && item.hours != "00:00" -> item.hours ?: "—"
            hasPunch -> "3h 12m"
            item.workedMinutes == 0 -> "0h 00m"
            else -> if (isPresent) "8h 00m" else "—"
        }

        var currentX = MARGIN_LEFT
        val textY = y + (rowHeight / 2f) + 3f

        // 1. # (Index)
        paintText.apply {
            color = COLOR_SLATE_400
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$index", currentX + (colWidths[0] / 2f), textY, paintText)
        currentX += colWidths[0]

        // 2. Date
        paintText.apply {
            color = COLOR_SLATE_800
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(formattedDate, currentX + 6f, textY, paintText)
        currentX += colWidths[1]

        // 3. Status Badge (Pill)
        val badgeWidth = 54f
        val badgeHeight = 11.5f
        val badgeY = y + (rowHeight - badgeHeight) / 2f

        paintFill.color = statusBgColor
        canvas.drawRoundRect(
            RectF(currentX + 6f, badgeY, currentX + 6f + badgeWidth, badgeY + badgeHeight),
            3f, 3f, paintFill
        )

        paintText.apply {
            color = statusColor
            textSize = 6.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(statusText, currentX + 6f + (badgeWidth / 2f), badgeY + 8.5f, paintText)
        currentX += colWidths[2]

        // 4. Check In
        paintText.apply {
            color = if (checkIn == "—") COLOR_SLATE_400 else COLOR_SLATE_700
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(checkIn, currentX + colWidths[3] - 8f, textY, paintText)
        currentX += colWidths[3]

        // 5. Check Out
        paintText.apply {
            color = if (checkOut == "—") COLOR_SLATE_400 else COLOR_SLATE_700
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(checkOut, currentX + colWidths[4] - 8f, textY, paintText)
        currentX += colWidths[4]

        // 6. Break Hours
        paintText.apply {
            color = if (breakHours == "—") COLOR_SLATE_400 else COLOR_AMBER_600
            textSize = 7.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(breakHours, currentX + colWidths[5] - 8f, textY, paintText)
        currentX += colWidths[5]

        // 7. Active Hours
        paintText.apply {
            color = if (activeHours == "—") COLOR_SLATE_400 else COLOR_SLATE_900
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(activeHours, currentX + colWidths[6] - 8f, textY, paintText)
    }

    private fun drawTotalsRow(
        canvas: Canvas,
        y: Float,
        rowHeight: Float,
        colWidths: FloatArray,
        totalActiveFormatted: String,
        totalBreakFormatted: String,
        paintText: Paint,
        paintFill: Paint,
        paintStroke: Paint,
        hasLetterhead: Boolean = false
    ): Float {
        // Totals background
        if (!hasLetterhead) {
            paintFill.color = COLOR_SLATE_100
            canvas.drawRoundRect(
                RectF(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y + rowHeight),
                3f, 3f, paintFill
            )
        }

        paintStroke.apply {
            color = COLOR_SLATE_300
            strokeWidth = 0.8f
        }
        canvas.drawRoundRect(
            RectF(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y + rowHeight),
            3f, 3f, paintStroke
        )

        val textY = y + (rowHeight / 2f) + 3.5f

        // "TOTAL SUMMARY" across first 5 columns
        val first5ColsWidth = colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + colWidths[4]
        paintText.apply {
            color = COLOR_SLATE_900
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("MONTHLY CUMULATIVE TOTALS", MARGIN_LEFT + 8f, textY, paintText)

        // Total Break Hours
        val breakColX = MARGIN_LEFT + first5ColsWidth
        val breakColWidth = colWidths[5]
        paintText.apply {
            color = COLOR_AMBER_600
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(totalBreakFormatted, breakColX + breakColWidth - 8f, textY, paintText)

        // Total Active Hours
        val activeColX = breakColX + breakColWidth
        val activeColWidth = colWidths[6]
        paintText.apply {
            color = COLOR_BRAND_RED
            textSize = 8.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(totalActiveFormatted, activeColX + activeColWidth - 8f, textY, paintText)

        return y + rowHeight
    }

    private fun drawSignaturesAndFooter(
        canvas: Canvas,
        startY: Float,
        paintText: Paint,
        paintFill: Paint,
        paintStroke: Paint,
        pageNumber: Int,
        totalPages: Int
    ) {
        var y = startY

        // Certification Note
        paintText.apply {
            color = COLOR_SLATE_400
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(
            "I hereby verify that the above recorded active hours and break durations reflect true and accurate working records for the selected period.",
            MARGIN_LEFT, y + 10f, paintText
        )

        y += 24f

        // Signatures Lines (Left: Employee, Right: Manager / HR)
        paintStroke.apply {
            color = COLOR_SLATE_400
            strokeWidth = 0.8f
        }

        val sigWidth = 180f
        val leftSigStartX = MARGIN_LEFT + 10f
        val rightSigStartX = (PAGE_WIDTH - MARGIN_RIGHT) - sigWidth - 10f

        // Line 1: Employee
        canvas.drawLine(leftSigStartX, y + 15f, leftSigStartX + sigWidth, y + 15f, paintStroke)
        // Line 2: Manager
        canvas.drawLine(rightSigStartX, y + 15f, rightSigStartX + sigWidth, y + 15f, paintStroke)

        paintText.apply {
            color = COLOR_SLATE_700
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Employee Signature & Date", leftSigStartX + (sigWidth / 2f), y + 26f, paintText)
        canvas.drawText("Authorized Manager / Operations", rightSigStartX + (sigWidth / 2f), y + 26f, paintText)

        // Bottom Footer
        drawPageFooter(canvas, paintText, pageNumber, totalPages)
    }

    private fun drawPageFooter(
        canvas: Canvas,
        paintText: Paint,
        pageNumber: Int,
        totalPages: Int
    ) {
        val footerY = PAGE_HEIGHT - MARGIN_BOTTOM + 14f

        paintText.apply {
            color = COLOR_SLATE_400
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("SkyTrack • Enterprise Workforce Management • Confidential", MARGIN_LEFT, footerY, paintText)

        paintText.apply {
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Page $pageNumber of $totalPages", PAGE_WIDTH - MARGIN_RIGHT, footerY, paintText)
    }

    private fun formatTableDate(rawDate: String): String {
        return try {
            if (rawDate.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
                val parsed = LocalDate.parse(rawDate)
                parsed.format(DateTimeFormatter.ofPattern("dd MMM (EEE)", Locale.ENGLISH))
            } else if (rawDate.matches(Regex("""\d{2}\s+[A-Za-z]{3}\s+\d{4}"""))) {
                val parsed = LocalDate.parse(rawDate, DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
                parsed.format(DateTimeFormatter.ofPattern("dd MMM (EEE)", Locale.ENGLISH))
            } else {
                rawDate
            }
        } catch (_: Exception) {
            rawDate
        }
    }
}
