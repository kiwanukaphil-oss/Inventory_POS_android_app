package com.kline.inventorypos.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OperationsModelsTest {
    @Test
    fun expenseSummaryDerivesTotalsAverageAndTopCategory() {
        val workspace = ExpenseWorkspace(
            emptyList(),
            listOf(
                expense("1", "Transport", 40_000),
                expense("2", "Supplies", 100_000),
                expense("3", "Transport", 80_000),
            ),
        )

        assertEquals(220_000, workspace.summary.total)
        assertEquals(73_333, workspace.summary.average)
        assertEquals("Transport", workspace.summary.topCategory)
        assertEquals(120_000, workspace.summary.topCategoryTotal)
    }

    private fun expense(id: String, category: String, amount: Long) = Expense(
        id, "2026-07-21", category, category, amount, null, "cash", null, null, null, "now",
    )
}
