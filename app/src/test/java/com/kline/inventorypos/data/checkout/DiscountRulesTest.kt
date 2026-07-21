package com.kline.inventorypos.data.checkout

import com.google.gson.Gson
import com.kline.inventorypos.core.model.CartLine
import com.kline.inventorypos.core.model.DiscountOption
import com.kline.inventorypos.core.model.Product
import com.kline.inventorypos.core.model.ProductTone
import com.kline.inventorypos.data.network.CartItemRequest
import com.kline.inventorypos.data.network.CreateSaleRequest
import com.kline.inventorypos.data.network.SalePaymentRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscountRulesTest {
    private val shirt = Product(
        id = "shirt-variant",
        productId = "shirt",
        name = "Shirt",
        sku = "SHIRT-1",
        price = 100_000,
        stock = 10,
        category = "Shirts",
        categoryId = "shirts",
        tone = ProductTone.NAVY,
        variant = "M",
    )
    private val belt = Product(
        id = "belt-variant",
        productId = "belt",
        name = "Belt",
        sku = "BELT-1",
        price = 50_000,
        stock = 10,
        category = "Accessories",
        categoryId = "accessories",
        tone = ProductTone.CHARCOAL,
        variant = "Black",
    )
    private val cart = listOf(CartLine(shirt, 2), CartLine(belt, 1))

    @Test
    fun percentageDiscountUsesTransactionSubtotal() {
        val option = DiscountOption("ten", "10% off", "percentage", 10.0)

        assertEquals(25_000, DiscountRules.calculate(option, cart))
    }

    @Test
    fun fixedDiscountCannotExceedEligibleCategory() {
        val option = DiscountOption(
            id = "accessory",
            name = "Accessories offer",
            type = "fixed_amount",
            value = 80_000.0,
            scope = "category",
            scopeId = "accessories",
        )

        assertEquals(50_000, DiscountRules.calculate(option, cart))
    }

    @Test
    fun minimumSpendPreventsPreview() {
        val option = DiscountOption(
            id = "minimum",
            name = "Big basket",
            type = "percentage",
            value = 20.0,
            minimumSpend = 300_000,
        )

        assertEquals(0, DiscountRules.calculate(option, cart))
    }

    @Test
    fun createSaleSerializesPresetAndManualFields() {
        val request = CreateSaleRequest(
            customerId = null,
            items = listOf(CartItemRequest("shirt-variant", 1)),
            payments = listOf(SalePaymentRequest("cash", 90_000, null)),
            paymentMethod = "cash",
            amountPaid = 90_000,
            discountAmount = 10_000,
            discountId = "discount-id",
            paymentReference = null,
        )

        val json = Gson().toJson(request)
        assertTrue(json.contains("\"discount_amount\":10000"))
        assertTrue(json.contains("\"discount_id\":\"discount-id\""))
        assertFalse(json.contains("discountId"))
    }
}
