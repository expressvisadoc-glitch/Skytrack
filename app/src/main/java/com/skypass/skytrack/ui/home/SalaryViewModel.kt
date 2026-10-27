package com.skypass.skytrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.skytrack.data.local.Session
import com.skypass.skytrack.data.remote.SalaryDto
import com.skypass.skytrack.data.repository.SalaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SalaryCardState(
    val isRevealed: Boolean = false,
    val isLoading: Boolean = false,
    val salary: SalaryDto? = null,
    val error: String? = null
)

/**
 * "This month's salary" card. Hidden by default; figures are fetched only while revealed, kept in memory
 * only, and dropped when the card is hidden or the app goes to the background.
 */
@HiltViewModel
class SalaryViewModel @Inject constructor(
    private val salaryRepository: SalaryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SalaryCardState())
    val state: StateFlow<SalaryCardState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun toggle(session: Session) {
        if (_state.value.isRevealed) hide() else {
            _state.value = SalaryCardState(isRevealed = true, isLoading = true)
            load(session)
        }
    }

    fun hide() {
        loadJob?.cancel()
        _state.value = SalaryCardState()
    }

    /** Called after attendance changes and on resume; does nothing while the card is hidden. */
    fun refreshIfRevealed(session: Session) {
        if (_state.value.isRevealed) load(session)
    }

    fun retry(session: Session) {
        if (!_state.value.isRevealed) return
        _state.value = _state.value.copy(isLoading = true, error = null)
        load(session)
    }

    private fun load(session: Session) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            salaryRepository.currentMonth(session.token)
                .onSuccess { salary ->
                    if (_state.value.isRevealed) {
                        _state.value = _state.value.copy(isLoading = false, salary = salary, error = null)
                    }
                }
                .onFailure { error ->
                    if (_state.value.isRevealed) {
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = error.message ?: "Unable to load your salary."
                        )
                    }
                }
        }
    }
}
