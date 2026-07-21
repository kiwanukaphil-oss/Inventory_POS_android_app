package com.kline.inventorypos.data.operations

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.kline.inventorypos.core.model.ApprovalDetail
import com.kline.inventorypos.core.model.ApprovalRequest
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.ApprovalDecisionRequest
import com.kline.inventorypos.data.network.ApprovalRequestDto
import com.kline.inventorypos.data.network.InventoryPosApi
import java.io.IOException
import java.time.Instant
import kotlin.math.roundToLong
import retrofit2.HttpException

interface ApprovalRepository {
    suspend fun pending(): List<ApprovalRequest>
    suspend fun decide(id: String, approve: Boolean, note: String)
}

class ApprovalMutationUncertainException : IllegalStateException(
    "The connection ended before confirmation. Do not repeat this decision until the request and its affected sale, cash, stock, or price record have been verified.",
)

class DefaultApprovalRepository(
    private val api: InventoryPosApi,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
) : ApprovalRepository {
    private val demoPending by lazy { demoApprovals().toMutableList() }

    override suspend fun pending(): List<ApprovalRequest> {
        if (isDemo()) return demoPending.toList()
        return read { api.pendingApprovals().data }.map { it.toDomain() }
    }

    override suspend fun decide(id: String, approve: Boolean, note: String) {
        if (!approve) require(note.trim().isNotBlank()) { "A rejection reason is required." }
        if (isDemo()) {
            val request = demoPending.firstOrNull { it.id == id } ?: error("Request not found or already resolved.")
            demoPending.remove(request)
            return
        }
        val body = ApprovalDecisionRequest(note.trim())
        mutation { if (approve) api.approveRequest(id, body) else api.rejectRequest(id, body) }
    }

    private suspend fun <T> read(block: suspend () -> T): T = try { block() }
    catch (error: HttpException) { throw error.asFailure("Approval requests are unavailable.") }
    catch (error: IOException) { throw IllegalStateException("Approval requests are unavailable while offline.", error) }

    private suspend fun <T> mutation(block: suspend () -> T): T = try { block() }
    catch (error: HttpException) { throw error.asFailure("The approval decision was rejected.") }
    catch (_: IOException) { throw ApprovalMutationUncertainException() }

    private fun HttpException.asFailure(fallback: String): IllegalStateException {
        val detail = response()?.errorBody()?.string()?.let { body -> runCatching { gson.fromJson(body, ApiError::class.java).message }.getOrNull() }
        return IllegalStateException(detail?.takeIf(String::isNotBlank) ?: fallback, this)
    }
}

private fun ApprovalRequestDto.toDomain(): ApprovalRequest {
    val data = requestData ?: JsonObject()
    val summary = data.objectOrNull("summary")
    val policy = data.objectOrNull("policy")
    val payload = data.objectOrNull("payload")
    val amount = firstNumber(summary, "discount_amount", "total_amount", "amount")
        ?: firstNumber(data, "discount_amount", "total_amount", "amount")
        ?: firstNumber(payload, "amount", "total_amount", "new_price")
    val threshold = firstNumber(policy, "threshold") ?: firstNumber(data, "threshold")
    val reason = firstText(data, "reason") ?: firstText(summary, "reason") ?: firstText(payload, "reason", "notes") ?: firstText(policy, "type")
    val reference = firstText(summary, "receipt_number", "reference_number", "sku") ?: firstText(payload, "reference_number", "sku", "barcode")
    return ApprovalRequest(id, requestType, requestType.typeLabel(), requestedBy, requestedByName?.takeIf(String::isNotBlank) ?: "Unknown staff", createdAt, amount, threshold, reason, reference, flatten(data))
}

private fun flatten(root: JsonObject): List<ApprovalDetail> {
    val result = mutableListOf<ApprovalDetail>()
    fun visit(prefix: String, element: JsonElement, depth: Int) {
        if (result.size >= 24) return
        when {
            element.isJsonNull -> Unit
            element.isJsonPrimitive -> {
                val key = prefix.substringAfterLast('.')
                val primitive = element.asJsonPrimitive
                val value = when {
                    primitive.isBoolean -> if (primitive.asBoolean) "Yes" else "No"
                    else -> primitive.asString
                }
                result += ApprovalDetail(prefix.replace('.', ' ').words(), value, key.isMoneyKey())
            }
            element.isJsonArray -> result += ApprovalDetail(prefix.replace('.', ' ').words(), "${element.asJsonArray.size()} item${if (element.asJsonArray.size() == 1) "" else "s"}")
            element.isJsonObject && depth < 2 -> element.asJsonObject.entrySet().forEach { (key, value) -> visit(if (prefix.isBlank()) key else "$prefix.$key", value, depth + 1) }
        }
    }
    root.entrySet().forEach { (key, value) -> visit(key, value, 0) }
    return result.distinctBy { it.label to it.value }
}

private fun JsonObject?.objectOrNull(key: String) = this?.get(key)?.takeIf { it.isJsonObject }?.asJsonObject
private fun firstNumber(source: JsonObject?, vararg keys: String): Long? = keys.firstNotNullOfOrNull { key -> source?.get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asDouble.roundToLong() }.getOrNull() } }
private fun firstText(source: JsonObject?, vararg keys: String): String? = keys.firstNotNullOfOrNull { key -> source?.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank) }
private fun String.isMoneyKey() = contains("amount") || contains("total") || contains("price") || contains("cost") || contains("spend") || contains("threshold")
private fun String.words() = replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
private fun String.typeLabel() = when (this) {
    "sale_discount_override" -> "Discount override"
    "cash_manual_movement" -> "Cash exception"
    "stock_adjustment" -> "Stock adjustment"
    "price_change" -> "Price change"
    "no_sale" -> "No-sale drawer open"
    else -> words()
}

private fun demoApprovals() = listOf(
    ApprovalRequest("approval-1", "cash_manual_movement", "Cash exception", "u2", "Sarah Namusoke", Instant.now().minusSeconds(900).toString(), 180_000, 50_000, "Emergency courier and packaging", "PC-204", listOf(ApprovalDetail("Movement", "Petty cash"), ApprovalDetail("Amount", "180000", true), ApprovalDetail("Direction", "Outflow"))),
    ApprovalRequest("approval-2", "stock_adjustment", "Stock adjustment", "u3", "Grace Akello", Instant.now().minusSeconds(2_400).toString(), 320_000, 100_000, "Damaged during delivery", "LINEN-BLU-M", listOf(ApprovalDetail("SKU", "LINEN-BLU-M"), ApprovalDetail("Quantity", "-4"), ApprovalDetail("Total impact", "320000", true))),
    ApprovalRequest("approval-3", "price_change", "Price change", "u2", "Sarah Namusoke", Instant.now().minusSeconds(4_500).toString(), 145_000, null, "Seasonal price update", "OXFORD-WHT-L", listOf(ApprovalDetail("Old price", "120000", true), ApprovalDetail("New price", "145000", true), ApprovalDetail("Change", "20.8%"))),
)
