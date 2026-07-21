package com.kline.inventorypos.feature.operations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.Expense
import com.kline.inventorypos.core.model.ExpenseWorkspace
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.data.operations.ExpenseMutationUncertainException
import com.kline.inventorypos.data.operations.ExpenseRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExpensePeriod(val label: String) { Today("Today"), Last7Days("7 days"), ThisMonth("This month") }

data class ExpenseUiState(
    val period: ExpensePeriod = ExpensePeriod.ThisMonth,
    val categoryId: String? = null,
    val workspace: ExpenseWorkspace = ExpenseWorkspace(emptyList(), emptyList()),
    val canView: Boolean = false,
    val canCreate: Boolean = false,
    val canEdit: Boolean = false,
    val canDelete: Boolean = false,
    val loading: Boolean = false,
    val working: Boolean = false,
    val uncertain: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState.asStateFlow()
    private var sessionKey: String? = null

    fun bindSession(session: PosSession) {
        val key = "${session.user.id}:${session.branch.id}"
        if (key == sessionKey) return
        sessionKey = key
        val canView = session.user.hasPermission("expenses.view")
        _uiState.value = ExpenseUiState(
            canView = canView,
            canCreate = session.user.hasPermission("expenses.create"),
            canEdit = session.user.hasPermission("expenses.edit"),
            canDelete = session.user.hasPermission("expenses.delete"),
        )
        if (canView) refresh()
    }

    fun setPeriod(period: ExpensePeriod) {
        if (period == _uiState.value.period || _uiState.value.working) return
        _uiState.update { it.copy(period = period, loading = false, workspace = it.workspace.copy(expenses = emptyList())) }
        refresh()
    }

    fun setCategory(categoryId: String?) {
        if (categoryId == _uiState.value.categoryId || _uiState.value.working) return
        _uiState.update { it.copy(categoryId = categoryId, loading = false, workspace = it.workspace.copy(expenses = emptyList())) }
        refresh()
    }

    fun refresh() {
        val snapshot = _uiState.value
        val boundKey = sessionKey
        if (!snapshot.canView || snapshot.loading || snapshot.working || boundKey == null) return
        val (from, to) = snapshot.period.range()
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.workspace(from, to, snapshot.categoryId) }
                .onSuccess { workspace -> _uiState.update { current ->
                    if (current.period == snapshot.period && current.categoryId == snapshot.categoryId && sessionKey == boundKey) current.copy(workspace = workspace, loading = false, uncertain = false) else current
                } }
                .onFailure { error -> _uiState.update { current ->
                    if (current.period == snapshot.period && current.categoryId == snapshot.categoryId && sessionKey == boundKey) current.copy(loading = false, error = error.userMessage()) else current
                } }
        }
    }

    fun save(id: String?, date: String, categoryId: String, amount: Long, payee: String, method: String, reference: String, notes: String) {
        if (_uiState.value.working || _uiState.value.uncertain) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { repository.save(id, date, categoryId, amount, payee, method, reference, notes) }
                .onSuccess { saved ->
                    _uiState.update { state ->
                        val named = saved.copy(categoryName = state.workspace.categories.firstOrNull { it.id == saved.categoryId }?.name ?: saved.categoryName)
                        val rows = state.workspace.expenses.filterNot { it.id == named.id }.toMutableList().apply {
                            val (from, to) = state.period.range()
                            if (named.date in from..to && (state.categoryId == null || state.categoryId == named.categoryId)) add(named)
                        }.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })
                        state.copy(working = false, workspace = state.workspace.copy(expenses = rows), message = if (id == null) "Expense recorded" else "Expense updated")
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, uncertain = error is ExpenseMutationUncertainException, error = error.userMessage()) } }
        }
    }

    fun delete(id: String) {
        if (_uiState.value.working || _uiState.value.uncertain) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { repository.delete(id) }
                .onSuccess { _uiState.update { state -> state.copy(working = false, workspace = state.workspace.copy(expenses = state.workspace.expenses.filterNot { it.id == id }), message = "Expense deleted") } }
                .onFailure { error -> _uiState.update { it.copy(working = false, uncertain = error is ExpenseMutationUncertainException, error = error.userMessage()) } }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ExpenseViewModel(container.expenseRepository) as T
    }
}

private fun ExpensePeriod.range(): Pair<String, String> {
    val today = LocalDate.now()
    val from = when (this) { ExpensePeriod.Today -> today; ExpensePeriod.Last7Days -> today.minusDays(6); ExpensePeriod.ThisMonth -> today.withDayOfMonth(1) }
    return from.toString() to today.toString()
}
private fun Throwable.userMessage() = message?.takeIf(String::isNotBlank) ?: "Something went wrong. Please try again."
