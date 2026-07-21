package com.kline.inventorypos.data.report

import com.google.gson.Gson
import com.kline.inventorypos.core.model.*
import com.kline.inventorypos.data.network.*
import java.io.IOException
import kotlin.math.roundToLong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

interface ManagementReportRepository { suspend fun load(from: String, to: String, salesPermission: Boolean, financialPermission: Boolean): ManagementReportWorkspace }
class DefaultManagementReportRepository(private val api: InventoryPosApi, private val gson: Gson, private val isDemo: () -> Boolean) : ManagementReportRepository {
    override suspend fun load(from: String, to: String, salesPermission: Boolean, financialPermission: Boolean): ManagementReportWorkspace {
        if (isDemo()) return demo(salesPermission, financialPermission)
        return coroutineScope {
            val sales = async { if (salesPermission) read { api.periodSales(from, to).data }.domain() else null }
            val income = async { if (financialPermission) read { api.incomeStatement(from, to).data } else null }
            val cash = async { if (financialPermission) read { api.cashFlow(from, to).data } else null }
            ManagementReportWorkspace(sales.await(), income.await()?.domain(cash.await()))
        }
    }
    private suspend fun <T> read(block:suspend()->T):T=try{block()}catch(e:HttpException){val m=e.response()?.errorBody()?.string()?.let{runCatching{gson.fromJson(it,ApiError::class.java).message}.getOrNull()};throw IllegalStateException(m?:"Management report is unavailable.",e)}catch(e:IOException){throw IllegalStateException("Management report is unavailable while offline.",e)}
}
private fun PeriodSalesDto.domain()=SalesPeriodReport(summary.totalTransactions,summary.grossRevenue.money(),summary.totalReturns.money(),summary.netRevenue.money(),summary.totalDiscounts.money(),summary.totalTax.money(),summary.averageSale.money(),summary.uniqueCustomers?:0,dailyBreakdown.orEmpty().map{SalesDay(it.saleDay.take(10),it.transactionCount,(it.dailyNetRevenue?:it.dailyGrossRevenue).money())})
private fun IncomeStatementDto.domain(cash:CashFlowDto?)=FinancialReport(revenue.netRevenue.money(),cogs.money(),grossProfit.money(),grossMarginPct,operatingExpenses.total.money(),netProfit.money(),netMarginPct,operatingExpenses.byCategory.orEmpty().map{ReportCategory(it.name,it.total.money(),it.entryCount)},cash?.inflows?.total.money(),cash?.outflows?.total.money(),cash?.netCashFlow.money())
private fun Double?.money()=this?.roundToLong()?:0
private fun demo(s:Boolean,f:Boolean)=ManagementReportWorkspace(if(s)SalesPeriodReport(126,24_850_000,620_000,24_230_000,1_120_000,4_032_000,192_301,87,listOf(SalesDay("2026-07-21",18,2_850_000),SalesDay("2026-07-20",21,3_420_000)))else null,if(f)FinancialReport(24_230_000,12_400_000,11_830_000,48.8,2_450_000,9_380_000,38.7,listOf(ReportCategory("Rent & premises",1_400_000,2),ReportCategory("Transport",650_000,8)),25_100_000,15_720_000,9_380_000)else null)
