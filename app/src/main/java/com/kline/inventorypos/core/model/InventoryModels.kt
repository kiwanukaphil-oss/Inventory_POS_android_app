package com.kline.inventorypos.core.model

data class InventorySummary(
    val productCount: Int,
    val variantCount: Int,
    val unitsOnHand: Int,
    val lowStockCount: Int,
    val outOfStockCount: Int,
    val inventoryValue: Long,
    val deadStockCount: Int,
    val stalePriceCount: Int,
)

data class StockMovement(
    val id: String,
    val variantId: String,
    val productName: String,
    val sku: String,
    val type: String,
    val quantityChange: Int,
    val previousQuantity: Int,
    val newQuantity: Int,
    val reason: String?,
    val reference: String?,
    val performedBy: String,
    val createdAt: String,
)

data class SupplierSummary(
    val id: String,
    val name: String,
    val phone: String?,
)

data class ReceiveStockLine(
    val product: Product,
    val quantity: Int,
    val unitCost: Long?,
)

data class InventoryMutationResult(
    val pendingApproval: Boolean,
    val message: String,
    val reference: String? = null,
)

data class StockTransfer(
    val id: String,
    val number: String,
    val fromBranchId: String,
    val fromBranchName: String,
    val toBranchId: String,
    val toBranchName: String,
    val status: String,
    val itemCount: Int,
    val notes: String?,
    val requestedBy: String?,
    val requestedAt: String,
)

data class ReceiveStockDraft(
    val id: String,
    val supplierId: String?,
    val supplierName: String?,
    val lines: List<ReceiveDraftLine>,
    val notes: String?,
    val updatedAt: String,
)

data class ReceiveDraftLine(
    val variantId: String,
    val quantity: Int,
    val unitCost: Long?,
)

data class GrnSummary(
    val reference: String,
    val receivedAt: String,
    val supplierName: String?,
    val receivedBy: String,
    val itemCount: Int,
    val totalUnits: Int,
)

data class GrnLine(
    val movementId: String,
    val variantId: String,
    val productName: String,
    val sku: String,
    val variant: String,
    val quantity: Int,
    val previousQuantity: Int,
    val newQuantity: Int,
    val unitCost: Long?,
)

data class PricingVariant(
    val id: String,
    val productName: String,
    val sku: String,
    val variant: String,
    val price: Long,
    val costPrice: Long?,
    val stock: Int,
    val categoryName: String?,
)

data class PriceHistoryEntry(
    val id: String,
    val sku: String,
    val productName: String,
    val oldPrice: Long,
    val newPrice: Long,
    val reason: String,
    val changedBy: String,
    val createdAt: String,
)

data class PricingSnapshot(
    val variants: List<PricingVariant>,
    val history: List<PriceHistoryEntry>,
)

data class PriceUpdate(val variantId: String, val newPrice: Long)

data class LabelPrintItem(val product: Product, val copies: Int)
