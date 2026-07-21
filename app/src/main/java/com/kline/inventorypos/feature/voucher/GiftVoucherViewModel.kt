package com.kline.inventorypos.feature.voucher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.CreateGiftVoucher
import com.kline.inventorypos.core.model.GiftVoucher
import com.kline.inventorypos.core.model.GiftVoucherTemplate
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.data.voucher.GiftVoucherRepository
import com.kline.inventorypos.data.voucher.VoucherMutationUncertainException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GiftVoucherUiState(
    val vouchers: List<GiftVoucher> = emptyList(),
    val templates: List<GiftVoucherTemplate> = emptyList(),
    val query: String = "",
    val status: String = "all",
    val selected: GiftVoucher? = null,
    val loading: Boolean = false,
    val detailLoading: Boolean = false,
    val working: Boolean = false,
    val uncertain: Boolean = false,
    val hasOpenRegister: Boolean = false,
    val canIssue: Boolean = false,
    val canRedeem: Boolean = false,
    val canManage: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class GiftVoucherViewModel(private val repository: GiftVoucherRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(GiftVoucherUiState())
    val uiState: StateFlow<GiftVoucherUiState> = _uiState.asStateFlow()
    private var sessionKey: String? = null
    private var searchJob: Job? = null

    fun bindSession(session: PosSession) {
        val key = "${session.user.id}:${session.branch.id}"
        if (key == sessionKey) return
        sessionKey = key
        _uiState.value = GiftVoucherUiState(
            hasOpenRegister = session.register != null,
            canIssue = session.user.hasPermission("gift_vouchers.issue"),
            canRedeem = session.user.hasPermission("gift_vouchers.redeem"),
            canManage = session.user.hasPermission("gift_vouchers.manage"),
        )
        refresh()
        loadTemplates()
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(350); refresh() }
    }

    fun setStatus(value: String) { _uiState.update { it.copy(status = value) }; refresh() }

    fun refresh() = viewModelScope.launch {
        _uiState.update { it.copy(loading = true, error = null) }
        runCatching { repository.vouchers(_uiState.value.query, _uiState.value.status) }
            .onSuccess { vouchers -> _uiState.update { it.copy(vouchers = vouchers, loading = false) } }
            .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.userMessage()) } }
    }

    private fun loadTemplates() = viewModelScope.launch {
        runCatching { repository.templates() }
            .onSuccess { templates -> _uiState.update { it.copy(templates = templates) } }
            .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
    }

    fun open(voucher: GiftVoucher) = loadDetail(voucher.id, clearUncertain = true)

    fun verify(value: String) {
        if (_uiState.value.detailLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(detailLoading = true, selected = null, uncertain = false, error = null) }
            runCatching { repository.validate(value) }
                .onSuccess { voucher -> _uiState.update { it.copy(selected = voucher, detailLoading = false, message = "Voucher verified") } }
                .onFailure { error -> _uiState.update { it.copy(detailLoading = false, error = error.userMessage()) } }
        }
    }

    fun create(request: CreateGiftVoucher) = mutate("Voucher draft created") { repository.create(request) }
    fun activate(method: String, reference: String?) {
        val voucher = _uiState.value.selected ?: return
        mutate("Voucher activated") { repository.activate(voucher.id, method, reference) }
    }
    fun redeem(amount: Long, notes: String) {
        val voucher = _uiState.value.selected ?: return
        mutate("Voucher redeemed") { repository.redeem(voucher.id, amount, notes) }
    }
    fun cancel(reason: String, refund: Boolean) {
        val voucher = _uiState.value.selected ?: return
        mutate(if (refund) "Voucher cancelled and refunded" else "Voucher cancelled") { repository.cancel(voucher.id, reason, refund) }
    }

    private fun mutate(successMessage: String, block: suspend () -> GiftVoucher) {
        if (_uiState.value.working || _uiState.value.uncertain) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { block() }
                .onSuccess { voucher ->
                    _uiState.update { it.copy(selected = voucher, working = false, uncertain = false, message = successMessage) }
                    refresh()
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, uncertain = error is VoucherMutationUncertainException, error = error.userMessage()) } }
        }
    }

    private fun loadDetail(id: String, clearUncertain: Boolean) {
        if (_uiState.value.detailLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(
                detailLoading = true,
                selected = if (clearUncertain) null else it.selected,
                uncertain = if (clearUncertain) false else it.uncertain,
                error = null,
            ) }
            runCatching { repository.detail(id) }
                .onSuccess { voucher -> _uiState.update { it.copy(selected = voucher, detailLoading = false, uncertain = false) } }
                .onFailure { error -> _uiState.update { it.copy(detailLoading = false, error = error.userMessage()) } }
        }
    }

    fun refreshDetail() = _uiState.value.selected?.id?.let { loadDetail(it, clearUncertain = false) }
    fun closeDetail() = _uiState.update { it.copy(selected = null, uncertain = false) }
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = GiftVoucherViewModel(container.giftVoucherRepository) as T
    }
}

private fun Throwable.userMessage() = message?.takeIf(String::isNotBlank) ?: "Something went wrong. Please try again."
