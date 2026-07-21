package com.kline.inventorypos.feature.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.CashSessionSummary
import com.kline.inventorypos.core.model.CashWorkspace
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.data.cash.CashMutationUncertainException
import com.kline.inventorypos.data.cash.CashRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CashUiState(
    val workspace: CashWorkspace = CashWorkspace(null, emptyList(), null, emptyList()),
    val loading: Boolean = false,
    val working: Boolean = false,
    val uncertain: Boolean = false,
    val closeResult: CashSessionSummary? = null,
    val canOperateDrawer: Boolean = false,
    val canViewBook: Boolean = false,
    val canManage: Boolean = false,
    val canHandover: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class CashViewModel(private val repository: CashRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CashUiState())
    val uiState: StateFlow<CashUiState> = _uiState.asStateFlow()
    private var session: PosSession? = null
    private var sessionKey: String? = null

    fun bindSession(value: PosSession) {
        val key = "${value.user.id}:${value.branch.id}:${value.register?.id}"
        if (key == sessionKey) return
        sessionKey = key
        session = value
        _uiState.value = CashUiState(
            canOperateDrawer = value.user.hasPermission("sales.create"),
            canViewBook = value.user.hasPermission("cash.view"),
            canManage = value.user.hasPermission("cash.manage"),
            canHandover = value.user.hasPermission("users.view"),
        )
        refresh()
    }

    fun refresh() {
        val current = session ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.workspace(_uiState.value.canOperateDrawer, _uiState.value.canViewBook, _uiState.value.canHandover, current.user.id, current.register != null) }
                .onSuccess { workspace -> _uiState.update { it.copy(workspace = workspace, loading = false, uncertain = false) } }
                .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.userMessage()) } }
        }
    }

    fun close(counted: Long, note: String) {
        val active = _uiState.value.workspace.activeSession ?: return
        mutate { repository.close(active.id, counted, note) }
    }

    fun handover(counted: Long, incomingUserId: String, note: String) {
        val active = _uiState.value.workspace.activeSession ?: return
        mutate { repository.handover(active.id, counted, incomingUserId, note) }
    }

    private fun mutate(block: suspend () -> CashSessionSummary) {
        if (_uiState.value.working || _uiState.value.uncertain) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { block() }
                .onSuccess { result -> _uiState.update { it.copy(working = false, closeResult = result, workspace = it.workspace.copy(activeSession = null)) } }
                .onFailure { error -> _uiState.update { it.copy(working = false, uncertain = error is CashMutationUncertainException, error = error.userMessage()) } }
        }
    }

    fun recordMovement(type: String, direction: String, amount: Long, category: String, notes: String) {
        if (_uiState.value.working || _uiState.value.uncertain) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { repository.recordMovement(type, direction, amount, category, notes) }
                .onSuccess { result ->
                    _uiState.update { it.copy(working = false, message = result.message) }
                    refresh()
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, uncertain = error is CashMutationUncertainException, error = error.userMessage()) } }
        }
    }

    fun consumeCloseResult() = _uiState.update { it.copy(closeResult = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CashViewModel(container.cashRepository) as T
    }
}

private fun Throwable.userMessage() = message?.takeIf(String::isNotBlank) ?: "Something went wrong. Please try again."
