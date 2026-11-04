package com.skypass.skytrack.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skypass.skytrack.data.remote.NotificationDto
import com.skypass.skytrack.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val items: List<NotificationDto> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    /** Unread count for the bell on Home */
    val unreadCount: StateFlow<Int> = repository.unreadCount

    fun refreshUnreadCount(token: String) {
        viewModelScope.launch { repository.refreshUnreadCount(token) }
    }

    /** Loads the newest page, then marks everything read (items keep their unread dot until the next visit) */
    fun open(token: String) {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.list(token)
                .onSuccess { page ->
                    _state.update { it.copy(isLoading = false, items = page.items, hasMore = page.hasMore) }
                    if (page.unreadCount > 0) repository.markAllRead(token)
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }

    fun loadMore(token: String) {
        val current = _state.value
        val before = current.items.lastOrNull()?.createdAt ?: return
        if (current.isLoadingMore || !current.hasMore) return
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            repository.list(token, before)
                .onSuccess { page ->
                    _state.update {
                        it.copy(isLoadingMore = false, items = it.items + page.items, hasMore = page.hasMore)
                    }
                    if (page.unreadCount > 0) repository.markAllRead(token)
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoadingMore = false, error = error.message) }
                }
        }
    }
}
