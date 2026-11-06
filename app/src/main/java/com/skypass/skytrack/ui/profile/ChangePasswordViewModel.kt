package com.skypass.skytrack.ui.profile

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import com.skypass.skytrack.data.auth.BiometricAuth
import com.skypass.skytrack.data.auth.BiometricLoginStore
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.remote.ApiErrorExtractor
import com.skypass.skytrack.data.repository.AuthRepository
import com.skypass.skytrack.data.repository.SignInRejectedException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val biometricLoginStore: BiometricLoginStore
) : ViewModel() {

    sealed interface Outcome {
        data class Changed(val message: String) : Outcome
        data class Error(val message: String) : Outcome
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** The "Verify" link next to the current password: checks it with the server */
    suspend fun verifyCurrentPassword(employeeId: String, password: String): String {
        if (password.isBlank()) return "Please enter your current password"
        _busy.value = true
        return try {
            authRepository.verifyPassword(employeeId, password).fold(
                onSuccess = { "Current password verified" },
                onFailure = { error ->
                    if (error is SignInRejectedException) "Your current password is incorrect."
                    else ApiErrorExtractor.getMessage(error, "Could not check your password. Please try again.")
                }
            )
        } finally {
            _busy.value = false
        }
    }

    /**
     * Changes the password on the server. If fingerprint sign-in is on for this employee, the saved password is
     * replaced too (one fingerprint touch); if the user skips that, fingerprint sign-in is turned off because the
     * saved password would no longer work.
     */
    suspend fun changePassword(
        activity: FragmentActivity?,
        session: Session,
        currentPassword: String,
        newPassword: String
    ): Outcome {
        _busy.value = true
        val result = try {
            authRepository.changeOwnPassword(session.token, session.employeeId, currentPassword, newPassword)
        } finally {
            _busy.value = false
        }
        result.onFailure { error ->
            return Outcome.Error(ApiErrorExtractor.getMessage(error, "Your password could not be changed. Please try again."))
        }

        val biometric = biometricLoginStore.currentAccount()
        if (biometric == null || !biometric.employeeId.equals(session.employeeId, ignoreCase = true)) {
            return Outcome.Changed("Your password has been changed.")
        }
        val updated = activity != null && runCatching {
            val cipher = biometricLoginStore.encryptCipher()
            val outcome = BiometricAuth.authenticate(
                activity = activity,
                title = "Update fingerprint sign-in",
                subtitle = "Touch the sensor to keep signing in with your fingerprint",
                negativeText = "Not now",
                cipher = cipher
            )
            if (outcome is BiometricAuth.Outcome.Success) {
                biometricLoginStore.save(session.employeeId, newPassword, outcome.cipher)
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (!updated) biometricLoginStore.clear()
        return Outcome.Changed(
            if (updated) "Password changed. Fingerprint sign-in updated."
            else "Password changed. Fingerprint sign-in is off; turn it on in Profile."
        )
    }
}
