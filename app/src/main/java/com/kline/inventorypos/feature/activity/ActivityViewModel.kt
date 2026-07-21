package com.kline.inventorypos.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.ConfirmedReceipt
import com.kline.inventorypos.core.model.AftercareResult
import com.kline.inventorypos.core.model.ExchangePreview
import com.kline.inventorypos.core.model.ExchangeRequest
import com.kline.inventorypos.core.model.ReturnRequest
import com.kline.inventorypos.core.model.ReturnableItem
import com.kline.inventorypos.core.model.SaleSummary
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.data.activity.ActivityRepository
import com.kline.inventorypos.data.activity.AftercareUncertainException
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
    val hasOpenRegister: Boolean = false,
    val returnableItems: List<ReturnableItem> = emptyList(),
    val aftercareLoading: Boolean = false,
    val aftercareWorking: Boolean = false,
    val exchangePreview: ExchangePreview? = null,
    val aftercareResult: AftercareResult? = null,
    val aftercareUncertain: Boolean = false,
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
            hasOpenRegister = session.register != null,
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

    fun loadReturnableItems() {
        val sale = _uiState.value.selectedSale ?: return
        if (_uiState.value.aftercareLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(aftercareLoading = true, returnableItems = emptyList(), aftercareResult = null, exchangePreview = null, aftercareUncertain = false, error = null) }
            runCatching { repository.returnableItems(sale) }
                .onSuccess { items -> _uiState.update { it.copy(returnableItems = items, aftercareLoading = false) } }
                .onFailure { error -> _uiState.update { it.copy(aftercareLoading = false, error = error.userMessage()) } }
        }
    }

    fun submitReturn(request: ReturnRequest) {
        if (_uiState.value.aftercareWorking || _uiState.value.aftercareUncertain) return
        viewModelScope.launch {
            _uiState.update { it.copy(aftercareWorking = true, error = null) }
            runCatching { repository.createReturn(request) }
                .onSuccess { result -> _uiState.update { it.copy(aftercareWorking = false, aftercareResult = result) } }
                .onFailure(::handleAftercareFailure)
        }
    }

    fun previewExchange(request: ExchangeRequest) {
        if (_uiState.value.aftercareWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(aftercareWorking = true, exchangePreview = null, error = null) }
            runCatching { repository.previewExchange(request) }
                .onSuccess { preview -> _uiState.update { it.copy(aftercareWorking = false, exchangePreview = preview) } }
                .onFailure { error -> _uiState.update { it.copy(aftercareWorking = false, error = error.userMessage()) } }
        }
    }

    fun submitExchange(request: ExchangeRequest) {
        if (_uiState.value.aftercareWorking || _uiState.value.aftercareUncertain) return
        viewModelScope.launch {
            _uiState.update { it.copy(aftercareWorking = true, error = null) }
            runCatching { repository.createExchange(request) }
                .onSuccess { result -> _uiState.update { it.copy(aftercareWorking = false, aftercareResult = result) } }
                .onFailure(::handleAftercareFailure)
        }
    }

    private fun handleAftercareFailure(error: Throwable) = _uiState.update {
        it.copy(
            aftercareWorking = false,
            aftercareUncertain = error is AftercareUncertainException,
            error = error.userMessage(),
        )
    }

    fun finishAftercare() {
        _uiState.update { it.copy(selectedSale = null, receipt = null, returnableItems = emptyList(), exchangePreview = null, aftercareResult = null, aftercareUncertain = false) }
        refresh()
    }

    fun clearAftercare() = _uiState.update { it.copy(returnableItems = emptyList(), exchangePreview = null, aftercareResult = null, aftercareUncertain = false) }

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
