package com.kline.inventorypos.core.common

import com.kline.inventorypos.core.model.CartLine
import com.kline.inventorypos.core.model.SampleProducts
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test
    fun `UGX formatting uses grouping and no decimal places`() {
        assertEquals("UGX 4,850,000", formatUgx(4_850_000))
    }

    @Test
    fun `amount input adds grouping separators while typing`() {
        assertEquals("1,234,567", formatAmountInput("1234567"))
        assertEquals("1,234,567", formatAmountInput("1,234,567"))
    }

    @Test
    fun `amount input removes unsafe leading zeroes`() {
        assertEquals("", formatAmountInput(""))
        assertEquals("0", formatAmountInput("0"))
        assertEquals("5,000", formatAmountInput("0005000"))
    }

    @Test
    fun `formatted amount parses to integer currency`() {
        assertEquals(4_850_000L, parseAmountInput("4,850,000"))
        assertEquals(null, parseAmountInput(""))
    }

    @Test
    fun `grouping transformation preserves the cursor after the separator`() {
        val transformed = AmountGroupingVisualTransformation.filter(AnnotatedString("555600"))

        assertEquals("555,600", transformed.text.text)
        assertEquals(4, transformed.offsetMapping.originalToTransformed(3))
        assertEquals(3, transformed.offsetMapping.transformedToOriginal(4))
    }

    @Test
    fun `cart line total uses integer arithmetic`() {
        val line = CartLine(SampleProducts.first(), quantity = 3)
        assertEquals(495_000L, line.lineTotal)
    }
}
