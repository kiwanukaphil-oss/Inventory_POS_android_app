package com.kline.inventorypos.core.model

data class ExpenseCategory(val id: String, val name: String, val description: String?)

data class Expense(
    val id: String,
    val date: String,
    val categoryId: String,
    val categoryName: String,
    val amount: Long,
    val payee: String?,
    val paymentMethod: String,
    val reference: String?,
    val notes: String?,
    val recordedBy: String?,
    val createdAt: String,
)

data class ExpenseSummary(
    val total: Long,
    val count: Int,
    val average: Long,
    val topCategory: String?,
    val topCategoryTotal: Long,
)

data class ExpenseWorkspace(
    val categories: List<ExpenseCategory>,
    val expenses: List<Expense>,
) {
    val summary: ExpenseSummary
        get() {
            val total = expenses.sumOf { it.amount }
            val top = expenses.groupBy { it.categoryName }.mapValues { (_, rows) -> rows.sumOf { it.amount } }.maxByOrNull { it.value }
            return ExpenseSummary(total, expenses.size, if (expenses.isEmpty()) 0 else total / expenses.size, top?.key, top?.value ?: 0)
        }
}

data class ApprovalDetail(val label: String, val value: String, val money: Boolean = false)

data class ApprovalRequest(
    val id: String,
    val type: String,
    val typeLabel: String,
    val requestedById: String,
    val requestedByName: String,
    val createdAt: String,
    val amount: Long?,
    val threshold: Long?,
    val reason: String?,
    val reference: String?,
    val details: List<ApprovalDetail>,
)
