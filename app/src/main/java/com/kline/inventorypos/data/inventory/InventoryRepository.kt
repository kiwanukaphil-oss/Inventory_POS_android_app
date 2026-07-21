package com.kline.inventorypos.data.inventory

import com.google.gson.Gson
import com.kline.inventorypos.core.model.InventoryMutationResult
import com.kline.inventorypos.core.model.InventorySummary
import com.kline.inventorypos.core.model.GrnLine
import com.kline.inventorypos.core.model.GrnSummary
import com.kline.inventorypos.core.model.Product
import com.kline.inventorypos.core.model.PriceHistoryEntry
import com.kline.inventorypos.core.model.PriceUpdate
import com.kline.inventorypos.core.model.PricingSnapshot
import com.kline.inventorypos.core.model.PricingVariant
import com.kline.inventorypos.core.model.LabelPrintItem
import com.kline.inventorypos.core.model.ReceiveStockLine
import com.kline.inventorypos.core.model.ReceiveStockDraft
import com.kline.inventorypos.core.model.ReceiveDraftLine
import com.kline.inventorypos.core.model.SampleProducts
import com.kline.inventorypos.core.model.StockMovement
import com.kline.inventorypos.core.model.StockTransfer
import com.kline.inventorypos.core.model.SupplierSummary
import com.kline.inventorypos.core.session.PosBranch
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.BulkStockAdjustmentItemRequest
import com.kline.inventorypos.data.network.BulkStockAdjustmentRequest
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.BulkPriceUpdateRequest
import com.kline.inventorypos.data.network.LabelPrintRunItemRequest
import com.kline.inventorypos.data.network.LabelPrintRunRequest
import com.kline.inventorypos.data.network.PriceUpdateRequest
import com.kline.inventorypos.data.network.CreateStockTransferItemRequest
import com.kline.inventorypos.data.network.CreateStockTransferRequest
import com.kline.inventorypos.data.network.ReceiveDraftItemDto
import com.kline.inventorypos.data.network.SaveReceiveStockDraftRequest
import com.kline.inventorypos.data.network.StockAdjustmentRequest
import com.kline.inventorypos.data.network.StockMovementDto
import com.kline.inventorypos.data.network.StockTransferDto
import com.kline.inventorypos.data.network.toDomain
import retrofit2.HttpException
import java.time.Instant
import java.time.LocalDate

interface InventoryRepository {
    suspend fun summary(products: List<Product>): InventorySummary
    suspend fun movements(type: String? = null): List<StockMovement>
    suspend fun suppliers(): List<SupplierSummary>
    suspend fun adjust(product: Product, quantityChange: Int, reason: String): InventoryMutationResult
    suspend fun receive(
        supplier: SupplierSummary,
        lines: List<ReceiveStockLine>,
        notes: String?,
    ): InventoryMutationResult
    suspend fun destinationBranches(currentBranchId: String): List<PosBranch>
    suspend fun transfers(): List<StockTransfer>
    suspend fun createTransfer(destination: PosBranch, lines: List<ReceiveStockLine>, notes: String?): StockTransfer
    suspend fun transitionTransfer(transfer: StockTransfer, action: String): StockTransfer
    suspend fun receiveDraft(): ReceiveStockDraft?
    suspend fun saveDraft(supplier: SupplierSummary?, lines: List<ReceiveStockLine>, notes: String?): ReceiveStockDraft
    suspend fun deleteDraft(id: String)
    suspend fun grnHistory(): List<GrnSummary>
    suspend fun grnDetail(reference: String): List<GrnLine>
    suspend fun pricing(products: List<Product>): PricingSnapshot
    suspend fun updatePrices(updates: List<PriceUpdate>, reason: String): InventoryMutationResult
    suspend fun recordLabelPrint(items: List<LabelPrintItem>)
}

class DefaultInventoryRepository(
    private val api: InventoryPosApi,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
) : InventoryRepository {
    override suspend fun summary(products: List<Product>): InventorySummary {
        if (isDemo()) return localSummary(products.ifEmpty { SampleProducts })
        val dto = api.inventorySummary().data
        return InventorySummary(
            productCount = dto.totalProducts,
            variantCount = dto.totalVariants,
            unitsOnHand = dto.totalStockUnits,
            lowStockCount = dto.lowStockCount,
            outOfStockCount = dto.outOfStockCount,
            inventoryValue = dto.totalInventoryValue.toLong(),
            deadStockCount = dto.deadStockCount,
            stalePriceCount = dto.stalePriceCount,
        )
    }

    override suspend fun movements(type: String?): List<StockMovement> {
        if (isDemo()) return demoMovements().filter { type == null || it.type == type }
        return api.stockMovements(movementType = type).data.map(StockMovementDto::toDomain)
    }

    override suspend fun suppliers(): List<SupplierSummary> {
        if (isDemo()) return listOf(
            SupplierSummary("supplier-1", "Uganda Garments Ltd", "+256 700 111 222"),
            SupplierSummary("supplier-2", "Kampala Textile House", "+256 700 333 444"),
            SupplierSummary("supplier-3", "East Africa Accessories", null),
        )
        return api.suppliers().data.filter { it.isActive != false }.map { SupplierSummary(it.id, it.name, it.phone) }
    }

    override suspend fun adjust(
        product: Product,
        quantityChange: Int,
        reason: String,
    ): InventoryMutationResult {
        require(quantityChange != 0) { "Enter a stock change other than zero." }
        require(reason.trim().length >= 3) { "Add a clear reason for the audit trail." }
        require(product.stock + quantityChange >= 0) { "This adjustment would make stock negative." }
        if (isDemo()) return InventoryMutationResult(false, "${product.name} stock adjusted in demo")
        return mutation {
            api.adjustStock(StockAdjustmentRequest(product.id, quantityChange, reason.trim()))
        }
    }

    override suspend fun receive(
        supplier: SupplierSummary,
        lines: List<ReceiveStockLine>,
        notes: String?,
    ): InventoryMutationResult {
        require(lines.isNotEmpty()) { "Add at least one delivered item." }
        require(lines.all { it.quantity > 0 }) { "Received quantities must be greater than zero." }
        if (isDemo()) {
            val reference = "DEMO-GRN-${System.currentTimeMillis().toString().takeLast(6)}"
            return InventoryMutationResult(false, "Stock received", reference)
        }
        return mutation {
            api.receiveStock(
                BulkStockAdjustmentRequest(
                    adjustments = lines.map {
                        BulkStockAdjustmentItemRequest(it.product.id, it.quantity, it.unitCost)
                    },
                    reason = notes?.trim().takeUnless { it.isNullOrBlank() } ?: "Stock received",
                    supplierId = supplier.id,
                ),
            )
        }
    }

    override suspend fun destinationBranches(currentBranchId: String): List<PosBranch> {
        if (isDemo()) return listOf(
            PosBranch("acacia", "ACA", "Acacia Mall", "active", "Kampala", false, false, true),
            PosBranch("village", "VIL", "Village Mall", "active", "Kampala", false, false, true),
        ).filter { it.id != currentBranchId }
        return api.myBranches().data.branches.map { it.toDomain() }
            .filter { it.id != currentBranchId && it.canSwitchTo && it.status == "active" }
    }

    override suspend fun transfers(): List<StockTransfer> {
        if (isDemo()) return demoTransfers()
        return api.stockTransfers().data.map(StockTransferDto::toDomain)
    }

    override suspend fun createTransfer(
        destination: PosBranch,
        lines: List<ReceiveStockLine>,
        notes: String?,
    ): StockTransfer {
        require(lines.isNotEmpty()) { "Add at least one transfer item." }
        require(lines.all { it.quantity > 0 && it.quantity <= it.product.stock }) {
            "Transfer quantities must be available at this branch."
        }
        if (isDemo()) return StockTransfer(
            id = "demo-transfer-${System.currentTimeMillis()}",
            number = "DEMO-TRF-${System.currentTimeMillis().toString().takeLast(5)}",
            fromBranchId = "main",
            fromBranchName = "Main Branch",
            toBranchId = destination.id,
            toBranchName = destination.name,
            status = "requested",
            itemCount = lines.size,
            notes = notes,
            requestedBy = "Philip Kiwanuka",
            requestedAt = Instant.now().toString(),
        )
        return apiCall {
            api.createStockTransfer(
                CreateStockTransferRequest(
                    toBranchId = destination.id,
                    notes = notes?.trim().takeUnless { it.isNullOrBlank() },
                    items = lines.map { CreateStockTransferItemRequest(it.product.id, it.quantity) },
                ),
            ).data.toDomain()
        }
    }

    override suspend fun transitionTransfer(transfer: StockTransfer, action: String): StockTransfer {
        if (isDemo()) {
            val status = when (action) {
                "dispatch" -> "dispatched"
                "receive" -> "received"
                "cancel" -> "cancelled"
                else -> error("Unknown transfer action")
            }
            return transfer.copy(status = status)
        }
        return apiCall {
            when (action) {
                "dispatch" -> api.dispatchStockTransfer(transfer.id)
                "receive" -> api.receiveStockTransfer(transfer.id)
                "cancel" -> api.cancelStockTransfer(transfer.id)
                else -> error("Unknown transfer action")
            }.data.toDomain()
        }
    }

    override suspend fun receiveDraft(): ReceiveStockDraft? {
        if (isDemo()) return null
        return api.receiveStockDraft().data?.let { dto ->
            ReceiveStockDraft(
                id = dto.id,
                supplierId = dto.supplierId,
                supplierName = dto.supplierName,
                lines = dto.items.orEmpty().map { ReceiveDraftLine(it.variantId, it.quantity, it.costPrice?.toLong()) },
                notes = dto.notes,
                updatedAt = dto.updatedAt,
            )
        }
    }

    override suspend fun saveDraft(
        supplier: SupplierSummary?,
        lines: List<ReceiveStockLine>,
        notes: String?,
    ): ReceiveStockDraft {
        if (isDemo()) return ReceiveStockDraft(
            id = "demo-draft",
            supplierId = supplier?.id,
            supplierName = supplier?.name,
            lines = lines.map { ReceiveDraftLine(it.product.id, it.quantity, it.unitCost) },
            notes = notes,
            updatedAt = Instant.now().toString(),
        )
        val dto = apiCall {
            api.saveReceiveStockDraft(
                SaveReceiveStockDraftRequest(
                    supplierId = supplier?.id,
                    items = lines.map {
                        ReceiveDraftItemDto(
                            variantId = it.product.id,
                            sku = it.product.sku,
                            productName = it.product.name,
                            variantAttributes = emptyMap(),
                            quantity = it.quantity,
                            costPrice = it.unitCost?.toDouble(),
                        )
                    },
                    notes = notes?.trim().takeUnless { it.isNullOrBlank() },
                ),
            ).data
        }
        return ReceiveStockDraft(
            dto.id,
            dto.supplierId,
            dto.supplierName ?: supplier?.name,
            dto.items.orEmpty().map { ReceiveDraftLine(it.variantId, it.quantity, it.costPrice?.toLong()) },
            dto.notes,
            dto.updatedAt,
        )
    }

    override suspend fun deleteDraft(id: String) {
        if (!isDemo()) apiCall { api.deleteReceiveStockDraft(id) }
    }

    override suspend fun grnHistory(): List<GrnSummary> {
        if (isDemo()) return demoGrns()
        return api.grnHistory().data.map {
            GrnSummary(it.grnReference, it.receivedAt, it.supplierName, it.receivedBy ?: "Team member", it.itemCount, it.totalUnits)
        }
    }

    override suspend fun grnDetail(reference: String): List<GrnLine> {
        if (isDemo()) return demoGrnLines(reference)
        return api.grnDetail(reference).data.map {
            GrnLine(
                movementId = it.movementId,
                variantId = it.variantId,
                productName = it.productName,
                sku = it.sku,
                variant = it.variantAttributes.orEmpty().values.joinToString(" · ").ifBlank { "Standard" },
                quantity = it.quantityChange,
                previousQuantity = it.previousQuantity,
                newQuantity = it.newQuantity,
                unitCost = it.currentCostPrice?.toLong(),
            )
        }
    }

    override suspend fun pricing(products: List<Product>): PricingSnapshot {
        if (isDemo()) {
            val variants = products.map { product ->
                PricingVariant(
                    id = product.id,
                    productName = product.name,
                    sku = product.sku,
                    variant = product.variant,
                    price = product.price,
                    costPrice = (product.price * 62) / 100,
                    stock = product.stock,
                    categoryName = product.category,
                )
            }
            return PricingSnapshot(variants, demoPriceHistory(variants))
        }
        return apiCall {
            val variants = api.pricingVariants().data.map { dto ->
                PricingVariant(
                    id = dto.id,
                    productName = dto.productName,
                    sku = dto.sku,
                    variant = dto.variantAttributes.orEmpty().values.joinToString(" · ").ifBlank { "Standard" },
                    price = dto.price.toLong(),
                    costPrice = dto.costPrice?.toLong(),
                    stock = dto.stockQuantity.toInt(),
                    categoryName = dto.categoryName,
                )
            }
            val history = api.priceHistory().data.map { dto ->
                PriceHistoryEntry(
                    id = dto.id,
                    sku = dto.sku,
                    productName = dto.productName,
                    oldPrice = dto.oldPrice.toLong(),
                    newPrice = dto.newPrice.toLong(),
                    reason = dto.reason.orEmpty(),
                    changedBy = dto.changedByName ?: dto.changedByUsername ?: "Team member",
                    createdAt = dto.createdAt,
                )
            }
            PricingSnapshot(variants, history)
        }
    }

    override suspend fun updatePrices(updates: List<PriceUpdate>, reason: String): InventoryMutationResult {
        require(updates.isNotEmpty()) { "Choose at least one price to update." }
        require(updates.all { it.newPrice > 0 }) { "Selling prices must be greater than zero." }
        require(reason.trim().length >= 3) { "Add a clear reason for the price audit trail." }
        if (isDemo()) return InventoryMutationResult(false, "Price updated in demo")
        return mutation {
            api.bulkUpdatePrices(
                BulkPriceUpdateRequest(
                    updates = updates.map { PriceUpdateRequest(it.variantId, it.newPrice) },
                    reason = reason.trim(),
                    effectiveDate = LocalDate.now().toString(),
                ),
            )
        }
    }

    override suspend fun recordLabelPrint(items: List<LabelPrintItem>) {
        require(items.isNotEmpty()) { "Choose at least one label to print." }
        require(items.all { it.copies in 1..99 }) { "Label copies must be between 1 and 99." }
        if (!isDemo()) {
            apiCall {
                api.recordLabelPrintRun(
                    LabelPrintRunRequest(items.map { LabelPrintRunItemRequest(it.product.id, it.copies) }),
                )
            }
        }
    }

    private suspend fun mutation(
        block: suspend () -> com.kline.inventorypos.data.network.InventoryMutationResponseDto,
    ): InventoryMutationResult = try {
        val response = block()
        InventoryMutationResult(
            pendingApproval = response.status == "pending_approval",
            message = response.message ?: if (response.status == "pending_approval") {
                "Submitted for approval"
            } else {
                "Inventory updated"
            },
            reference = response.grnReference,
        )
    } catch (error: HttpException) {
        val message = error.response()?.errorBody()?.string()?.let { body ->
            runCatching { gson.fromJson(body, ApiError::class.java).message }.getOrNull()
        } ?: "The inventory update was rejected."
        throw IllegalStateException(message, error)
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        val message = error.response()?.errorBody()?.string()?.let { body ->
            runCatching { gson.fromJson(body, ApiError::class.java).message }.getOrNull()
        } ?: "The inventory request was rejected."
        throw IllegalStateException(message, error)
    }
}

fun localSummary(products: List<Product>): InventorySummary = InventorySummary(
    productCount = products.map { it.productId }.distinct().size,
    variantCount = products.size,
    unitsOnHand = products.sumOf { it.stock },
    lowStockCount = products.count { it.stock in 1..it.reorderLevel },
    outOfStockCount = products.count { it.stock <= 0 },
    inventoryValue = products.sumOf { it.stock.toLong() * it.price },
    deadStockCount = 0,
    stalePriceCount = 0,
)

private fun StockMovementDto.toDomain() = StockMovement(
    id = id,
    variantId = variantId,
    productName = productName ?: sku,
    sku = sku,
    type = movementType,
    quantityChange = quantityChange,
    previousQuantity = previousQuantity,
    newQuantity = newQuantity,
    reason = reason,
    reference = referenceId,
    performedBy = performedByName ?: "Team member",
    createdAt = createdAt,
)

private fun demoMovements(): List<StockMovement> = listOf(
    StockMovement("movement-1", "v1", "Premium Linen Shirt", "LIN-SKY-M", "purchase", 12, 3, 15, "Delivery received", "DEMO-GRN-1042", "Philip Kiwanuka", Instant.now().minusSeconds(3_600).toString()),
    StockMovement("movement-2", "v2", "Oxford Cotton Shirt", "OXF-NV-L", "sale", -1, 3, 2, "Sale", "DEMO-SALE-8831", "Philip Kiwanuka", Instant.now().minusSeconds(7_200).toString()),
    StockMovement("movement-3", "v5", "Field Jacket", "JKT-OL-M", "adjustment", -1, 7, 6, "Damaged during handling", null, "Store Manager", Instant.now().minusSeconds(86_400).toString()),
)

private fun StockTransferDto.toDomain() = StockTransfer(
    id = id,
    number = transferNumber,
    fromBranchId = fromBranchId,
    fromBranchName = fromBranchName,
    toBranchId = toBranchId,
    toBranchName = toBranchName,
    status = status,
    itemCount = itemCount ?: 0,
    notes = notes,
    requestedBy = requestedByName,
    requestedAt = requestedAt,
)

private fun demoTransfers(): List<StockTransfer> = listOf(
    StockTransfer("transfer-1", "TRF-MAIN-0042", "main", "Main Branch", "acacia", "Acacia Mall", "requested", 2, "Replenish weekend stock", "Philip Kiwanuka", Instant.now().minusSeconds(7_200).toString()),
    StockTransfer("transfer-2", "TRF-ACA-0038", "acacia", "Acacia Mall", "main", "Main Branch", "dispatched", 3, null, "Sarah Manager", Instant.now().minusSeconds(86_400).toString()),
)

private fun demoGrns(): List<GrnSummary> = listOf(
    GrnSummary("GRN-MAIN-01042", Instant.now().minusSeconds(3_600).toString(), "Uganda Garments Ltd", "Philip Kiwanuka", 2, 18),
    GrnSummary("GRN-MAIN-01039", Instant.now().minusSeconds(172_800).toString(), "Kampala Textile House", "Store Manager", 3, 25),
)

private fun demoGrnLines(reference: String): List<GrnLine> = listOf(
    GrnLine("grn-line-$reference-1", "v1", "Premium Linen Shirt", "LIN-SKY-M", "Sky blue · M", 12, 3, 15, 82_000),
    GrnLine("grn-line-$reference-2", "v2", "Oxford Cotton Shirt", "OXF-NV-L", "Navy · L", 6, 2, 8, 70_000),
)

private fun demoPriceHistory(variants: List<PricingVariant>): List<PriceHistoryEntry> =
    variants.take(3).mapIndexed { index, variant ->
        PriceHistoryEntry(
            id = "demo-price-$index",
            sku = variant.sku,
            productName = variant.productName,
            oldPrice = variant.price - 5_000,
            newPrice = variant.price,
            reason = "Seasonal price review",
            changedBy = "Store Manager",
            createdAt = Instant.now().minusSeconds((index + 1) * 86_400L).toString(),
        )
    }
