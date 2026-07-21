package com.kline.inventorypos.core.model

data class SaleSummary(
    val id: String,
    val receiptNumber: String,
    val customerName: String?,
    val total: Long,
    val paymentMethod: String,
    val status: String,
    val saleDate: String,
    val cashierName: String,
    val staffName: String?,
    val itemCount: Int,
    val returnStatus: String,
    val totalReturned: Long,
)

data class ReturnableItem(
    val saleItemId: String,
    val variantId: String,
    val productName: String,
    val variant: String,
    val sku: String,
    val unitPrice: Long,
    val taxAmount: Long,
    val originalQuantity: Int,
    val maxReturnable: Int,
)

data class ReturnLine(
    val item: ReturnableItem,
    val quantity: Int,
    val condition: String = "sellable",
) {
    val estimatedValue: Long
        get() = (item.unitPrice * quantity) +
            if (item.originalQuantity > 0) item.taxAmount * quantity / item.originalQuantity else 0
}

data class ReturnRequest(
    val saleId: String,
    val type: String,
    val reason: String,
    val notes: String,
    val refundMethod: String?,
    val lines: List<ReturnLine>,
)

data class ExchangeNewLine(val product: Product, val quantity: Int) {
    val value: Long get() = product.price * quantity
}

data class ExchangePreview(
    val returnedValue: Long,
    val newItemsValue: Long,
    val netAmount: Long,
)

data class ExchangeRequest(
    val saleId: String,
    val mode: String,
    val reason: String,
    val notes: String,
    val returnedLines: List<ReturnLine>,
    val newLines: List<ExchangeNewLine>,
    val settlementMethod: String? = null,
)

data class AftercareResult(
    val title: String,
    val reference: String,
    val message: String,
    val amount: Long,
)

object AftercareRules {
    fun estimatedReturn(lines: List<ReturnLine>): Long = lines.sumOf(ReturnLine::estimatedValue)
    fun settlementRequired(netAmount: Long): Boolean = netAmount != 0L
    fun storeOwes(netAmount: Long): Boolean = netAmount < 0L
}
