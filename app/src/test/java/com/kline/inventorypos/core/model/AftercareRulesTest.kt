package com.kline.inventorypos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AftercareRulesTest {
    private val item = ReturnableItem("i1", "v1", "Shirt", "Blue · M", "SKU", 100_000, 18_000, 2, 2)

    @Test
    fun estimateIncludesProratedOriginalTax() {
        assertEquals(109_000, AftercareRules.estimatedReturn(listOf(ReturnLine(item, 1))))
    }

    @Test
    fun settlementDirectionUsesSignedServerNetAmount() {
        assertFalse(AftercareRules.settlementRequired(0))
        assertTrue(AftercareRules.settlementRequired(10_000))
        assertTrue(AftercareRules.storeOwes(-10_000))
        assertFalse(AftercareRules.storeOwes(10_000))
    }
}
