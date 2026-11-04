package com.skypass.skytrack.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skypass.skytrack.data.notifications.PushNotifier
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.skypass.skytrack.util.ImageCompressor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.ui.components.SkyTrackBottomNavigationDock
import com.skypass.skytrack.ui.components.SkyTrackNavTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// Palette matching Tailwind Stitch Configuration
private val BgSurface = Color(0xFFFAF9FD)
private val CardBg = Color(0xFFFFFFFF)
private val BrandRed = Color(0xFFA20513)
private val BrandCrimson = Color(0xFFC62828)
private val LightRed = Color(0xFFFFE0DD)
private val RedBg50 = Color(0xFFFEF2F2)
private val RedBorderDashed = Color(0xFFFCA5A5)

private val Slate900 = Color(0xFF0F172A)
private val Slate800 = Color(0xFF1E293B)
private val Slate700 = Color(0xFF334155)
private val Slate600 = Color(0xFF475569)
private val Slate500 = Color(0xFF64748B)
private val Slate400 = Color(0xFF94A3B8)
private val Slate300 = Color(0xFFCBD5E1)
private val Slate200 = Color(0xFFE2E8F0)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate50 = Color(0xFFF8FAFC)

private val Emerald500 = Color(0xFF10B981)
private val Emerald50 = Color(0xFFECFDF5)
private val Emerald100 = Color(0xFFD1FAE5)
private val Emerald600 = Color(0xFF059669)
private val Emerald700 = Color(0xFF047857)

private val Purple50 = Color(0xFFFAF5FF)
private val Purple600 = Color(0xFF9333EA)

@Composable
fun ProfileScreen(
    session: Session,
    onLogout: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateLeaves: () -> Unit,
    onNavigateStatistics: () -> Unit,
    onChangePasswordClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val profileUiState by viewModel.uiState.collectAsState()

    LaunchedEffect(session.token) {
        viewModel.loadProfileData(session)
    }

    // Notifications show only when this switch is on and Android allows SkyTrack to post them
    val notificationsPreferred by viewModel.notificationsEnabled.collectAsState()
    var systemAllowsNotifications by remember { mutableStateOf(PushNotifier.canShow(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemAllowsNotifications = PushNotifier.canShow(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val openNotificationSettings = {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
        Toast.makeText(context, "Allow notifications for SkyTrack to receive alerts.", Toast.LENGTH_LONG).show()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        systemAllowsNotifications = PushNotifier.canShow(context)
        if (!granted) openNotificationSettings()
    }
    val punchInAlertsEnabled = notificationsPreferred && systemAllowsNotifications
    val onTogglePunchInAlerts = {
        if (punchInAlertsEnabled) {
            viewModel.setNotificationsEnabled(false)
        } else {
            viewModel.setNotificationsEnabled(true)
            if (!systemAllowsNotifications) {
                val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (canAsk) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openNotificationSettings()
                }
            }
        }
    }
    var showPhotoSourceSheet by remember { mutableStateOf(false) }

    // Active photo URL from live UI state or session fallback
    val currentPhotoUrl = profileUiState.profilePhotoUrl.ifBlank { session.profilePhotoUrl }
    var loadedAvatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Asynchronously load the compressed avatar from DB / DataStore (base64 or remote URL)
    LaunchedEffect(currentPhotoUrl) {
        if (currentPhotoUrl.isBlank()) {
            loadedAvatarBitmap = null
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                if (currentPhotoUrl.startsWith("data:image/") || (!currentPhotoUrl.startsWith("http://") && !currentPhotoUrl.startsWith("https://"))) {
                    val bmp = ImageCompressor.decodeBase64ToBitmap(currentPhotoUrl)
                    if (bmp != null) {
                        loadedAvatarBitmap = bmp.asImageBitmap()
                    } else {
                        loadedAvatarBitmap = null
                    }
                } else {
                    val url = URL(currentPhotoUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.doInput = true
                    conn.connect()
                    val stream = conn.inputStream
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        loadedAvatarBitmap = bmp.asImageBitmap()
                    } else {
                        loadedAvatarBitmap = null
                    }
                }
            } catch (_: Exception) {
                loadedAvatarBitmap = null
            }
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            showPhotoSourceSheet = false
            Toast.makeText(context, "Compressing & saving photo...", Toast.LENGTH_SHORT).show()
            viewModel.updateProfilePhoto(
                session = session,
                bitmap = bitmap,
                onSuccess = {
                    Toast.makeText(context, "Profile photo saved to database", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    Toast.makeText(context, "Failed to save photo: $err", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(context, "Camera permission is required to capture photo", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            showPhotoSourceSheet = false
            val bmp = ImageCompressor.decodeSampledBitmapFromUri(context, uri, reqSize = 512)
            if (bmp != null) {
                Toast.makeText(context, "Compressing & saving photo...", Toast.LENGTH_SHORT).show()
                viewModel.updateProfilePhoto(
                    session = session,
                    bitmap = bmp,
                    onSuccess = {
                        Toast.makeText(context, "Profile photo saved to database", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        Toast.makeText(context, "Failed to save photo: $err", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                Toast.makeText(context, "Failed to read image from gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .padding(bottom = 96.dp)
        ) {
            // Clean Top Bar Header
            ProfileTopHeader()

            Spacer(modifier = Modifier.height(20.dp))

            // Circular Profile Hero Section
            ProfileHeroSection(
                name = session.name.ifBlank { "" },
                employeeId = session.employeeId.ifBlank { "" },
                role = session.role.ifBlank { "" },
                avatarBitmap = loadedAvatarBitmap,
                isUpdatingPhoto = profileUiState.isUpdatingPhoto,
                onEditPhotoClick = {
                    showPhotoSourceSheet = true
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Trio Metric Cards (Attendance, Leave Quota, YTD Overtime)
            TrioMetricsSection(
                attendancePercent = profileUiState.attendancePercent,
                leaveQuota = profileUiState.leaveQuota,
                overtimeWork = profileUiState.overtimeWork
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Employment Details Card
            EmploymentDetailsCard(
                reportingManager = profileUiState.reportingManager,
                department = profileUiState.department,
                workShift = profileUiState.workShift
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Preferences & Settings List Card
            PreferencesCard(
                punchInAlertsEnabled = punchInAlertsEnabled,
                onTogglePunchInAlerts = onTogglePunchInAlerts,
                onChangePasswordClick = onChangePasswordClick
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Action: Sign Out & Version Footer
            SignOutSection(
                onLogout = onLogout,
                onCheckForUpdates = {
                    viewModel.checkForUpdatesManual { _, msg ->
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Floating Bottom Navigation Dock (Active on PROFILE)
        SkyTrackBottomNavigationDock(
            currentTab = SkyTrackNavTab.PROFILE,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            onHomeClick = onNavigateHome,
            onLeavesClick = onNavigateLeaves,
            onAnalyticsClick = onNavigateStatistics,
            onProfileClick = { /* Already on Profile */ }
        )

        // Change Profile Photo Bottom Sheet Modal
        AnimatedVisibility(
            visible = showPhotoSourceSheet,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            ChangeProfilePhotoBottomSheet(
                onDismiss = { showPhotoSourceSheet = false },
                onCameraClick = {
                    val hasCamPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasCamPermission) {
                        cameraLauncher.launch(null)
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onGalleryClick = {
                    galleryLauncher.launch("image/*")
                },
                onRemovePhotoClick = {
                    showPhotoSourceSheet = false
                    viewModel.removeProfilePhoto(
                        session = session,
                        onSuccess = {
                            Toast.makeText(context, "Profile photo removed", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            Toast.makeText(context, "Failed to remove photo: $err", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            )
        }
    }
}

// -------------------------------------------------------------
// CHANGE PROFILE PHOTO BOTTOM SHEET MODAL
// -------------------------------------------------------------
@Composable
private fun ChangeProfilePhotoBottomSheet(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onRemovePhotoClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 412.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent dismiss when clicking content */ }
                ),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = CardBg,
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                // Drag Bar Handle
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Slate300)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Sheet Header: Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Change Profile Photo",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            letterSpacing = (-0.3).sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Choose a source to update your employee photo",
                            fontSize = 12.sp,
                            color = Slate500
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Slate100)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close modal",
                            tint = Slate500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Source Options List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Option 1: Camera
                    PhotoSourceOptionItem(
                        icon = Icons.Rounded.PhotoCamera,
                        title = "Camera",
                        subtitle = "Take a new photo with your camera",
                        isDestructive = false,
                        isDashed = false,
                        onClick = onCameraClick
                    )

                    // Option 2: Choose from Gallery
                    PhotoSourceOptionItem(
                        icon = Icons.Rounded.Image,
                        title = "Choose from Gallery",
                        subtitle = "Select an image from device library",
                        isDestructive = false,
                        isDashed = false,
                        onClick = onGalleryClick
                    )

                    // Option 3: Remove Current Photo
                    PhotoSourceOptionItem(
                        icon = Icons.Rounded.Delete,
                        title = "Remove Current Photo",
                        subtitle = "Revert to default employee initials",
                        isDestructive = true,
                        isDashed = true,
                        onClick = onRemovePhotoClick
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Cancel Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss),
                    shape = RoundedCornerShape(12.dp),
                    color = Slate100
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate700
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoSourceOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean,
    isDashed: Boolean,
    onClick: () -> Unit
) {
    val titleColor = if (isDestructive) BrandCrimson else Slate800

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .then(
                if (isDashed) {
                    Modifier.drawBehind {
                        val stroke = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                        drawRoundRect(
                            color = RedBorderDashed,
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            style = stroke
                        )
                    }
                } else {
                    Modifier.border(1.dp, Slate100, RoundedCornerShape(16.dp))
                }
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RedBg50),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = BrandCrimson,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = Slate400
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Slate400,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// TOP BAR HEADER
// -------------------------------------------------------------
@Composable
private fun ProfileTopHeader() {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "MY ACCOUNT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = Slate400
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Emerald500)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Employee Profile",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Slate900,
            letterSpacing = (-0.5).sp
        )
    }
}

// -------------------------------------------------------------
// CIRCULAR PROFILE HERO SECTION
// -------------------------------------------------------------
@Composable
private fun ProfileHeroSection(
    name: String,
    employeeId: String,
    role: String,
    avatarBitmap: ImageBitmap?,
    isUpdatingPhoto: Boolean = false,
    onEditPhotoClick: () -> Unit
) {
    // Pulse animation for Active Employment status dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Outer Ring Avatar Container with ambient glow & floating badge
        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ambient Outer Glow
            Box(
                modifier = Modifier
                    .size(102.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                LightRed.copy(alpha = 0.5f),
                                BrandCrimson.copy(alpha = 0.15f)
                            )
                        ),
                        shape = CircleShape
                    )
            )

            // Outer Ring Container
            Surface(
                modifier = Modifier.size(96.dp),
                shape = CircleShape,
                color = CardBg,
                border = BorderStroke(1.dp, Slate100),
                shadowElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(Slate100),
                    contentAlignment = Alignment.Center
                ) {
                    if (isUpdatingPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = BrandCrimson,
                            strokeWidth = 3.dp
                        )
                    } else if (avatarBitmap != null) {
                        Image(
                            bitmap = avatarBitmap,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Monogram Fallback
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.split(" ")
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .mapNotNull { it.firstOrNull()?.uppercase() }
                                    .joinToString("")
                                    .ifBlank { "ST" },
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate700
                            )
                        }
                    }
                }
            }

            // Floating Red Edit Icon Badge Button
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.BottomEnd)
                    .shadow(elevation = 6.dp, shape = CircleShape, spotColor = BrandCrimson.copy(alpha = 0.45f))
                    .clip(CircleShape)
                    .border(2.dp, CardBg, CircleShape)
                    .background(BrandCrimson)
                    .clickable(onClick = onEditPhotoClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit Profile Photo",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Name & Identity Badges
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
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
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = Slate700
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Role & Office Subtitle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = role.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Slate500
            )
//            Box(
//                modifier = Modifier
//                    .size(4.dp)
//                    .clip(CircleShape)
//                    .background(Slate300)
//            )
//            Text(
//                text = "Skypass Dubai HQ",
//                fontSize = 12.sp,
//                fontWeight = FontWeight.SemiBold,
//                color = Slate600
//            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Active Employment Pill Chip
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Emerald50)
                .border(1.dp, Emerald100, CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Emerald500.copy(alpha = pulseAlpha))
            )
            Text(
                text = "Active Employment • Confirmed",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Emerald700
            )
        }
    }
}

// -------------------------------------------------------------
// TRIO METRIC CARDS
// -------------------------------------------------------------
@Composable
private fun TrioMetricsSection(
    attendancePercent: String,
    leaveQuota: String,
    overtimeWork: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Attendance Card
        MetricItemCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Check,
            iconTint = Emerald600,
            iconBg = Emerald50,
            value = attendancePercent,
            label = "Attendance"
        )

        // Leave Quota Card
        MetricItemCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.CalendarMonth,
            iconTint = BrandCrimson,
            iconBg = RedBg50,
            value = leaveQuota,
            label = "Leave Quota"
        )

        // YTD Overtime Card
        MetricItemCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Schedule,
            iconTint = Purple600,
            iconBg = Purple50,
            value = overtimeWork,
            label = "YTD Overtime"
        )
    }
}

@Composable
private fun MetricItemCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    value: String,
    label: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // Subtle dot pattern background
                    val dotColor = Slate300.copy(alpha = 0.35f)
                    val step = 10.dp.toPx()
                    var x = 4.dp.toPx()
                    while (x < size.width) {
                        var y = 4.dp.toPx()
                        while (y < size.height) {
                            drawCircle(
                                color = dotColor,
                                radius = 1.dp.toPx(),
                                center = Offset(x, y)
                            )
                            y += step
                        }
                        x += step
                    }
                }
                .padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate900,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Slate400,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// -------------------------------------------------------------
// EMPLOYMENT DETAILS CARD
// -------------------------------------------------------------
@Composable
private fun EmploymentDetailsCard(
    reportingManager: String,
    department: String,
    workShift: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EMPLOYMENT DETAILS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Slate400
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Slate100, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Reporting Manager
            DetailRowItem(
                icon = Icons.Rounded.Person,
                label = "Reporting Manager",
                value = reportingManager
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Department / Unit
            DetailRowItem(
                icon = Icons.Rounded.Apartment,
                label = "Department / Unit",
                value = department
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Work Shift
            DetailRowItem(
                icon = Icons.Rounded.Schedule,
                label = "Work Shift",
                value = workShift
            )
        }
    }
}

@Composable
private fun DetailRowItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Slate50),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Slate600,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = Slate500
            )
        }
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Slate800
        )
    }
}

// -------------------------------------------------------------
// PREFERENCES & SETTINGS LIST
// -------------------------------------------------------------
@Composable
private fun PreferencesCard(
    punchInAlertsEnabled: Boolean,
    onTogglePunchInAlerts: () -> Unit,
    onChangePasswordClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        Column {
            // Item 1: Punch-in Alerts with custom switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTogglePunchInAlerts)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(RedBg50),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = "Punch-in Alerts",
                            tint = BrandCrimson,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Punch-in Alerts",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )
                        Text(
                            text = "Notify 15m before shift begins",
                            fontSize = 10.sp,
                            color = Slate400
                        )
                    }
                }

                // Custom Pill Toggle matching HTML
                CustomPillToggle(
                    checked = punchInAlertsEnabled,
                    onCheckedChange = { onTogglePunchInAlerts() }
                )
            }

            HorizontalDivider(color = Slate100, thickness = 1.dp)

            // Item 2: Change Password
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onChangePasswordClick)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate100),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Change Password",
                            tint = Slate600,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Change Password",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate800
                        )
                        Text(
                            text = "Updated 45 days ago",
                            fontSize = 10.sp,
                            color = Slate400
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Open",
                    tint = Slate400,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CustomPillToggle(
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    val trackBg = if (checked) BrandCrimson else Slate300
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(20.dp)
            .clip(CircleShape)
            .background(trackBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCheckedChange
            )
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

// -------------------------------------------------------------
// SIGN OUT BUTTON & VERSION FOOTER
// -------------------------------------------------------------
@Composable
private fun SignOutSection(
    onLogout: () -> Unit,
    onCheckForUpdates: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onLogout),
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            border = BorderStroke(1.dp, BrandCrimson.copy(alpha = 0.3f)),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(RedBg50),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Sign Out",
                        tint = BrandCrimson,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign Out of SkyTrack",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandCrimson
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCheckForUpdates)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SkyTrack Mobile v${com.skypass.skytrack.BuildConfig.VERSION_NAME} • Check for updates",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Slate400,
                textAlign = TextAlign.Center
            )
        }
    }
}
