package com.kline.inventorypos.core.model

data class SalesPeriodReport(val transactions: Int, val grossRevenue: Long, val returns: Long, val netRevenue: Long, val discounts: Long, val tax: Long, val averageSale: Long, val uniqueCustomers: Int, val daily: List<SalesDay>)
data class SalesDay(val date: String, val transactions: Int, val revenue: Long)
data class FinancialReport(val revenue: Long, val cogs: Long, val grossProfit: Long, val grossMargin: Double?, val expenses: Long, val netProfit: Long, val netMargin: Double?, val expenseCategories: List<ReportCategory>, val cashInflows: Long, val cashOutflows: Long, val netCashFlow: Long)
data class ReportCategory(val name: String, val total: Long, val entries: Int)
data class ManagementReportWorkspace(val sales: SalesPeriodReport?, val financial: FinancialReport?)
