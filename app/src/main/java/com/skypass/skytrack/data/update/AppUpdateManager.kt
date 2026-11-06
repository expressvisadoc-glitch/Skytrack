package com.skypass.skytrack.data.update

import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.repository.AppUpdateInfo
import com.skypass.skytrack.data.repository.AppUpdateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages in-app update state across the application lifecycle.
 * Ensures the update dialog is not repeatedly shown within a single app session
 * when the user selects 'Later'.
 */
@Singleton
class AppUpdateManager @Inject constructor(
    private val repository: AppUpdateRepository
) {

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    // In-memory session tracking
    private var hasDismissedThisSession = false
    private var hasCheckedThisSession = false

    val isStagingBuild: Boolean = BuildConfig.APPLICATION_ID.endsWith(".staging")

    /**
     * Checks for updates asynchronously.
     * @param currentVersion Installed version name (defaults to BuildConfig.VERSION_NAME)
     * @param isManualCheck When true, bypasses session suppression (e.g. user tapped "Check for Updates")
     */
    suspend fun checkForUpdates(
        currentVersion: String = BuildConfig.VERSION_NAME,
        isManualCheck: Boolean = false
    ): AppUpdateInfo? {
        // Staging (test) builds never look at GitHub releases: those hold the live app, and installing it would
        // move testers onto production. New test builds are shared with testers directly.
        if (isStagingBuild) return null

        // If not a manual check and user already dismissed the dialog this session, skip
        if (!isManualCheck && hasDismissedThisSession) {
            return null
        }

        // If not a manual check and we already verified this session with no update, skip
        if (!isManualCheck && hasCheckedThisSession && _updateInfo.value == null) {
            return null
        }

        _isChecking.value = true
        return try {
            val update = repository.checkForUpdate(currentVersion)
            hasCheckedThisSession = true

            if (isManualCheck) {
                // User explicitly asked, so reset any previous session dismissal
                hasDismissedThisSession = false
            }

            if (update != null && !hasDismissedThisSession) {
                _updateInfo.value = update
            }

            update
        } catch (_: Exception) {
            null
        } finally {
            _isChecking.value = false
        }
    }

    /**
     * Called when the user taps "Later".
     * Dismisses the dialog and remembers the dismissal for the rest of this session.
     */
    fun dismissUpdate() {
        hasDismissedThisSession = true
        _updateInfo.value = null
    }

    /**
     * Resets update state without marking session as permanently dismissed.
     */
    fun clearUpdate() {
        _updateInfo.value = null
    }
}
