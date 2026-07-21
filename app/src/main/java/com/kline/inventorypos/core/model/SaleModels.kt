package com.kline.inventorypos.core.model

data class CustomerSummary(
    val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val loyaltyPoints: Int,
)

data class PromotionSummary(
    val name: String,
    val savings: Long,
    val applicableVariantIds: Set<String> = emptySet(),
)

data class DiscountOption(
    val id: String?,
    val name: String,
    val type: String,
    val value: Double,
    val scope: String = "transaction",
    val scopeId: String? = null,
    val requiresApproval: Boolean = false,
    val approvalThreshold: Long? = null,
    val minimumSpend: Long? = null,
    val minimumQuantity: Int? = null,
)

data class AppliedDiscount(
    val option: DiscountOption,
    val amount: Long,
)

data class HeldCart(
    val id: String,
    val itemCount: Int,
    val customerName: String?,
    val notes: String?,
    val heldAt: Long,
    val pendingSync: Boolean,
)

data class CatalogFreshness(
    val syncedAt: Long?,
    val refreshing: Boolean,
    val error: String? = null,
)
