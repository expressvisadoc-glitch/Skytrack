package com.skypass.skytrack.data.repository

import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.local.RememberedAccount
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.local.SessionDataStore
import com.skypass.skytrack.data.remote.ApiErrorExtractor
import com.skypass.skytrack.data.remote.AttendanceRequest
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

@Singleton
class AuthRepository @Inject constructor(
    private val api: SkyTrackApi,
    private val sessionDataStore: SessionDataStore
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
            throw IllegalStateException(friendlyMsg)
        }

        if (!response.success || response.session == null || response.employee == null) {
            val message = response.message
                ?.takeIf { it.isNotBlank() }
                ?: "Invalid Employee ID or password."
            throw IllegalStateException(ApiErrorExtractor.cleanErrorString(message, "Invalid Employee ID or password."))
        }

        Session(
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
        ).also {
            sessionDataStore.saveSession(it)
            sessionDataStore.saveRememberMe(rememberMe, trimmedEmployeeId)
        }
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
                role = emp.role.ifBlank { currentSession.role }
            )
            sessionDataStore.saveSession(updated)
        }
        emp
    }

    suspend fun logout() {
        sessionDataStore.clearSession()
    }
}
