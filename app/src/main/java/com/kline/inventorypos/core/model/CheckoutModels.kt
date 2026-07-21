package com.kline.inventorypos.core.model

data class CheckoutQuote(
    val subtotal: Long,
    val promotionSavings: Long,
    val discountAmount: Long,
    val taxAmount: Long,
    val total: Long,
    val serverValidated: Boolean,
)

data class PaymentLeg(
    val method: String,
    val amount: Long,
    val reference: String? = null,
)

data class ReceiptLine(
    val name: String,
    val variant: String,
    val sku: String,
    val unitPrice: Long,
    val quantity: Int,
    val lineTotal: Long,
)

data class ConfirmedReceipt(
    val saleId: String,
    val receiptNumber: String,
    val saleDate: String,
    val branchName: String,
    val cashierName: String,
    val customerName: String?,
    val lines: List<ReceiptLine>,
    val subtotal: Long,
    val discountAmount: Long,
    val taxAmount: Long,
    val total: Long,
    val amountPaid: Long,
    val change: Long,
    val payments: List<PaymentLeg>,
)

enum class CheckoutAttemptStatus { SUBMITTING, UNCERTAIN, FAILED, CONFIRMED, CLOSED, PENDING_APPROVAL }

data class CheckoutAttempt(
    val id: String,
    val status: CheckoutAttemptStatus,
    val createdAt: Long,
    val receipt: ConfirmedReceipt? = null,
    val message: String? = null,
)

data class CheckoutResult(
    val attempt: CheckoutAttempt,
    val receipt: ConfirmedReceipt? = null,
)
