package com.kline.inventorypos.data.checkout

import com.google.gson.Gson
import com.kline.inventorypos.core.model.CartLine
import com.kline.inventorypos.core.model.AppliedDiscount
import com.kline.inventorypos.core.model.CheckoutAttempt
import com.kline.inventorypos.core.model.CheckoutAttemptStatus
import com.kline.inventorypos.core.model.CheckoutQuote
import com.kline.inventorypos.core.model.CheckoutResult
import com.kline.inventorypos.core.model.ConfirmedReceipt
import com.kline.inventorypos.core.model.CustomerSummary
import com.kline.inventorypos.core.model.DiscountOption
import com.kline.inventorypos.core.model.PaymentLeg
import com.kline.inventorypos.core.model.ReceiptLine
import com.kline.inventorypos.data.local.CheckoutAttemptEntity
import com.kline.inventorypos.data.local.InventoryPosDao
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.CartItemRequest
import com.kline.inventorypos.data.network.CreateSaleRequest
import com.kline.inventorypos.data.network.EmailReceiptRequest
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.PendingApprovalDto
import com.kline.inventorypos.data.network.ReceiptDto
import com.kline.inventorypos.data.network.SaleDto
import com.kline.inventorypos.data.network.SalePaymentRequest
import com.kline.inventorypos.data.network.TaxPreviewItemRequest
import com.kline.inventorypos.data.network.TaxPreviewRequest
import com.kline.inventorypos.data.network.ValidateCartRequest
import com.kline.inventorypos.data.sale.SaleRepository
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToLong

interface CheckoutRepository {
    suspend fun availableDiscounts(): List<DiscountOption>
    suspend fun quote(lines: List<CartLine>, customer: CustomerSummary?, discount: AppliedDiscount?): CheckoutQuote
    suspend fun submit(
        sessionKey: String,
        branchName: String,
        cashierName: String,
        lines: List<CartLine>,
        customer: CustomerSummary?,
        discount: AppliedDiscount?,
        quote: CheckoutQuote,
        payments: List<PaymentLeg>,
    ): CheckoutResult
    suspend fun latestAttempt(sessionKey: String): CheckoutAttempt?
    suspend fun acknowledgeUncertain(sessionKey: String)
    suspend fun closeReceipt(sessionKey: String)
    suspend fun emailReceipt(receipt: ConfirmedReceipt, email: String)
}

class DefaultCheckoutRepository(
    private val dao: InventoryPosDao,
    private val api: InventoryPosApi,
    private val saleRepository: SaleRepository,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
) : CheckoutRepository {
    override suspend fun availableDiscounts(): List<DiscountOption> {
        if (isDemo()) return listOf(
            DiscountOption("demo-10", "Staff-approved 10%", "percentage", 10.0),
            DiscountOption("demo-5000", "UGX 5,000 off", "fixed_amount", 5_000.0),
        )
        return api.discounts().data
            .filter { it.maxUses == null || (it.currentUses ?: 0) < it.maxUses }
            .map { dto ->
                DiscountOption(
                    id = dto.id,
                    name = dto.name,
                    type = dto.discountType,
                    value = dto.value,
                    scope = dto.scope,
                    scopeId = dto.scopeId,
                    requiresApproval = dto.requiresApproval,
                    approvalThreshold = dto.approvalThreshold?.roundToLong(),
                    minimumSpend = dto.conditions?.minimumSpend?.roundToLong(),
                    minimumQuantity = dto.conditions?.minimumQuantity,
                )
            }
    }

    override suspend fun quote(
        lines: List<CartLine>,
        customer: CustomerSummary?,
        discount: AppliedDiscount?,
    ): CheckoutQuote {
        check(lines.isNotEmpty()) { "Add an item before checkout." }
        val subtotal = lines.sumOf { it.lineTotal }
        val promotions = saleRepository.evaluatePromotions(lines, customer?.id)
        val promotionSavings = promotions.sumOf { it.savings }.coerceAtMost(subtotal)
        val discountAmount = discount?.amount.orZero().coerceAtMost(subtotal - promotionSavings)
        if (isDemo()) {
            return CheckoutQuote(
                subtotal = subtotal,
                promotionSavings = promotionSavings,
                discountAmount = discountAmount,
                taxAmount = 0,
                total = subtotal - promotionSavings - discountAmount,
                serverValidated = false,
            )
        }

        val cartItems = lines.map { CartItemRequest(it.product.id, it.quantity) }
        val validation = api.validateCart(ValidateCartRequest(cartItems))
        if (!validation.valid) {
            val reason = validation.items.firstOrNull { !it.valid }?.let { item ->
                item.error ?: "Only ${item.availableQuantity?.toInt() ?: 0} available."
            } ?: "One or more items are no longer available."
            throw CheckoutRejectedException(reason)
        }

        val tax = api.previewTax(
            TaxPreviewRequest(
                lines.map { line ->
                    TaxPreviewItemRequest(
                        variantId = line.product.id,
                        productId = line.product.productId,
                        categoryId = line.product.categoryId,
                        unitPrice = line.product.price,
                        quantity = line.quantity,
                    )
                },
            ),
        ).data
        val discountByVariant = mutableMapOf<String, Long>()
        promotions.forEach { promotion ->
            val eligible = if (promotion.applicableVariantIds.isEmpty()) lines
            else lines.filter { it.product.id in promotion.applicableVariantIds }
            allocateDiscount(promotion.savings, eligible).forEach { (variantId, amount) ->
                discountByVariant[variantId] = (discountByVariant[variantId] ?: 0L) + amount
            }
        }
        discount?.let { selected ->
            val eligible = DiscountRules.eligibleLines(selected.option, lines)
            allocateDiscount(discountAmount, eligible).forEach { (variantId, amount) ->
                discountByVariant[variantId] = (discountByVariant[variantId] ?: 0L) + amount
            }
        }

        val taxConfig = tax.items.associateBy { it.variantId }
        var reportedTax = 0L
        var addedTax = 0L
        lines.forEach { line ->
            val base = (line.lineTotal - (discountByVariant[line.product.id] ?: 0L)).coerceAtLeast(0L)
            val config = taxConfig[line.product.id]
            val rate = config?.taxRate ?: 0.0
            val lineTax = when (config?.taxType) {
                "inclusive" -> (base - base / (1.0 + rate)).roundToLong()
                else -> (base * rate).roundToLong()
            }
            reportedTax += lineTax
            if (config?.taxType != "inclusive") addedTax += lineTax
        }
        return CheckoutQuote(
            subtotal = subtotal,
            promotionSavings = promotionSavings,
            discountAmount = discountAmount,
            taxAmount = reportedTax,
            total = subtotal - promotionSavings - discountAmount + addedTax,
            serverValidated = true,
        )
    }

    override suspend fun submit(
        sessionKey: String,
        branchName: String,
        cashierName: String,
        lines: List<CartLine>,
        customer: CustomerSummary?,
        discount: AppliedDiscount?,
        quote: CheckoutQuote,
        payments: List<PaymentLeg>,
    ): CheckoutResult {
        PaymentRules.requireValid(payments, quote.total, customer != null)
        val attemptId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val request = CreateSaleRequest(
            customerId = customer?.id,
            items = lines.map { CartItemRequest(it.product.id, it.quantity) },
            payments = payments.map { SalePaymentRequest(it.method, it.amount, it.reference) },
            paymentMethod = if (payments.size > 1) "split" else payments.single().method,
            amountPaid = payments.sumOf { it.amount },
            // Presets are recalculated from discountId by the server. For the
            // manual path, submit the same capped amount shown in this quote.
            discountAmount = quote.discountAmount,
            discountId = discount?.option?.id,
            paymentReference = payments.singleOrNull()?.reference,
        )
        var attempt = CheckoutAttemptEntity(
            attemptId = attemptId,
            sessionKey = sessionKey,
            requestJson = gson.toJson(request),
            status = CheckoutAttemptStatus.SUBMITTING.name,
            createdAt = now,
            receiptJson = null,
            message = null,
        )
        dao.putCheckoutAttempt(attempt)

        if (isDemo()) {
            val receipt = demoReceipt(attemptId, branchName, cashierName, lines, customer, quote, payments)
            attempt = attempt.copy(status = CheckoutAttemptStatus.CONFIRMED.name, receiptJson = gson.toJson(receipt))
            dao.putCheckoutAttempt(attempt)
            return CheckoutResult(attempt.toDomain(gson), receipt)
        }

        try {
            val response = api.createSale(attemptId, request)
            if (response.status == "pending_approval") {
                val pending = gson.fromJson(response.data, PendingApprovalDto::class.java)
                val message = "Approval requested · ${pending.approvalRequestId.take(8)}"
                attempt = attempt.copy(status = CheckoutAttemptStatus.PENDING_APPROVAL.name, message = message)
                dao.putCheckoutAttempt(attempt)
                return CheckoutResult(attempt.toDomain(gson))
            }

            val sale = gson.fromJson(response.data, SaleDto::class.java)
            val receipt = runCatching { api.receipt(sale.id).data.toDomain(branchName) }
                .getOrElse { sale.toFallbackReceipt(branchName, cashierName, customer, payments) }
            attempt = attempt.copy(
                status = CheckoutAttemptStatus.CONFIRMED.name,
                receiptJson = gson.toJson(receipt),
                message = response.message,
            )
            dao.putCheckoutAttempt(attempt)
            return CheckoutResult(attempt.toDomain(gson), receipt)
        } catch (error: HttpException) {
            val message = error.response()?.errorBody()?.string()?.let { body ->
                runCatching { gson.fromJson(body, ApiError::class.java).message }.getOrNull()
            } ?: "Checkout was rejected by the server."
            dao.putCheckoutAttempt(attempt.copy(status = CheckoutAttemptStatus.FAILED.name, message = message))
            throw CheckoutRejectedException(message, error)
        } catch (error: IOException) {
            val message = "The connection ended before confirmation. Do not submit again until this sale is checked in Activity."
            dao.putCheckoutAttempt(attempt.copy(status = CheckoutAttemptStatus.UNCERTAIN.name, message = message))
            throw CheckoutUncertainException(message, error)
        }
    }

    override suspend fun latestAttempt(sessionKey: String): CheckoutAttempt? =
        dao.getLatestCheckoutAttempt(sessionKey)?.toDomain(gson)?.let { attempt ->
            if (attempt.status == CheckoutAttemptStatus.SUBMITTING) {
                attempt.copy(
                    status = CheckoutAttemptStatus.UNCERTAIN,
                    message = "Checkout was interrupted before confirmation. Verify it in Activity before retrying.",
                )
            } else attempt
        }

    override suspend fun acknowledgeUncertain(sessionKey: String) {
        val latest = dao.getLatestCheckoutAttempt(sessionKey) ?: return
        if (latest.status in setOf(CheckoutAttemptStatus.SUBMITTING.name, CheckoutAttemptStatus.UNCERTAIN.name)) {
            dao.putCheckoutAttempt(
                latest.copy(
                    status = CheckoutAttemptStatus.FAILED.name,
                    message = "Cashier verified Activity before retrying.",
                ),
            )
        }
    }

    override suspend fun closeReceipt(sessionKey: String) {
        val latest = dao.getLatestCheckoutAttempt(sessionKey) ?: return
        if (latest.status == CheckoutAttemptStatus.CONFIRMED.name) {
            dao.putCheckoutAttempt(latest.copy(status = CheckoutAttemptStatus.CLOSED.name))
        }
    }

    override suspend fun emailReceipt(receipt: ConfirmedReceipt, email: String) {
        require(email.isNotBlank() && '@' in email) { "Enter a valid email address." }
        if (isDemo()) return
        api.emailReceipt(receipt.saleId, EmailReceiptRequest(email.trim()))
    }
}

object PaymentRules {
    fun requireValid(payments: List<PaymentLeg>, total: Long, hasCustomer: Boolean) {
        require(total > 0) { "Sale total must be greater than zero." }
        require(payments.isNotEmpty()) { "Add a payment method." }
        require(payments.map { it.method }.distinct().size == payments.size) { "Each payment method can be used once." }
        require(payments.all { it.amount > 0 }) { "Payment amounts must be greater than zero." }
        require(hasCustomer || payments.none { it.method in CustomerMethods }) {
            "Attach a customer before using prepaid, credit, or loyalty."
        }
        val allocated = payments.sumOf { it.amount }
        require(allocated >= total) { "Payment is short by ${total - allocated}." }
        val cash = payments.filter { it.method == "cash" }.sumOf { it.amount }
        val nonCash = allocated - cash
        require(allocated == total || cash > 0) { "Only cash can create change." }
        require(nonCash <= total) { "Non-cash payments exceed the sale total." }
    }

    private val CustomerMethods = setOf("prepaid", "credit", "loyalty")
}

class CheckoutRejectedException(message: String, cause: Throwable? = null) : Exception(message, cause)
class CheckoutUncertainException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun allocateDiscount(amount: Long, lines: List<CartLine>): Map<String, Long> {
    if (amount <= 0 || lines.isEmpty()) return emptyMap()
    val base = lines.sumOf { it.lineTotal }
    if (base <= 0) return emptyMap()
    var allocated = 0L
    return lines.mapIndexed { index, line ->
        val share = if (index == lines.lastIndex) amount - allocated
        else (amount * line.lineTotal.toDouble() / base).roundToLong()
        allocated += share
        line.product.id to share
    }.toMap()
}

private fun CheckoutAttemptEntity.toDomain(gson: Gson): CheckoutAttempt = CheckoutAttempt(
    id = attemptId,
    status = CheckoutAttemptStatus.valueOf(status),
    createdAt = createdAt,
    receipt = receiptJson?.let { gson.fromJson(it, ConfirmedReceipt::class.java) },
    message = message,
)

private fun ReceiptDto.toDomain(branchName: String) = ConfirmedReceipt(
    saleId = saleId,
    receiptNumber = receiptNumber,
    saleDate = date,
    branchName = branchName,
    cashierName = processedBy,
    customerName = customer?.name,
    lines = items.map {
        ReceiptLine(
            name = it.productName,
            variant = it.variantAttributes.orEmpty().values.joinToString(" · ").ifBlank { "Standard" },
            sku = it.sku,
            unitPrice = it.unitPrice.roundToLong(),
            quantity = it.quantity,
            lineTotal = it.lineTotal.roundToLong(),
        )
    },
    subtotal = subtotal.roundToLong(),
    discountAmount = discountAmount.roundToLong(),
    taxAmount = taxAmount.roundToLong(),
    total = totalAmount.roundToLong(),
    amountPaid = amountPaid.roundToLong(),
    change = changeAmount.roundToLong(),
    payments = payments.map { PaymentLeg(it.method, it.amount.roundToLong(), it.reference) },
)

private fun SaleDto.toFallbackReceipt(
    branchName: String,
    cashierName: String,
    customer: CustomerSummary?,
    payments: List<PaymentLeg>,
) = ConfirmedReceipt(
    saleId = id,
    receiptNumber = receiptNumber,
    saleDate = saleDate ?: createdAt.orEmpty(),
    branchName = branchName,
    cashierName = cashierName,
    customerName = customer?.name,
    lines = items.orEmpty().map {
        ReceiptLine(
            it.productName,
            it.variantAttributes.orEmpty().values.joinToString(" · ").ifBlank { "Standard" },
            it.sku,
            it.unitPrice.roundToLong(),
            it.quantity,
            it.lineTotal.roundToLong(),
        )
    },
    subtotal = subtotal.roundToLong(),
    discountAmount = discountAmount.roundToLong(),
    taxAmount = taxAmount.roundToLong(),
    total = totalAmount.roundToLong(),
    amountPaid = amountPaid.roundToLong(),
    change = changeAmount.roundToLong(),
    payments = payments,
)

private fun demoReceipt(
    id: String,
    branchName: String,
    cashierName: String,
    lines: List<CartLine>,
    customer: CustomerSummary?,
    quote: CheckoutQuote,
    payments: List<PaymentLeg>,
) = ConfirmedReceipt(
    saleId = id,
    receiptNumber = "DEMO-${id.take(8).uppercase()}",
    saleDate = Instant.now().toString(),
    branchName = branchName,
    cashierName = cashierName,
    customerName = customer?.name,
    lines = lines.map { ReceiptLine(it.product.name, it.product.variant, it.product.sku, it.product.price, it.quantity, it.lineTotal) },
    subtotal = quote.subtotal,
    discountAmount = quote.promotionSavings + quote.discountAmount,
    taxAmount = quote.taxAmount,
    total = quote.total,
    amountPaid = payments.sumOf { it.amount },
    change = (payments.sumOf { it.amount } - quote.total).coerceAtLeast(0),
    payments = payments,
)

object DiscountRules {
    fun calculate(option: DiscountOption, lines: List<CartLine>): Long {
        if (lines.isEmpty()) return 0
        val subtotal = lines.sumOf { it.lineTotal }
        val quantity = lines.sumOf { it.quantity }
        if (option.minimumSpend != null && subtotal < option.minimumSpend) return 0
        if (option.minimumQuantity != null && quantity < option.minimumQuantity) return 0
        val eligibleSubtotal = eligibleLines(option, lines).sumOf { it.lineTotal }
        return when (option.type) {
            "percentage" -> (eligibleSubtotal * option.value / 100.0).roundToLong()
            "fixed_amount" -> option.value.roundToLong().coerceAtMost(eligibleSubtotal)
            else -> 0
        }.coerceIn(0, subtotal)
    }

    fun eligibleLines(option: DiscountOption, lines: List<CartLine>): List<CartLine> = when (option.scope) {
        "item" -> lines.filter { it.product.id == option.scopeId }
        "category" -> lines.filter { it.product.categoryId == option.scopeId }
        // The variants endpoint currently exposes the brand name but not its ID.
        // The server remains authoritative for brand-scoped preset discounts.
        else -> lines
    }
}

private fun Long?.orZero(): Long = this ?: 0L
