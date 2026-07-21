package com.kline.inventorypos.data.cash

import com.google.gson.Gson
import com.kline.inventorypos.core.model.CashBookSummary
import com.kline.inventorypos.core.model.CashMovement
import com.kline.inventorypos.core.model.CashMovementTotal
import com.kline.inventorypos.core.model.CashMutationResult
import com.kline.inventorypos.core.model.CashSession
import com.kline.inventorypos.core.model.CashSessionSummary
import com.kline.inventorypos.core.model.CashWorkspace
import com.kline.inventorypos.core.model.StaffOption
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.CashBookSummaryDto
import com.kline.inventorypos.data.network.CashDrawerDto
import com.kline.inventorypos.data.network.CashMovementDto
import com.kline.inventorypos.data.network.CashMovementTotalDto
import com.kline.inventorypos.data.network.CashSessionSummaryDto
import com.kline.inventorypos.data.network.CloseCashDrawerRequest
import com.kline.inventorypos.data.network.HandoverCashDrawerRequest
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.RecordCashMovementRequest
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

interface CashRepository {
    suspend fun workspace(canOperateDrawer: Boolean, canViewBook: Boolean, canLoadStaff: Boolean, currentUserId: String, hasRegister: Boolean): CashWorkspace
    suspend fun close(sessionId: String, counted: Long, note: String): CashSessionSummary
    suspend fun handover(sessionId: String, counted: Long, incomingUserId: String, note: String): CashSessionSummary
    suspend fun recordMovement(type: String, direction: String, amount: Long, category: String, notes: String): CashMutationResult
}

class CashMutationUncertainException : IllegalStateException(
    "The connection ended before confirmation. Do not repeat this cash action until the drawer and cash book are refreshed.",
)

class DefaultCashRepository(
    private val api: InventoryPosApi,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
) : CashRepository {
    override suspend fun workspace(canOperateDrawer: Boolean, canViewBook: Boolean, canLoadStaff: Boolean, currentUserId: String, hasRegister: Boolean): CashWorkspace {
        if (isDemo()) return demoWorkspace(canOperateDrawer && hasRegister, canViewBook)
        val today = LocalDate.now().toString()
        return coroutineScope {
            val active = async { if (canOperateDrawer) apiCall { api.activeDrawer().data }?.toDomain() else null }
            val movements = async { if (canViewBook) apiCall { api.cashMovements(today, today).data.movements }.map { it.toDomain() } else emptyList() }
            val summary = async { if (canViewBook) apiCall { api.cashBookSummary(today, today).data }.toDomain() else null }
            val staff = async { if (canLoadStaff) apiCall { api.cashStaff().data }.filter { it.isActive != false && it.id != currentUserId }.map { StaffOption(it.id, it.fullName?.takeIf(String::isNotBlank) ?: it.username, it.username) } else emptyList() }
            CashWorkspace(active.await(), movements.await(), summary.await(), staff.await())
        }
    }

    override suspend fun close(sessionId: String, counted: Long, note: String): CashSessionSummary {
        require(counted >= 0) { "Enter the total cash counted." }
        if (isDemo()) {
            val expected = 640_000L
            val session = demoSession().copy(expectedClosing = expected, actualClosing = counted, variance = counted - expected, varianceNote = note.takeIf(String::isNotBlank), status = "closed", closedAt = Instant.now().toString())
            return CashSessionSummary(session, demoTotals(), expected)
        }
        val closed = mutationCall { api.closeDrawer(sessionId, CloseCashDrawerRequest(counted, varianceNote = note.trim().takeIf(String::isNotBlank))).data }
        return runCatching { apiCall { api.cashDrawerSummary(sessionId).data }.toDomain() }
            .getOrElse { CashSessionSummary(closed.toDomain(), emptyList(), closed.expectedClosing?.roundToLong() ?: 0) }
    }

    override suspend fun handover(sessionId: String, counted: Long, incomingUserId: String, note: String): CashSessionSummary {
        require(counted >= 0) { "Enter the total cash counted." }
        require(incomingUserId.isNotBlank()) { "Select the incoming cashier." }
        if (isDemo()) {
            val expected = 640_000L
            return CashSessionSummary(demoSession().copy(expectedClosing = expected, actualClosing = counted, variance = counted - expected, status = "closed", closedAt = Instant.now().toString()), demoTotals(), expected)
        }
        val result = mutationCall { api.handoverDrawer(HandoverCashDrawerRequest(sessionId, counted, incomingUserId, note.trim().takeIf(String::isNotBlank))).data }
        return runCatching { apiCall { api.cashDrawerSummary(sessionId).data }.toDomain() }
            .getOrElse { CashSessionSummary(result.closedSession.toDomain(), emptyList(), result.closedSession.expectedClosing?.roundToLong() ?: 0) }
    }

    override suspend fun recordMovement(type: String, direction: String, amount: Long, category: String, notes: String): CashMutationResult {
        require(type.isNotBlank()) { "Select a cash movement type." }
        require(amount > 0) { "Amount must be greater than zero." }
        require(notes.trim().isNotBlank()) { "Notes are required for a manual cash movement." }
        if (isDemo()) return CashMutationResult("Cash movement recorded")
        val response = mutationCall { api.recordCashMovement(RecordCashMovementRequest(type, direction, amount, notes.trim(), category.trim().takeIf(String::isNotBlank))) }
        return CashMutationResult(response.message ?: "Cash movement recorded", response.status == "pending_approval")
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = try { block() }
    catch (error: HttpException) { throw IllegalStateException(error.apiMessage("Cash information is unavailable."), error) }
    catch (error: IOException) { throw IllegalStateException("Cash information is unavailable while offline.", error) }

    private suspend fun <T> mutationCall(block: suspend () -> T): T = try { block() }
    catch (error: HttpException) { throw IllegalStateException(error.apiMessage("The cash action was rejected."), error) }
    catch (_: IOException) { throw CashMutationUncertainException() }

    private fun HttpException.apiMessage(fallback: String): String = response()?.errorBody()?.string()?.let { body -> runCatching { gson.fromJson(body, ApiError::class.java).message }.getOrNull() } ?: fallback
}

private fun CashDrawerDto.toDomain() = CashSession(id, openedByName, openingFloat.money(), expectedClosing?.roundToLong(), actualClosing?.roundToLong(), variance?.roundToLong(), varianceNote, status ?: "open", openedAt.orEmpty(), closedAt)
private fun CashMovementDto.toDomain() = CashMovement(id, transactionType, movementType, amount.roundToLong(), category, referenceNumber, processedByName ?: "Team member", notes, transactionDate)
private fun CashMovementTotalDto.toDomain() = CashMovementTotal(transactionType, movementType, count, total.roundToLong())
private fun CashBookSummaryDto?.toDomain() = this?.let { CashBookSummary(totalInflows.money(), totalOutflows.money(), netMovement.money(), movementCount ?: 0, breakdown.orEmpty().map { row -> row.toDomain() }) }
private fun CashSessionSummaryDto.toDomain() = CashSessionSummary(session.toDomain(), movements.map { it.toDomain() }, expected.roundToLong())
private fun Double?.money() = this?.roundToLong() ?: 0

private fun demoSession() = CashSession("demo-register", "Philip Kiwanuka", 200_000, null, null, null, null, "open", Instant.now().minusSeconds(14_400).toString(), null)
private fun demoTotals() = listOf(CashMovementTotal("sale", "inflow", 4, 455_000), CashMovementTotal("petty_cash", "outflow", 1, 15_000))
private fun demoWorkspace(canOperate: Boolean, canView: Boolean) = CashWorkspace(
    if (canOperate) demoSession() else null,
    if (canView) listOf(CashMovement("m1", "sale", "inflow", 285_000, null, "KLM-10481", "Philip Kiwanuka", null, Instant.now().minusSeconds(1_800).toString()), CashMovement("m2", "petty_cash", "outflow", 15_000, "Transport", "PC-018", "Sarah Namusoke", "Courier fare", Instant.now().minusSeconds(3_600).toString())) else emptyList(),
    if (canView) CashBookSummary(485_000, 45_000, 440_000, 6, demoTotals()) else null,
    listOf(StaffOption("u2", "Sarah Namusoke", "sarah"), StaffOption("u3", "Grace Akello", "grace")),
)
