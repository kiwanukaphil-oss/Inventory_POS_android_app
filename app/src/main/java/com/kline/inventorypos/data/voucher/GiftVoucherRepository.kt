package com.kline.inventorypos.data.voucher

import com.google.gson.Gson
import com.kline.inventorypos.core.model.CreateGiftVoucher
import com.kline.inventorypos.core.model.GiftVoucher
import com.kline.inventorypos.core.model.GiftVoucherTemplate
import com.kline.inventorypos.core.model.GiftVoucherTransaction
import com.kline.inventorypos.data.network.ActivateGiftVoucherRequest
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.CancelGiftVoucherRequest
import com.kline.inventorypos.data.network.CreateGiftVoucherRequest
import com.kline.inventorypos.data.network.GiftVoucherDto
import com.kline.inventorypos.data.network.GiftVoucherPaymentRequest
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.RedeemGiftVoucherRequest
import com.kline.inventorypos.data.network.ValidateGiftVoucherRequest
import java.io.IOException
import java.time.Instant
import retrofit2.HttpException
import kotlin.math.roundToLong

interface GiftVoucherRepository {
    suspend fun vouchers(query: String, status: String): List<GiftVoucher>
    suspend fun templates(): List<GiftVoucherTemplate>
    suspend fun detail(id: String): GiftVoucher
    suspend fun create(request: CreateGiftVoucher): GiftVoucher
    suspend fun activate(id: String, method: String, reference: String?): GiftVoucher
    suspend fun validate(codeOrToken: String): GiftVoucher
    suspend fun redeem(id: String, amount: Long, notes: String): GiftVoucher
    suspend fun cancel(id: String, reason: String, refund: Boolean): GiftVoucher
}

class VoucherMutationUncertainException : IllegalStateException(
    "The connection ended before confirmation. Do not retry until you refresh and verify this voucher.",
)

class DefaultGiftVoucherRepository(
    private val api: InventoryPosApi,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
) : GiftVoucherRepository {
    override suspend fun vouchers(query: String, status: String): List<GiftVoucher> {
        if (isDemo()) return DemoVouchers.filter { voucher ->
            (status == "all" || voucher.status == status) &&
                (query.isBlank() || voucher.code.contains(query, true) || voucher.recipientName.contains(query, true))
        }
        return apiCall { api.giftVouchers(query.trim().takeIf(String::isNotBlank), status.takeUnless { it == "all" }).data }.map { it.toDomain() }
    }

    override suspend fun templates(): List<GiftVoucherTemplate> {
        if (isDemo()) return DemoTemplates
        return apiCall { api.giftVoucherTemplates().data }.filter { it.isActive != false }.map { GiftVoucherTemplate(it.id, it.name, it.description, it.category) }
    }

    override suspend fun detail(id: String): GiftVoucher {
        if (isDemo()) return DemoVouchers.firstOrNull { it.id == id } ?: DemoVouchers.first()
        return apiCall { api.giftVoucher(id).data }.toDomain()
    }

    override suspend fun create(request: CreateGiftVoucher): GiftVoucher {
        require(request.recipientName.isNotBlank()) { "Recipient name is required." }
        require(request.templateId.isNotBlank()) { "Select a voucher design." }
        require(request.amount > 0) { "Voucher amount must be greater than zero." }
        if (isDemo()) return GiftVoucher("gv-demo-new", "GV-DEMO-0104", DemoTemplates.firstOrNull { it.id == request.templateId }?.name, request.recipientName.trim(), request.fromName.trim().takeIf(String::isNotBlank), request.message.trim().takeIf(String::isNotBlank), request.phone.trim().takeIf(String::isNotBlank), request.amount, request.amount, null, request.expiryDate.takeIf(String::isNotBlank), "pending_payment", "unpaid", "Demo user", Instant.now().toString(), null)
        return mutationCall {
            api.createGiftVoucher(
                CreateGiftVoucherRequest(request.templateId, request.recipientName.trim(), request.fromName.trim().takeIf(String::isNotBlank), request.message.trim().takeIf(String::isNotBlank), request.phone.trim().takeIf(String::isNotBlank), request.amount, request.expiryDate.takeIf(String::isNotBlank), request.notes.trim().takeIf(String::isNotBlank)),
            ).data
        }.toDomain()
    }

    override suspend fun activate(id: String, method: String, reference: String?): GiftVoucher {
        val current = detail(id)
        if (isDemo()) return current.copy(status = "active", paymentStatus = "paid", issueDate = Instant.now().toString())
        return mutationCall { api.activateGiftVoucher(id, ActivateGiftVoucherRequest(listOf(GiftVoucherPaymentRequest(method, current.originalAmount, reference?.trim()?.takeIf(String::isNotBlank))))).data }.toDomain()
    }

    override suspend fun validate(codeOrToken: String): GiftVoucher {
        require(codeOrToken.trim().isNotBlank()) { "Enter or scan a voucher code." }
        if (isDemo()) return DemoVouchers.firstOrNull { it.code.equals(codeOrToken.trim(), true) } ?: DemoVouchers.first()
        return apiCall { api.validateGiftVoucher(ValidateGiftVoucherRequest(code = codeOrToken.trim())).data }.toDomain()
    }

    override suspend fun redeem(id: String, amount: Long, notes: String): GiftVoucher {
        require(amount > 0) { "Redemption amount must be greater than zero." }
        if (isDemo()) {
            val voucher = detail(id)
            require(amount <= voucher.remainingBalance) { "Redemption exceeds the available balance." }
            val next = voucher.remainingBalance - amount
            return voucher.copy(remainingBalance = next, status = if (next == 0L) "fully_redeemed" else "partially_redeemed")
        }
        return mutationCall { api.redeemGiftVoucher(id, RedeemGiftVoucherRequest(amount, notes = notes.trim().takeIf(String::isNotBlank))).data }.toDomain()
    }

    override suspend fun cancel(id: String, reason: String, refund: Boolean): GiftVoucher {
        require(reason.trim().isNotBlank()) { "Cancellation reason is required." }
        if (isDemo()) return detail(id).copy(status = "cancelled", paymentStatus = if (refund) "refunded" else "paid", remainingBalance = if (refund) 0 else detail(id).remainingBalance, cancelReason = reason.trim())
        return mutationCall { api.cancelGiftVoucher(id, CancelGiftVoucherRequest(reason.trim(), refund)).data }.toDomain()
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        throw IllegalStateException(error.apiMessage("Gift voucher request was rejected."), error)
    } catch (error: IOException) {
        throw IllegalStateException("Gift vouchers are unavailable while offline.", error)
    }

    private suspend fun <T> mutationCall(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        throw IllegalStateException(error.apiMessage("Gift voucher action was rejected."), error)
    } catch (_: IOException) {
        throw VoucherMutationUncertainException()
    }

    private fun HttpException.apiMessage(fallback: String): String = response()?.errorBody()?.string()?.let { body ->
        runCatching { gson.fromJson(body, ApiError::class.java).message }.getOrNull()
    } ?: fallback
}

private fun GiftVoucherDto.toDomain() = GiftVoucher(
    id, voucherCode, templateName, recipientName, fromName, message, phoneNumber,
    originalAmount.roundToLong(), remainingBalance.roundToLong(), issueDate, expiryDate,
    status, paymentStatus, createdByName, createdAt, cancelReason,
    transactions.orEmpty().map { GiftVoucherTransaction(it.id, it.transactionType, it.amount.roundToLong(), it.balanceAfter.roundToLong(), it.paymentMethod, it.reference, it.notes, it.createdByName, it.createdAt) },
)

private val DemoTemplates = listOf(
    GiftVoucherTemplate("t1", "Signature Black", "Classic premium voucher", "Signature"),
    GiftVoucherTemplate("t2", "Scarlet Ribbon", "Celebration gift design", "Celebration"),
    GiftVoucherTemplate("t3", "Tailored Navy", "Refined corporate design", "Corporate"),
)

private val DemoVouchers = listOf(
    GiftVoucher("gv1", "GV-01031", "Signature Black", "Amina Nakato", "David", "Enjoy something special.", "+256700555014", 500_000, 320_000, Instant.now().minusSeconds(86_400 * 30).toString(), Instant.now().plusSeconds(86_400 * 150).toString(), "partially_redeemed", "paid", "Philip Kiwanuka", Instant.now().minusSeconds(86_400 * 30).toString(), null, listOf(GiftVoucherTransaction("tx1", "redeem", 180_000, 320_000, "gift_voucher", "KLM-10402", null, "Sarah Namusoke", Instant.now().minusSeconds(86_400 * 5).toString()))),
    GiftVoucher("gv2", "GV-01032", "Scarlet Ribbon", "Grace Akello", "Team K-Line", "Happy birthday!", null, 250_000, 250_000, Instant.now().minusSeconds(86_400 * 4).toString(), Instant.now().plusSeconds(86_400 * 360).toString(), "active", "paid", "Sarah Namusoke", Instant.now().minusSeconds(86_400 * 4).toString(), null),
    GiftVoucher("gv3", "GV-01033", "Tailored Navy", "John Sserwanga", null, null, null, 1_000_000, 1_000_000, null, null, "pending_payment", "unpaid", "Philip Kiwanuka", Instant.now().minusSeconds(3_600).toString(), null),
)
