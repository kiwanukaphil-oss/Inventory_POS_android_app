package com.kline.inventorypos.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.ConfirmedReceipt
import com.kline.inventorypos.core.model.SaleSummary
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.data.activity.ActivityRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityUiState(
    val sales: List<SaleSummary> = emptyList(),
    val query: String = "",
    val filter: String = "All",
    val selectedSale: SaleSummary? = null,
    val receipt: ConfirmedReceipt? = null,
    val branchName: String = "",
    val canRefund: Boolean = false,
    val loading: Boolean = false,
    val detailLoading: Boolean = false,
    val working: Boolean = false,
    val error: String? = null,
    val message: String? = null,
) {
    val visibleSales: List<SaleSummary>
        get() = when (filter) {
            "Completed" -> sales.filter { it.status == "completed" && it.returnStatus == "none" }
            "Returns" -> sales.filter { it.returnStatus != "none" || it.status.contains("refund") }
            else -> sales
        }
}

class ActivityViewModel(private val repository: ActivityRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()
    private var sessionKey: String? = null
    private var searchJob: Job? = null
    private var refreshJob: Job? = null

    fun bindSession(session: PosSession) {
        val key = "${session.user.id}:${session.branch.id}"
        if (sessionKey == key) return
        sessionKey = key
        _uiState.value = ActivityUiState(
            branchName = session.branch.name,
            canRefund = session.user.hasPermission("sales.refund"),
        )
        refresh()
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            refresh()
        }
    }

    fun setFilter(value: String) = _uiState.update { it.copy(filter = value) }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.sales(_uiState.value.query) }
                .onSuccess { sales -> _uiState.update { it.copy(sales = sales, loading = false) } }
                .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.userMessage()) } }
        }
    }

    fun openSale(sale: SaleSummary) {
        if (_uiState.value.detailLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(selectedSale = sale, receipt = null, detailLoading = true, error = null) }
            runCatching { repository.receipt(sale, _uiState.value.branchName) }
                .onSuccess { receipt -> _uiState.update { it.copy(receipt = receipt, detailLoading = false) } }
                .onFailure { error ->
                    _uiState.update { it.copy(selectedSale = null, receipt = null, detailLoading = false, error = error.userMessage()) }
                }
        }
    }

    fun closeSale() = _uiState.update { it.copy(selectedSale = null, receipt = null) }

    fun emailReceipt(email: String) {
        val receipt = _uiState.value.receipt ?: return
        if (_uiState.value.working) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { repository.emailReceipt(receipt, email) }
                .onSuccess { _uiState.update { it.copy(working = false, message = "Receipt sent to ${email.trim()}") } }
                .onFailure { error -> _uiState.update { it.copy(working = false, error = error.userMessage()) } }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ActivityViewModel(container.activityRepository) as T
    }
}

private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank)
    ?: "Something went wrong. Please try again."
