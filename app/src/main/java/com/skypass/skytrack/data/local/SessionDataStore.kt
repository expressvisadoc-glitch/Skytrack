package com.skypass.skytrack.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "skytrack_session")

@Singleton
class SessionDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val token = stringPreferencesKey("session_token")
        val employeeId = stringPreferencesKey("employee_id")
        val employeeName = stringPreferencesKey("employee_name")
        val employeeRole = stringPreferencesKey("employee_role")
        val managerName = stringPreferencesKey("manager_name")
        val departmentName = stringPreferencesKey("department_name")
        val workShiftName = stringPreferencesKey("work_shift_name")
        val attendancePercent = stringPreferencesKey("attendance_percent")
        val leaveQuota = stringPreferencesKey("leave_quota")
        val ytdOvertime = stringPreferencesKey("ytd_overtime")
        val createdAt = stringPreferencesKey("created_at")
        val joiningDate = stringPreferencesKey("joining_date")
        val profilePhotoUrl = stringPreferencesKey("profile_photo_url")

        val rememberMe = booleanPreferencesKey("remember_me")
        val savedEmployeeId = stringPreferencesKey("saved_employee_id")
        val savedPassword = stringPreferencesKey("saved_password")
    }

    val session: Flow<Session?> = context.sessionDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val token = prefs[Keys.token]
            if (token.isNullOrBlank()) {
                null
            } else {
                Session(
                    token = token,
                    employeeId = prefs[Keys.employeeId].orEmpty(),
                    name = prefs[Keys.employeeName].orEmpty(),
                    role = prefs[Keys.employeeRole].orEmpty(),
                    managerName = prefs[Keys.managerName].orEmpty(),
                    departmentName = prefs[Keys.departmentName].orEmpty(),
                    workShiftName = prefs[Keys.workShiftName].orEmpty(),
                    attendancePercent = prefs[Keys.attendancePercent].orEmpty(),
                    leaveQuota = prefs[Keys.leaveQuota].orEmpty(),
                    ytdOvertime = prefs[Keys.ytdOvertime].orEmpty(),
                    createdAt = prefs[Keys.createdAt].orEmpty(),
                    joiningDate = prefs[Keys.joiningDate].orEmpty(),
                    profilePhotoUrl = prefs[Keys.profilePhotoUrl].orEmpty()
                )
            }
        }

    val rememberedAccount: Flow<RememberedAccount> = context.sessionDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val remember = prefs[Keys.rememberMe] ?: false
            RememberedAccount(
                employeeId = if (remember) prefs[Keys.savedEmployeeId].orEmpty() else "",
                password = "",
                rememberMe = remember
            )
        }

    suspend fun saveSession(session: Session) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.token] = session.token
            prefs[Keys.employeeId] = session.employeeId
            prefs[Keys.employeeName] = session.name
            prefs[Keys.employeeRole] = session.role
            prefs[Keys.managerName] = session.managerName
            prefs[Keys.departmentName] = session.departmentName
            prefs[Keys.workShiftName] = session.workShiftName
            prefs[Keys.attendancePercent] = session.attendancePercent
            prefs[Keys.leaveQuota] = session.leaveQuota
            prefs[Keys.ytdOvertime] = session.ytdOvertime
            prefs[Keys.createdAt] = session.createdAt
            prefs[Keys.joiningDate] = session.joiningDate
            prefs[Keys.profilePhotoUrl] = session.profilePhotoUrl
        }
    }

    suspend fun updateProfilePhoto(photoUrl: String) {
        context.sessionDataStore.edit { prefs ->
            if (photoUrl.isBlank()) {
                prefs.remove(Keys.profilePhotoUrl)
            } else {
                prefs[Keys.profilePhotoUrl] = photoUrl
            }
        }
    }

    suspend fun saveRememberMe(remember: Boolean, employeeId: String, password: String = "") {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.rememberMe] = remember
            if (remember) {
                prefs[Keys.savedEmployeeId] = employeeId.trim()
            } else {
                prefs.remove(Keys.savedEmployeeId)
            }
            // Never persist user password in DataStore
            prefs.remove(Keys.savedPassword)
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(Keys.token)
            prefs.remove(Keys.employeeId)
            prefs.remove(Keys.employeeName)
            prefs.remove(Keys.employeeRole)
            prefs.remove(Keys.managerName)
            prefs.remove(Keys.departmentName)
            prefs.remove(Keys.workShiftName)
            prefs.remove(Keys.attendancePercent)
            prefs.remove(Keys.leaveQuota)
            prefs.remove(Keys.ytdOvertime)
            prefs.remove(Keys.profilePhotoUrl)
            prefs.remove(Keys.savedPassword)
        }
    }

    suspend fun clear() {
        clearSession()
    }
}

data class RememberedAccount(
    val employeeId: String = "",
    val password: String = "",
    val rememberMe: Boolean = false
)

data class Session(
    val token: String,
    val employeeId: String,
    val name: String,
    val role: String,
    val managerName: String = "",
    val departmentName: String = "",
    val workShiftName: String = "",
    val attendancePercent: String = "",
    val leaveQuota: String = "",
    val ytdOvertime: String = "",
    val createdAt: String = "",
    val joiningDate: String = "",
    val profilePhotoUrl: String = ""
)
