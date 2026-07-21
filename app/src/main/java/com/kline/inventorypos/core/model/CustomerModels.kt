package com.kline.inventorypos.core.model

data class CustomerAccount(
    val id: String,
    val name: String,
    val type: String,
    val phone: String?,
    val email: String?,
    val city: String?,
    val totalPurchases: Int,
    val totalSpent: Long,
    val creditBalance: Long,
    val creditLimit: Long?,
    val prepaidBalance: Long,
    val loyaltyPoints: Int,
    val lastPurchaseDate: String?,
    val tier: String?,
    val segment: String?,
    val tags: List<String>,
)

data class CustomerPurchase(
    val id: String,
    val receiptNumber: String,
    val total: Long,
    val returned: Long,
    val net: Long,
    val paymentMethod: String,
    val date: String,
    val status: String,
    val itemCount: Int,
    val productNames: List<String>,
)

data class CustomerAging(
    val current: Long = 0,
    val days0To30: Long = 0,
    val days31To60: Long = 0,
    val days61Plus: Long = 0,
)

data class CustomerLedgerEntry(
    val id: String,
    val account: String,
    val event: String,
    val signedAmount: Long,
    val runningBalance: Long,
    val date: String,
    val receiptNumber: String?,
    val notes: String?,
)

data class CustomerNote(
    val id: String,
    val body: String,
    val pinned: Boolean,
    val author: String?,
    val date: String,
)

data class CustomerContact(
    val id: String,
    val name: String,
    val title: String?,
    val phone: String?,
    val email: String?,
    val primary: Boolean,
)

data class LoyaltyEntry(
    val id: String,
    val type: String,
    val points: Int,
    val balanceAfter: Int,
    val description: String?,
    val date: String,
)

data class StoreCreditSummary(
    val activeBalance: Long = 0,
    val activeCount: Int = 0,
    val nextExpiryDate: String? = null,
)

data class CustomerWorkspace(
    val customer: CustomerAccount,
    val purchases: List<CustomerPurchase>,
    val aging: CustomerAging,
    val ledger: List<CustomerLedgerEntry>,
    val notes: List<CustomerNote>,
    val contacts: List<CustomerContact>,
    val loyalty: List<LoyaltyEntry>,
    val storeCredit: StoreCreditSummary,
)
