package com.kline.inventorypos.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.InventorySummary
import com.kline.inventorypos.core.model.InventoryMutationResult
import com.kline.inventorypos.core.model.GrnLine
import com.kline.inventorypos.core.model.GrnSummary
import com.kline.inventorypos.core.model.Product
import com.kline.inventorypos.core.model.PriceHistoryEntry
import com.kline.inventorypos.core.model.PriceUpdate
import com.kline.inventorypos.core.model.PricingVariant
import com.kline.inventorypos.core.model.LabelPrintItem
import com.kline.inventorypos.core.model.ReceiveStockLine
import com.kline.inventorypos.core.model.ReceiveStockDraft
import com.kline.inventorypos.core.model.StockMovement
import com.kline.inventorypos.core.model.StockTransfer
import com.kline.inventorypos.core.model.SupplierSummary
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.core.session.PosBranch
import com.kline.inventorypos.data.catalog.CatalogRepository
import com.kline.inventorypos.data.inventory.InventoryRepository
import com.kline.inventorypos.data.inventory.localSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InventoryUiState(
    val products: List<Product> = emptyList(),
    val summary: InventorySummary? = null,
    val movements: List<StockMovement> = emptyList(),
    val suppliers: List<SupplierSummary> = emptyList(),
    val destinationBranches: List<PosBranch> = emptyList(),
    val transfers: List<StockTransfer> = emptyList(),
    val receiveDraft: ReceiveStockDraft? = null,
    val grns: List<GrnSummary> = emptyList(),
    val grnDetails: Map<String, List<GrnLine>> = emptyMap(),
    val pricingVariants: List<PricingVariant> = emptyList(),
    val priceHistory: List<PriceHistoryEntry> = emptyList(),
    val canReceive: Boolean = false,
    val canAdjust: Boolean = false,
    val canTransfer: Boolean = false,
    val canViewProducts: Boolean = false,
    val canEditPrices: Boolean = false,
    val pricingLoading: Boolean = false,
    val refreshing: Boolean = false,
    val working: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val receiveResult: InventoryMutationResult? = null,
)

class InventoryViewModel(
    private val catalogRepository: CatalogRepository,
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()
    private var session: PosSession? = null
    private var sessionKey: String? = null
    private var productBinding: Job? = null

    fun bindSession(session: PosSession) {
        val nextKey = "${session.user.id}:${session.branch.id}"
        if (sessionKey == nextKey) return
        this.session = session
        sessionKey = nextKey
        productBinding?.cancel()
        _uiState.value = InventoryUiState(
            canReceive = session.user.hasPermission("inventory.receive"),
            canAdjust = session.user.hasPermission("inventory.adjust"),
            canTransfer = session.user.hasPermission("inventory.transfer"),
            canViewProducts = session.user.hasPermission("products.view"),
            canEditPrices = session.user.hasPermission("products.edit"),
        )
        productBinding = viewModelScope.launch {
            catalogRepository.observeProducts(session.branch.id, "", null).collect { products ->
                _uiState.update { current ->
                    current.copy(
                        products = products,
                        summary = if (session.isDemo) localSummary(products) else current.summary,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        val active = session ?: return
        if (_uiState.value.refreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true, error = null) }
            runCatching {
                runCatching { catalogRepository.refresh(active.branch.id) }
                val products = _uiState.value.products
                val summary = inventoryRepository.summary(products)
                val movements = inventoryRepository.movements()
                val suppliers = if (_uiState.value.canReceive) {
                    runCatching { inventoryRepository.suppliers() }.getOrDefault(emptyList())
                } else emptyList()
                val destinations = if (_uiState.value.canTransfer) {
                    runCatching { inventoryRepository.destinationBranches(active.branch.id) }.getOrDefault(emptyList())
                } else emptyList()
                val transfers = if (_uiState.value.canTransfer) {
                    runCatching { inventoryRepository.transfers() }.getOrDefault(emptyList())
                } else emptyList()
                val draft = if (_uiState.value.canReceive) {
                    runCatching { inventoryRepository.receiveDraft() }.getOrNull()
                } else null
                val grns = runCatching { inventoryRepository.grnHistory() }.getOrDefault(emptyList())
                InventoryRefresh(summary, movements, suppliers, destinations, transfers, draft, grns)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        summary = result.summary,
                        movements = result.movements,
                        suppliers = result.suppliers,
                        destinationBranches = result.destinations,
                        transfers = result.transfers,
                        receiveDraft = result.draft,
                        grns = result.grns,
                        refreshing = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(refreshing = false, error = error.userMessage()) }
            }
        }
    }

    fun adjust(product: Product, quantityChange: Int, reason: String) {
        if (!_uiState.value.canAdjust || _uiState.value.working) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { inventoryRepository.adjust(product, quantityChange, reason) }
                .onSuccess { result ->
                    if (session?.isDemo == true && !result.pendingApproval) {
                        updateLocalStock(mapOf(product.id to quantityChange))
                    }
                    _uiState.update { it.copy(working = false, message = result.message) }
                    if (session?.isDemo != true && !result.pendingApproval) refresh()
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, error = error.userMessage()) } }
        }
    }

    fun receive(supplier: SupplierSummary, lines: List<ReceiveStockLine>, notes: String?) {
        if (!_uiState.value.canReceive || _uiState.value.working) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null, receiveResult = null) }
            runCatching { inventoryRepository.receive(supplier, lines, notes) }
                .onSuccess { result ->
                    if (session?.isDemo == true && !result.pendingApproval) {
                        updateLocalStock(lines.associate { it.product.id to it.quantity })
                    }
                    _uiState.update {
                        it.copy(
                            working = false,
                            message = result.message,
                            receiveResult = result,
                            receiveDraft = null,
                        )
                    }
                    if (session?.isDemo != true && !result.pendingApproval) refresh()
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, error = error.userMessage()) } }
        }
    }

    fun createTransfer(destination: PosBranch, lines: List<ReceiveStockLine>, notes: String?) {
        if (!_uiState.value.canTransfer || _uiState.value.working) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { inventoryRepository.createTransfer(destination, lines, notes) }
                .onSuccess { transfer ->
                    _uiState.update {
                        it.copy(
                            working = false,
                            transfers = listOf(transfer) + it.transfers.filterNot { existing -> existing.id == transfer.id },
                            message = "${transfer.number} requested",
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, error = error.userMessage()) } }
        }
    }

    fun transitionTransfer(transfer: StockTransfer, action: String) {
        if (!_uiState.value.canTransfer || _uiState.value.working) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { inventoryRepository.transitionTransfer(transfer, action) }
                .onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            working = false,
                            transfers = it.transfers.map { current -> if (current.id == updated.id) updated else current },
                            message = "${updated.number} ${updated.status}",
                        )
                    }
                    if (session?.isDemo != true && action in setOf("dispatch", "receive")) refresh()
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, error = error.userMessage()) } }
        }
    }

    fun saveReceiveDraft(supplier: SupplierSummary?, lines: List<ReceiveStockLine>, notes: String?) {
        if (!_uiState.value.canReceive || _uiState.value.working) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { inventoryRepository.saveDraft(supplier, lines, notes) }
                .onSuccess { draft ->
                    _uiState.update { it.copy(working = false, receiveDraft = draft, message = "Receive draft saved") }
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, error = error.userMessage()) } }
        }
    }

    fun deleteReceiveDraft() {
        val draft = _uiState.value.receiveDraft ?: return
        viewModelScope.launch {
            runCatching { inventoryRepository.deleteDraft(draft.id) }
                .onSuccess { _uiState.update { it.copy(receiveDraft = null, message = "Draft discarded") } }
                .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
        }
    }

    fun loadGrn(reference: String) {
        if (reference in _uiState.value.grnDetails || _uiState.value.working) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { inventoryRepository.grnDetail(reference) }
                .onSuccess { lines ->
                    _uiState.update { it.copy(working = false, grnDetails = it.grnDetails + (reference to lines)) }
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, error = error.userMessage()) } }
        }
    }

    fun loadPricing(force: Boolean = false) {
        if (!_uiState.value.canViewProducts || _uiState.value.pricingLoading) return
        if (!force && _uiState.value.pricingVariants.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(pricingLoading = true, error = null) }
            runCatching { inventoryRepository.pricing(_uiState.value.products) }
                .onSuccess { snapshot ->
                    _uiState.update {
                        it.copy(
                            pricingLoading = false,
                            pricingVariants = snapshot.variants,
                            priceHistory = snapshot.history,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(pricingLoading = false, error = error.userMessage()) }
                }
        }
    }

    fun updatePrice(variant: PricingVariant, newPrice: Long, reason: String) {
        if (!_uiState.value.canEditPrices || _uiState.value.working) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { inventoryRepository.updatePrices(listOf(PriceUpdate(variant.id, newPrice)), reason) }
                .onSuccess { result ->
                    _uiState.update { current ->
                        current.copy(
                            working = false,
                            message = result.message,
                            pricingVariants = if (result.pendingApproval) current.pricingVariants else {
                                current.pricingVariants.map { item ->
                                    if (item.id == variant.id) item.copy(price = newPrice) else item
                                }
                            },
                        )
                    }
                    if (session?.isDemo != true && !result.pendingApproval) {
                        session?.let { active -> runCatching { catalogRepository.refresh(active.branch.id) } }
                        loadPricing(force = true)
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, error = error.userMessage()) } }
        }
    }

    fun recordLabelPrint(items: List<LabelPrintItem>) {
        if (!_uiState.value.canViewProducts || items.isEmpty()) return
        viewModelScope.launch {
            runCatching { inventoryRepository.recordLabelPrint(items) }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(message = "${items.sumOf(LabelPrintItem::copies)} labels sent to the print service")
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = "Labels opened for printing; audit sync failed: ${error.userMessage()}") }
                }
        }
    }

    fun consumeReceiveResult() = _uiState.update { it.copy(receiveResult = null) }
    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun updateLocalStock(changes: Map<String, Int>) {
        _uiState.update { current ->
            val products = current.products.map { product ->
                product.copy(stock = (product.stock + (changes[product.id] ?: 0)).coerceAtLeast(0))
            }
            current.copy(products = products, summary = localSummary(products))
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            InventoryViewModel(container.catalogRepository, container.inventoryRepository) as T
    }
}

private data class InventoryRefresh(
    val summary: InventorySummary,
    val movements: List<StockMovement>,
    val suppliers: List<SupplierSummary>,
    val destinations: List<PosBranch>,
    val transfers: List<StockTransfer>,
    val draft: ReceiveStockDraft?,
    val grns: List<GrnSummary>,
)

private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank)
    ?: "Something went wrong. Please try again."
