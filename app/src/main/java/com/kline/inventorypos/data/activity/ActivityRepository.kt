package com.kline.inventorypos.data.activity

import com.google.gson.Gson
import com.kline.inventorypos.core.model.ConfirmedReceipt
import com.kline.inventorypos.core.model.AftercareResult
import com.kline.inventorypos.core.model.ExchangePreview
import com.kline.inventorypos.core.model.ExchangeRequest
import com.kline.inventorypos.core.model.PaymentLeg
import com.kline.inventorypos.core.model.ReceiptLine
import com.kline.inventorypos.core.model.SaleSummary
import com.kline.inventorypos.core.model.ReturnRequest
import com.kline.inventorypos.core.model.ReturnableItem
import com.kline.inventorypos.core.model.SampleProducts
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.EmailReceiptRequest
import com.kline.inventorypos.data.network.CreateExchangeRequest
import com.kline.inventorypos.data.network.CreateReturnRequest
import com.kline.inventorypos.data.network.ExchangeNewItemRequest
import com.kline.inventorypos.data.network.ExchangePreviewRequest
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.ReceiptDto
import com.kline.inventorypos.data.network.ReturnItemRequest
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import kotlin.math.roundToLong

interface ActivityRepository {
    suspend fun sales(query: String): List<SaleSummary>
    suspend fun receipt(sale: SaleSummary, branchName: String): ConfirmedReceipt
    suspend fun emailReceipt(receipt: ConfirmedReceipt, email: String)
    suspend fun returnableItems(sale: SaleSummary): List<ReturnableItem>
    suspend fun createReturn(request: ReturnRequest): AftercareResult
    suspend fun previewExchange(request: ExchangeRequest): ExchangePreview
    suspend fun createExchange(request: ExchangeRequest): AftercareResult
}

class AftercareUncertainException : IllegalStateException(
    "The connection ended before confirmation. Do not submit again until you verify this receipt in Activity.",
)

class DefaultActivityRepository(
    private val api: InventoryPosApi,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
) : ActivityRepository {
    override suspend fun sales(query: String): List<SaleSummary> {
        if (isDemo()) return demoSales().filter { sale ->
            query.isBlank() || listOf(sale.receiptNumber, sale.customerName, sale.cashierName, sale.staffName)
                .filterNotNull().any { it.contains(query, true) }
        }
        return apiCall { api.sales(search = query.trim().takeIf(String::isNotBlank)).data }.map { dto ->
            SaleSummary(
                id = dto.id,
                receiptNumber = dto.receiptNumber,
                customerName = listOfNotNull(dto.customerFirstName, dto.customerLastName).joinToString(" ").ifBlank { null },
                total = dto.totalAmount.roundToLong(),
                paymentMethod = dto.paymentMethod,
                status = dto.status,
                saleDate = dto.saleDate,
                cashierName = dto.processedByName ?: "Team member",
                staffName = dto.staffName,
                itemCount = dto.itemCount ?: 0,
                returnStatus = dto.returnStatus ?: "none",
                totalReturned = dto.totalReturned?.roundToLong() ?: 0,
            )
        }
    }

    override suspend fun receipt(sale: SaleSummary, branchName: String): ConfirmedReceipt {
        if (isDemo()) return demoReceipt(sale, branchName)
        return apiCall { api.receipt(sale.id).data }.toDomain(branchName)
    }

    override suspend fun emailReceipt(receipt: ConfirmedReceipt, email: String) {
        require(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())) { "Enter a valid email address." }
        if (!isDemo()) apiCall { api.emailReceipt(receipt.saleId, EmailReceiptRequest(email.trim())) }
    }

    override suspend fun returnableItems(sale: SaleSummary): List<ReturnableItem> {
        if (isDemo()) return demoReturnableItems(sale)
        return apiCall { api.returnableItems(sale.id).data }.map { dto ->
            ReturnableItem(
                saleItemId = dto.id,
                variantId = dto.variantId,
                productName = dto.productName,
                variant = dto.variantAttributes.orEmpty().values.joinToString(" · ").ifBlank { "Standard" },
                sku = dto.sku,
                unitPrice = dto.unitPrice.roundToLong(),
                taxAmount = dto.taxAmount?.roundToLong() ?: 0,
                originalQuantity = dto.quantity,
                maxReturnable = dto.maxReturnable,
            )
        }
    }

    override suspend fun createReturn(request: ReturnRequest): AftercareResult {
        if (isDemo()) return AftercareResult("Return completed", "RET-DEMO-001", "Inventory and sale totals were updated.", request.lines.sumOf { it.estimatedValue })
        val response = mutationCall {
            api.createReturn(
                CreateReturnRequest(
                    request.saleId,
                    request.type,
                    request.reason,
                    request.notes.takeIf(String::isNotBlank),
                    request.refundMethod,
                    request.lines.map { ReturnItemRequest(it.item.saleItemId, it.item.variantId, it.quantity, it.condition) },
                ),
            )
        }
        return AftercareResult(
            title = if (request.type == "store_credit") "Store credit issued" else "Refund completed",
            reference = response.returnNumber ?: "Return recorded",
            message = response.message ?: "Inventory and sale totals were updated.",
            amount = response.totalRefund?.roundToLong() ?: request.lines.sumOf { it.estimatedValue },
        )
    }

    override suspend fun previewExchange(request: ExchangeRequest): ExchangePreview {
        if (isDemo()) {
            val returned = request.returnedLines.sumOf { it.estimatedValue }
            val replacement = request.newLines.sumOf { it.value }
            return ExchangePreview(returned, replacement, replacement - returned)
        }
        val response = apiCall { api.previewExchange(request.toPreviewRequest()).data }
        return ExchangePreview(response.returnedValue.roundToLong(), response.newItemsValue.roundToLong(), response.netAmount.roundToLong())
    }

    override suspend fun createExchange(request: ExchangeRequest): AftercareResult {
        if (isDemo()) {
            val net = request.newLines.sumOf { it.value } - request.returnedLines.sumOf { it.estimatedValue }
            return AftercareResult("Exchange completed", "EX-DEMO-001", "Returned and replacement stock were updated.", net)
        }
        val response = mutationCall {
            api.createExchange(
                CreateExchangeRequest(
                    request.saleId,
                    request.mode,
                    request.reason,
                    request.notes.takeIf(String::isNotBlank),
                    request.returnedLines.toApi(),
                    request.newLines.map { ExchangeNewItemRequest(it.product.id, it.quantity) },
                    request.settlementMethod,
                ),
            )
        }
        return AftercareResult(
            "Exchange completed",
            response.newReceiptNumber ?: response.returnNumber ?: "Exchange recorded",
            response.message ?: "Returned and replacement stock were updated.",
            response.netAmount?.roundToLong() ?: 0,
        )
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        val message = error.response()?.errorBody()?.string()?.let { body ->
            runCatching { gson.fromJson(body, ApiError::class.java).message }.getOrNull()
        } ?: "The sales request was rejected."
        throw IllegalStateException(message, error)
    } catch (error: IOException) {
        throw IllegalStateException("Sales activity is unavailable while offline.", error)
    }

    private suspend fun <T> mutationCall(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        val message = error.response()?.errorBody()?.string()?.let { body ->
            runCatching { gson.fromJson(body, ApiError::class.java).message }.getOrNull()
        } ?: "The return or exchange was rejected."
        throw IllegalStateException(message, error)
    } catch (_: IOException) {
        throw AftercareUncertainException()
    }
}

private fun List<com.kline.inventorypos.core.model.ReturnLine>.toApi() = map {
    ReturnItemRequest(it.item.saleItemId, it.item.variantId, it.quantity, it.condition)
}

private fun ExchangeRequest.toPreviewRequest() = ExchangePreviewRequest(
    saleId = saleId,
    returnedItems = returnedLines.toApi(),
    newItems = newLines.map { ExchangeNewItemRequest(it.product.id, it.quantity) },
    exchangeMode = mode,
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
    payments = payments.map { PaymentLeg(it.method, it.amount.roundToLong(), it.reference) },
)

private fun demoSales(): List<SaleSummary> = listOf(
    SaleSummary("demo-sale-10481", "KLM-10481", null, 285_000, "cash", "completed", Instant.now().minusSeconds(1_800).toString(), "Philip Kiwanuka", null, 2, "none", 0),
    SaleSummary("demo-sale-10480", "KLM-10480", "Amina Nakato", 620_000, "card", "completed", Instant.now().minusSeconds(4_200).toString(), "Sarah Namusoke", "Grace Akello", 4, "none", 0),
    SaleSummary("demo-sale-10477", "KLM-10477", "David Okello", 180_000, "cash", "partially_refunded", Instant.now().minusSeconds(7_400).toString(), "Philip Kiwanuka", null, 2, "partial", 95_000),
    SaleSummary("demo-sale-10479", "KLM-10479", null, 455_000, "mobile_money", "completed", Instant.now().minusSeconds(6_000).toString(), "Philip Kiwanuka", "John Sserwanga", 3, "none", 0),
)

private fun demoReceipt(sale: SaleSummary, branchName: String): ConfirmedReceipt {
    val products = SampleProducts.take(sale.itemCount.coerceIn(1, 4))
    val lines = products.map { ReceiptLine(it.name, it.variant, it.sku, it.price, 1, it.price) }
    return ConfirmedReceipt(
        saleId = sale.id,
        receiptNumber = sale.receiptNumber,
        saleDate = sale.saleDate,
        branchName = branchName,
        cashierName = sale.cashierName,
        customerName = sale.customerName,
        lines = lines,
        subtotal = sale.total,
        discountAmount = 0,
        taxAmount = 0,
        total = sale.total,
        amountPaid = sale.total,
        change = 0,
        payments = listOf(PaymentLeg(sale.paymentMethod, sale.total)),
    )
}

private fun demoReturnableItems(sale: SaleSummary): List<ReturnableItem> = SampleProducts
    .take(sale.itemCount.coerceIn(1, 4))
    .mapIndexed { index, product ->
        ReturnableItem("demo-item-${sale.id}-$index", product.id, product.name, product.variant, product.sku, product.price, 0, 1, 1)
    }
