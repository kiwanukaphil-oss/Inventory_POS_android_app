package com.kline.inventorypos.data.customer

import com.google.gson.Gson
import com.kline.inventorypos.core.model.CustomerAccount
import com.kline.inventorypos.core.model.CustomerAging
import com.kline.inventorypos.core.model.CustomerContact
import com.kline.inventorypos.core.model.CustomerLedgerEntry
import com.kline.inventorypos.core.model.CustomerNote
import com.kline.inventorypos.core.model.CustomerPurchase
import com.kline.inventorypos.core.model.CustomerWorkspace
import com.kline.inventorypos.core.model.LoyaltyEntry
import com.kline.inventorypos.core.model.StoreCreditSummary
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.CreateCustomerNoteRequest
import com.kline.inventorypos.data.network.CustomerDto
import com.kline.inventorypos.data.network.InventoryPosApi
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException
import kotlin.math.roundToLong

interface CustomerRepository {
    suspend fun customers(query: String, view: String): List<CustomerAccount>
    suspend fun workspace(customerId: String): CustomerWorkspace
    suspend fun addNote(customerId: String, body: String, pinned: Boolean): CustomerNote
}

class CustomerNoteUncertainException : IllegalStateException(
    "The connection ended before the note was confirmed. Refresh notes before trying again.",
)

class DefaultCustomerRepository(
    private val api: InventoryPosApi,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
) : CustomerRepository {
    override suspend fun customers(query: String, view: String): List<CustomerAccount> {
        if (isDemo()) return DemoCustomers.filter { customer ->
            (view == "all" || view == "with_credit" && customer.creditBalance > 0 || view == "with_prepaid" && customer.prepaidBalance > 0) &&
                (query.isBlank() || listOfNotNull(customer.name, customer.phone, customer.email).any { it.contains(query, true) })
        }
        return apiCall { api.customers(query.trim().takeIf(String::isNotBlank), view).data }.map(CustomerDto::toDomain)
    }

    override suspend fun workspace(customerId: String): CustomerWorkspace {
        if (isDemo()) return demoWorkspace(customerId)
        return coroutineScope {
            val customer = async { apiCall { api.customer(customerId).data }.toDomain() }
            val purchases = async { apiCall { api.customerPurchases(customerId).data.data }.map { dto ->
                CustomerPurchase(dto.id, dto.receiptNumber, dto.totalAmount.roundToLong(), dto.totalReturned?.roundToLong() ?: 0, dto.netAmount?.roundToLong() ?: dto.totalAmount.roundToLong(), dto.paymentMethod, dto.saleDate, dto.status, dto.itemCount ?: 0, dto.productNames.orEmpty())
            } }
            val aging = async { apiCall { api.customerAging(customerId).data }.let { dto -> CustomerAging(dto.currentBucket.money(), dto.bucket0To30.money(), dto.bucket31To60.money(), dto.bucket61Plus.money()) } }
            val ledger = async { apiCall { api.customerLedger(customerId).data }.map { dto -> CustomerLedgerEntry(dto.id, dto.account, dto.event, dto.signedAmount.money(), dto.runningBalance.money(), dto.createdAt, dto.receiptNumber, dto.notes) } }
            val notes = async { apiCall { api.customerNotes(customerId).data }.map { it.toDomain() } }
            val contacts = async { apiCall { api.customerContacts(customerId).data }.map { dto -> CustomerContact(dto.id, dto.name, dto.title, dto.phone, dto.email, dto.isPrimary == true) } }
            val loyalty = async { apiCall { api.customerLoyalty(customerId).data }.map { dto -> LoyaltyEntry(dto.id, dto.type, dto.points, dto.balanceAfter, dto.description, dto.createdAt) } }
            val storeCredit = async { apiCall { api.customerStoreCredit(customerId).data }.let { dto -> StoreCreditSummary(dto.activeBalance.money(), dto.activeCreditCount ?: 0, dto.nextExpiryDate) } }
            CustomerWorkspace(customer.await(), purchases.await(), aging.await(), ledger.await(), notes.await(), contacts.await(), loyalty.await(), storeCredit.await())
        }
    }

    override suspend fun addNote(customerId: String, body: String, pinned: Boolean): CustomerNote {
        require(body.trim().isNotBlank()) { "Enter a note before saving." }
        if (isDemo()) return CustomerNote("demo-note-${System.currentTimeMillis()}", body.trim(), pinned, "Demo user", Instant.now().toString())
        return try {
            api.createCustomerNote(customerId, CreateCustomerNoteRequest(body.trim(), pinned)).data.toDomain()
        } catch (error: HttpException) {
            throw IllegalStateException(error.apiMessage("The note was rejected."), error)
        } catch (_: IOException) {
            throw CustomerNoteUncertainException()
        }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        throw IllegalStateException(error.apiMessage("Customer information is unavailable."), error)
    } catch (error: IOException) {
        throw IllegalStateException("Customer information is unavailable while offline.", error)
    }

    private fun HttpException.apiMessage(fallback: String): String = response()?.errorBody()?.string()?.let { body ->
        runCatching { gson.fromJson(body, ApiError::class.java).message }.getOrNull()
    } ?: fallback
}

private fun Double?.money(): Long = this?.roundToLong() ?: 0

private fun CustomerDto.toDomain() = CustomerAccount(
    id = id,
    name = companyName?.takeIf(String::isNotBlank) ?: listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { "Unnamed customer" },
    type = customerType ?: if (companyName.isNullOrBlank()) "individual" else "business",
    phone = phone,
    email = email,
    city = city,
    totalPurchases = totalPurchases ?: 0,
    totalSpent = totalSpent.money(),
    creditBalance = creditBalance.money(),
    creditLimit = creditLimit?.roundToLong(),
    prepaidBalance = prepaidBalance.money(),
    loyaltyPoints = loyaltyPoints?.toInt() ?: 0,
    lastPurchaseDate = lastPurchaseDate,
    tier = tier?.name,
    segment = segment?.label ?: segment?.segment,
    tags = tags.orEmpty(),
)

private fun com.kline.inventorypos.data.network.CustomerNoteDto.toDomain() =
    CustomerNote(id, body, pinned == true, createdByName, createdAt)

private val DemoCustomers = listOf(
    CustomerAccount("c1", "Amina Nakato", "individual", "+256 700 555 014", "amina@example.com", "Kampala", 14, 3_420_000, 180_000, 500_000, 240_000, 1_240, Instant.now().minusSeconds(86_400 * 5).toString(), "Gold", "VIP", listOf("Tailoring")),
    CustomerAccount("c2", "David Okello", "individual", "+256 772 555 209", null, "Entebbe", 6, 1_180_000, 0, 300_000, 90_000, 420, Instant.now().minusSeconds(86_400 * 18).toString(), "Silver", "Regular", emptyList()),
    CustomerAccount("c3", "Nile Corporate Wear", "business", "+256 312 555 880", "buying@nile.example", "Kampala", 22, 18_600_000, 2_450_000, 5_000_000, 0, 0, Instant.now().minusSeconds(86_400 * 3).toString(), null, "VIP", listOf("Corporate", "Invoice")),
)

private fun demoWorkspace(customerId: String): CustomerWorkspace {
    val customer = DemoCustomers.firstOrNull { it.id == customerId } ?: DemoCustomers.first()
    return CustomerWorkspace(
        customer = customer,
        purchases = listOf(
            CustomerPurchase("s1", "KLM-10480", 620_000, 0, 620_000, "visa", Instant.now().minusSeconds(86_400 * 5).toString(), "completed", 4, listOf("Premium Linen Shirt", "Tailored Chino Trouser")),
            CustomerPurchase("s2", "KLM-10392", 455_000, 95_000, 360_000, "cash", Instant.now().minusSeconds(86_400 * 32).toString(), "completed", 3, listOf("Classic Piqué Polo")),
        ),
        aging = CustomerAging(customer.creditBalance / 2, customer.creditBalance / 3, customer.creditBalance / 6, 0),
        ledger = listOf(
            CustomerLedgerEntry("l1", "credit", "Credit sale", 180_000, customer.creditBalance, Instant.now().minusSeconds(86_400 * 5).toString(), "KLM-10480", null),
            CustomerLedgerEntry("l2", "prepaid", "Deposit", 200_000, customer.prepaidBalance, Instant.now().minusSeconds(86_400 * 12).toString(), null, "Mobile money deposit"),
        ),
        notes = listOf(CustomerNote("n1", "Prefers SMS before tailoring collection.", true, "Sarah Namusoke", Instant.now().minusSeconds(86_400 * 20).toString())),
        contacts = if (customer.type == "business") listOf(CustomerContact("ct1", "Jane Atim", "Procurement", "+256 700 555 991", "jane@nile.example", true)) else emptyList(),
        loyalty = listOf(LoyaltyEntry("p1", "earn", 120, customer.loyaltyPoints, "KLM-10480 purchase", Instant.now().minusSeconds(86_400 * 5).toString())),
        storeCredit = StoreCreditSummary(95_000, 1, Instant.now().plusSeconds(86_400 * 90).toString()),
    )
}
