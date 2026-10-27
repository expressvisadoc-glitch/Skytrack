package com.skypass.skytrack.ui.monthly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.remote.PayslipDto
import com.skypass.skytrack.data.remote.PayslipListItemDto
import com.skypass.skytrack.data.repository.PayslipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PayslipsState(
    val isLoading: Boolean = false,
    val payslips: List<PayslipListItemDto> = emptyList(),
    val error: String? = null,
    val openingMonth: String? = null
)

@HiltViewModel
class PayslipsViewModel @Inject constructor(
    private val repository: PayslipRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PayslipsState())
    val state: StateFlow<PayslipsState> = _state.asStateFlow()

    fun load(session: Session) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.list(session.token)
                .onSuccess { _state.value = _state.value.copy(isLoading = false, payslips = it) }
                .onFailure { _state.value = _state.value.copy(isLoading = false, error = it.message ?: "Unable to load payslips.") }
        }
    }

    /** Fetches one payslip; the caller turns it into a PDF. */
    fun open(session: Session, month: String, onLoaded: (PayslipDto) -> Unit) {
        if (_state.value.openingMonth != null) return
        _state.value = _state.value.copy(openingMonth = month, error = null)
        viewModelScope.launch {
            repository.get(session.token, month)
                .onSuccess {
                    _state.value = _state.value.copy(openingMonth = null)
                    onLoaded(it)
                }
                .onFailure {
                    _state.value = _state.value.copy(openingMonth = null, error = it.message ?: "Unable to open the payslip.")
                }
        }
    }
}
