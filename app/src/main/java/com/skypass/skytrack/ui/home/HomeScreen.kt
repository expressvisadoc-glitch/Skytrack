package com.skypass.skytrack.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import com.skypass.skytrack.ui.components.ComingSoonDialog
import com.skypass.skytrack.ui.components.SkyTrackDialog
import com.skypass.skytrack.ui.components.SkyTrackDialogType
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.remote.AttendanceDto
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// Theme Colors from Tailwind Stitch config
val BrandColor = Color(0xFFE53935)
val BrandDarkColor = Color(0xFFC62828)
val BrandSubtleColor = Color(0xFFFFF5F5)
val BrandLightColor = Color(0xFFFFEBEE)

val SurfaceBase = Color(0xFFF8F9FB)
val CardSurface = Color(0xFFFFFFFF)
val DockBg = Color(0xFF1E2530)

val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

val Amber600 = Color(0xFFD97706)
val Amber500 = Color(0xFFF59E0B)
val Amber200 = Color(0xFFFDE68A)
val Amber50 = Color(0xFFFFFBEB)

val Orange500 = Color(0xFFF97316)
val Orange50 = Color(0xFFFFF7ED)

val Blue600 = Color(0xFF2563EB)
val Blue50 = Color(0xFFEFF6FF)

val Emerald600 = Color(0xFF059669)
val Emerald500 = Color(0xFF10B981)
val Emerald50 = Color(0xFFECFDF5)
val Emerald100 = Color(0xFFD1FAE5)

@Composable
fun HomeScreen(
    session: Session,
    viewModel: HomeViewModel,
    salaryViewModel: SalaryViewModel,
    onLogout: () -> Unit,
    onOpenLeave: () -> Unit,
    onOpenMonthlyStatistics: () -> Unit,
    onOpenProfile: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val salaryState by salaryViewModel.state.collectAsState()
    var showComingSoonDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Location permission is requested only when the employee checks in or out (foreground only)
    var pendingLocationAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingIsCheckIn by remember { mutableStateOf(true) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val action = pendingLocationAction
        pendingLocationAction = null
        if (grants.values.any { it }) {
            viewModel.refreshGeofence(session)
            action?.invoke()
        } else if (action != null) {
            viewModel.onLocationPermissionDenied(pendingIsCheckIn)
        } else {
            viewModel.refreshGeofence(session)
        }
    }
    val requestLocationPermission = {
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
    val runWithLocationPermission: (Boolean, () -> Unit) -> Unit = { isCheckIn, action ->
        if (hasLocationPermission(context)) {
            action()
        } else {
            pendingLocationAction = action
            pendingIsCheckIn = isCheckIn
            requestLocationPermission()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Salary figures are dropped when the app leaves the foreground
                salaryViewModel.hide()
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshTimers()
                viewModel.loadToday(session)
                if (viewModel.state.value.attendance?.logout.isNullOrBlank()) {
                    viewModel.refreshGeofence(session)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(session.token) {
        viewModel.loadToday(session)
    }

    // Recalculate a revealed salary card whenever attendance settles after a change
    LaunchedEffect(state.attendance?.login, state.attendance?.logout, state.attendance?.breakStartedAt, state.isActionLoading) {
        if (!state.isActionLoading) {
            salaryViewModel.refreshIfRevealed(session)
        }
    }

    if (state.error != null) {
        SkyTrackDialog(
            title = "SkyTrack",
            message = state.error.orEmpty(),
            type = SkyTrackDialogType.ERROR,
            confirmText = "OK",
            onConfirm = viewModel::clearError,
            onDismissRequest = viewModel::clearError
        )
    }

    val hasStarted = !state.attendance?.login.isNullOrBlank()
    val hasEnded = !state.attendance?.logout.isNullOrBlank()

    // Dynamic live work duration counter
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.attendance?.login, state.attendance?.logout, state.attendance?.workedMinutes, hasStarted, hasEnded) {
        if (hasStarted && !hasEnded) {
            val loginStr = state.attendance?.login
            val checkInInstant = parseInstant(loginStr)
            while (true) {
                val now = java.time.Instant.now()
                val diff = java.time.Duration.between(checkInInstant, now).seconds
                val safeElapsedSeconds = maxOf(0L, diff)
                elapsedSeconds = maxOf(0L, safeElapsedSeconds - state.breakSeconds)
                delay(1000)
            }
        } else if (hasStarted && hasEnded) {
            val workedMins = state.attendance?.workedMinutes
            if (workedMins != null) {
                elapsedSeconds = (workedMins * 60).toLong()
            } else {
                val loginStr = state.attendance?.login
                val logoutStr = state.attendance?.logout
                val checkInInstant = parseInstant(loginStr)
                val checkOutInstant = parseInstant(logoutStr)
                val diff = java.time.Duration.between(checkInInstant, checkOutInstant).seconds
                val safeElapsedSeconds = maxOf(0L, diff)
                elapsedSeconds = maxOf(0L, safeElapsedSeconds - state.breakSeconds)
            }
        } else {
            elapsedSeconds = 0L
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
                .padding(bottom = 100.dp) // Space for docked nav
        ) {
            // Top Header: Calendar Title & Horizontal Date Strip
            DashboardHeader(
                onNotificationClick = { showComingSoonDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // User Info Greeting Section
            UserGreetingSection(
                name = session.name,
                employeeId = session.employeeId
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Office geofence guidance (advisory; the server makes the final decision)
            val geofence = state.geofence
            if (!hasEnded && geofence != null) {
                GeofenceStatusCard(
                    feedback = geofence,
                    onRetry = { viewModel.refreshGeofence(session) },
                    onRequestPermission = { requestLocationPermission() },
                    onOpenAppSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onOpenLocationSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Attendance Action Area:
            // State 1: Ready to Check-In
            // State 2: Active Working / Take Break Hero
            // State 3: On Break Active Hero
            if (hasStarted && !hasEnded) {
                if (state.isOnBreak) {
                    // State 3: On Break Active Hero
                    OnBreakHero(
                        state = state,
                        onResumeWork = {
                            viewModel.toggleBreak(session)
                        }
                    )
                } else {
                    // State 2: Active Working / Take Break Hero
                    CircularBreakHero(
                        state = state,
                        elapsedSeconds = elapsedSeconds,
                        onTakeBreak = {
                            viewModel.toggleBreak(session)
                        }
                    )
                }
            } else {
                // State 1: Ready to Check-In or State 3: Completed
                AttendanceClockArea(
                    state = state,
                    onAction = {
                        if (!hasStarted) {
                            runWithLocationPermission(true) { viewModel.startWork(session) }
                        } else if (!hasEnded) {
                            runWithLocationPermission(false) { viewModel.endWork(session) }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Attendance Stats Grid (3 Metrics Cards)
            AttendanceMetricsGrid(
                attendance = state.attendance,
                isCheckedIn = hasStarted && !hasEnded,
                isOnBreak = state.isOnBreak,
                breakSeconds = state.breakSeconds,
                elapsedSeconds = elapsedSeconds
            )

            // State 2: End Shift / Check-Out Wide Action Button
            if (hasStarted && !hasEnded) {
                Spacer(modifier = Modifier.height(12.dp))
                CheckOutActionButton(
                    isLoading = state.isActionLoading || state.isEndingWork,
                    isOnBreak = state.isOnBreak,
                    onEndShift = { runWithLocationPermission(false) { viewModel.endWork(session) } }
                )
            }

            // This month's salary (hidden until the eye icon is tapped)
            Spacer(modifier = Modifier.height(16.dp))
            SalaryCard(
                state = salaryState,
                onToggle = { salaryViewModel.toggle(session) },
                onRetry = { salaryViewModel.retry(session) }
            )
        }

        // Docked Bottom Navigation Bar
        com.skypass.skytrack.ui.components.SkyTrackBottomNavigationDock(
            currentTab = com.skypass.skytrack.ui.components.SkyTrackNavTab.HOME,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            onHomeClick = { /* Already Home */ },
            onLeavesClick = onOpenLeave,
            onAnalyticsClick = onOpenMonthlyStatistics,
            onProfileClick = onOpenProfile
        )

        // SkyTrack Global Loading Dialog
        com.skypass.skytrack.ui.components.SkyTrackLoadingOverlay(
            visible = state.isLoading || state.isActionLoading || state.isStartingWork || state.isEndingWork
        )

        if (showComingSoonDialog) {
            ComingSoonDialog(
                title = "Notifications",
                message = "This feature is coming soon.",
                onDismiss = { showComingSoonDialog = false }
            )
        }
    }
}

data class StitchCalendarDay(
    val dayNumber: String,
    val dayOfWeek: String,
    val isToday: Boolean
)

@Composable
fun DashboardHeader(
    modifier: Modifier = Modifier,
    onNotificationClick: () -> Unit = {}
) {
    val today = remember { LocalDate.now() }
    val currentMonthYear = remember(today) {
        try {
            today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
        } catch (_: Exception) {
            "${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}"
        }
    }

    // 8 items centered around today (offset -4 to +3, so today is the 5th item matching Stitch layout)
    val daysList = remember(today) {
        (-4..3).map { offset ->
            val date = today.plusDays(offset.toLong())
            StitchCalendarDay(
                dayNumber = date.dayOfMonth.toString(),
                dayOfWeek = date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)),
                isToday = (offset == 0)
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Calendar Title Row (Calendar Icon, Attendance subtitle, Current Month & Year, Notification Bell)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Calendar Icon Card (p-2 rounded-xl bg-white shadow-sm border border-slate-100 text-brand)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(elevation = 1.dp, shape = RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.04f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardSurface)
                    .border(1.dp, Slate100, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = "Calendar",
                    tint = BrandColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "Attendance",
                    color = Slate400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = currentMonthYear,
                    color = Slate900,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Notification Bell Icon Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(elevation = 1.dp, shape = RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.04f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardSurface)
                    .border(1.dp, Slate100, RoundedCornerShape(12.dp))
                    .clickable(onClick = onNotificationClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = "Notifications",
                    tint = Slate700,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal Date Strip Row (#date-strip-container)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            daysList.forEach { item ->
                DateStripItem(
                    day = item.dayNumber,
                    dayOfWeek = item.dayOfWeek,
                    isSelected = item.isToday,
                    onClick = {
                        // Clicking other dates does nothing
                    }
                )
            }
        }
    }
}

@Composable
fun DateStripItem(
    day: String,
    dayOfWeek: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "date_scale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) BrandSubtleColor else CardSurface,
        animationSpec = tween(durationMillis = 180),
        label = "date_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) BrandColor else Slate200.copy(alpha = 0.8f),
        animationSpec = tween(durationMillis = 180),
        label = "date_border"
    )
    val dayColor by animateColorAsState(
        targetValue = if (isSelected) BrandColor else Slate800,
        animationSpec = tween(durationMillis = 180),
        label = "date_num_color"
    )
    val dayOfWeekColor by animateColorAsState(
        targetValue = if (isSelected) BrandColor else Slate400,
        animationSpec = tween(durationMillis = 180),
        label = "date_dow_color"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = Modifier
            .scale(scale)
            .width(42.dp)
            .height(64.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day,
                color = dayColor,
                fontSize = if (isSelected) 16.sp else 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dayOfWeek,
                color = dayOfWeekColor,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun UserGreetingSection(
    name: String,
    employeeId: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Good day,",
            color = Slate400,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name.ifBlank { "Employee" },
                color = Slate900,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Slate100)
                    .border(1.dp, Slate200.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = employeeId.ifBlank { "" },
                    color = Slate500,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

/**
 * State 3: On Break Active Hero
 * Shown when user clicks "Take Break" button in State 2.
 */
@Composable
fun OnBreakHero(
    state: HomeUiState,
    onResumeWork: () -> Unit
) {
    val breakHours = state.breakSeconds / 3600
    val breakMinutes = (state.breakSeconds % 3600) / 60
    val breakSecs = state.breakSeconds % 60
    val breakTimeDisplay = if (breakHours > 0) {
        String.format(Locale.ENGLISH, "%02d:%02d:%02d", breakHours, breakMinutes, breakSecs)
    } else {
        String.format(Locale.ENGLISH, "%02d:%02d", breakMinutes, breakSecs)
    }

    val radarTransition = rememberInfiniteTransition(label = "amberPulse")
    val pulseDotAlpha by radarTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amberDot"
    )

    val resumeInteraction = remember { MutableInteractionSource() }
    val isResumePressed by resumeInteraction.collectIsPressedAsState()
    val resumeScale by animateFloatAsState(
        targetValue = if (isResumePressed) 0.95f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "resumeScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(256.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. SVG Circular Gauge Accent with Amber Arc (viewBox 0 0 240 240, r=106, stroke=10)
            // Active arc: stroke="#f59e0b", stroke-dasharray="666", stroke-dashoffset="440" -> 122 degrees
            Box(
                modifier = Modifier
                    .size(232.dp)
                    .drawBehind {
                        val strokeW = 10.dp.toPx()
                        // Track: stroke="#f1f5f9"
                        drawCircle(
                            color = Slate100,
                            style = Stroke(strokeW)
                        )
                        // 1 hour = 3600 seconds max for break
                        val breakSweep = (state.breakSeconds.toFloat() / 3600f) * 360f
                        drawArc(
                            color = Amber500,
                            startAngle = -90f,
                            sweepAngle = breakSweep.coerceIn(0f, 360f),
                            useCenter = false,
                            style = Stroke(strokeW, cap = StrokeCap.Round)
                        )
                    }
            )

            // 2. Outer decorative pulse ring: inset-2 (8.dp) border border-amber-200/60
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .border(1.dp, Amber200.copy(alpha = 0.6f), CircleShape)
            )

            // 3. Central interactive container (w-48 h-48 = 192.dp)
            Box(
                modifier = Modifier
                    .size(192.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(CircleShape)
                    .background(CardSurface)
                    .border(1.dp, Slate200.copy(alpha = 0.9f), CircleShape)
                    .drawBehind {
                        // Dashed inner ring: inset-2.5 (10.dp) border-dashed border-amber-200/80
                        val inset = 10.dp.toPx()
                        drawCircle(
                            color = Amber200.copy(alpha = 0.8f),
                            radius = (size.minDimension / 2) - inset,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Top Pill: On Break • Active
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Amber50)
                            .border(1.dp, Amber200.copy(alpha = 0.8f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Amber500.copy(alpha = pulseDotAlpha))
                        )
                        Text(
                            text = "On Break • Active",
                            color = Amber600,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Break Duration Live Timer (text-3xl font-extrabold)
                    Text(
                        text = breakTimeDisplay,
                        color = Slate900,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )

                    // Subtext: Break Duration (1h Max)
                    Text(
                        text = "Break Duration (1h Max)",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // End Break Button
                    Button(
                        onClick = onResumeWork,
                        enabled = !state.isActionLoading,
                        modifier = Modifier
                            .scale(resumeScale)
                            .height(30.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        interactionSource = resumeInteraction
                    ) {
                        Text(
                            text = "End Break",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

/**
 * State 2: Circular Break Action Hero
 * Shown after clicking Check-In (Active Working).
 */
@Composable
fun CircularBreakHero(
    state: HomeUiState,
    elapsedSeconds: Long,
    onTakeBreak: () -> Unit
) {
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val durationText = remember(elapsedSeconds) {
        String.format(Locale.ENGLISH, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    // Tap feedback
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "breakButtonScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(256.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. SVG Circular Gauge Accent (viewBox 0 0 240 240, r=106, stroke=10)
            Box(
                modifier = Modifier
                    .size(232.dp)
                    .drawBehind {
                        val strokeW = 10.dp.toPx()
                        // Base track: stroke="#f1f5f9"
                        drawCircle(
                            color = Slate100,
                            style = Stroke(strokeW)
                        )
                        // Active gauge arc: stroke="#e53935", stroke-dasharray="666", stroke-dashoffset="500"
                        // 8 hours = 28800 seconds max
                        val sweep = (elapsedSeconds.toFloat() / 28800f) * 360f
                        drawArc(
                            color = BrandColor,
                            startAngle = -90f,
                            sweepAngle = sweep.coerceIn(0f, 360f),
                            useCenter = false,
                            style = Stroke(strokeW, cap = StrokeCap.Round)
                        )
                    }
            )

            // 2. Outer decorative pulse ring: border border-amber-200/60 (inset-2: 8.dp)
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .border(1.dp, Amber200.copy(alpha = 0.6f), CircleShape)
            )

            // 3. Central interactive break container button (w-48 h-48 = 192.dp)
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(192.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(CircleShape)
                    .background(CardSurface)
                    .border(1.dp, Slate200.copy(alpha = 0.9f), CircleShape)
                    .drawBehind {
                        // Dashed inner accent ring: inset-2.5 (10.dp) border-dashed border-amber-200/80
                        val inset = 10.dp.toPx()
                        drawCircle(
                            color = Amber200.copy(alpha = 0.8f),
                            radius = (size.minDimension / 2) - inset,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !state.isActionLoading,
                        onClick = onTakeBreak
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Start Break Action Pill
                    val isOnBreak = state.isOnBreak
                    val pillBg = if (isOnBreak) Color(0xFFFEF3C7) else Amber50
                    val pillBorder = if (isOnBreak) Amber600 else Amber200.copy(alpha = 0.6f)
                    val pillText = if (isOnBreak) "End Break" else "Start Break"

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(pillBg)
                            .border(1.dp, pillBorder, CircleShape)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalCafe,
                            contentDescription = "Break",
                            tint = Amber600,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = pillText,
                            color = Amber600,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Work Duration Label
                    Text(
                        text = "WORK DURATION",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Dynamic Live Counter
                    Text(
                        text = durationText,
                        color = Slate900,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Break Policy Subtext
                    val subtext = if (state.isOnBreak) {
                        val breakMin = state.breakSeconds / 60
                        val breakSec = state.breakSeconds % 60
                        String.format(Locale.ENGLISH, "On Break • %02d:%02d elapsed", breakMin, breakSec)
                    } else {
                        "Tea / Lunch • 1h allowed"
                    }
                    Text(
                        text = subtext,
                        color = if (state.isOnBreak) Amber600 else Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * State 1: Ready to Check-In or State 3: Completed
 */
@Composable
fun AttendanceClockArea(
    state: HomeUiState,
    onAction: () -> Unit
) {
    val hasStarted = !state.attendance?.login.isNullOrBlank()
    val hasEnded = !state.attendance?.logout.isNullOrBlank()
    val isLoading = state.isActionLoading || state.isStartingWork || state.isEndingWork

    val elapsedSeconds = remember(state.attendance, state.breakSeconds) {
        val loginStr = state.attendance?.login
        val logoutStr = state.attendance?.logout
        val workedMins = state.attendance?.workedMinutes
        if (loginStr != null && logoutStr != null) {
            if (workedMins != null) {
                (workedMins * 60).toLong()
            } else {
                val startTime = parseInstant(loginStr)
                val endTime = parseInstant(logoutStr)
                val diff = java.time.Duration.between(startTime, endTime).seconds
                maxOf(0L, maxOf(0L, diff) - state.breakSeconds)
            }
        } else {
            0L
        }
    }

    // Live Clock ticker formatting
    var currentTimeStr by remember {
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTimeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
        }
    }

    // Concentric pulse radar animation matching Stitch CSS
    val radarTransition = rememberInfiniteTransition(label = "radar")
    val radarScale by radarTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radarScale"
    )
    val radarAlpha by radarTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radarAlpha"
    )

    // Pulse dot for status pill (animate-pulse)
    val pulseDotAlpha by radarTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseDot"
    )

    // Interaction feedback (active:scale-95)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    // Container matching Stitch: w-64 h-64 (256.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(256.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. Outer diagonal striped track (radar-ring + striped-pattern)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(radarScale)
                    .clip(CircleShape)
                    .drawBehind {
                        val stripeColor = BrandColor.copy(alpha = 0.045f * radarAlpha)
                        val step = 14.dp.toPx()
                        val strokeW = 4.dp.toPx()
                        for (offset in -size.width.toInt()..(size.width.toInt() * 2) step step.toInt()) {
                            drawLine(
                                color = stripeColor,
                                start = Offset(offset.toFloat(), 0f),
                                end = Offset(offset.toFloat() + size.height, size.height),
                                strokeWidth = strokeW
                            )
                        }
                        // Outer border: border-slate-200/60
                        drawCircle(
                            color = Slate200.copy(alpha = 0.6f * radarAlpha),
                            style = Stroke(1.dp.toPx())
                        )
                    }
            )

            // 2. Intermediate soft shadow ring (inset-3: 232.dp)
            Box(
                modifier = Modifier
                    .size(232.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f),
                                Slate50
                            )
                        )
                    )
                    .border(1.dp, Slate100, CircleShape)
                    .shadow(elevation = 1.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.04f))
            )

            // 3. Circular Progress Ring (viewBox 0 0 260 260, r=118, stroke=5)
            Box(
                modifier = Modifier
                    .size(232.dp)
                    .drawBehind {
                        val strokeWidthPx = 5.dp.toPx()
                        // Outer shift background track circle (#f1f5f9)
                        drawCircle(
                            color = Slate100,
                            style = Stroke(strokeWidthPx)
                        )
                        // Outer active shift progress arc (#e53935)
                        val sweep = (elapsedSeconds.toFloat() / 28800f) * 360f
                        drawArc(
                            color = BrandColor,
                            startAngle = -90f,
                            sweepAngle = sweep.coerceIn(0f, 360f),
                            useCenter = false,
                            style = Stroke(strokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
            )

            // 4. Atmospheric Red Backlight Glow
            Box(
                modifier = Modifier
                    .size(216.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                BrandColor.copy(alpha = 0.22f),
                                BrandColor.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // 5. Central Touch Button Surface (w-48 h-48 = 192.dp)
            Box(
                modifier = Modifier
                    .scale(buttonScale)
                    .size(192.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = BrandColor.copy(alpha = 0.35f),
                        ambientColor = BrandColor.copy(alpha = 0.15f)
                    )
                    .clip(CircleShape)
                    .background(CardSurface)
                    .border(1.dp, Slate200.copy(alpha = 0.9f), CircleShape)
                    .drawBehind {
                        // Dashed highlight ring
                        val inset = 8.dp.toPx()
                        drawCircle(
                            color = Slate200,
                            radius = (size.minDimension / 2) - inset,
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = !isLoading && !hasEnded,
                        onClick = onAction
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Status Badge Indicator Pill
                    val pillBg = if (hasEnded) Slate100 else Emerald50
                    val pillBorder = if (hasEnded) Slate200 else Emerald100
                    val pillText = if (hasEnded) Slate600 else Emerald600
                    val dotColor = if (hasEnded) Slate400 else Emerald500
                    val statusTitle = if (hasEnded) "Completed" else "Ready"

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(pillBg)
                            .border(1.dp, pillBorder, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor.copy(alpha = pulseDotAlpha))
                        )
                        Text(
                            text = statusTitle,
                            color = pillText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Main Primary Action Label
                    val label = if (hasEnded) "Completed" else "Check-In"
                    Text(
                        text = label,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate900,
                        letterSpacing = (-0.5).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Secondary tap hint / time counter
//                    val hint = if (hasEnded) "Tap to record next session" else "Current time: $currentTimeStr"
                    val hint = "Current time: $currentTimeStr"
                    Text(
                        text = hint,
                        color = Slate400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * State 2: Check-Out Action Section (Wide white button)
 */
@Composable
fun CheckOutActionButton(
    isLoading: Boolean,
    isOnBreak: Boolean = false,
    onEndShift: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "checkoutButtonScale"
    )

    val isButtonEnabled = !isLoading && !isOnBreak
    val containerBg = if (isOnBreak) Slate100 else CardSurface
    val textLabel = if (isOnBreak) "End Break before Check-Out" else "End Shift / Check-Out"
    val textColor = if (isOnBreak) Slate400 else Slate700
    val iconTint = if (isOnBreak) Slate400 else BrandColor

    Button(
        onClick = onEndShift,
        enabled = isButtonEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .height(50.dp)
            .shadow(
                elevation = if (isOnBreak) 0.dp else 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.04f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerBg,
            contentColor = textColor,
            disabledContainerColor = Slate100,
            disabledContentColor = Slate400
        ),
        border = BorderStroke(1.dp, if (isOnBreak) Slate200.copy(alpha = 0.5f) else Slate200),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        interactionSource = interactionSource
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = textLabel,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }
    }
}

/**
 * Metrics Grid: Check-In, Break Time, Active Hours
 */
@Composable
fun AttendanceMetricsGrid(
    attendance: AttendanceDto?,
    isCheckedIn: Boolean,
    isOnBreak: Boolean = false,
    breakSeconds: Long = 0L,
    elapsedSeconds: Long = 0L
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Metric Card 1: Check-In
        val checkInVal = when {
            !attendance?.login.isNullOrBlank() -> formatDisplayTime(attendance?.login)
            else -> "--:--"
        }
        AttendanceStatCard(
            modifier = Modifier.weight(1f),
            title = "Check-In",
            value = checkInVal,
            icon = Icons.Rounded.AccessTime,
            iconBg = Amber50,
            iconTint = Amber500
        )

        // Metric Card 2: Break Time
        val breakMinutes = breakSeconds / 60
        val breakSecs = breakSeconds % 60
        val breakVal = String.format(Locale.ENGLISH, "%02d:%02d", breakMinutes, breakSecs)

        AttendanceStatCard(
            modifier = Modifier.weight(1f),
            title = "Break (1h Max)",
            value = breakVal,
            icon = Icons.Rounded.LocalCafe,
            iconBg = Orange50,
            iconTint = Orange500
        )

        // Metric Card 3: Total Active Hours
        val activeHours = elapsedSeconds / 3600
        val activeMins = (elapsedSeconds % 3600) / 60
        val activeHoursVal = when {
            !attendance?.hours.isNullOrBlank() -> attendance?.hours.orEmpty()
            isCheckedIn -> String.format(Locale.ENGLISH, "%02dh %02dm", activeHours, activeMins)
            else -> "--:--"
        }
        val activeHoursTitle = if (isCheckedIn) "Active Hours" else "Total Hours"

        AttendanceStatCard(
            modifier = Modifier.weight(1f),
            title = activeHoursTitle,
            value = activeHoursVal,
            icon = Icons.Rounded.BarChart,
            iconBg = Blue50,
            iconTint = Blue600
        )
    }
}

@Composable
fun AttendanceStatCard(
    modifier: Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color
) {
    Box(
        modifier = modifier
            .height(104.dp)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.04f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .border(1.dp, Slate100, RoundedCornerShape(16.dp))
    ) {
        // Top-right subtle pattern track
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(48.dp)
                .fillMaxHeight()
        ) {
            val dotColor = Slate200.copy(alpha = 0.4f)
            val step = 10.dp.toPx()
            for (x in 0..size.width.toInt() step step.toInt()) {
                for (y in 0..size.height.toInt() step step.toInt()) {
                    drawCircle(
                        color = dotColor,
                        radius = 1.2.dp.toPx(),
                        center = Offset(x.toFloat(), y.toFloat())
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon Box
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Stat Text
            Column {
                Text(
                    text = value,
                    color = Slate800,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    color = Slate400,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun DockedBottomNavigationBar(
    modifier: Modifier = Modifier,
    onHomeClick: () -> Unit = {},
    onLeavesClick: () -> Unit = {},
    onAnalyticsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    com.skypass.skytrack.ui.components.SkyTrackBottomNavigationDock(
        currentTab = com.skypass.skytrack.ui.components.SkyTrackNavTab.HOME,
        modifier = modifier,
        onHomeClick = onHomeClick,
        onLeavesClick = onLeavesClick,
        onAnalyticsClick = onAnalyticsClick,
        onProfileClick = onProfileClick
    )
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

// Strings without a timezone are legacy server values formatted in India time; ISO timestamps carry their own offset.
private fun parseInstant(timeStr: String?): java.time.Instant {
    if (timeStr.isNullOrBlank()) return java.time.Instant.now()
    val trimmed = timeStr.trim()
    return try {
        when {
            trimmed.contains("T") || trimmed.endsWith("Z", ignoreCase = true) -> {
                try {
                    java.time.Instant.parse(trimmed)
                } catch (_: Exception) {
                    try {
                        java.time.OffsetDateTime.parse(trimmed).toInstant()
                    } catch (_: Exception) {
                        java.time.LocalDateTime.parse(trimmed).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant()
                    }
                }
            }
            trimmed.contains("AM", ignoreCase = true) || trimmed.contains("PM", ignoreCase = true) -> {
                val localTime = LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).atTime(localTime).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant()
            }
            trimmed.count { it == ':' } == 2 -> {
                val localTime = LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("HH:mm:ss"))
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).atTime(localTime).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant()
            }
            trimmed.count { it == ':' } == 1 -> {
                val localTime = LocalTime.parse(trimmed, DateTimeFormatter.ofPattern("HH:mm"))
                java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).atTime(localTime).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant()
            }
            else -> {
                java.time.Instant.now()
            }
        }
    } catch (_: Exception) {
        java.time.Instant.now()
    }
}

private fun formatDisplayTime(timeStr: String?): String {
    if (timeStr.isNullOrBlank()) return "--:--"
    return try {
        val instant = parseInstant(timeStr)
        instant.atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH))
    } catch (_: Exception) {
        timeStr
    }
}
