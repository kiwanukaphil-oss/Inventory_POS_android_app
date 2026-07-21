package com.kline.inventorypos.core.model

data class CashSession(
    val id: String,
    val openedByName: String?,
    val openingFloat: Long,
    val expectedClosing: Long?,
    val actualClosing: Long?,
    val variance: Long?,
    val varianceNote: String?,
    val status: String,
    val openedAt: String,
    val closedAt: String?,
)

data class CashMovement(
    val id: String,
    val type: String,
    val direction: String,
    val amount: Long,
    val category: String?,
    val reference: String?,
    val processedBy: String,
    val notes: String?,
    val date: String,
)

data class CashMovementTotal(
    val type: String,
    val direction: String,
    val count: Int,
    val total: Long,
)

data class CashBookSummary(
    val inflows: Long = 0,
    val outflows: Long = 0,
    val net: Long = 0,
    val count: Int = 0,
    val breakdown: List<CashMovementTotal> = emptyList(),
)

data class CashSessionSummary(
    val session: CashSession,
    val movements: List<CashMovementTotal>,
    val expected: Long,
)

data class StaffOption(val id: String, val name: String, val username: String)

data class CashMutationResult(val message: String, val pendingApproval: Boolean = false)

data class CashWorkspace(
    val activeSession: CashSession?,
    val movements: List<CashMovement>,
    val bookSummary: CashBookSummary?,
    val staff: List<StaffOption>,
)
