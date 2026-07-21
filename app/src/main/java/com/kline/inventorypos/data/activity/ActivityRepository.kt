package com.kline.inventorypos.data.activity

import com.google.gson.Gson
import com.kline.inventorypos.core.model.ConfirmedReceipt
import com.kline.inventorypos.core.model.PaymentLeg
import com.kline.inventorypos.core.model.ReceiptLine
import com.kline.inventorypos.core.model.SaleSummary
import com.kline.inventorypos.core.model.SampleProducts
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.EmailReceiptRequest
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.ReceiptDto
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import kotlin.math.roundToLong

interface ActivityRepository {
    suspend fun sales(query: String): List<SaleSummary>
    suspend fun receipt(sale: SaleSummary, branchName: String): ConfirmedReceipt
    suspend fun emailReceipt(receipt: ConfirmedReceipt, email: String)
}

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
}

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
