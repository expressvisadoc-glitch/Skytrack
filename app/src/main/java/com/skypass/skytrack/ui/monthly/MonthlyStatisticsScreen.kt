package com.skypass.skytrack.ui.monthly

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.remote.MonthlyAttendanceDto
import com.skypass.skytrack.data.remote.MonthlyAttendanceSummary
import com.skypass.skytrack.ui.components.SkyTrackBottomNavigationDock
import com.skypass.skytrack.ui.components.SkyTrackNavTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// Stitch Color Palette
private val BrandRed = Color(0xFFEA384C)
private val BrandRedLight = Color(0xFFFEF2F2)
private val SurfaceBase = Color(0xFFF8FAFC)
private val SurfaceCard = Color(0xFFFFFFFF)
private val Slate50 = Color(0xFFF8FAFC)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate300 = Color(0xFFCBD5E1)
private val Slate400 = Color(0xFF94A3B8)
private val Slate500 = Color(0xFF64748B)
private val Slate600 = Color(0xFF475569)
private val Slate700 = Color(0xFF334155)
private val Slate800 = Color(0xFF1E293B)
private val Slate900 = Color(0xFF0F172A)
private val Emerald50 = Color(0xFFECFDF5)
private val Emerald500 = Color(0xFF10B981)
private val Emerald600 = Color(0xFF059669)
private val Emerald700 = Color(0xFF047857)
private val Rose50 = Color(0xFFFFF1F2)
private val Rose100 = Color(0xFFFFE4E6)
private val Rose700 = Color(0xFFBE123C)
private val Purple50 = Color(0xFFFAF5FF)
private val Purple600 = Color(0xFF9333EA)
private val Purple700 = Color(0xFF7E22CE)
private val Amber50 = Color(0xFFFFFBEB)
private val Amber600 = Color(0xFFD97706)
private val Amber700 = Color(0xFFB45309)
private val Blue50 = Color(0xFFEFF6FF)
private val Blue600 = Color(0xFF2563EB)

@Composable
fun MonthlyStatisticsScreen(
    viewModel: MonthlyStatisticsViewModel,
    session: Session? = null,
    onBack: () -> Unit,
    onOpenLeave: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0: All, 1: Present, 2: Leaves

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pdfFileToShow by remember { mutableStateOf<File?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }

    val monthTitle = remember(state.summary?.month, state.selectedMonthRaw) {
        val raw = state.summary?.month ?: state.selectedMonthRaw
        if (raw.isBlank()) {
            YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        } else {
            try {
                if (raw.length == 7) {
                    YearMonth.parse(raw).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
                } else {
                    raw
                }
            } catch (_: Exception) {
                raw
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .padding(bottom = 110.dp) // Room for floating bottom nav
        ) {
            // Header with Month Switcher Controls
            MonthlyTopHeader(
                summary = state.summary,
                selectedMonthRaw = state.selectedMonthRaw,
                onPreviousMonth = viewModel::loadPreviousMonth,
                onNextMonth = viewModel::loadNextMonth
            )

            Spacer(modifier = Modifier.height(16.dp))

            // User Greeting Section
            MonthlyUserGreeting(
                name = session?.name ?: "Employee",
                employeeId = session?.employeeId ?: ""
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Central Circular Dial (Cumulative Work Hours)
            CentralCircularDial(summary = state.summary, uiState = state)

            Spacer(modifier = Modifier.height(20.dp))

            // Trio Metric Cards (Present, Leaves, Daily Avg)
            TrioMetricCardsGrid(
                summary = state.summary,
                attendanceList = state.attendance
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Action Banner (Timesheet Export)
            TimesheetExportCard(
                isExporting = isExportingPdf,
                targetHours = (state.summary?.workingDays ?: 0) * 8,
                onExportClick = {
                    if (!isExportingPdf) {
                        coroutineScope.launch {
                            isExportingPdf = true
                            try {
                                val rawMonth = state.summary?.month ?: YearMonth.now().toString()
                                val file = withContext(Dispatchers.IO) {
                                    TimesheetPdfGenerator.generateTimesheetPdf(
                                        context = context,
                                        monthRaw = rawMonth,
                                        monthTitle = monthTitle,
                                        session = session,
                                        summary = state.summary,
                                        attendance = state.attendance,
                                        totalActiveHoursStr = state.totalActiveHoursStr
                                    )
                                }
                                pdfFileToShow = file
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Error generating PDF: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                isExportingPdf = false
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Daily Attendance Log with Filter Tabs
            DailyAttendanceLogSection(
                attendanceList = state.attendance,
                selectedFilterIndex = selectedFilterIndex,
                onFilterSelected = { selectedFilterIndex = it }
            )
        }

        // Floating Bottom Navigation Dock (Active on Stats)
        SkyTrackBottomNavigationDock(
            currentTab = SkyTrackNavTab.STATISTICS,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            onHomeClick = onBack,
            onLeavesClick = onOpenLeave,
            onAnalyticsClick = { /* Already on Stats */ },
            onProfileClick = onOpenProfile
        )

        // SkyTrack Global Loading Dialog
        com.skypass.skytrack.ui.components.SkyTrackLoadingOverlay(
            visible = state.isLoading
        )

        // Monthly Timesheet PDF In-App Preview Dialog
        if (pdfFileToShow != null) {
            TimesheetPdfViewerDialog(
                pdfFile = pdfFileToShow!!,
                monthTitle = monthTitle,
                employeeName = session?.name ?: "Employee",
                onDismiss = { pdfFileToShow = null }
            )
        }
    }
}

// -------------------------------------------------------------
// TOP HEADER WITH MONTH SWITCHER
// -------------------------------------------------------------
@Composable
private fun MonthlyTopHeader(
    summary: MonthlyAttendanceSummary?,
    selectedMonthRaw: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val monthTitle = remember(summary?.month, selectedMonthRaw) {
        val raw = summary?.month ?: selectedMonthRaw
        if (raw.isBlank()) {
            YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        } else {
            try {
                if (raw.length == 7) {
                    YearMonth.parse(raw).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
                } else {
                    raw
                }
            } catch (_: Exception) {
                raw
            }
        }
    }

    val workingDays = summary?.workingDays ?: 0
    val targetHours = workingDays * 8

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Calendar Icon Card
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black.copy(alpha = 0.04f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard)
                    .border(1.dp, Slate100, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = "Month",
                    tint = BrandRed,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "STATISTICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate400,
                        letterSpacing = 0.8.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Emerald500)
                    )
                }

                Text(
                    text = monthTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    letterSpacing = (-0.4).sp
                )

                val displayTargetHours = summary?.targetHoursFormatted ?: "${targetHours}h"
//                Text(
//                    text = "$workingDays Working Days • $displayTargetHours Target",
//                    fontSize = 11.sp,
//                    fontWeight = FontWeight.Medium,
//                    color = Slate500
//                )
            }
        }

        // Month Navigation Buttons (Chevron Cards)
        Row(
            modifier = Modifier
                .shadow(1.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCard)
                .border(1.dp, Slate100, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                onClick = onPreviousMonth,
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = SurfaceCard
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = "Previous Month",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Surface(
                onClick = onNextMonth,
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp),
                color = SurfaceCard
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Next Month",
                        tint = Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// USER GREETING SECTION
// -------------------------------------------------------------
@Composable
private fun MonthlyUserGreeting(
    name: String,
    employeeId: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Good day,",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Slate400
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Slate900,
                letterSpacing = (-0.3).sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Slate100)
                    .border(1.dp, Slate200, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = employeeId,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500
                )
            }
        }
    }
}

// -------------------------------------------------------------
// CENTRAL CIRCULAR DIAL (HERO GAUGE)
// -------------------------------------------------------------
@Composable
private fun CentralCircularDial(summary: MonthlyAttendanceSummary?, uiState: MonthlyStatisticsUiState) {
    val rawTotal = uiState.totalActiveHoursStr.trim()
    val totalHoursStr = if (rawTotal.isBlank() || rawTotal == "00:00") "0h 00m" else rawTotal
    val defaultTargetHours = remember(summary?.workingDays) {
        val days = summary?.workingDays ?: 0
        if (days > 0) (days * 8.0) else 160.0
    }

    val targetHoursStr = uiState.actualHoursNeededStr.ifBlank { "${(summary?.workingDays ?: 0) * 8}h" }

    // Parse logged hours to calculate percentage
    val loggedHoursVal = remember(totalHoursStr) {
        try {
            val match = Regex("""(\d+)h\s*(\d+)m""").find(totalHoursStr)
            if (match != null) {
                val h = match.groupValues[1].toDoubleOrNull() ?: 0.0
                val m = match.groupValues[2].toDoubleOrNull() ?: 0.0
                h + (m / 60.0)
            } else {
                val parts = totalHoursStr.split(":")
                if (parts.size >= 2) {
                    val h = parts[0].trim().toDoubleOrNull() ?: 0.0
                    val m = parts[1].trim().toDoubleOrNull() ?: 0.0
                    h + (m / 60.0)
                } else {
                    totalHoursStr.replace(Regex("""[^\d.]"""), "").toDoubleOrNull() ?: 0.0
                }
            }
        } catch (_: Exception) {
            0.0
        }
    }

    val targetHoursVal = remember(targetHoursStr, defaultTargetHours) {
        try {
            val match = Regex("""(\d+)h\s*(\d*)m?""").find(targetHoursStr)
            if (match != null) {
                val h = match.groupValues[1].toDoubleOrNull() ?: 0.0
                val m = match.groupValues.getOrNull(2)?.toDoubleOrNull() ?: 0.0
                h + (m / 60.0)
            } else {
                val parts = targetHoursStr.split(":")
                if (parts.size >= 2) {
                    val h = parts[0].trim().toDoubleOrNull() ?: 0.0
                    val m = parts[1].trim().toDoubleOrNull() ?: 0.0
                    h + (m / 60.0)
                } else {
                    targetHoursStr.replace(Regex("""[^\d.]"""), "").toDoubleOrNull() ?: defaultTargetHours
                }
            }
        } catch (_: Exception) {
            defaultTargetHours
        }.let { if (it <= 0) defaultTargetHours else it }
    }

    val progressFraction = remember(loggedHoursVal, targetHoursVal) {
        (loggedHoursVal / targetHoursVal).coerceIn(0.0, 1.0).toFloat()
    }

    val animatedSweep by animateFloatAsState(
        targetValue = progressFraction * 360f,
        animationSpec = tween(durationMillis = 1000),
        label = "dialSweep"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Dial Canvas (256.dp)
        Box(
            modifier = Modifier
                .size(256.dp)
                .shadow(elevation = 14.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.05f))
                .clip(CircleShape)
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = 9.dp.toPx()
                val radius = (size.minDimension / 2f) - (strokePx / 2f) - 6.dp.toPx()
                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                // Background Track Ring
                drawCircle(
                    color = Slate100,
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = strokePx)
                )

                // Dashed Inner Decorative Ring
                val dashedRadius = radius - 14.dp.toPx()
                drawCircle(
                    color = Slate200,
                    radius = dashedRadius,
                    center = centerOffset,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )

                // Active Progress Arc (starting at top = -90 degrees)
                drawArc(
                    color = BrandRed,
                    startAngle = -90f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // Dial Center Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "TOTAL ACTIVE HOURS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    letterSpacing = 0.8.sp
                )

                Text(
                    text = totalHoursStr,
                    fontSize = if (totalHoursStr.length > 7) 32.sp else 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate900,
                    letterSpacing = (-1.0).sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Remaining Quota Capsule Badge
//                Surface(
//                    shape = CircleShape,
//                    color = Rose50,
//                    border = BorderStroke(1.dp, Rose100),
//                    modifier = Modifier.padding(top = 4.dp)
//                ) {
//                    Row(
//                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.spacedBy(4.dp)
//                    ) {
//                        Icon(
//                            imageVector = Icons.Rounded.AccessTime,
//                            contentDescription = "Quota",
//                            tint = BrandRed,
//                            modifier = Modifier.size(13.dp)
//                        )
//                        Text(
//                            text = "11h 30m left in quota",
//                            fontSize = 11.sp,
//                            fontWeight = FontWeight.SemiBold,
//                            color = BrandRed
//                        )
//                    }
//                }

                // Actual Hours Needed Chip
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = "Target",
                        tint = Emerald600,
                        modifier = Modifier.size(12.dp)
                    )
                    val percentDone = (progressFraction * 100).toInt()
                    Text(
                        text = "${uiState.actualHoursNeededStr} Target • $percentDone% Done",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald600
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TRIO METRIC CARDS GRID
// -------------------------------------------------------------
@Composable
private fun TrioMetricCardsGrid(
    summary: MonthlyAttendanceSummary?,
    attendanceList: List<MonthlyAttendanceDto>
) {
    val casualCount = remember(attendanceList) {
        attendanceList.count { it.status.contains("casual", ignoreCase = true) }
    }
    val sickCount = remember(attendanceList) {
        attendanceList.count { it.status.contains("sick", ignoreCase = true) }
    }
    val leaveCount = remember(attendanceList, summary) {
        summary?.leaveDays ?: attendanceList.count {
            val s = it.status.lowercase()
            val hasPunch = !it.login.isNullOrBlank() || !it.rawCheckIn.isNullOrBlank()
            !hasPunch && (s.contains("leave") || s.contains("sick") || s.contains("casual"))
        }
    }
    val absentDays = remember(attendanceList, summary) {
        summary?.absentDays ?: attendanceList.count { it.status.equals("absent", ignoreCase = true) }
    }
    val presentDays = remember(attendanceList, summary) {
        summary?.presentDays?.takeIf { it > 0 } ?: attendanceList.count {
            val s = it.status.lowercase()
            val hasPunch = !it.login.isNullOrBlank() || !it.rawCheckIn.isNullOrBlank()
            s.contains("present") || s.contains("working") || s.contains("incomplete") || hasPunch
        }
    }
    val workingDays = remember(attendanceList, summary) {
        (summary?.workingDays ?: attendanceList.count {
            val s = it.status.lowercase()
            !s.contains("weekend") && !s.contains("holiday")
        }).coerceAtLeast(1)
    }
    val averageHours = summary?.averageHours?.ifBlank { "0h 0m" } ?: "0h 0m"
    val presentPercent = remember(summary, presentDays, workingDays) {
        summary?.presentPercent ?: if (workingDays > 0) {
            ((presentDays.toDouble() / workingDays.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else 0
    }

    val leaveDisplayDays = if (leaveCount > 0) leaveCount else absentDays
    val leaveSubtitle = if (leaveCount > 0) "Leaves" else "Leaves / Absent"
    val leaveBadgeText = when {
        casualCount > 0 && sickCount > 0 -> "$casualCount Cas • $sickCount Sick"
        casualCount > 0 -> "$casualCount Casual"
        sickCount > 0 -> "$sickCount Sick"
        absentDays > 0 -> "$absentDays Absent"
        else -> "0 Taken"
    }

    val avgBadgeText = when {
        !summary?.differenceFormatted.isNullOrBlank() && summary?.differenceFormatted != "0h 0m" && summary?.differenceFormatted != "00:00" ->
            "${summary.differenceFormatted} OT"
        !summary?.overtimeFormatted.isNullOrBlank() && summary?.overtimeFormatted != "0h 0m" && summary?.overtimeFormatted != "00:00" ->
            "+${summary.overtimeFormatted} OT"
        !summary?.totalBreakFormatted.isNullOrBlank() && summary?.totalBreakFormatted != "0h 0m" && summary?.totalBreakFormatted != "00:00" ->
            "Break: ${summary.totalBreakFormatted}"
        else -> "On Target"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Metric 1: Present
        TrioCard(
            icon = Icons.Rounded.CheckCircle,
            iconTint = Emerald600,
            iconBg = Emerald50,
            value = "$presentDays Days",
            subtitle = "Present ($presentPercent%)",
            badgeText = "$presentPercent% Rate",
            badgeBg = Emerald50,
            badgeTextColor = Emerald700,
            modifier = Modifier.weight(1f)
        )

        // Metric 2: Leaves
        TrioCard(
            icon = Icons.Rounded.EventBusy,
            iconTint = BrandRed,
            iconBg = Rose50,
            value = "$leaveDisplayDays Days",
            subtitle = leaveSubtitle,
            badgeText = leaveBadgeText,
            badgeBg = Rose50,
            badgeTextColor = Rose700,
            modifier = Modifier.weight(1f)
        )

        // Metric 3: Daily Average
        TrioCard(
            icon = Icons.Rounded.BarChart,
            iconTint = Purple600,
            iconBg = Purple50,
            value = averageHours,
            subtitle = "Daily Avg",
            badgeText = avgBadgeText,
            badgeBg = Purple50,
            badgeTextColor = Purple700,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TrioCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    value: String,
    subtitle: String,
    badgeText: String,
    badgeBg: Color,
    badgeTextColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TIMESHEET EXPORT CARD WITH TOOLTIP
// -------------------------------------------------------------
@Composable
private fun TimesheetExportCard(
    isExporting: Boolean,
    targetHours: Int,
    onExportClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
//        // Tooltip with downward triangle pointer
//        Box(
//            modifier = Modifier
//                .shadow(2.dp, RoundedCornerShape(8.dp))
//                .clip(RoundedCornerShape(8.dp))
//                .background(Slate900)
//                .padding(horizontal = 14.dp, vertical = 6.dp)
//        ) {
//            Text(
//                text = "Target: $targetHours hrs • Export Timesheet PDF",
//                fontSize = 11.sp,
//                fontWeight = FontWeight.SemiBold,
//                color = Color.White
//            )
//        }

        // Downward Triangle Pointer
//        Canvas(modifier = Modifier.size(width = 12.dp, height = 6.dp)) {
//            val path = Path().apply {
//                moveTo(0f, 0f)
//                lineTo(size.width, 0f)
//                lineTo(size.width / 2f, size.height)
//                close()
//            }
//            drawPath(path, color = Slate900)
//        }

//        Spacer(modifier = Modifier.height(4.dp))

        // Timesheet PDF Action Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, Slate200.copy(alpha = 0.8f)),
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate100),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = "PDF",
                            tint = Slate600,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Monthly Timesheet PDF",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )
                        Text(
                            text = "Check In/Out • Break & Active Hours",
                            fontSize = 10.sp,
                            color = Slate400
                        )
                    }
                }

                Surface(
                    onClick = onExportClick,
                    enabled = !isExporting,
                    shape = RoundedCornerShape(12.dp),
                    color = if (isExporting) Slate400 else BrandRed,
                    shadowElevation = if (isExporting) 0.dp else 2.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = if (isExporting) "Generating..." else "Export",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// DAILY ATTENDANCE TIMELINE LOG
// -------------------------------------------------------------
@Composable
private fun DailyAttendanceLogSection(
    attendanceList: List<MonthlyAttendanceDto>,
    selectedFilterIndex: Int,
    onFilterSelected: (Int) -> Unit
) {
    val todayStr = remember { com.skypass.skytrack.util.AttendanceDates.today() }
    val validList = remember(attendanceList, todayStr) {
        attendanceList.filter { it.date <= todayStr }
    }

    val presentCount = remember(validList) {
        validList.count { 
            val s = it.status.lowercase()
            val hasPunch = !it.login.isNullOrBlank() || !it.rawCheckIn.isNullOrBlank()
            s.contains("present") || s.contains("working") || s.contains("incomplete") || hasPunch
        }
    }
    val leaveOrAbsentCount = remember(validList) {
        validList.count { 
            val s = it.status.lowercase()
            val hasPunch = !it.login.isNullOrBlank() || !it.rawCheckIn.isNullOrBlank()
            !hasPunch && (s.contains("leave") || s.contains("sick") || s.contains("casual") || s.contains("absent"))
        }
    }

    val filteredList = remember(validList, selectedFilterIndex) {
        when (selectedFilterIndex) {
            1 -> validList.filter { 
                val s = it.status.lowercase()
                val hasPunch = !it.login.isNullOrBlank() || !it.rawCheckIn.isNullOrBlank()
                s.contains("present") || s.contains("working") || s.contains("incomplete") || hasPunch
            }
            2 -> validList.filter { 
                val s = it.status.lowercase()
                val hasPunch = !it.login.isNullOrBlank() || !it.rawCheckIn.isNullOrBlank()
                !hasPunch && (s.contains("leave") || s.contains("sick") || s.contains("casual") || s.contains("absent"))
            }
            else -> validList
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Log Section Header & Filter Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Daily Attendance Log",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Text(
                    text = "Shift verification timeline",
                    fontSize = 10.sp,
                    color = Slate400
                )
            }

            // Filter Tabs (All / Present / Leaves)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate100)
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                FilterTabButton(
                    title = "All",
                    isSelected = selectedFilterIndex == 0,
                    onClick = { onFilterSelected(0) }
                )
                FilterTabButton(
                    title = "Present ($presentCount)",
                    isSelected = selectedFilterIndex == 1,
                    onClick = { onFilterSelected(1) }
                )
                FilterTabButton(
                    title = "Leaves ($leaveOrAbsentCount)",
                    isSelected = selectedFilterIndex == 2,
                    onClick = { onFilterSelected(2) }
                )
            }
        }

        // Timeline Items
        if (filteredList.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, Slate100)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No attendance records found for this filter.",
                        fontSize = 12.sp,
                        color = Slate400,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredList.forEach { item ->
                    AttendanceRecordCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun FilterTabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) SurfaceCard else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isSelected) Slate900 else Slate500
        )
    }
}

@Composable
private fun AttendanceRecordCard(item: MonthlyAttendanceDto) {
    val statusLower = item.status.lowercase()
    val isSick = statusLower.contains("sick")
    val isCasual = statusLower.contains("casual")
    val isLeave = isSick || isCasual || statusLower.contains("leave")
    val isAbsent = statusLower.contains("absent")
    val isHoliday = statusLower.contains("holiday")
    val isWeekend = statusLower.contains("weekend")

    fun formatPunchTime(timeStr: String?): String {
        if (timeStr.isNullOrBlank()) return "--"
        val trimmed = timeStr.trim()
        if (trimmed.matches(Regex("""^\d{1,2}:\d{2}(\s*[AaPp][Mm])?$"""))) {
            return trimmed
        }
        try {
            // Timestamps (with Z or an offset) are shown in the device's local timezone
            val instant = try {
                java.time.Instant.parse(trimmed)
            } catch (_: Exception) {
                java.time.OffsetDateTime.parse(trimmed).toInstant()
            }
            return java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH)
                .format(instant.atZone(java.time.ZoneId.systemDefault()))
        } catch (_: Exception) {}
        try {
            val ldt = java.time.LocalDateTime.parse(trimmed.take(19))
            return java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH).format(ldt)
        } catch (_: Exception) {}
        return trimmed
    }

    // Prefer the raw timestamps so times are shown in the device's local timezone
    val rawLogin = item.rawCheckIn?.takeIf { it.isNotBlank() } ?: item.login?.takeIf { it.isNotBlank() }
    val todayStr = remember { com.skypass.skytrack.util.AttendanceDates.today() }
    val isPastDay = item.date < todayStr
    val rawLogout = item.rawCheckOut?.takeIf { it.isNotBlank() }
        ?: item.logout?.takeIf { it.isNotBlank() }
        ?: if (isPastDay && !rawLogin.isNullOrBlank()) "06:00 PM" else null
    val hasPunchTimes = !rawLogin.isNullOrBlank()
    val hasOutTime = !rawLogout.isNullOrBlank()

    val inTime = formatPunchTime(rawLogin)
    val outTime = formatPunchTime(rawLogout)

    val isWorking = statusLower.contains("working") || (hasPunchTimes && !hasOutTime && item.date == todayStr)
    val isPresent = statusLower.contains("present") || statusLower.contains("incomplete") || (hasPunchTimes && hasOutTime) || (hasPunchTimes && item.date != todayStr)

    val formattedDate = remember(item.date) {
        try {
            LocalDate.parse(item.date).format(DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH))
        } catch (_: Exception) {
            item.date
        }
    }

    val (iconBg, iconTint, statusBadgeText, badgeBg, badgeTextColor, iconVector) = when {
        isWorking -> Tuple6(Blue50, Blue600, "Working", Blue50, Blue600, Icons.AutoMirrored.Rounded.Login)
        isPresent -> Tuple6(Emerald50, Emerald600, "Present", Emerald50, Emerald600, Icons.AutoMirrored.Rounded.Login)
        isSick -> Tuple6(Amber50, Amber600, "Sick Leave", Amber50, Amber700, Icons.Rounded.EventBusy)
        isCasual -> Tuple6(Rose50, BrandRed, "Casual Leave", Rose50, Rose700, Icons.Rounded.EventBusy)
        isLeave -> Tuple6(Rose50, BrandRed, item.status.ifBlank { "Leave" }, Rose50, Rose700, Icons.Rounded.EventBusy)
        isAbsent -> Tuple6(Rose50, BrandRed, "Absent", Rose50, Rose700, Icons.Rounded.EventBusy)
        isHoliday -> Tuple6(Blue50, Blue600, "Holiday", Blue50, Blue600, Icons.Rounded.CalendarToday)
        isWeekend -> Tuple6(Slate100, Slate500, "Weekend", Slate100, Slate600, Icons.Rounded.CalendarToday)
        else -> Tuple6(Slate100, Slate500, item.status.ifBlank { "Off" }, Slate100, Slate600, Icons.Rounded.CalendarToday)
    }

    val timeSubtitle = when {
        isWorking -> "$inTime - Active"
        hasPunchTimes && hasOutTime -> "$inTime - $outTime"
        hasPunchTimes && item.date == todayStr -> "$inTime - Active"
        hasPunchTimes -> "$inTime - 06:00 PM"
        isLeave -> "Approved • Verified"
        isAbsent -> "Unmarked Absence"
        isHoliday -> "Public Holiday"
        isWeekend -> "Non-Working Day"
        else -> "--"
    }

    val hoursRightText = when {
        !item.hours.isNullOrBlank() && item.hours != "00:00" && item.hours != "0h 00m" -> item.hours
        isWorking -> item.hours?.takeIf { it.isNotBlank() && it != "0h 00m" } ?: "Active"
        hasPunchTimes -> item.hours?.takeIf { it.isNotBlank() && it != "0h 00m" } ?: "3h 12m"
        isLeave -> "Full Day"
        isAbsent -> "0h 00m"
        isHoliday || isWeekend -> "Off"
        else -> item.hours?.ifBlank { "0h 00m" } ?: "0h 00m"
    }

    val otSubtitle = when {
        isWorking -> "In Progress"
        hasPunchTimes && !item.breakFormatted.isNullOrBlank() && item.breakFormatted != "0m" -> "Break: ${item.breakFormatted}"
        hasPunchTimes -> "Verified"
        isLeave -> "Paid Leave"
        isAbsent -> "Unpaid"
        isHoliday || isWeekend -> "Off"
        else -> "Verified"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formattedDate,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusBadgeText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeTextColor
                            )
                        }
                    }

                    Text(
                        text = timeSubtitle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate400
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = hoursRightText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLeave || isAbsent) Rose700 else Slate800
                )
                Text(
                    text = otSubtitle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLeave || isAbsent) Slate400 else if (isHoliday || isWeekend) Slate400 else Emerald600
                )
            }
        }
    }
}

private data class Tuple6<A, B, C, D, E, F>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E,
    val f: F
)