package com.kline.inventorypos.feature.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.ApprovalRequest
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.data.operations.ApprovalMutationUncertainException
import com.kline.inventorypos.data.operations.ApprovalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApprovalUiState(
    val requests: List<ApprovalRequest> = emptyList(),
    val currentUserId: String = "",
    val loading: Boolean = false,
    val working: Boolean = false,
    val uncertain: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class ApprovalViewModel(private val repository: ApprovalRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ApprovalUiState())
    val uiState: StateFlow<ApprovalUiState> = _uiState.asStateFlow()
    private var sessionKey: String? = null

    fun bindSession(session: PosSession) {
        val key = "${session.user.id}:${session.branch.id}"
        if (key == sessionKey) return
        sessionKey = key
        _uiState.value = ApprovalUiState(currentUserId = session.user.id)
        refresh()
    }

    fun refresh() {
        val key = sessionKey ?: return
        if (_uiState.value.loading || _uiState.value.working) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.pending() }
                .onSuccess { requests -> _uiState.update { current -> if (sessionKey == key) current.copy(requests = requests, loading = false) else current } }
                .onFailure { error -> _uiState.update { current -> if (sessionKey == key) current.copy(loading = false, error = error.userMessage()) else current } }
        }
    }

    fun decide(request: ApprovalRequest, approve: Boolean, note: String) {
        if (_uiState.value.working || _uiState.value.uncertain) return
        if (request.requestedById == _uiState.value.currentUserId) {
            _uiState.update { it.copy(error = "You cannot resolve your own approval request.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { repository.decide(request.id, approve, note) }
                .onSuccess { _uiState.update { state -> state.copy(working = false, requests = state.requests.filterNot { it.id == request.id }, message = if (approve) "Request approved and executed" else "Request rejected") } }
                .onFailure { error -> _uiState.update { it.copy(working = false, uncertain = error is ApprovalMutationUncertainException, error = error.userMessage()) } }
        }
    }

    fun acknowledgeVerified() {
        _uiState.update { it.copy(uncertain = false, message = "Verification acknowledged; approval queue refreshed") }
        refresh()
    }
    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ApprovalViewModel(container.approvalRepository) as T
    }
}

private fun Throwable.userMessage() = message?.takeIf(String::isNotBlank) ?: "Something went wrong. Please try again."
