package com.kline.inventorypos.data.operations

import com.google.gson.Gson
import com.kline.inventorypos.core.model.Expense
import com.kline.inventorypos.core.model.ExpenseCategory
import com.kline.inventorypos.core.model.ExpenseWorkspace
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.ExpenseDto
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.SaveExpenseRequest
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToLong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

interface ExpenseRepository {
    suspend fun workspace(from: String, to: String, categoryId: String?): ExpenseWorkspace
    suspend fun save(id: String?, date: String, categoryId: String, amount: Long, payee: String, paymentMethod: String, reference: String, notes: String): Expense
    suspend fun delete(id: String)
}

class ExpenseMutationUncertainException : IllegalStateException(
    "The connection ended before confirmation. Refresh the expense ledger before attempting another change.",
)

class DefaultExpenseRepository(
    private val api: InventoryPosApi,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
) : ExpenseRepository {
    private val demoCategories = listOf(
        ExpenseCategory("rent", "Rent & premises", "Rent, utilities and premises costs"),
        ExpenseCategory("transport", "Transport", "Delivery and local travel"),
        ExpenseCategory("supplies", "Office supplies", "Consumables and stationery"),
        ExpenseCategory("marketing", "Marketing", "Promotions and advertising"),
    )
    private val demoExpenses = mutableListOf(
        Expense("expense-1", LocalDate.now().minusDays(2).toString(), "transport", "Transport", 45_000, "City Rider", "cash", "PET-104", "Delivery to Acacia", "Philip Kiwanuka", Instant.now().minusSeconds(172_800).toString()),
        Expense("expense-2", LocalDate.now().minusDays(5).toString(), "supplies", "Office supplies", 128_000, "Paper House", "mobile_money", "MM-4821", "Receipt rolls", "Sarah Namusoke", Instant.now().minusSeconds(432_000).toString()),
    )

    override suspend fun workspace(from: String, to: String, categoryId: String?): ExpenseWorkspace {
        if (isDemo()) return ExpenseWorkspace(
            demoCategories,
            demoExpenses.filter { it.date in from..to && (categoryId == null || it.categoryId == categoryId) }.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt }),
        )
        return coroutineScope {
            val categories = async { read { api.expenseCategories().data }.filter { it.isActive }.sortedWith(compareBy({ it.sortOrder }, { it.name })).map { ExpenseCategory(it.id, it.name, it.description) } }
            val expenses = async { read { api.expenses(from, to, categoryId).data }.map { it.toDomain() } }
            ExpenseWorkspace(categories.await(), expenses.await())
        }
    }

    override suspend fun save(id: String?, date: String, categoryId: String, amount: Long, payee: String, paymentMethod: String, reference: String, notes: String): Expense {
        require(runCatching { LocalDate.parse(date) }.isSuccess) { "Select a valid expense date." }
        require(categoryId.isNotBlank()) { "Select an expense category." }
        require(amount > 0) { "Amount must be greater than zero." }
        require(paymentMethod in setOf("cash", "mobile_money", "bank", "credit")) { "Select a valid payment method." }
        val request = SaveExpenseRequest(date, categoryId, amount, payee.clean(), paymentMethod, reference.clean(), notes.clean())
        if (isDemo()) {
            val existing = id?.let { value -> demoExpenses.firstOrNull { it.id == value } }
            val categoryName = demoCategories.firstOrNull { it.id == categoryId }?.name ?: "Uncategorised"
            val saved = Expense(existing?.id ?: "expense-${UUID.randomUUID()}", date, categoryId, categoryName, amount, request.payee, paymentMethod, request.reference, request.notes, existing?.recordedBy ?: "Philip Kiwanuka", existing?.createdAt ?: Instant.now().toString())
            if (existing == null) demoExpenses.add(saved) else demoExpenses[demoExpenses.indexOf(existing)] = saved
            return saved
        }
        val dto = mutation { if (id == null) api.createExpense(request).data else api.updateExpense(id, request).data }
        return dto.toDomain()
    }

    override suspend fun delete(id: String) {
        if (isDemo()) { demoExpenses.removeAll { it.id == id }; return }
        mutation { api.deleteExpense(id) }
    }

    private suspend fun <T> read(block: suspend () -> T): T = try { block() }
    catch (error: HttpException) { throw error.asFailure("Expense records are unavailable.") }
    catch (error: IOException) { throw IllegalStateException("Expense records are unavailable while offline.", error) }

    private suspend fun <T> mutation(block: suspend () -> T): T = try { block() }
    catch (error: HttpException) { throw error.asFailure("The expense change was rejected.") }
    catch (_: IOException) { throw ExpenseMutationUncertainException() }

    private fun HttpException.asFailure(fallback: String): IllegalStateException {
        val detail = response()?.errorBody()?.string()?.let { body -> runCatching { gson.fromJson(body, ApiError::class.java).message }.getOrNull() }
        return IllegalStateException(detail?.takeIf(String::isNotBlank) ?: fallback, this)
    }
}

private fun ExpenseDto.toDomain() = Expense(id, expenseDate.take(10), categoryId, categoryName ?: "Uncategorised", amount.roundToLong(), payee, paymentMethod, reference, notes, recordedByName, createdAt)
private fun String.clean() = trim().takeIf(String::isNotBlank)
