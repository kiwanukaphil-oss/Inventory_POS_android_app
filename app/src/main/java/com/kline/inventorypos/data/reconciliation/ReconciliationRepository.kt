package com.kline.inventorypos.data.reconciliation

import com.google.gson.Gson
import com.kline.inventorypos.core.model.DailySalesSummary
import com.kline.inventorypos.core.model.EndOfDayWorkspace
import com.kline.inventorypos.core.model.PaymentMethodSummary
import com.kline.inventorypos.core.model.PendingStaffSignoff
import com.kline.inventorypos.core.model.Reconciliation
import com.kline.inventorypos.core.model.ReconciliationChannel
import com.kline.inventorypos.core.model.ReconciliationSignoff
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.DailySalesSummaryDto
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.PaymentMethodItemDto
import com.kline.inventorypos.data.network.ReconciliationDto
import com.kline.inventorypos.data.network.ReconciliationSignoffRequest
import com.kline.inventorypos.data.network.UpdateReconciliationChannelRequest
import java.io.IOException
import java.time.LocalDate
import kotlin.math.roundToLong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

interface ReconciliationRepository {
    suspend fun workspace(date: String, canViewReconciliation: Boolean, canViewReports: Boolean): EndOfDayWorkspace
    suspend fun open(date: String): Reconciliation
    suspend fun updateChannel(date: String, method: String, externalTotal: Long, charges: Long, note: String): Reconciliation
    suspend fun signOff(date: String, confirmedTotal: Long, notes: String): Reconciliation
    suspend fun close(date: String): Reconciliation
}

class ReconciliationMutationUncertainException : IllegalStateException(
    "The connection ended before confirmation. Refresh this date before attempting another end-of-day action.",
)

class DefaultReconciliationRepository(
    private val api: InventoryPosApi,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
) : ReconciliationRepository {
    private val demoRecords = mutableMapOf<String, Reconciliation>()

    override suspend fun workspace(date: String, canViewReconciliation: Boolean, canViewReports: Boolean): EndOfDayWorkspace {
        if (isDemo()) return EndOfDayWorkspace(
            reconciliation = demoRecord(date).takeIf { canViewReconciliation },
            sales = DailySalesSummary(date, 18, 2_945_000, 95_000, 2_850_000, 125_000, 486_500, 158_333).takeIf { canViewReports },
            payments = if (canViewReports) demoPayments() else emptyList(),
        )
        return coroutineScope {
            val reconciliation = async {
                if (!canViewReconciliation) null else try {
                    api.reconciliation(date).data.toDomain()
                } catch (error: HttpException) {
                    if (error.code() == 404) null else throw error.readError("Reconciliation is unavailable.")
                } catch (error: IOException) {
                    throw IllegalStateException("Reconciliation is unavailable while offline.", error)
                }
            }
            val sales = async { if (canViewReports) read { api.dailySalesSummary(date).data }.toDomain() else null }
            val payments = async {
                if (canViewReports) read { api.paymentMethodReport(date, date).data.paymentMethods.orEmpty() }.map { it.toDomain() }
                else emptyList()
            }
            EndOfDayWorkspace(reconciliation.await(), sales.await(), payments.await())
        }
    }

    override suspend fun open(date: String): Reconciliation {
        if (isDemo()) return demoRecord(date)
        return mutate { api.openReconciliation(date).data }.toDomain()
    }

    override suspend fun updateChannel(date: String, method: String, externalTotal: Long, charges: Long, note: String): Reconciliation {
        require(externalTotal >= 0) { "Enter the external total for this channel." }
        require(charges >= 0) { "Charges cannot be negative." }
        if (isDemo()) {
            val base = demoRecord(date)
            return base.copy(channels = base.channels.map { channel ->
                if (channel.method == method) channel.copy(
                    externalTotal = externalTotal,
                    charges = charges,
                    variance = externalTotal - charges - channel.systemTotal,
                    discrepancyNote = note.trim().takeIf(String::isNotBlank),
                    verifiedBy = "Philip Kiwanuka",
                    verifiedAt = "2026-07-21T18:00:00Z",
                ) else channel
            }).also { demoRecords[date] = it }
        }
        return mutate {
            api.updateReconciliationChannel(
                date,
                method,
                UpdateReconciliationChannelRequest(externalTotal, charges, note.trim().takeIf(String::isNotBlank)),
            ).data
        }.toDomain()
    }

    override suspend fun signOff(date: String, confirmedTotal: Long, notes: String): Reconciliation {
        require(confirmedTotal >= 0) { "Confirmed sales cannot be negative." }
        if (isDemo()) {
            val base = demoRecord(date)
            val mine = base.pendingStaff.firstOrNull() ?: return base
            return base.copy(
                status = "signed_off",
                pendingStaff = base.pendingStaff.filterNot { it.userId == mine.userId },
                signoffs = base.signoffs + ReconciliationSignoff("sign-demo", mine.userId, mine.staffName, confirmedTotal, mine.salesTotal, "2026-07-21T18:10:00Z", notes.takeIf(String::isNotBlank)),
            ).also { demoRecords[date] = it }
        }
        return mutate { api.signOffReconciliation(date, ReconciliationSignoffRequest(confirmedTotal, notes.trim().takeIf(String::isNotBlank))).data }.toDomain()
    }

    override suspend fun close(date: String): Reconciliation {
        if (isDemo()) return demoRecord(date).copy(status = "closed", closedBy = "Philip Kiwanuka", closedAt = "2026-07-21T18:15:00Z", pendingStaff = emptyList()).also { demoRecords[date] = it }
        return mutate { api.closeReconciliation(date).data }.toDomain()
    }

    private suspend fun <T> read(block: suspend () -> T): T = try { block() }
    catch (error: HttpException) { throw error.readError("Management report is unavailable.") }
    catch (error: IOException) { throw IllegalStateException("Management report is unavailable while offline.", error) }

    private suspend fun <T> mutate(block: suspend () -> T): T = try { block() }
    catch (error: HttpException) { throw error.readError("The end-of-day action was rejected.") }
    catch (_: IOException) { throw ReconciliationMutationUncertainException() }

    private fun HttpException.readError(fallback: String): IllegalStateException {
        val detail = response()?.errorBody()?.string()?.let { body -> runCatching { gson.fromJson(body, ApiError::class.java).message }.getOrNull() }
        return IllegalStateException(detail?.takeIf(String::isNotBlank) ?: fallback, this)
    }

    private fun demoRecord(date: String) = demoRecords.getOrPut(date) { demoReconciliation(date) }
}

private fun ReconciliationDto.toDomain() = Reconciliation(
    id, reconciliationDate, status, createdByName, closedByName, closedAt, notes,
    channels.orEmpty().map { channel -> ReconciliationChannel(channel.id, channel.paymentMethod, channel.systemTotal.money(), channel.externalTotal?.money(), channel.charges.money(), channel.variance.money(), channel.discrepancyNote, channel.verifiedByName, channel.verifiedAt) },
    signoffs.orEmpty().map { signoff -> ReconciliationSignoff(signoff.id, signoff.userId, signoff.staffName, signoff.confirmedSalesTotal.money(), signoff.systemSalesTotal.money(), signoff.signedAt, signoff.notes) },
    pendingStaff.orEmpty().map { staff -> PendingStaffSignoff(staff.userId, staff.staffName, staff.transactionCount, staff.salesTotal.money()) },
)

private fun DailySalesSummaryDto?.toDomain() = this?.let { DailySalesSummary(date, totalTransactions, (grossRevenue ?: totalRevenue).money(), totalReturns.money(), netRevenue.money(), totalDiscounts.money(), totalTax.money(), averageSale.money()) }
private fun PaymentMethodItemDto.toDomain() = PaymentMethodSummary(paymentMethod, transactionCount, totalAmount.money(), percentageOfTotal ?: 0.0)
private fun Double?.money() = this?.roundToLong() ?: 0L

private fun demoPayments() = listOf(PaymentMethodSummary("cash", 7, 1_120_000, 39.3), PaymentMethodSummary("mtn_mobile_money", 6, 980_000, 34.4), PaymentMethodSummary("visa", 5, 750_000, 26.3))

private fun demoReconciliation(date: String) = Reconciliation(
    "recon-$date", date, "counting", "Philip Kiwanuka", null, null, null,
    listOf(
        ReconciliationChannel("rc1", "cash", 1_120_000, 1_120_000, 0, 0, null, "Philip Kiwanuka", "2026-07-21T17:50:00Z"),
        ReconciliationChannel("rc2", "mtn_mobile_money", 980_000, null, 0, 0, null, null, null),
        ReconciliationChannel("rc3", "visa", 750_000, null, 0, 0, null, null, null),
    ),
    emptyList(),
    listOf(PendingStaffSignoff("demo-admin", "Philip Kiwanuka", 18, 2_850_000)),
)
