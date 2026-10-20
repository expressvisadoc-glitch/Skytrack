package com.skypass.skytrack.ui.leave

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.WbSunny
import com.skypass.skytrack.ui.components.SkyTrackDialog
import com.skypass.skytrack.ui.components.SkyTrackDialogType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.remote.LeaveDto
import com.skypass.skytrack.data.remote.PublicHoliday
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Stitch Color Palette
private val BrandRed = Color(0xFFEF4444)
private val BrandRedHover = Color(0xFFDC2626)
private val BrandRedDark = Color(0xFFB91C1C)
private val BrandLightRed = Color(0xFFFEF2F2)
private val BrandRedBorder = Color(0xFFFEE2E2)
private val SurfaceBase = Color(0xFFF8FAFC)
private val SurfaceCard = Color(0xFFFFFFFF)
private val DockBg = Color(0xFF1E2530)
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
private val Amber50 = Color(0xFFFFFBEB)
private val Amber200 = Color(0xFFFDE68A)
private val Amber500 = Color(0xFFF59E0B)
private val Amber600 = Color(0xFFD97706)
private val Amber800 = Color(0xFF92400E)
private val Amber900 = Color(0xFF78350F)
private val Teal600 = Color(0xFF0D9488)
private fun getHolidaysForMonth(
    yearMonth: YearMonth,
    remoteHolidays: List<PublicHoliday> = emptyList()
): List<PublicHoliday> {
    return remoteHolidays.filter {
        it.date.year == yearMonth.year && it.date.monthValue == yearMonth.monthValue
    }
}


@Composable
fun LeaveScreen(
    session: Session,
    viewModel: LeaveViewModel,
    onBack: () -> Unit,
    onNavigateHome: () -> Unit = onBack,
    onNavigateStatistics: () -> Unit = {},
    onNavigateProfile: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    // Form State
    val casualBalance = state.balance?.casual?.balance ?: 0f
    val sickBalance = state.balance?.sick?.balance ?: 0f
    val isCasualAvailable = state.balance == null || casualBalance > 0f
    val isSickAvailable = state.balance == null || sickBalance > 0f

    val initialLeaveType = remember(state.balance) {
        if (state.balance != null) {
            when {
                casualBalance > 0f -> "Casual Leave"
                sickBalance > 0f -> "Sick Leave"
                else -> "Loss of Pay"
            }
        } else {
            "Casual Leave"
        }
    }
    var leaveType by remember { mutableStateOf(initialLeaveType) }
    val today = remember { LocalDate.now() }
    var currentMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var selectedStartDate by remember { mutableStateOf(today) }
    var selectedEndDate by remember { mutableStateOf(today.plusDays(1)) }
    var isHalfDay by remember { mutableStateOf(false) }
    var selectedSession by remember { mutableStateOf("First Half") }
    var reason by remember { mutableStateOf("") }
    var holidayConflictMessage by remember { mutableStateOf<String?>(null) }
    var showPastDateDialog by remember { mutableStateOf(false) }
    var leaveToCancel by remember { mutableStateOf<LeaveDto?>(null) }
    var resetSliderTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(session.token) {
        viewModel.loadHistory(session)
    }

    LaunchedEffect(state.balance) {
        val balance = state.balance
        if (balance != null) {
            val casualLeft = balance.casual?.balance ?: 0f
            val sickLeft = balance.sick?.balance ?: 0f
            if (casualLeft <= 0f && sickLeft <= 0f) {
                leaveType = "Loss of Pay"
            } else if (leaveType.contains("Casual", ignoreCase = true) && casualLeft <= 0f) {
                leaveType = if (sickLeft > 0f) "Sick Leave" else "Loss of Pay"
            } else if (leaveType.contains("Sick", ignoreCase = true) && sickLeft <= 0f) {
                leaveType = if (casualLeft > 0f) "Casual Leave" else "Loss of Pay"
            }
        }
    }

    // Reset screen to initial state when leave submission succeeds
    LaunchedEffect(state.successMessage) {
        if (state.successMessage != null) {
            val casualLeft = state.balance?.casual?.balance ?: 0f
            val sickLeft = state.balance?.sick?.balance ?: 0f
            leaveType = when {
                casualLeft > 0f -> "Casual Leave"
                sickLeft > 0f -> "Sick Leave"
                else -> "Loss of Pay"
            }
            selectedStartDate = today
            selectedEndDate = today.plusDays(1)
            isHalfDay = false
            selectedSession = "First Half"
            reason = ""
            holidayConflictMessage = null
            showPastDateDialog = false
            resetSliderTrigger++
        }
    }

    if (holidayConflictMessage != null) {
        SkyTrackDialog(
            title = "Public Holiday Selected",
            message = holidayConflictMessage.orEmpty(),
            icon = Icons.Rounded.EventBusy,
            type = SkyTrackDialogType.WARNING,
            confirmText = "OK",
            onConfirm = { holidayConflictMessage = null },
            onDismissRequest = { holidayConflictMessage = null }
        )
    }

    val performSubmit = {
        val rangeStart = selectedStartDate
        val rangeEnd = if (isHalfDay) selectedStartDate else selectedEndDate

        val finalFrom = rangeStart.toString()
        val finalTo = rangeEnd.toString()
        val finalReason = if (isHalfDay) {
            if (reason.isBlank()) "Half Day ($selectedSession)" else "$reason ($selectedSession)"
        } else {
            if (reason.isBlank()) "Leave Application" else reason
        }

        viewModel.submit(
            session = session,
            from = finalFrom,
            to = finalTo,
            type = if (isHalfDay) "$leaveType (Half Day)" else leaveType,
            reason = finalReason
        )
    }

    if (showPastDateDialog) {
        SkyTrackDialog(
            title = "Past Date Selected",
            message = "You are applying for leave on a past date. Do you want to proceed?",
            icon = Icons.Rounded.History,
            type = SkyTrackDialogType.WARNING,
            confirmText = "Submit",
            dismissText = "Cancel",
            onConfirm = {
                showPastDateDialog = false
                performSubmit()
            },
            onDismiss = {
                showPastDateDialog = false
                resetSliderTrigger++
            },
            onDismissRequest = {
                showPastDateDialog = false
                resetSliderTrigger++
            }
        )
    }

    if (state.error != null) {
        SkyTrackDialog(
            title = "SkyTrack",
            message = state.error.orEmpty(),
            type = SkyTrackDialogType.ERROR,
            confirmText = "OK",
            onConfirm = {
                viewModel.clearMessages()
                resetSliderTrigger++
                viewModel.loadHistory(session)
            },
            onDismissRequest = {
                viewModel.clearMessages()
                resetSliderTrigger++
                viewModel.loadHistory(session)
            }
        )
    }

    if (leaveToCancel != null) {
        val target = leaveToCancel!!
        val dateText = if (!target.from.isNullOrBlank() && !target.to.isNullOrBlank() && target.from != target.to) {
            "${target.from} to ${target.to}"
        } else {
            target.from ?: "the selected date"
        }
        SkyTrackDialog(
            title = "Cancel Leave Request",
            message = "Are you sure you want to cancel your ${target.type.orEmpty().ifBlank { "leave" }} request for $dateText?",
            icon = Icons.Rounded.Close,
            type = SkyTrackDialogType.WARNING,
            confirmText = "Yes, Cancel",
            dismissText = "Keep Request",
            onConfirm = {
                val toCancel = leaveToCancel
                leaveToCancel = null
                if (toCancel != null) {
                    viewModel.cancelLeave(session, toCancel)
                }
            },
            onDismiss = { leaveToCancel = null },
            onDismissRequest = { leaveToCancel = null }
        )
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = 110.dp) // Room for docked navigation bar
        ) {
            // Top Bar: Back Arrow, Title, Subtitle
            LeaveTopBar(onBack = onBack)

            Spacer(modifier = Modifier.height(14.dp))

            // Employee Context Sub-Header
            EmployeeContextSubHeader(
                name = session.name,
                employeeId = session.employeeId,
                role = session.role
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Leave Balances Overview (3 Horizontal Cards)
            LeaveBalancesSection(
                currentMonth = currentMonth,
                balance = state.balance,
                leaves = state.leaves
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Main Leave Configuration Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, Slate100),
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Step Pill Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 6.dp, height = 16.dp)
                                    .clip(CircleShape)
                                    .background(BrandRed)
                            )
                            Text(
                                text = "Select Leave Type",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(BrandLightRed)
                                .border(1.dp, BrandRedBorder, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Step 1 of 2",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandRed
                            )
                        }
                    }

                    // Leave Type Selector Group
                    LeaveTypeSelector(
                        selectedType = leaveType,
                        isCasualEnabled = isCasualAvailable,
                        isSickEnabled = isSickAvailable,
                        onTypeSelected = { leaveType = it }
                    )

                    // Custom Calendar Container (Includes Holidays, Duration switch, and Selection summary)
                    LeaveCalendarSection(
                        currentMonth = currentMonth,
                        remoteHolidays = state.holidays,
                        onPreviousMonth = { currentMonth = currentMonth.minusMonths(1) },
                        onNextMonth = { currentMonth = currentMonth.plusMonths(1) },
                        selectedStartDate = selectedStartDate,
                        selectedEndDate = if (isHalfDay) selectedStartDate else selectedEndDate,
                        isHalfDay = isHalfDay,
                        onToggleHalfDay = { isHalfDay = !isHalfDay },
                        selectedSession = selectedSession,
                        onSessionSelected = { selectedSession = it },
                        onDateClicked = { clickedDate ->
                            if (isHalfDay) {
                                selectedStartDate = clickedDate
                                selectedEndDate = clickedDate
                            } else {
                                if (selectedStartDate != selectedEndDate) {
                                    // Start a fresh range
                                    selectedStartDate = clickedDate
                                    selectedEndDate = clickedDate
                                } else {
                                    if (clickedDate < selectedStartDate) {
                                        selectedStartDate = clickedDate
                                    } else {
                                        selectedEndDate = clickedDate
                                    }
                                }
                            }
                        }
                    )

                    // Reason for Leave Input with Quick Tags
                    LeaveReasonInput(
                        reason = reason,
                        onReasonChange = { if (it.length <= 150) reason = it },
                        onTagSelected = { tag ->
                            reason = if (reason.isBlank()) tag else "$reason, $tag"
                        }
                    )

                    // Approver Handover Note
                    val effectiveManager = state.managerName?.takeIf { it.isNotBlank() } ?: session.managerName
                    ApproverHandoverCard(managerName = effectiveManager)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Slide-To-Submit Action Slider
            SwipeToSubmitSlider(
                isLoading = state.isSubmitting,
                resetTrigger = resetSliderTrigger,
                onSubmit = {
                    val rangeStart = selectedStartDate
                    val rangeEnd = if (isHalfDay) selectedStartDate else selectedEndDate

                    // Check if any public holiday falls in the requested range
                    val conflictingHolidays = state.holidays.filter { holiday ->
                        holiday.date in rangeStart..rangeEnd
                    }

                    if (conflictingHolidays.isNotEmpty()) {
                        val holidayNames = conflictingHolidays.joinToString { "${it.name} (${it.date})" }
                        holidayConflictMessage = "Cannot apply leave on a public holiday: $holidayNames. Please adjust your dates."
                        resetSliderTrigger++
                        return@SwipeToSubmitSlider
                    }

                    // Check if applying for a past date
                    if (rangeStart < today) {
                        showPastDateDialog = true
                        return@SwipeToSubmitSlider
                    }

                    performSubmit()
                }
            )

            // Success Card Feedback
//            AnimatedVisibility(visible = state.successMessage != null) {
//                Spacer(modifier = Modifier.height(12.dp))
//                SubmissionSuccessCard(message = state.successMessage.orEmpty())
//            }

            // Recent Leaves History
            if (state.leaves.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                RecentLeaveHistorySection(
                    leaves = state.leaves,
                    isCancelling = state.isCancelling,
                    cancellingLeaveId = state.cancellingLeaveId,
                    onCancelLeave = { leaveToCancel = it }
                )
            }
        }

        // Floating Bottom Navigation Dock (Active on Leaves)
        LeaveBottomNavigationDock(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            onHomeClick = onNavigateHome,
            onAnalyticsClick = onNavigateStatistics,
            onProfileClick = onNavigateProfile
        )

        // SkyTrack Global Loading Dialog (Initial data loading)
        com.skypass.skytrack.ui.components.SkyTrackLoadingOverlay(
            visible = state.isLoading
        )
    }
}

// -------------------------------------------------------------
// TOP BAR
// -------------------------------------------------------------
@Composable
private fun LeaveTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, Slate100),
                shadowElevation = 1.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Slate700,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column {
                Text(
                    text = "TIME OFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Apply Leave",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    letterSpacing = (-0.4).sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// EMPLOYEE CONTEXT SUB-HEADER
// -------------------------------------------------------------
@Composable
private fun EmployeeContextSubHeader(
    name: String,
    employeeId: String,
    role: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Slate100.copy(alpha = 0.9f)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pulse Green Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Emerald500)
                )

                Text(
                    text = if (name.isNotBlank()) name else "Employee",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Slate100)
                        .border(1.dp, Slate200.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (employeeId.isNotBlank()) employeeId else "",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate600
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Slate50)
                    .border(1.dp, Slate200.copy(alpha = 0.6f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (role.isNotBlank()) role else "Employee",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate500
                )
            }
        }
    }
}

// -------------------------------------------------------------
// LEAVE BALANCES OVERVIEW (3 CARDS)
// -------------------------------------------------------------
@Composable
private fun LeaveBalancesSection(
    currentMonth: YearMonth,
    balance: com.skypass.skytrack.data.remote.LeaveBalanceDataDto?,
    leaves: List<LeaveDto> = emptyList()
) {
    val monthName = remember(currentMonth) {
        currentMonth.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH))
    }

    // Safely format float without trailing .0 if integer
    val formatValue = { value: Float? ->
        if (value == null) "0"
        else if (value % 1.0f == 0.0f) value.toInt().toString()
        else String.format(Locale.US, "%.1f", value)
    }

    val casualBalance = formatValue(balance?.casual?.balance)
    val casualUsed = formatValue(balance?.casual?.used)
    
    val sickBalance = formatValue(balance?.sick?.balance)
    val sickUsed = formatValue(balance?.sick?.used)

    // Calculate dynamic Loss of Pay (LOP) days taken
    val lopUsed = remember(leaves, currentMonth) {
        val monthPrefix = currentMonth.toString() // e.g. "2026-10"
        leaves
            .filter {
                val isLopType = it.type?.contains("Loss of Pay", ignoreCase = true) == true ||
                        it.type?.contains("lop", ignoreCase = true) == true ||
                        (it.lopDays != null && it.lopDays > 0f)
                val isApproved = it.status.equals("approved", ignoreCase = true)
                val isCurrentMonth = it.from?.startsWith(monthPrefix) == true || it.to?.startsWith(monthPrefix) == true
                isLopType && isApproved && isCurrentMonth
            }
            .sumOf { (it.lopDays ?: it.leaveDays ?: 1f).toDouble() }
    }
    val lopDisplay = if (lopUsed % 1.0 == 0.0) lopUsed.toInt().toString() else String.format(Locale.US, "%.1f", lopUsed)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card 1: Casual
        BalanceCard(
            title = "CASUAL",
            icon = Icons.Rounded.BeachAccess,
            iconTint = BrandRed,
            count = casualBalance.padStart(2, '0'),
            countColor = BrandRed,
            statusLabel = "Available",
            footerLeft = "Used",
            footerRight = casualUsed,
            footerBadgeBg = Amber50,
            footerBadgeText = Amber600,
            minWidth = 145
        )

        // Card 2: Sick
        BalanceCard(
            title = "SICK",
            icon = Icons.Rounded.Medication,
            iconTint = Teal600,
            count = sickBalance.padStart(2, '0'),
            countColor = Slate900,
            statusLabel = "Available",
            footerLeft = "Used",
            footerRight = sickUsed,
            footerBadgeBg = Emerald50,
            footerBadgeText = Emerald600,
            minWidth = 145
        )

        // Card 3: Loss of Pay
        BalanceCard(
            title = "LOP",
            icon = Icons.Rounded.EventBusy,
            iconTint = Slate400,
            count = lopDisplay.padStart(2, '0'),
            countColor = Slate900,
            statusLabel = "Taken ($monthName)",
            footerLeft = "Quota",
            footerRight = "Standard",
            footerBadgeBg = Color.Transparent,
            footerBadgeText = Slate500,
            minWidth = 135
        )
    }
}

@Composable
private fun BalanceCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    count: String,
    countColor: Color,
    statusLabel: String,
    footerLeft: String,
    footerRight: String,
    footerBadgeBg: Color,
    footerBadgeText: Color,
    minWidth: Int
) {
    Surface(
        modifier = Modifier.width(minWidth.dp),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate400,
                    letterSpacing = 0.8.sp
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Count
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = count,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = countColor,
                    lineHeight = 30.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "days",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate400,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Text(
                text = statusLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate600
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Slate100)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = footerLeft,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate400
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(footerBadgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = footerRight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = footerBadgeText
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// LEAVE TYPE SELECTOR
// -------------------------------------------------------------
@Composable
private fun LeaveTypeSelector(
    selectedType: String,
    isCasualEnabled: Boolean = true,
    isSickEnabled: Boolean = true,
    onTypeSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LeaveTypeOptionButton(
            title = "Casual (CL)",
            icon = Icons.Rounded.BeachAccess,
            isSelected = selectedType.contains("Casual", ignoreCase = true),
            enabled = isCasualEnabled,
            onClick = { onTypeSelected("Casual Leave") },
            modifier = Modifier.weight(1f)
        )
        LeaveTypeOptionButton(
            title = "Sick (SL)",
            icon = Icons.Rounded.MedicalServices,
            isSelected = selectedType.contains("Sick", ignoreCase = true),
            enabled = isSickEnabled,
            onClick = { onTypeSelected("Sick Leave") },
            modifier = Modifier.weight(1f)
        )
        LeaveTypeOptionButton(
            title = "Loss of Pay",
            icon = Icons.Rounded.MoneyOff,
            isSelected = selectedType.contains("Loss", ignoreCase = true) || selectedType.contains("LOP", ignoreCase = true),
            enabled = true,
            onClick = { onTypeSelected("Loss of Pay") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LeaveTypeOptionButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetBgColor = when {
        !enabled -> Slate100
        isSelected -> BrandRed
        else -> Slate50
    }
    val targetContentColor = when {
        !enabled -> Slate400
        isSelected -> Color.White
        else -> Slate600
    }
    val targetBorderColor = when {
        !enabled -> Slate200
        isSelected -> BrandRed
        else -> Slate100
    }

    val bgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(150),
        label = "btnBg"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(150),
        label = "btnContent"
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(150),
        label = "btnBorder"
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (isSelected && enabled) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// -------------------------------------------------------------
// CUSTOM CALENDAR MATRIX WITH HOLIDAYS & INTEGRATED DURATION
// -------------------------------------------------------------
@Composable
private fun LeaveCalendarSection(
    currentMonth: YearMonth,
    remoteHolidays: List<PublicHoliday> = emptyList(),
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    selectedStartDate: LocalDate,
    selectedEndDate: LocalDate,
    isHalfDay: Boolean,
    onToggleHalfDay: () -> Unit,
    selectedSession: String,
    onSessionSelected: (String) -> Unit,
    onDateClicked: (LocalDate) -> Unit
) {
    val monthTitle = remember(currentMonth) {
        currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
    }
    val holidays = remember(currentMonth, remoteHolidays) {
        getHolidaysForMonth(currentMonth, remoteHolidays)
    }
    val holidayDates = remember(holidays) { holidays.map { it.date }.toSet() }
    var holidayIndex by remember(currentMonth, holidays) { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Slate50,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Month Header with Prev/Next buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .shadow(1.dp, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard)
                            .border(1.dp, Slate100, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = "Month",
                            tint = BrandRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = monthTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        onClick = onPreviousMonth,
                        modifier = Modifier.size(28.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, Slate100),
                        shadowElevation = 0.5.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronLeft,
                                contentDescription = "Prev",
                                tint = Slate700,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onNextMonth,
                        modifier = Modifier.size(28.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceCard,
                        border = BorderStroke(1.dp, Slate100),
                        shadowElevation = 0.5.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Next",
                                tint = Slate700,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Weekday Headers: M, T, W, T, F, S, S (Sunday in Red)
            val weekDays = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDays.forEachIndexed { index, dayLetter ->
                    Text(
                        text = dayLetter,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 6) BrandRed else Slate400
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Days Matrix (7 columns)
            val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value // 1 = Mon ... 7 = Sun
            val daysInMonth = currentMonth.lengthOfMonth()
            val prevMonth = currentMonth.minusMonths(1)
            val daysInPrevMonth = prevMonth.lengthOfMonth()

            val totalLeading = firstDayOfWeek - 1
            val totalCells = ((totalLeading + daysInMonth + 6) / 7) * 7

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until (totalCells / 7)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val dayNumber = cellIndex - totalLeading + 1

                            if (dayNumber in 1..daysInMonth) {
                                val thisDate = currentMonth.atDay(dayNumber)
                                val isHoliday = holidayDates.contains(thisDate)
                                val isStart = thisDate == selectedStartDate
                                val isEnd = thisDate == selectedEndDate
                                val isInRange = !isHalfDay && thisDate > selectedStartDate && thisDate < selectedEndDate
                                val isSelected = isStart || isEnd
                                val isSunday = (col == 6)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .padding(horizontal = if (isSelected || isInRange) 0.5.dp else 2.dp)
                                        .clip(
                                            when {
                                                isStart && isEnd -> RoundedCornerShape(12.dp)
                                                isStart -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                                                isEnd -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                                                isInRange -> RoundedCornerShape(0.dp)
                                                else -> RoundedCornerShape(8.dp)
                                            }
                                        )
                                        .background(
                                            when {
                                                isSelected -> BrandRed
                                                isInRange -> BrandLightRed
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable { onDateClicked(thisDate) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = dayNumber.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected || isInRange) FontWeight.Bold else FontWeight.SemiBold,
                                            color = when {
                                                isSelected -> Color.White
                                                isInRange -> BrandRed
                                                isHoliday -> BrandRed
                                                isSunday -> BrandRed
                                                else -> Slate700
                                            }
                                        )
                                        if (isHoliday) {
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color.White else BrandRed)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Muted Overflow Days
                                val overflowDay = if (dayNumber <= 0) {
                                    daysInPrevMonth + dayNumber
                                } else {
                                    dayNumber - daysInMonth
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = overflowDay.toString(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate300
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Public Holiday Banner & Legend
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Slate200.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Holiday Banner Pill
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 0.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = BrandLightRed,
                    border = BorderStroke(1.dp, BrandRedBorder.copy(alpha = 0.7f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(BrandRed)
                            )
                            if (holidays.isNotEmpty()) {
                                val safeIndex = holidayIndex.coerceIn(0, holidays.size - 1)
                                val currentHoliday = holidays[safeIndex]
                                val holidayDateStr = currentHoliday.date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Public Holiday: ",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BrandRedHover
                                    )
                                    Text(
                                        text = "$holidayDateStr (${currentHoliday.name})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = BrandRed,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text(
                                    text = "No public holidays this month",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate600
                                )
                            }
                        }

                        if (holidays.size > 1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            holidayIndex = if (holidayIndex > 0) holidayIndex - 1 else holidays.size - 1
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronLeft,
                                        contentDescription = "Previous Holiday",
                                        tint = Amber800,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Text(
                                    text = "${holidayIndex.coerceIn(0, holidays.size - 1) + 1} of ${holidays.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Amber900,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            holidayIndex = if (holidayIndex < holidays.size - 1) holidayIndex + 1 else 0
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = "Next Holiday",
                                        tint = Amber800,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

//                // Legend
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(4.dp)
//                ) {
//                    Box(
//                        modifier = Modifier
//                            .size(6.dp)
//                            .clip(CircleShape)
//                            .background(Amber500)
//                    )
//                    Text(
//                        text = "Holiday",
//                        fontSize = 10.sp,
//                        fontWeight = FontWeight.SemiBold,
//                        color = Slate400
//                    )
//                }
            }

            // Leave Duration Section & Half Day Switch
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Slate200.copy(alpha = 0.6f))
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timelapse,
                        contentDescription = "Duration",
                        tint = Slate400,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "LEAVE DURATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        letterSpacing = 0.8.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Half Day",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate600
                    )

                    // Custom Switch
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 20.dp)
                            .clip(CircleShape)
                            .background(if (isHalfDay) BrandRed else Slate200)
                            .clickable(onClick = onToggleHalfDay)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .offset(x = if (isHalfDay) 20.dp else 0.dp)
                                .shadow(1.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }

            // Half Day Session Options
            AnimatedVisibility(visible = isHalfDay) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SessionOptionButton(
                            title = "First Half (Morning)",
                            icon = Icons.Rounded.WbSunny,
                            isSelected = selectedSession == "First Half",
                            onClick = { onSessionSelected("First Half") },
                            modifier = Modifier.weight(1f)
                        )
                        SessionOptionButton(
                            title = "Second Half (Afternoon)",
                            icon = Icons.Rounded.Bedtime,
                            isSelected = selectedSession == "Second Half",
                            onClick = { onSessionSelected("Second Half") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Selected Dates & Duration Badge Summary
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Slate200.copy(alpha = 0.7f))
            )
            Spacer(modifier = Modifier.height(10.dp))

            val durationText = remember(selectedStartDate, selectedEndDate, isHalfDay) {
                if (isHalfDay) {
                    "0.5 Day (Half Day)"
                } else {
                    val days = ChronoUnit.DAYS.between(selectedStartDate, selectedEndDate) + 1
                    if (days <= 1) "1 Day" else "$days Days"
                }
            }

            val selectedDatesFormatted = remember(selectedStartDate, selectedEndDate, isHalfDay) {
                val formatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
                if (isHalfDay || selectedStartDate == selectedEndDate) {
                    selectedStartDate.format(formatter)
                } else {
                    "${selectedStartDate.format(formatter)} – ${selectedEndDate.format(formatter)}"
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(BrandRed)
                    )
                    Text(
                        text = "Selected: ",
                        fontSize = 12.sp,
                        color = Slate700
                    )
                    Text(
                        text = selectedDatesFormatted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandRed
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isHalfDay) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Slate100)
                                .border(1.dp, Slate200.copy(alpha = 0.7f), CircleShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = selectedSession,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate600
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(BrandLightRed)
                            .border(1.dp, BrandRedBorder, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = durationText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionOptionButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) BrandRed else SurfaceCard,
        border = BorderStroke(1.dp, if (isSelected) BrandRed else Slate200)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) Color.White else Slate400,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Color.White else Slate600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// REASON FOR LEAVE WITH QUICK TAGS
// -------------------------------------------------------------
@Composable
private fun LeaveReasonInput(
    reason: String,
    onReasonChange: (String) -> Unit,
    onTagSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REASON FOR LEAVE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate400,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "${reason.length}/150",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Slate400
            )
        }

        // Quick Tags Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Family Event", "Medical checkup", "Personal matter").forEach { tag ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Slate100)
                        .clickable { onTagSelected(tag) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "+ $tag",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700
                    )
                }
            }
        }

        // Multiline Text Input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Slate50)
                .border(1.dp, Slate200.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            if (reason.isEmpty()) {
                Text(
                    text = "E.g., Attending family wedding, medical checkup at Aster Medcity...",
                    fontSize = 12.sp,
                    color = Slate400
                )
            }
            BasicTextField(
                value = reason,
                onValueChange = onReasonChange,
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    color = Slate800,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(BrandRed)
            )
        }
    }
}

// -------------------------------------------------------------
// APPROVER HANDOVER NOTE
// -------------------------------------------------------------
@Composable
private fun ApproverHandoverCard(managerName: String) {
    val cleanManagerName = managerName.trim()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Slate50,
        border = BorderStroke(1.dp, Slate100)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Slate200.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SupervisorAccount,
                    contentDescription = "Approver",
                    tint = Slate700,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (cleanManagerName.isNotBlank()) "Reporting Manager: $cleanManagerName" else "Reporting Manager: Not Assigned",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate800,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (cleanManagerName.isNotBlank()) "Request will be routed to $cleanManagerName for approval" else "Request will be routed to Admin & HR Desk",
                    fontSize = 11.sp,
                    color = Slate400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -------------------------------------------------------------
// INTERACTIVE SWIPE-TO-SUBMIT SLIDER
// -------------------------------------------------------------
@Composable
private fun SwipeToSubmitSlider(
    isLoading: Boolean,
    resetTrigger: Int = 0,
    onSubmit: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val knobSizeDp = 48.dp
    val knobSizePx = with(LocalDensity.current) { knobSizeDp.toPx() }
    val maxDragDistance = (trackWidthPx - knobSizePx - 8f).coerceAtLeast(0f)
    var isSubmitted by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "slider_loading_pulse")
    val loadingPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loading_pulse_alpha"
    )

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            isSubmitted = false
            dragOffsetX.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    LaunchedEffect(isLoading) {
        if (isLoading && maxDragDistance > 0f) {
            dragOffsetX.animateTo(maxDragDistance, animationSpec = tween(200))
        }
    }

    val progress = if (maxDragDistance > 0f) dragOffsetX.value / maxDragDistance else 0f
    val labelAlpha = (1f - progress * 1.5f).coerceIn(0f, 1f)

    val trackBorderColor = when {
        isSubmitted && !isLoading -> Emerald500.copy(alpha = 0.5f)
        isLoading -> BrandRed.copy(alpha = 0.4f)
        else -> Slate200
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Slate200.copy(alpha = 0.7f))
            .border(1.dp, trackBorderColor, RoundedCornerShape(28.dp))
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Dynamic expanding fill
        val fillColor = when {
            isSubmitted && !isLoading -> Emerald500.copy(alpha = 0.22f)
            isLoading -> BrandRed.copy(alpha = loadingPulseAlpha)
            else -> BrandRed.copy(alpha = 0.15f)
        }

        val fillWidth = if (isLoading && maxDragDistance > 0f) {
            with(LocalDensity.current) { (maxDragDistance + knobSizePx).toDp() }
        } else {
            with(LocalDensity.current) { (dragOffsetX.value + knobSizePx).toDp() }
        }

        Box(
            modifier = Modifier
                .height(48.dp)
                .width(fillWidth)
                .clip(RoundedCornerShape(24.dp))
                .background(fillColor)
        )

        // Center Content Label / Loading State
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 44.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BrandRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sending Leave Request...",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandRedDark
                        )
                    }
                }
                isSubmitted -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TaskAlt,
                            contentDescription = null,
                            tint = Emerald600,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Leave Requested!",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald600
                        )
                    }
                }
                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.offset(x = 12.dp)
                    ) {
                        Text(
                            text = if (progress >= 0.75f) "Release to Submit" else "Slide to Submit Request",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (progress >= 0.75f) BrandRed else Slate500.copy(alpha = labelAlpha)
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = if (progress >= 0.75f) BrandRed else Slate400.copy(alpha = labelAlpha),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Draggable Knob
        val knobBg = when {
            isSubmitted && !isLoading -> Emerald600
            else -> BrandRed
        }

        val knobTargetX = if (isLoading && maxDragDistance > 0f) maxDragDistance else dragOffsetX.value

        Box(
            modifier = Modifier
                .offset { IntOffset(knobTargetX.roundToInt(), 0) }
                .size(knobSizeDp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(knobBg)
                .then(
                    if (!isLoading && !isSubmitted) {
                        Modifier
                            .pointerInput(maxDragDistance) {
                                detectDragGestures(
                                    onDragEnd = {
                                        if (dragOffsetX.value >= maxDragDistance * 0.75f) {
                                            coroutineScope.launch {
                                                dragOffsetX.animateTo(
                                                    targetValue = maxDragDistance,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                )
                                                isSubmitted = true
                                                onSubmit()
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                dragOffsetX.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                )
                                            }
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val next = (dragOffsetX.value + dragAmount.x).coerceIn(0f, maxDragDistance)
                                        coroutineScope.launch {
                                            dragOffsetX.snapTo(next)
                                        }
                                    }
                                )
                            }
                            .clickable {
                                coroutineScope.launch {
                                    dragOffsetX.animateTo(maxDragDistance)
                                    isSubmitted = true
                                    onSubmit()
                                }
                            }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = Color.White
                )
            } else if (isSubmitted) {
                Icon(
                    imageVector = Icons.Rounded.TaskAlt,
                    contentDescription = "Success",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.KeyboardDoubleArrowRight,
                    contentDescription = "Submit",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}


// -------------------------------------------------------------
// SUBMISSION SUCCESS CARD
// -------------------------------------------------------------
@Composable
private fun SubmissionSuccessCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Emerald50,
        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.TaskAlt,
                contentDescription = "Success",
                tint = Emerald600,
                modifier = Modifier.size(22.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Leave Request Submitted!",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF064E3B)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (message.isNotBlank()) message else "Approver notification sent via SkyTrack dispatch.",
                    fontSize = 11.sp,
                    color = Color(0xFF065F46)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// RECENT LEAVE HISTORY SECTION
// -------------------------------------------------------------
@Composable
private fun RecentLeaveHistorySection(
    leaves: List<LeaveDto>,
    isCancelling: Boolean = false,
    cancellingLeaveId: String? = null,
    onCancelLeave: (LeaveDto) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "RECENT REQUESTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Slate400,
            letterSpacing = 0.8.sp
        )

        if (leaves.isEmpty()) {
            Text(
                text = "No leave requests yet.",
                fontSize = 12.sp,
                color = Slate500,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        leaves.take(4).forEach { leave ->
            val fromParsed = try { LocalDate.parse(leave.from.orEmpty()) } catch(e: Exception) { null }
            val toParsed = try { LocalDate.parse(leave.to.orEmpty()) } catch(e: Exception) { null }
            val fromFormatted = fromParsed?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) ?: leave.from.orEmpty()
            val toFormatted = toParsed?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) ?: leave.to.orEmpty()

            val dateRange = when {
                fromFormatted.isNotBlank() && toFormatted.isNotBlank() -> {
                    if (fromFormatted == toFormatted) fromFormatted else "$fromFormatted to $toFormatted"
                }
                !leave.from.isNullOrBlank() && !leave.to.isNullOrBlank() -> {
                    if (leave.from == leave.to) leave.from.orEmpty() else "${leave.from} to ${leave.to}"
                }
                !leave.from.isNullOrBlank() -> leave.from.orEmpty()
                else -> "Leave"
            }
            
            val status = leave.status.orEmpty().lowercase()
            
            // Format float without trailing .0
            val formatDays = { value: Float? ->
                if (value == null) ""
                else if (value % 1.0f == 0.0f) value.toInt().toString()
                else String.format("%.1f", value)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, Slate100),
                shadowElevation = 0.5.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = dateRange,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = Slate300
                                )
                                Text(
                                    text = leave.type.orEmpty().ifBlank { "Leave" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )
                            }
//                            if (!leave.requestId.isNullOrBlank()) {
//                                Text(
//                                    text = leave.requestId,
//                                    fontSize = 11.sp,
//                                    fontWeight = FontWeight.Medium,
//                                    color = Slate400
//                                )
//                            }
                        }

                        val (badgeBg, badgeText) = when {
                            status.contains("approv") -> Emerald50 to Emerald600
                            status.contains("reject") -> BrandLightRed to BrandRed
                            status.contains("cancel") -> Slate100 to Slate600
                            else -> Amber50 to Amber600
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(badgeBg)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = leave.status.orEmpty().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }.ifBlank { "Pending" },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeText
                            )
                        }
                    }

                    Text(
                        text = leave.reason.orEmpty(),
                        fontSize = 12.sp,
                        color = Slate500,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (leave.leaveDays != null) {
                                Text(text = "Total: ${formatDays(leave.leaveDays)}d", fontSize = 11.sp, color = Slate600, fontWeight = FontWeight.Medium)
                            }
                            if (leave.paidLeaveDays != null) {
                                Text(text = "Paid: ${formatDays(leave.paidLeaveDays)}d", fontSize = 11.sp, color = Emerald600, fontWeight = FontWeight.Medium)
                            }
                            if (leave.lopDays != null && leave.lopDays > 0f) {
                                Text(text = "LOP: ${formatDays(leave.lopDays)}d", fontSize = 11.sp, color = BrandRed, fontWeight = FontWeight.Medium)
                            }
                        }

                        // Cancel Button (ONLY for Pending state)
                        if (status.contains("pending") || status.isBlank()) {
                            val isThisCancelling = isCancelling && (cancellingLeaveId == (leave.requestId ?: leave.id))
                            OutlinedButton(
                                onClick = { onCancelLeave(leave) },
                                enabled = !isCancelling,
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = BrandRed
                                ),
                                border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isThisCancelling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(11.dp),
                                        strokeWidth = 1.5.dp,
                                        color = BrandRed
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cancelling...", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Cancel",
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Cancel", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    
                    if (!leave.managerComment.isNullOrBlank()) {
                        Surface(
                            color = Slate50,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Admin: ${leave.managerComment}",
                                fontSize = 11.sp,
                                color = Slate700,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// FLOATING BOTTOM NAVIGATION DOCK (ACTIVE ON LEAVES)
// -------------------------------------------------------------
@Composable
fun LeaveBottomNavigationDock(
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    com.skypass.skytrack.ui.components.SkyTrackBottomNavigationDock(
        currentTab = com.skypass.skytrack.ui.components.SkyTrackNavTab.LEAVES,
        modifier = modifier,
        onHomeClick = onHomeClick,
        onLeavesClick = { /* Already on Leaves */ },
        onAnalyticsClick = onAnalyticsClick,
        onProfileClick = onProfileClick
    )
}
