package com.skypass.skytrack.data.repository

import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.auth.BiometricLoginStore
import com.skypass.skytrack.data.local.RememberedAccount
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.local.SessionDataStore
import com.skypass.skytrack.data.remote.ApiErrorExtractor
import com.skypass.skytrack.data.remote.AttendanceRequest
import com.skypass.skytrack.data.remote.ChangeOwnPasswordRequest
import com.skypass.skytrack.data.remote.EmployeeDto
import com.skypass.skytrack.data.remote.LoginRequest
import com.skypass.skytrack.data.remote.SkyTrackApi
import com.skypass.skytrack.data.remote.UpdateProfilePhotoRequest
import com.skypass.skytrack.util.JwtUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** The server refused the employee ID and password (wrong password, or the account is not active) */
class SignInRejectedException(message: String) : IllegalStateException(message)

@Singleton
class AuthRepository @Inject constructor(
    private val api: SkyTrackApi,
    private val sessionDataStore: SessionDataStore,
    private val biometricLoginStore: BiometricLoginStore
) {
    val session: Flow<Session?> = sessionDataStore.session
        .map { storedSession ->
            if (storedSession == null) {
                null
            } else if (JwtUtils.isTokenExpired(storedSession.token)) {
                sessionDataStore.clearSession()
                null
            } else {
                storedSession
            }
        }
        .distinctUntilChanged()

    val rememberedAccount: Flow<RememberedAccount> = sessionDataStore.rememberedAccount

    suspend fun login(
        employeeId: String,
        password: String,
        rememberMe: Boolean = false
    ): Result<Session> = runCatching {
        val trimmedEmployeeId = employeeId.trim()
        authenticate(trimmedEmployeeId, password).also { session ->
            sessionDataStore.saveSession(session)
            sessionDataStore.saveRememberMe(rememberMe, trimmedEmployeeId)
            // Biometric sign-in belongs to one account; signing in as someone else on this phone turns it off
            val biometricAccount = biometricLoginStore.currentAccount()
            if (biometricAccount != null && !biometricAccount.employeeId.equals(session.employeeId, ignoreCase = true)) {
                biometricLoginStore.clear()
            }
        }
    }

    /** Checks the signed-in employee's password with the server (used before turning on biometric sign-in) */
    suspend fun verifyPassword(employeeId: String, password: String): Result<Session> = runCatching {
        authenticate(employeeId.trim(), password).also { sessionDataStore.saveSession(it) }
    }

    private suspend fun authenticate(trimmedEmployeeId: String, password: String): Session {
        val response = try {
            api.login(
                endpoint = BuildConfig.SKYTRACK_API_URL,
                request = LoginRequest(
                    employeeId = trimmedEmployeeId,
                    password = password
                )
            )
        } catch (httpException: HttpException) {
            val friendlyMsg = ApiErrorExtractor.getMessage(
                httpException,
                "Invalid Employee ID or password."
            )
            if (httpException.code() == 401 || httpException.code() == 403) {
                throw SignInRejectedException(friendlyMsg)
            }
            throw IllegalStateException(friendlyMsg)
        }

        if (!response.success || response.session == null || response.employee == null) {
            val message = response.message
                ?.takeIf { it.isNotBlank() }
                ?: "Invalid Employee ID or password."
            throw SignInRejectedException(ApiErrorExtractor.cleanErrorString(message, "Invalid Employee ID or password."))
        }

        return Session(
            token = response.session.token,
            employeeId = response.employee.employeeId,
            name = response.employee.name,
            role = response.employee.role,
            managerName = response.employee.managerName ?: "",
            departmentName = response.employee.departmentName ?: "",
            workShiftName = response.employee.workShiftName ?: "",
            attendancePercent = response.employee.attendancePercent ?: "",
            leaveQuota = response.employee.leaveQuota ?: "",
            ytdOvertime = response.employee.ytdOvertime ?: "",
            createdAt = response.employee.createdAt ?: "",
            joiningDate = response.employee.joiningDate ?: "",
            profilePhotoUrl = response.employee.profilePhotoUrl.orEmpty()
        )
    }

    suspend fun updateProfilePhoto(token: String, base64Photo: String?): Result<String> = runCatching {
        val response = api.updateProfilePhoto(
            endpoint = BuildConfig.SKYTRACK_API_URL,
            authorization = "Bearer $token",
            request = UpdateProfilePhotoRequest(
                action = "update_profile_photo",
                photo = base64Photo
            )
        )
        if (!response.success) {
            throw IllegalStateException(response.message ?: "Failed to update profile photo.")
        }
        val photoUrl = response.profilePhotoUrl ?: base64Photo.orEmpty()
        sessionDataStore.updateProfilePhoto(photoUrl)
        photoUrl
    }

    suspend fun refreshProfile(token: String): Result<EmployeeDto> = runCatching {
        val response = api.getProfile(
            endpoint = BuildConfig.SKYTRACK_API_URL,
            authorization = "Bearer $token",
            request = AttendanceRequest(
                action = "get_profile",
                token = token
            )
        )
        if (!response.success || response.employee == null) {
            throw IllegalStateException(response.message ?: "Failed to fetch profile.")
        }
        val emp = response.employee
        val photoUrl = emp.profilePhotoUrl.orEmpty()
        val currentSession = sessionDataStore.session.first()
        if (currentSession != null) {
            val updated = currentSession.copy(
                profilePhotoUrl = photoUrl.ifBlank { currentSession.profilePhotoUrl },
                name = emp.name.ifBlank { currentSession.name },
                role = emp.role.ifBlank { currentSession.role },
                managerName = emp.managerName?.takeIf { it.isNotBlank() } ?: currentSession.managerName,
                departmentName = emp.departmentName?.takeIf { it.isNotBlank() } ?: currentSession.departmentName,
                workShiftName = emp.workShiftName?.takeIf { it.isNotBlank() } ?: currentSession.workShiftName
            )
            sessionDataStore.saveSession(updated)
        }
        emp
    }

    /**
     * Changes the signed-in employee's own password; the server checks the current one first. Changing a password
     * ends every session of the account (other phones are signed out), so this phone signs in again right away
     * with the new password and stays signed in.
     */
    suspend fun changeOwnPassword(
        token: String,
        employeeId: String,
        currentPassword: String,
        newPassword: String
    ): Result<String> = runCatching {
        val response = try {
            api.changeOwnPassword(
                endpoint = BuildConfig.SKYTRACK_API_URL,
                authorization = "Bearer $token",
                request = ChangeOwnPasswordRequest(currentPassword = currentPassword, newPassword = newPassword)
            )
        } catch (httpException: HttpException) {
            throw IllegalStateException(
                ApiErrorExtractor.getMessage(httpException, "Your password could not be changed. Please try again.")
            )
        }
        if (!response.success) {
            throw IllegalStateException(response.message ?: "Your password could not be changed. Please try again.")
        }
        runCatching { sessionDataStore.saveSession(authenticate(employeeId.trim(), newPassword)) }
        response.message ?: "Your password has been changed."
    }

    suspend fun logout() {
        sessionDataStore.clearSession()
    }
}
