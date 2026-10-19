package com.skypass.skytrack.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.skytrack.data.local.RememberedAccount
import com.skypass.skytrack.data.repository.AuthRepository
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
    private val repository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    val rememberedAccount: StateFlow<RememberedAccount?> = repository.rememberedAccount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

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

    private fun messageFor(error: Throwable): String =
        com.skypass.skytrack.data.remote.ApiErrorExtractor.getMessage(
            error,
            "Sign in failed. Please check your credentials and try again."
        )
}
