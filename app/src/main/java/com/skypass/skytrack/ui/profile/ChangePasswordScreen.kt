package com.skypass.skytrack.ui.profile

import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import com.skypass.skytrack.data.auth.findFragmentActivity
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.ui.components.SkyTrackBottomNavigationDock
import com.skypass.skytrack.ui.components.SkyTrackNavTab

// Palette matching Tailwind Stitch Configuration
private val BgSurface = Color(0xFFFAF9FD)
private val CardBg = Color(0xFFFFFFFF)
private val BrandRed = Color(0xFFA20513)
private val BrandCrimson = Color(0xFFC62828)
private val BrandRedLight = Color(0xFFFEF2F2)
private val RedBorder = Color(0xFFFEE2E2)

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
private val Emerald600 = Color(0xFF059669)
private val Emerald50 = Color(0xFFECFDF5)
private val Emerald100 = Color(0xFFD1FAE5)

@Composable
fun ChangePasswordScreen(
    session: Session,
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateLeaves: () -> Unit,
    onNavigateStatistics: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by viewModel.busy.collectAsState()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    // Security Policy Evaluation
    val hasMinLength = newPassword.length >= 8
    val hasUpperAndLower = newPassword.any { it.isUpperCase() } && newPassword.any { it.isLowerCase() }
    val hasDigit = newPassword.any { it.isDigit() }
    val hasSpecial = newPassword.any { !it.isLetterOrDigit() }

    val metCount = listOf(hasMinLength, hasUpperAndLower, hasDigit, hasSpecial).count { it }
    val passwordsMatch = confirmPassword.isNotEmpty() && confirmPassword == newPassword

    val complexityLabel = when (metCount) {
        0 -> "Enter Password"
        1 -> "Weak Security"
        2 -> "Moderate Security"
        3 -> "Good Security"
        else -> "Strong Security"
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
            // Top Bar Header with Circular Back Button
            ChangePasswordTopBar(
                employeeId = session.employeeId.ifBlank { "" },
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Notice Card: Protect Your Enterprise Account
            NoticeSecurityCard()

            Spacer(modifier = Modifier.height(16.dp))

            // Form Container & Password Input Fields
            CredentialsFormCard(
                currentPassword = currentPassword,
                onCurrentPasswordChange = { currentPassword = it },
                showCurrentPassword = showCurrentPassword,
                onToggleCurrentPassword = { showCurrentPassword = !showCurrentPassword },
                onVerifyCurrentPassword = {
                    if (!busy) {
                        scope.launch {
                            val message = viewModel.verifyCurrentPassword(session.employeeId, currentPassword)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                newPassword = newPassword,
                onNewPasswordChange = { newPassword = it },
                showNewPassword = showNewPassword,
                onToggleNewPassword = { showNewPassword = !showNewPassword },
                confirmPassword = confirmPassword,
                onConfirmPasswordChange = { confirmPassword = it },
                showConfirmPassword = showConfirmPassword,
                onToggleConfirmPassword = { showConfirmPassword = !showConfirmPassword },
                metCount = metCount,
                complexityLabel = complexityLabel,
                passwordsMatch = passwordsMatch
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Security Policy Standard Card
            SecurityPolicyStandardCard(
                hasMinLength = hasMinLength,
                hasUpperAndLower = hasUpperAndLower,
                hasDigit = hasDigit,
                hasSpecial = hasSpecial,
                metCount = metCount
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Action & Links
            ActionsAndFooter(
                onUpdatePassword = {
                    if (currentPassword.isBlank()) {
                        Toast.makeText(context, "Please enter your current password", Toast.LENGTH_SHORT).show()
                        return@ActionsAndFooter
                    }
                    if (newPassword.isBlank()) {
                        Toast.makeText(context, "Please create a new password", Toast.LENGTH_SHORT).show()
                        return@ActionsAndFooter
                    }
                    if (metCount < 4) {
                        Toast.makeText(context, "Please fulfill all security requirements", Toast.LENGTH_SHORT).show()
                        return@ActionsAndFooter
                    }
                    if (!passwordsMatch) {
                        Toast.makeText(context, "New passwords do not match", Toast.LENGTH_SHORT).show()
                        return@ActionsAndFooter
                    }
                    if (busy) return@ActionsAndFooter
                    scope.launch {
                        when (val outcome = viewModel.changePassword(
                            activity = context.findFragmentActivity(),
                            session = session,
                            currentPassword = currentPassword,
                            newPassword = newPassword
                        )) {
                            is ChangePasswordViewModel.Outcome.Changed -> {
                                Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                                onBack()
                            }
                            is ChangePasswordViewModel.Outcome.Error ->
                                Toast.makeText(context, outcome.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onContactHr = {
                    Toast.makeText(context, "Contact HR at support@skypass.ae", Toast.LENGTH_SHORT).show()
                },
                onCancel = onBack
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
            onProfileClick = onBack
        )

        com.skypass.skytrack.ui.components.SkyTrackLoadingOverlay(visible = busy)
    }
}

// -------------------------------------------------------------
// TOP BAR HEADER
// -------------------------------------------------------------
@Composable
private fun ChangePasswordTopBar(
    employeeId: String,
    onBack: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Circular Back Button
        Surface(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            shape = CircleShape,
            color = CardBg,
            border = BorderStroke(1.dp, Slate200.copy(alpha = 0.8f)),
            shadowElevation = 2.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = "Go back",
                    tint = Slate700,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "MY ACCOUNT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Slate400
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Emerald500.copy(alpha = dotAlpha))
                )
                Text(
                    text = "• $employeeId",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = Slate400
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Change Password",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Slate900,
                letterSpacing = (-0.5).sp
            )
        }
    }
}

// -------------------------------------------------------------
// NOTICE CARD
// -------------------------------------------------------------
@Composable
private fun NoticeSecurityCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp), spotColor = BrandCrimson.copy(alpha = 0.35f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandCrimson),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "Security Shield",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Protect Your Enterprise Account",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Choose a robust password with at least 8 characters, combining uppercase, lowercase, digits, and special characters.",
                    fontSize = 11.sp,
                    color = Slate500,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// FORM CONTAINER & PASSWORD INPUT FIELDS
// -------------------------------------------------------------
@Composable
private fun CredentialsFormCard(
    currentPassword: String,
    onCurrentPasswordChange: (String) -> Unit,
    showCurrentPassword: Boolean,
    onToggleCurrentPassword: () -> Unit,
    onVerifyCurrentPassword: () -> Unit,
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    showNewPassword: Boolean,
    onToggleNewPassword: () -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    showConfirmPassword: Boolean,
    onToggleConfirmPassword: () -> Unit,
    metCount: Int,
    complexityLabel: String,
    passwordsMatch: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PASSWORD CREDENTIALS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Slate400
                )
                Text(
                    text = "ENCRYPTED SSL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    color = Slate400
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Slate100, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Field 1: Current Password
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Current Password",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate700
                )
                Text(
                    text = "Verify",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandCrimson,
                    modifier = Modifier.clickable(onClick = onVerifyCurrentPassword)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            PasswordInputField(
                value = currentPassword,
                onValueChange = onCurrentPasswordChange,
                placeholder = "Enter active password",
                showPassword = showCurrentPassword,
                onToggleShowPassword = onToggleCurrentPassword,
                leftIcon = Icons.Rounded.Lock
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter your active operational password",
                fontSize = 10.sp,
                color = Slate400
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Field 2: New Password
            Text(
                text = "New Password",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate700
            )

            Spacer(modifier = Modifier.height(6.dp))

            PasswordInputField(
                value = newPassword,
                onValueChange = onNewPasswordChange,
                placeholder = "Create new password",
                showPassword = showNewPassword,
                onToggleShowPassword = onToggleNewPassword,
                leftIcon = Icons.Rounded.Lock
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Complexity Score Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = "Complexity",
                        tint = BrandCrimson,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Complexity Score",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Slate500
                    )
                }

                Text(
                    text = complexityLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandCrimson
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4-Bar Pill Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 1..4) {
                    val isActive = i <= metCount
                    val barColor by animateColorAsState(
                        targetValue = if (isActive) BrandCrimson else Slate200,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "barColor$i"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(barColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Field 3: Confirm New Password
            Text(
                text = "Confirm New Password",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Slate700
            )

            Spacer(modifier = Modifier.height(6.dp))

            PasswordInputField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                placeholder = "Re-enter new password",
                showPassword = showConfirmPassword,
                onToggleShowPassword = onToggleConfirmPassword,
                leftIcon = Icons.Rounded.Shield,
                trailingSuccess = passwordsMatch
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (confirmPassword.isNotEmpty()) {
                if (passwordsMatch) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Emerald500)
                        )
                        Text(
                            text = "Passkeys match securely",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Emerald600
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BrandCrimson)
                        )
                        Text(
                            text = "Passwords do not match",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = BrandCrimson
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    showPassword: Boolean,
    onToggleShowPassword: () -> Unit,
    leftIcon: ImageVector,
    trailingSuccess: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Slate50)
            .border(1.dp, Slate200, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = leftIcon,
                contentDescription = null,
                tint = Slate400,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 12.sp,
                        color = Slate400,
                        fontFamily = FontFamily.Monospace
                    )
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation('•'),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = TextStyle(
                        color = Slate800,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = if (showPassword) 0.5.sp else 2.sp
                    ),
                    cursorBrush = SolidColor(BrandCrimson),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (trailingSuccess) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Emerald500),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Match",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = "Toggle password visibility",
                    tint = Slate400,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleShowPassword
                        )
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SECURITY POLICY STANDARD CARD
// -------------------------------------------------------------
@Composable
private fun SecurityPolicyStandardCard(
    hasMinLength: Boolean,
    hasUpperAndLower: Boolean,
    hasDigit: Boolean,
    hasSpecial: Boolean,
    metCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBg,
        border = BorderStroke(1.dp, Slate100),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Security Policy Standard",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(BrandRedLight)
                        .border(1.dp, RedBorder, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$metCount / 4 Complete",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandCrimson
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Slate100, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Criteria Rows
            PolicyCheckItem(
                isMet = hasMinLength,
                text = "At least 8 characters long"
            )
            Spacer(modifier = Modifier.height(10.dp))

            PolicyCheckItem(
                isMet = hasUpperAndLower,
                text = "Contains uppercase & lowercase letters"
            )
            Spacer(modifier = Modifier.height(10.dp))

            PolicyCheckItem(
                isMet = hasDigit,
                text = "Includes at least one numeric digit (0-9)"
            )
            Spacer(modifier = Modifier.height(10.dp))

            PolicyCheckItem(
                isMet = hasSpecial,
                text = "Includes a special character (@, #, $, %, etc.)"
            )
        }
    }
}

@Composable
private fun PolicyCheckItem(
    isMet: Boolean,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .shadow(elevation = if (isMet) 2.dp else 0.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(if (isMet) BrandCrimson else Slate200),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = if (isMet) Color.White else Slate400,
                modifier = Modifier.size(10.dp)
            )
        }

        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isMet) Slate800 else Slate600
        )
    }
}

// -------------------------------------------------------------
// PRIMARY ACTIONS & LINKS
// -------------------------------------------------------------
@Composable
private fun ActionsAndFooter(
    onUpdatePassword: () -> Unit,
    onContactHr: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Update Password Action Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), spotColor = BrandCrimson.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onUpdatePassword),
            shape = RoundedCornerShape(16.dp),
            color = BrandCrimson
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Update Password",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Update Password",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Link: Forgot current password? Contact HR
        Text(
            text = "Forgot current password? Contact HR",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = BrandCrimson,
            modifier = Modifier.clickable(onClick = onContactHr)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Link: Cancel
        Text(
            text = "Cancel",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Slate400,
            modifier = Modifier.clickable(onClick = onCancel)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "SkyTrack Mobile v${com.skypass.skytrack.BuildConfig.VERSION_NAME} • Skypass Visa Services",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Slate400,
            textAlign = TextAlign.Center
        )
    }
}
