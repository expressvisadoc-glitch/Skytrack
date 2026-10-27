package com.skypass.skytrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import com.skypass.skytrack.data.repository.AuthRepository
import com.skypass.skytrack.ui.home.HomeScreen
import com.skypass.skytrack.ui.home.HomeViewModel
import com.skypass.skytrack.ui.home.SalaryViewModel
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
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    @Inject
    lateinit var connectivityObserver: ConnectivityObserver

    @Inject
    lateinit var offlineSyncManager: OfflineSyncManager

    @Inject
    lateinit var appUpdateManager: com.skypass.skytrack.data.update.AppUpdateManager

    private val loginViewModel: LoginViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val salaryViewModel: SalaryViewModel by viewModels()
    private val leaveViewModel: LeaveViewModel by viewModels()
    private val monthlyStatisticsViewModel: MonthlyStatisticsViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

                    var showLeave by remember {
                        mutableStateOf(false)
                    }

                    var showMonthlyStatistics by remember {
                        mutableStateOf(false)
                    }

                    var showProfile by remember {
                        mutableStateOf(false)
                    }

                    var showChangePassword by remember {
                        mutableStateOf(false)
                    }

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

                                LoginScreen(
                                    state = loginState,
                                    onLogin = loginViewModel::login,
                                    onSuccess = {},
                                    onClearError = loginViewModel::clearError,
                                    rememberedAccount = rememberedAccount
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
                                        loginViewModel.resetState()
                                        lifecycleScope.launch {
                                            authRepository.logout()
                                        }
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
                                        loginViewModel.resetState()
                                        lifecycleScope.launch {
                                            authRepository.logout()
                                        }
                                    },
                                    onOpenLeave = {
                                        showLeave = true
                                    },
                                    onOpenMonthlyStatistics = {
                                        showMonthlyStatistics = true
                                    },
                                    onOpenProfile = {
                                        showProfile = true
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
}
