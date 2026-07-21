package com.kline.inventorypos.feature.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.CartLine
import com.kline.inventorypos.core.model.AppliedDiscount
import com.kline.inventorypos.core.model.CheckoutAttempt
import com.kline.inventorypos.core.model.CheckoutAttemptStatus
import com.kline.inventorypos.core.model.CheckoutQuote
import com.kline.inventorypos.core.model.ConfirmedReceipt
import com.kline.inventorypos.core.model.CatalogFreshness
import com.kline.inventorypos.core.model.CustomerSummary
import com.kline.inventorypos.core.model.DiscountOption
import com.kline.inventorypos.core.model.HeldCart
import com.kline.inventorypos.core.model.Product
import com.kline.inventorypos.core.model.PromotionSummary
import com.kline.inventorypos.core.model.PaymentLeg
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.data.catalog.CatalogRepository
import com.kline.inventorypos.data.sale.SaleRepository
import com.kline.inventorypos.data.checkout.CheckoutRepository
import com.kline.inventorypos.data.checkout.DiscountRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SaleUiState(
    val products: List<Product> = emptyList(),
    val categories: List<Pair<String, String>> = emptyList(),
    val cart: List<CartLine> = emptyList(),
    val heldCarts: List<HeldCart> = emptyList(),
    val query: String = "",
    val categoryId: String? = null,
    val customer: CustomerSummary? = null,
    val customerResults: List<CustomerSummary> = emptyList(),
    val promotions: List<PromotionSummary> = emptyList(),
    val discountOptions: List<DiscountOption> = emptyList(),
    val appliedDiscount: AppliedDiscount? = null,
    val checkoutQuote: CheckoutQuote? = null,
    val checkoutAttempt: CheckoutAttempt? = null,
    val receipt: ConfirmedReceipt? = null,
    val checkoutWorking: Boolean = false,
    val freshness: CatalogFreshness = CatalogFreshness(null, false),
    val working: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SaleViewModel(
    private val catalogRepository: CatalogRepository,
    private val saleRepository: SaleRepository,
    private val checkoutRepository: CheckoutRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SaleUiState())
    val uiState: StateFlow<SaleUiState> = _uiState.asStateFlow()
    private val query = MutableStateFlow("")
    private val categoryId = MutableStateFlow<String?>(null)
    private var session: PosSession? = null
    private var sessionKey: String? = null
    private var binding: Job? = null

    fun bindSession(session: PosSession) {
        val nextKey = "${session.user.id}:${session.branch.id}"
        if (nextKey == sessionKey) return
        this.session = session
        sessionKey = nextKey
        binding?.cancel()
        query.value = ""
        categoryId.value = null
        _uiState.value = SaleUiState(freshness = CatalogFreshness(null, true))
        binding = viewModelScope.launch {
            launch {
                combine(query, categoryId, ::Pair)
                    .flatMapLatest { (text, category) ->
                        catalogRepository.observeProducts(session.branch.id, text, category)
                    }
                    .collect { products -> _uiState.update { it.copy(products = products) } }
            }
            launch {
                catalogRepository.observeCategories(session.branch.id).collect { categories ->
                    _uiState.update { it.copy(categories = categories) }
                }
            }
            launch {
                catalogRepository.observeSyncedAt(session.branch.id).collect { syncedAt ->
                    _uiState.update { it.copy(freshness = it.freshness.copy(syncedAt = syncedAt)) }
                }
            }
            launch {
                saleRepository.observeCart(nextKey).collectLatest { cart ->
                    _uiState.update {
                        val adjustedDiscount = it.appliedDiscount?.option?.let { option ->
                            AppliedDiscount(option, DiscountRules.calculate(option, cart))
                                .takeIf { selected -> selected.amount > 0 }
                        }
                        it.copy(
                            cart = cart,
                            appliedDiscount = adjustedDiscount,
                            checkoutQuote = if (it.cart == cart) it.checkoutQuote else null,
                        )
                    }
                    val promotions = runCatching {
                        saleRepository.evaluatePromotions(cart, _uiState.value.customer?.id)
                    }.getOrDefault(emptyList())
                    _uiState.update { it.copy(promotions = promotions) }
                }
            }
            launch {
                saleRepository.observeHeldCarts(nextKey).collect { held ->
                    _uiState.update { it.copy(heldCarts = held) }
                }
            }
            launch { runCatching { saleRepository.refreshHeldCarts(nextKey) } }
            launch {
                runCatching { checkoutRepository.availableDiscounts() }
                    .onSuccess { options -> _uiState.update { it.copy(discountOptions = options) } }
            }
            launch {
                checkoutRepository.latestAttempt(nextKey)?.let { attempt ->
                    if (attempt.status in setOf(CheckoutAttemptStatus.UNCERTAIN, CheckoutAttemptStatus.PENDING_APPROVAL)) {
                        _uiState.update { it.copy(checkoutAttempt = attempt, error = attempt.message) }
                    } else if (
                        attempt.status == CheckoutAttemptStatus.CONFIRMED &&
                        attempt.receipt != null &&
                        System.currentTimeMillis() - attempt.createdAt < 24 * 60 * 60 * 1000L
                    ) {
                        _uiState.update { it.copy(checkoutAttempt = attempt, receipt = attempt.receipt) }
                    }
                }
            }
            refreshCatalog()
        }
    }

    fun setQuery(value: String) {
        query.value = value
        _uiState.update { it.copy(query = value) }
    }

    fun setCategory(id: String?) {
        categoryId.value = id
        _uiState.update { it.copy(categoryId = id) }
    }

    fun refreshCatalog() {
        val branch = session?.branch ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(freshness = it.freshness.copy(refreshing = true, error = null)) }
            runCatching { catalogRepository.refresh(branch.id) }
                .onSuccess {
                    _uiState.update { it.copy(freshness = it.freshness.copy(refreshing = false), message = "Catalog updated") }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(freshness = it.freshness.copy(refreshing = false, error = error.message))
                    }
                }
        }
    }

    fun addProduct(product: Product) = mutateCart { key -> saleRepository.add(key, product) }
    fun increase(variantId: String) = mutateCart { key -> saleRepository.increase(key, variantId) }
    fun decrease(variantId: String) = mutateCart { key -> saleRepository.decrease(key, variantId) }

    fun scan(code: String) {
        val branch = session?.branch ?: return
        val key = sessionKey ?: return
        viewModelScope.launch {
            val product = catalogRepository.findByCode(branch.id, code)
            if (product == null) {
                _uiState.update { it.copy(error = "No product matches barcode $code") }
            } else {
                runCatching { saleRepository.add(key, product) }
                    .onSuccess { _uiState.update { it.copy(message = "${product.name} added by scan") } }
                    .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
            }
        }
    }

    fun scannerFailed(message: String) = _uiState.update { it.copy(error = message) }

    fun searchCustomers(value: String) {
        viewModelScope.launch {
            val results = runCatching { saleRepository.searchCustomers(value) }
                .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
                .getOrDefault(emptyList())
            _uiState.update { it.copy(customerResults = results) }
        }
    }

    fun attachCustomer(customer: CustomerSummary?) {
        _uiState.update { it.copy(customer = customer, customerResults = emptyList()) }
        reevaluatePromotions()
    }

    fun applyDiscount(option: DiscountOption) {
        val amount = DiscountRules.calculate(option, _uiState.value.cart)
        if (amount <= 0) {
            _uiState.update { it.copy(error = "This discount does not apply to the current cart.") }
            return
        }
        _uiState.update {
            it.copy(
                appliedDiscount = AppliedDiscount(option, amount),
                checkoutQuote = null,
                message = "${option.name} applied",
            )
        }
    }

    fun removeDiscount() {
        _uiState.update { it.copy(appliedDiscount = null, checkoutQuote = null, message = "Discount removed") }
    }

    fun holdSale(notes: String?) {
        val key = sessionKey ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { saleRepository.hold(key, _uiState.value.customer, notes) }
                .onSuccess { syncState ->
                    _uiState.update {
                        it.copy(
                            working = false,
                            customer = null,
                            promotions = emptyList(),
                            appliedDiscount = null,
                            message = if (syncState == "pending") "Sale held offline · sync pending" else "Sale held",
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(working = false, error = error.userMessage()) } }
        }
    }

    fun recallHeld(id: String) {
        val key = sessionKey ?: return
        viewModelScope.launch {
            runCatching { saleRepository.recall(key, id) }
                .onSuccess { _uiState.update { it.copy(message = "Held sale restored") } }
                .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
        }
    }

    fun removeHeld(id: String) {
        viewModelScope.launch { saleRepository.removeHeld(id) }
    }

    fun clearCart() {
        val key = sessionKey ?: return
        viewModelScope.launch {
            saleRepository.clear(key)
            checkoutRepository.closeReceipt(key)
        }
    }

    fun prepareCheckout() {
        val current = _uiState.value
        if (current.cart.isEmpty() || current.checkoutWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(checkoutWorking = true, error = null, checkoutQuote = null) }
            runCatching { checkoutRepository.quote(current.cart, current.customer, current.appliedDiscount) }
                .onSuccess { quote -> _uiState.update { it.copy(checkoutWorking = false, checkoutQuote = quote) } }
                .onFailure { error -> _uiState.update { it.copy(checkoutWorking = false, error = error.userMessage()) } }
        }
    }

    fun submitCheckout(payments: List<PaymentLeg>) {
        val activeSession = session ?: return
        val key = sessionKey ?: return
        val current = _uiState.value
        val quote = current.checkoutQuote ?: return
        if (current.checkoutWorking || current.checkoutAttempt?.status == CheckoutAttemptStatus.UNCERTAIN) return
        viewModelScope.launch {
            _uiState.update { it.copy(checkoutWorking = true, error = null) }
            runCatching {
                checkoutRepository.submit(
                    sessionKey = key,
                    branchName = activeSession.branch.name,
                    cashierName = activeSession.user.fullName,
                    lines = current.cart,
                    customer = current.customer,
                    discount = current.appliedDiscount,
                    quote = quote,
                    payments = payments,
                )
            }.onSuccess { result ->
                if (result.receipt != null) saleRepository.clear(key)
                _uiState.update {
                    it.copy(
                        checkoutWorking = false,
                        checkoutAttempt = result.attempt,
                        receipt = result.receipt,
                        message = result.attempt.message,
                    )
                }
            }.onFailure { error ->
                val attempt = checkoutRepository.latestAttempt(key)
                _uiState.update {
                    it.copy(checkoutWorking = false, checkoutAttempt = attempt, error = error.userMessage())
                }
            }
        }
    }

    fun emailReceipt(email: String) {
        val receipt = _uiState.value.receipt ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { checkoutRepository.emailReceipt(receipt, email) }
                .onSuccess { _uiState.update { it.copy(working = false, message = "Receipt sent to $email") } }
                .onFailure { error -> _uiState.update { it.copy(working = false, error = error.userMessage()) } }
        }
    }

    fun startNewSale() {
        val key = sessionKey ?: return
        viewModelScope.launch { saleRepository.clear(key) }
        _uiState.update {
            it.copy(
                customer = null,
                promotions = emptyList(),
                appliedDiscount = null,
                checkoutQuote = null,
                checkoutAttempt = null,
                receipt = null,
            )
        }
    }

    fun acknowledgeUncertainCheckout() {
        val key = sessionKey ?: return
        viewModelScope.launch { checkoutRepository.acknowledgeUncertain(key) }
        _uiState.update { it.copy(checkoutAttempt = null) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun mutateCart(block: suspend (String) -> Unit) {
        val key = sessionKey ?: return
        viewModelScope.launch {
            runCatching { block(key) }
                .onFailure { error -> _uiState.update { it.copy(error = error.userMessage()) } }
        }
    }

    private fun reevaluatePromotions() {
        viewModelScope.launch {
            val promotions = runCatching {
                saleRepository.evaluatePromotions(_uiState.value.cart, _uiState.value.customer?.id)
            }.getOrDefault(emptyList())
            _uiState.update { it.copy(promotions = promotions) }
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SaleViewModel(container.catalogRepository, container.saleRepository, container.checkoutRepository) as T
    }
}

private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank)
    ?: "Something went wrong. Please try again."
