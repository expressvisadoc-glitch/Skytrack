package com.skypass.skytrack.ui.login

import androidx.lifecycle.ViewModel
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import com.skypass.skytrack.data.auth.BiometricAccount
import com.skypass.skytrack.data.auth.BiometricAuth
import com.skypass.skytrack.data.auth.BiometricLoginStore
import com.skypass.skytrack.data.local.RememberedAccount
import com.skypass.skytrack.data.repository.AuthRepository
import com.skypass.skytrack.data.repository.SignInRejectedException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data object Success : LoginState
    data class Error(val message: String) : LoginState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val biometricLoginStore: BiometricLoginStore
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    val rememberedAccount: StateFlow<RememberedAccount?> = repository.rememberedAccount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    /** The account biometric sign-in is set up for on this phone, if any */
    val biometricAccount: StateFlow<BiometricAccount?> = biometricLoginStore.account
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    // The fingerprint prompt opens by itself once, when the app starts on the sign-in screen (not after signing out)
    private var autoBiometricPromptAvailable = true

    fun consumeAutoBiometricPrompt(): Boolean = autoBiometricPromptAvailable.also { autoBiometricPromptAvailable = false }

    /**
     * Signs in with the fingerprint: the prompt unlocks the saved password, which is then used for a normal sign-in.
     * Runs in the caller's (activity) scope so the prompt closes with the screen.
     */
    suspend fun biometricLogin(activity: FragmentActivity) {
        if (_state.value is LoginState.Loading) return
        val account = biometricLoginStore.currentAccount() ?: return
        val cipher = try {
            biometricLoginStore.decryptCipher()
        } catch (_: Exception) {
            biometricLoginStore.clear()
            _state.value = LoginState.Error(
                "Fingerprint sign-in was turned off because the fingerprints on this phone changed. " +
                    "Sign in with your password, then turn it on again in Profile."
            )
            return
        }
        val outcome = BiometricAuth.authenticate(
            activity = activity,
            title = "Sign in to SkyTrack",
            subtitle = account.employeeId,
            negativeText = "Use password",
            cipher = cipher
        )
        when (outcome) {
            is BiometricAuth.Outcome.Cancelled -> Unit
            is BiometricAuth.Outcome.Failed -> _state.value = LoginState.Error(outcome.message)
            is BiometricAuth.Outcome.Success -> {
                val password = runCatching { biometricLoginStore.readPassword(outcome.cipher) }.getOrNull()
                if (password.isNullOrEmpty()) {
                    biometricLoginStore.clear()
                    _state.value = LoginState.Error("Fingerprint sign-in could not be used. Sign in with your password.")
                    return
                }
                _state.value = LoginState.Loading
                val rememberMe = rememberedAccount.value?.rememberMe == true
                repository.login(account.employeeId, password, rememberMe)
                    .onSuccess { _state.value = LoginState.Success }
                    .onFailure { error ->
                        if (error is SignInRejectedException) {
                            // The saved password no longer works (changed or reset, or the account is inactive)
                            biometricLoginStore.clear()
                            _state.value = LoginState.Error(
                                "Your saved password no longer works (it may have been changed or reset, or the " +
                                    "account is inactive), so fingerprint sign-in has been turned off. Sign in with " +
                                    "your password, then turn it on again in Profile."
                            )
                        } else {
                            _state.value = LoginState.Error(messageFor(error))
                        }
                    }
            }
        }
    }

    fun login(employeeId: String, password: String, rememberMe: Boolean = false) {
        if (employeeId.isBlank() || password.isBlank()) {
            _state.value = LoginState.Error(
                "Employee ID and password are required."
            )
            return
        }

        viewModelScope.launch {
            _state.value = LoginState.Loading

            repository.login(employeeId, password, rememberMe)
                .onSuccess {
                    _state.value = LoginState.Success
                }
                .onFailure {
                    _state.value = LoginState.Error(messageFor(it))
                }
        }
    }

    fun clearError() {
        if (_state.value is LoginState.Error) {
            _state.value = LoginState.Idle
        }
    }

    fun resetState() {
        _state.value = LoginState.Idle
    }

    /** After signing out, the sign-in screen waits for the user instead of opening the fingerprint prompt */
    fun suppressAutoBiometricPrompt() {
        autoBiometricPromptAvailable = false
    }

    private fun messageFor(error: Throwable): String =
        com.skypass.skytrack.data.remote.ApiErrorExtractor.getMessage(
            error,
            "Sign in failed. Please check your credentials and try again."
        )
}
