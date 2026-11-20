package com.skypass.skytrack.data.notifications

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Kept apart from the session store so the setting survives sign-out
private val Context.appPreferencesDataStore by preferencesDataStore(name = "skytrack_preferences")

/** The Profile notification switch. On by default; when off, pushes are received but not shown. */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val enabled = booleanPreferencesKey("notifications_enabled")
        val permissionAsked = booleanPreferencesKey("notification_permission_asked")
    }

    private val prefs = context.appPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }

    val enabled: Flow<Boolean> = prefs.map { it[Keys.enabled] ?: true }

    suspend fun isEnabled(): Boolean = enabled.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[Keys.enabled] = enabled }
    }

    /** True the first time only, so Android's permission prompt is shown once automatically */
    suspend fun claimPermissionPrompt(): Boolean {
        var first = false
        context.appPreferencesDataStore.edit { prefs ->
            first = prefs[Keys.permissionAsked] != true
            prefs[Keys.permissionAsked] = true
        }
        return first
    }
}
