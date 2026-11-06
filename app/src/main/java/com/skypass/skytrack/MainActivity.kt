package com.skypass.skytrack

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.notifications.NotificationPreferences
import com.skypass.skytrack.data.notifications.PushManager
import com.skypass.skytrack.data.notifications.PushNotifier
import com.skypass.skytrack.data.repository.AuthRepository
import com.skypass.skytrack.ui.notifications.NotificationsScreen
import com.skypass.skytrack.ui.notifications.NotificationsViewModel
import com.skypass.skytrack.ui.home.HomeScreen
import com.skypass.skytrack.ui.home.HomeViewModel
import com.skypass.skytrack.ui.home.SalaryViewModel
import com.skypass.skytrack.ui.monthly.PayslipsViewModel
import com.skypass.skytrack.ui.leave.LeaveScreen
import com.skypass.skytrack.ui.leave.LeaveViewModel
import com.skypass.skytrack.ui.login.LoginScreen
import com.skypass.skytrack.ui.login.LoginViewModel
import com.skypass.skytrack.ui.monthly.MonthlyStatisticsScreen
import com.skypass.skytrack.ui.monthly.MonthlyStatisticsViewModel
import com.skypass.skytrack.ui.profile.ChangePasswordScreen
import com.skypass.skytrack.ui.profile.ProfileScreen
import com.skypass.skytrack.ui.profile.ProfileViewModel
import com.skypass.skytrack.ui.splash.SplashScreen
import com.skypass.skytrack.ui.theme.SkyTrackTheme
import com.skypass.skytrack.data.local.ConnectivityObserver
import com.skypass.skytrack.data.sync.OfflineSyncManager
import com.skypass.skytrack.ui.components.OfflineBanner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import dagger.hilt.android.AndroidEntryPoint
import com.skypass.skytrack.data.auth.BiometricAuth
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
// FragmentActivity (a ComponentActivity) because the biometric prompt is hosted in a fragment
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var connectivityObserver: ConnectivityObserver

    @Inject
    lateinit var offlineSyncManager: OfflineSyncManager

    @Inject
    lateinit var appUpdateManager: com.skypass.skytrack.data.update.AppUpdateManager

    @Inject
    lateinit var pushManager: PushManager

    @Inject
    lateinit var notificationPreferences: NotificationPreferences

    // Screen named by a tapped push notification, handled once the session is loaded
    private var pendingOpenScreen by mutableStateOf<String?>(null)

    private val loginViewModel: LoginViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val salaryViewModel: SalaryViewModel by viewModels()
    private val payslipsViewModel: PayslipsViewModel by viewModels()
    private val leaveViewModel: LeaveViewModel by viewModels()
    private val monthlyStatisticsViewModel: MonthlyStatisticsViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()
    private val notificationsViewModel: NotificationsViewModel by viewModels()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenScreen = intent.getStringExtra(PushNotifier.EXTRA_OPEN_SCREEN)
    }

    // Android may recreate the activity while a tapped notification is still being opened (e.g. right after a
    // cold start), so the request survives recreation until it has been handled
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_OPEN_SCREEN, pendingOpenScreen)
    }

    private fun signOut(session: Session?) {
        loginViewModel.resetState()
        loginViewModel.suppressAutoBiometricPrompt()
        lifecycleScope.launch {
            session?.let { pushManager.unregisterCurrentDevice(it.token) }
            authRepository.logout()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOpenScreen = if (savedInstanceState == null) {
            intent?.getStringExtra(PushNotifier.EXTRA_OPEN_SCREEN)
        } else {
            savedInstanceState.getString(STATE_OPEN_SCREEN)
        }

        setContent {
            SkyTrackTheme {
                Surface(modifier = Modifier.fillMaxSize()) {

                    val session by authRepository.session
                        .collectAsState(initial = null)

                    val networkStatus by connectivityObserver.observe().collectAsState(
                        initial = if (connectivityObserver.isConnected()) ConnectivityObserver.Status.AVAILABLE else ConnectivityObserver.Status.UNAVAILABLE
                    )
                    val isSyncing by offlineSyncManager.isSyncing.collectAsState()
                    val isOffline = networkStatus != ConnectivityObserver.Status.AVAILABLE

                    val loginState by loginViewModel.state.collectAsState()
                    val rememberedAccount by loginViewModel.rememberedAccount.collectAsState()

                    var showLeave by rememberSaveable {
                        mutableStateOf(false)
                    }

                    var showMonthlyStatistics by rememberSaveable {
                        mutableStateOf(false)
                    }

                    var showProfile by rememberSaveable {
                        mutableStateOf(false)
                    }

                    var showChangePassword by rememberSaveable {
                        mutableStateOf(false)
                    }

                    var showNotifications by rememberSaveable {
                        mutableStateOf(false)
                    }

                    val unreadNotifications by notificationsViewModel.unreadCount.collectAsState()

                    val openScreen: (String) -> Unit = { screen ->
                        showNotifications = false
                        showChangePassword = false
                        showProfile = screen == "profile"
                        showLeave = screen == "leave"
                        showMonthlyStatistics = screen == "monthly"
                    }

                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { }

                    var splashAnimationFinished by remember {
                        mutableStateOf(false)
                    }

                    var isSessionLoaded by remember {
                        mutableStateOf(false)
                    }

                    val updateInfo by appUpdateManager.updateInfo.collectAsState()
                    val context = androidx.compose.ui.platform.LocalContext.current

                    LaunchedEffect(Unit) {
                        authRepository.session.first()
                        isSessionLoaded = true
                    }

                    LaunchedEffect(session) {
                        if (session == null) {
                            loginViewModel.resetState()
                        }
                    }

                    // Push registration and the bell count for the signed-in employee
                    LaunchedEffect(session?.token) {
                        val token = session?.token ?: return@LaunchedEffect
                        notificationsViewModel.refreshUnreadCount(token)
                        pushManager.registerCurrentDevice(token)
                        // Android 13+: ask once; afterwards the Profile switch handles it
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                            notificationPreferences.isEnabled() &&
                            notificationPreferences.claimPermissionPrompt()
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    LaunchedEffect(pendingOpenScreen, session?.token, isSessionLoaded) {
                        val screen = pendingOpenScreen ?: return@LaunchedEffect
                        if (!isSessionLoaded || session == null) return@LaunchedEffect
                        openScreen(screen)
                        pendingOpenScreen = null
                    }

                    // Check for updates asynchronously once the splash finishes
                    LaunchedEffect(splashAnimationFinished) {
                        if (splashAnimationFinished) {
                            appUpdateManager.checkForUpdates()
                        }
                    }

                    val showSplash = !splashAnimationFinished || !isSessionLoaded

                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            showSplash -> {
                                SplashScreen(
                                    onFinished = {
                                        splashAnimationFinished = true
                                    }
                                )
                            }
                            session == null -> {
                                showLeave = false
                                showMonthlyStatistics = false
                                showProfile = false
                                showChangePassword = false
                                showNotifications = false

                                val biometricAccount by loginViewModel.biometricAccount.collectAsState()
                                val startBiometricLogin = {
                                    lifecycleScope.launch { loginViewModel.biometricLogin(this@MainActivity) }
                                    Unit
                                }
                                LoginScreen(
                                    state = loginState,
                                    onLogin = loginViewModel::login,
                                    onSuccess = {},
                                    onClearError = loginViewModel::clearError,
                                    rememberedAccount = rememberedAccount,
                                    biometricEmployeeId = biometricAccount?.employeeId?.takeIf {
                                        BiometricAuth.availability(context) == BiometricAuth.Availability.AVAILABLE
                                    },
                                    onBiometricLogin = startBiometricLogin,
                                    consumeAutoBiometricPrompt = loginViewModel::consumeAutoBiometricPrompt
                                )
                            }

                            showNotifications -> {
                                NotificationsScreen(
                                    session = session!!,
                                    viewModel = notificationsViewModel,
                                    onBack = {
                                        showNotifications = false
                                    },
                                    onOpenScreen = openScreen
                                )
                            }

                            showChangePassword -> {
                                ChangePasswordScreen(
                                    session = session!!,
                                    onBack = {
                                        showChangePassword = false
                                    },
                                    onNavigateHome = {
                                        showChangePassword = false
                                        showProfile = false
                                        showLeave = false
                                        showMonthlyStatistics = false
                                    },
                                    onNavigateLeaves = {
                                        showChangePassword = false
                                        showProfile = false
                                        showMonthlyStatistics = false
                                        showLeave = true
                                    },
                                    onNavigateStatistics = {
                                        showChangePassword = false
                                        showProfile = false
                                        showLeave = false
                                        showMonthlyStatistics = true
                                    }
                                )
                            }

                            showProfile -> {
                                ProfileScreen(
                                    session = session!!,
                                    viewModel = profileViewModel,
                                    onLogout = {
                                        signOut(session)
                                    },
                                    onNavigateHome = {
                                        showProfile = false
                                        showLeave = false
                                        showMonthlyStatistics = false
                                    },
                                    onNavigateLeaves = {
                                        showProfile = false
                                        showMonthlyStatistics = false
                                        showLeave = true
                                    },
                                    onNavigateStatistics = {
                                        showProfile = false
                                        showLeave = false
                                        showMonthlyStatistics = true
                                    },
                                    onChangePasswordClick = {
                                        showChangePassword = true
                                    }
                                )
                            }

                            showMonthlyStatistics -> {
                                MonthlyStatisticsScreen(
                                    viewModel = monthlyStatisticsViewModel,
                                    payslipsViewModel = payslipsViewModel,
                                    session = session!!,
                                    onBack = {
                                        showMonthlyStatistics = false
                                    },
                                    onOpenLeave = {
                                        showMonthlyStatistics = false
                                        showLeave = true
                                    },
                                    onOpenProfile = {
                                        showMonthlyStatistics = false
                                        showLeave = false
                                        showProfile = true
                                    }
                                )
                            }

                            showLeave -> {
                                LeaveScreen(
                                    session = session!!,
                                    viewModel = leaveViewModel,
                                    onBack = {
                                        showLeave = false
                                    },
                                    onNavigateHome = {
                                        showLeave = false
                                    },
                                    onNavigateStatistics = {
                                        showLeave = false
                                        showMonthlyStatistics = true
                                    },
                                    onNavigateProfile = {
                                        showLeave = false
                                        showMonthlyStatistics = false
                                        showProfile = true
                                    }
                                )
                            }

                            else -> {
                                HomeScreen(
                                    session = session!!,
                                    viewModel = homeViewModel,
                                    salaryViewModel = salaryViewModel,
                                    onLogout = {
                                        signOut(session)
                                    },
                                    onOpenLeave = {
                                        showLeave = true
                                    },
                                    onOpenMonthlyStatistics = {
                                        showMonthlyStatistics = true
                                    },
                                    onOpenProfile = {
                                        showProfile = true
                                    },
                                    unreadNotifications = unreadNotifications,
                                    onOpenNotifications = {
                                        showNotifications = true
                                    }
                                )
                            }
                        }

                        if (!showSplash && session != null) {
                            OfflineBanner(
                                isOffline = isOffline,
                                isSyncing = isSyncing,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .statusBarsPadding()
                            )
                        }

                        // In-app Update Dialog (prompted once per session, dismissed via "Later" without interrupting app)
                        if (updateInfo != null && !showSplash) {
                            com.skypass.skytrack.ui.components.AppUpdateDialog(
                                updateInfo = updateInfo!!,
                                onUpdateClick = { releaseUrl ->
                                    try {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(releaseUrl)
                                        ).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Unable to open browser. Please visit: $releaseUrl",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                onDismiss = {
                                    appUpdateManager.dismissUpdate()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val STATE_OPEN_SCREEN = "open_screen"
    }
}
