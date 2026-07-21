package com.kline.inventorypos.core.model

data class Reconciliation(
    val id: String,
    val date: String,
    val status: String,
    val createdBy: String?,
    val closedBy: String?,
    val closedAt: String?,
    val notes: String?,
    val channels: List<ReconciliationChannel>,
    val signoffs: List<ReconciliationSignoff>,
    val pendingStaff: List<PendingStaffSignoff>,
) {
    val systemTotal: Long get() = channels.sumOf { it.systemTotal }
    val externalTotal: Long get() = channels.sumOf { it.externalTotal ?: 0 }
    val variance: Long get() = channels.sumOf { it.variance }
    val allChannelsVerified: Boolean get() = channels.all { it.externalTotal != null }
}

data class ReconciliationChannel(
    val id: String,
    val method: String,
    val systemTotal: Long,
    val externalTotal: Long?,
    val charges: Long,
    val variance: Long,
    val discrepancyNote: String?,
    val verifiedBy: String?,
    val verifiedAt: String?,
)

data class ReconciliationSignoff(
    val id: String,
    val userId: String,
    val staffName: String,
    val confirmedTotal: Long,
    val systemTotal: Long,
    val signedAt: String,
    val notes: String?,
)

data class PendingStaffSignoff(
    val userId: String,
    val staffName: String,
    val transactionCount: Int,
    val salesTotal: Long,
)

data class DailySalesSummary(
    val date: String,
    val transactions: Int,
    val grossRevenue: Long,
    val returns: Long,
    val netRevenue: Long,
    val discounts: Long,
    val tax: Long,
    val averageSale: Long,
)

data class PaymentMethodSummary(val method: String, val transactions: Int, val total: Long, val percentage: Double)

data class EndOfDayWorkspace(
    val reconciliation: Reconciliation?,
    val sales: DailySalesSummary?,
    val payments: List<PaymentMethodSummary>,
)
