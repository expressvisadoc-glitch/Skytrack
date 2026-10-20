package com.skypass.skytrack.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Bitmap
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.local.SessionDataStore
import com.skypass.skytrack.data.repository.AuthRepository
import com.skypass.skytrack.data.repository.LeaveRepository
import com.skypass.skytrack.data.repository.MonthlyStatisticsRepository
import com.skypass.skytrack.util.ImageCompressor
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoading: Boolean = false,
    val attendancePercent: String = "100.0%",
    val leaveQuota: String = "18 Days",
    val overtimeWork: String = "0h 00m",
    val reportingManager: String = "Not Assigned",
    val department: String = "Operations",
    val workShift: String = "Standard Shift (09:00 AM - 06:00 PM)",
    val profilePhotoUrl: String = "",
    val isUpdatingPhoto: Boolean = false,
    val photoError: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val monthlyRepository: MonthlyStatisticsRepository,
    private val leaveRepository: LeaveRepository,
    private val sessionDataStore: SessionDataStore,
    private val authRepository: AuthRepository,
    val appUpdateManager: com.skypass.skytrack.data.update.AppUpdateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun checkForUpdatesManual(onResult: (isUpdateAvailable: Boolean, message: String) -> Unit) {
        viewModelScope.launch {
            try {
                val update = appUpdateManager.checkForUpdates(isManualCheck = true)
                if (update != null) {
                    onResult(true, "Update found: v${update.versionName}")
                } else {
                    onResult(false, "SkyTrack is up to date (v${com.skypass.skytrack.BuildConfig.VERSION_NAME}).")
                }
            } catch (e: Exception) {
                onResult(false, "Unable to check for updates right now.")
            }
        }
    }

    fun loadProfileData(session: Session) {
        val currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

        // Extract and clean session organization values
        var manager = session.managerName
            .takeIf { it.isNotBlank() }
            ?: "Not Assigned"

        var dept = session.departmentName
            .takeIf { it.isNotBlank() }
            ?: "Unassigned"

        var shift = session.workShiftName
            .takeIf { it.isNotBlank() }
            ?: "Standard Shift (09:00 AM - 06:00 PM)"

        val initialAttendance = session.attendancePercent
            .takeIf { it.isNotBlank() }
            ?: "0.0%"

        val initialLeaveQuota = session.leaveQuota
            .takeIf { it.isNotBlank() }
            ?: "0 Days"

        val initialOvertime = session.ytdOvertime
            .takeIf { it.isNotBlank() }
            ?: "0h 0m"

        val initialPhoto = session.profilePhotoUrl

        _uiState.value = ProfileUiState(
            isLoading = true,
            attendancePercent = initialAttendance,
            leaveQuota = initialLeaveQuota,
            overtimeWork = initialOvertime,
            reportingManager = manager,
            department = dept,
            workShift = shift,
            profilePhotoUrl = initialPhoto
        )

        viewModelScope.launch {
            var finalAttendance = initialAttendance
            var finalOvertime = initialOvertime
            var finalLeaveQuota = initialLeaveQuota

            // 0. Sync fresh profile from DB in background
            try {
                authRepository.refreshProfile(session.token).onSuccess { emp ->
                    val freshPhoto = emp.profilePhotoUrl
                    if (!freshPhoto.isNullOrBlank()) {
                        _uiState.update { it.copy(profilePhotoUrl = freshPhoto) }
                    }
                    val freshManager = emp.managerName?.takeIf { it.isNotBlank() }
                    if (freshManager != null) {
                        manager = freshManager
                        _uiState.update { it.copy(reportingManager = freshManager) }
                    }
                    val freshDept = emp.departmentName?.takeIf { it.isNotBlank() }
                    if (freshDept != null) {
                        dept = freshDept
                        _uiState.update { it.copy(department = freshDept) }
                    }
                    val freshShift = emp.workShiftName?.takeIf { it.isNotBlank() }
                    if (freshShift != null) {
                        shift = freshShift
                        _uiState.update { it.copy(workShift = freshShift) }
                    }
                }
            } catch (_: Exception) {
                // Keep cached values on error
            }

            // 1. Calculate live Attendance % and Overtime from Monthly Attendance records
            try {
                val monthlyResult = monthlyRepository.getMonthlyAttendance(currentMonth)
                monthlyResult.onSuccess { response ->
                    val summary = response.actualSummary
                    if (summary != null) {
                        // Attendance % = presentPercent or (presentDays / workingDays) * 100
                        if (summary.presentPercent != null && summary.presentPercent > 0) {
                            finalAttendance = "${summary.presentPercent}%"
                        } else if (summary.workingDays > 0) {
                            val pct = (summary.presentDays.toDouble() / summary.workingDays.toDouble()) * 100.0
                            finalAttendance = String.format(Locale.US, "%.1f%%", pct)
                        } else if (summary.presentDays > 0) {
                            finalAttendance = "100.0%"
                        } else {
                            finalAttendance = "0.0%"
                        }

                        // Overtime = worked minutes exceeding standard 8h (480 mins) per present day
                        val expectedMinutes = summary.presentDays * 480
                        val totalWorked = summary.totalWorkedMinutes ?: 0
                        if (totalWorked > expectedMinutes) {
                            val otMinutes = totalWorked - expectedMinutes
                            val h = otMinutes / 60
                            val m = otMinutes % 60
                            finalOvertime = "+${h}h ${String.format(Locale.US, "%02dm", m)}"
                        } else {
                            finalOvertime = "0h 00m"
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep fallback on error
            }

            // 2. Calculate live Leave Quota from balance or history
            try {
                val balanceResult = leaveRepository.balance(session.token)
                if (balanceResult.isSuccess && balanceResult.getOrNull() != null) {
                    val b = balanceResult.getOrNull()!!
                    val casualLeft = b.casual?.balance ?: 0f
                    val sickLeft = b.sick?.balance ?: 0f
                    val totalLeft = casualLeft + sickLeft
                    finalLeaveQuota = "${totalLeft.toInt()} Days"
                } else {
                    val historyResult = leaveRepository.history(session.token)
                    historyResult.onSuccess { leaves ->
                        val approvedDays = leaves
                            .filter { it.status.equals("approved", ignoreCase = true) }
                            .sumOf { (it.leaveDays ?: 1).toDouble() }
                        val annualQuota = 18.0
                        val remaining = maxOf(0.0, annualQuota - approvedDays)
                        finalLeaveQuota = "${remaining.toInt()} Days"
                    }
                }
            } catch (_: Exception) {
                // Keep fallback on error
            }

            // Update UI State with computed dynamic values
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    attendancePercent = finalAttendance,
                    leaveQuota = finalLeaveQuota,
                    overtimeWork = finalOvertime,
                    reportingManager = manager,
                    department = dept,
                    workShift = shift
                )
            }

            // Cache updated dynamic metrics into SessionDataStore
            try {
                sessionDataStore.saveSession(
                    session.copy(
                        managerName = manager,
                        departmentName = dept,
                        workShiftName = shift,
                        attendancePercent = finalAttendance,
                        leaveQuota = finalLeaveQuota,
                        ytdOvertime = finalOvertime
                    )
                )
            } catch (_: Exception) {
                // Ignore cache write error
            }
        }
    }

    fun updateProfilePhoto(
        session: Session,
        bitmap: Bitmap,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingPhoto = true, photoError = null) }
            try {
                // Compress bitmap to 256x256 square JPEG (~15-25 KB) to save space in the DB
                val compressedBase64 = ImageCompressor.compressBitmapToBase64(bitmap)
                val result = authRepository.updateProfilePhoto(session.token, compressedBase64)
                result.onSuccess { savedUrl ->
                    _uiState.update { it.copy(isUpdatingPhoto = false, profilePhotoUrl = savedUrl) }
                    onSuccess()
                }.onFailure { err ->
                    val msg = err.message ?: "Failed to update profile photo."
                    _uiState.update { it.copy(isUpdatingPhoto = false, photoError = msg) }
                    onError(msg)
                }
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Failed to compress and save image."
                _uiState.update { it.copy(isUpdatingPhoto = false, photoError = msg) }
                onError(msg)
            }
        }
    }

    fun removeProfilePhoto(
        session: Session,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdatingPhoto = true, photoError = null) }
            val result = authRepository.updateProfilePhoto(session.token, null)
            result.onSuccess {
                _uiState.update { it.copy(isUpdatingPhoto = false, profilePhotoUrl = "") }
                onSuccess()
            }.onFailure { err ->
                val msg = err.message ?: "Failed to remove profile photo."
                _uiState.update { it.copy(isUpdatingPhoto = false, photoError = msg) }
                onError(msg)
            }
        }
    }
}
