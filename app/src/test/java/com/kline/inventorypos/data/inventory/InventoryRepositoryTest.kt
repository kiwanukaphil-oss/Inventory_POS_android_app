package com.kline.inventorypos.data.inventory

import com.google.gson.Gson
import com.kline.inventorypos.core.model.Product
import com.kline.inventorypos.core.model.ProductTone
import com.kline.inventorypos.data.network.BulkStockAdjustmentItemRequest
import com.kline.inventorypos.data.network.BulkStockAdjustmentRequest
import com.kline.inventorypos.data.network.CreateStockTransferItemRequest
import com.kline.inventorypos.data.network.CreateStockTransferRequest
import com.kline.inventorypos.data.network.BulkPriceUpdateRequest
import com.kline.inventorypos.data.network.PriceUpdateRequest
import com.kline.inventorypos.data.network.ReceiveDraftItemDto
import com.kline.inventorypos.data.network.SaveReceiveStockDraftRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRepositoryTest {
    private val products = listOf(
        product("v1", "p1", stock = 3, reorder = 5, price = 10_000),
        product("v2", "p1", stock = 0, reorder = 5, price = 15_000),
        product("v3", "p2", stock = 12, reorder = 4, price = 20_000),
    )

    @Test
    fun localSummarySeparatesLowAndOutOfStock() {
        val summary = localSummary(products)

        assertEquals(2, summary.productCount)
        assertEquals(3, summary.variantCount)
        assertEquals(15, summary.unitsOnHand)
        assertEquals(1, summary.lowStockCount)
        assertEquals(1, summary.outOfStockCount)
        assertEquals(270_000, summary.inventoryValue)
    }

    @Test
    fun receivePayloadUsesPurchaseMovementAndSupplier() {
        val request = BulkStockAdjustmentRequest(
            adjustments = listOf(BulkStockAdjustmentItemRequest("v1", 4, 7_500)),
            reason = "Invoice INV-42",
            supplierId = "supplier-1",
        )

        val json = Gson().toJson(request)
        assertTrue(json.contains("\"movement_type\":\"purchase\""))
        assertTrue(json.contains("\"supplier_id\":\"supplier-1\""))
        assertTrue(json.contains("\"cost_price\":7500"))
    }

    @Test
    fun transferPayloadUsesRequestedQuantityContract() {
        val request = CreateStockTransferRequest(
            toBranchId = "acacia",
            notes = "Weekend replenishment",
            items = listOf(CreateStockTransferItemRequest("v1", 3)),
        )

        val json = Gson().toJson(request)
        assertTrue(json.contains("\"to_branch_id\":\"acacia\""))
        assertTrue(json.contains("\"requested_quantity\":3"))
    }

    @Test
    fun receiveDraftPayloadPreservesCostAndSupplier() {
        val request = SaveReceiveStockDraftRequest(
            supplierId = "supplier-1",
            items = listOf(ReceiveDraftItemDto("v1", "SKU-v1", "Shirt", emptyMap(), 6, 8_500.0)),
            notes = "Partial delivery",
        )

        val json = Gson().toJson(request)
        assertTrue(json.contains("\"supplier_id\":\"supplier-1\""))
        assertTrue(json.contains("\"cost_price\":8500.0"))
        assertTrue(json.contains("\"is_partial\":false"))
    }

    @Test
    fun priceUpdatePayloadMatchesApprovalAwareBackendContract() {
        val request = BulkPriceUpdateRequest(
            updates = listOf(PriceUpdateRequest("v1", 125_000)),
            reason = "Margin review",
            effectiveDate = "2026-07-21",
        )

        val json = Gson().toJson(request)
        assertTrue(json.contains("\"variantId\":\"v1\""))
        assertTrue(json.contains("\"newPrice\":125000"))
        assertTrue(json.contains("\"effectiveDate\":\"2026-07-21\""))
    }

    private fun product(id: String, productId: String, stock: Int, reorder: Int, price: Long) = Product(
        id = id,
        productId = productId,
        name = "Product $id",
        sku = "SKU-$id",
        price = price,
        stock = stock,
        category = "Test",
        tone = ProductTone.NAVY,
        variant = "Standard",
        reorderLevel = reorder,
    )
}
