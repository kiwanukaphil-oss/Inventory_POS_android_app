package com.kline.inventorypos.feature.reconciliation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.EndOfDayWorkspace
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.data.reconciliation.ReconciliationMutationUncertainException
import com.kline.inventorypos.data.reconciliation.ReconciliationRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReconciliationUiState(
    val date: LocalDate = LocalDate.now(),
    val workspace: EndOfDayWorkspace = EndOfDayWorkspace(null, null, emptyList()),
    val currentUserId: String = "",
    val canViewReconciliation: Boolean = false,
    val canReconcile: Boolean = false,
    val canViewReports: Boolean = false,
    val loading: Boolean = false,
    val working: Boolean = false,
    val uncertain: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class ReconciliationViewModel(private val repository: ReconciliationRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ReconciliationUiState())
    val uiState: StateFlow<ReconciliationUiState> = _uiState.asStateFlow()
    private var sessionKey: String? = null

    fun bindSession(session: PosSession) {
        val key = "${session.user.id}:${session.branch.id}"
        if (key == sessionKey) return
        sessionKey = key
        _uiState.value = ReconciliationUiState(
            currentUserId = session.user.id,
            canViewReconciliation = session.user.hasPermission("cash.view") || session.user.hasPermission("cash.reconcile"),
            canReconcile = session.user.hasPermission("cash.reconcile"),
            canViewReports = session.user.hasPermission("reports.sales"),
        )
        refresh()
    }

    fun previousDate() = selectDate(_uiState.value.date.minusDays(1))
    fun nextDate() {
        val next = _uiState.value.date.plusDays(1)
        if (!next.isAfter(LocalDate.now())) selectDate(next)
    }
    fun today() = selectDate(LocalDate.now())

    private fun selectDate(date: LocalDate) {
        if (_uiState.value.working || date == _uiState.value.date) return
        _uiState.update { it.copy(date = date, workspace = EndOfDayWorkspace(null, null, emptyList()), loading = false, uncertain = false) }
        refresh()
    }

    fun refresh() {
        val snapshot = _uiState.value
        val boundSessionKey = sessionKey
        if (snapshot.loading || snapshot.working) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.workspace(snapshot.date.toString(), snapshot.canViewReconciliation, snapshot.canViewReports) }
                .onSuccess { workspace -> _uiState.update { current ->
                    if (current.date == snapshot.date && sessionKey == boundSessionKey) current.copy(workspace = workspace, loading = false, uncertain = false)
                    else current
                } }
                .onFailure { error -> _uiState.update { current ->
                    if (current.date == snapshot.date && sessionKey == boundSessionKey) current.copy(loading = false, error = error.userMessage())
                    else current
                } }
        }
    }

    fun open() = mutate("Reconciliation started") { repository.open(_uiState.value.date.toString()) }

    fun updateChannel(method: String, externalTotal: Long, charges: Long, note: String) =
        mutate("${method.label()} verified") { repository.updateChannel(_uiState.value.date.toString(), method, externalTotal, charges, note) }

    fun signOff(confirmedTotal: Long, notes: String) =
        mutate("Your sales are signed off") { repository.signOff(_uiState.value.date.toString(), confirmedTotal, notes) }

    fun closeDay() = mutate("Day closed and locked") { repository.close(_uiState.value.date.toString()) }

    private fun mutate(successMessage: String, block: suspend () -> com.kline.inventorypos.core.model.Reconciliation) {
        if (_uiState.value.working || _uiState.value.uncertain) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { block() }
                .onSuccess { result ->
                    _uiState.update { state -> state.copy(working = false, workspace = state.workspace.copy(reconciliation = result), message = successMessage) }
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, uncertain = error is ReconciliationMutationUncertainException, error = error.userMessage()) } }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ReconciliationViewModel(container.reconciliationRepository) as T
    }
}

private fun String.label() = replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun Throwable.userMessage() = message?.takeIf(String::isNotBlank) ?: "Something went wrong. Please try again."
