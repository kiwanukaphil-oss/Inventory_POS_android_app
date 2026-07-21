package com.kline.inventorypos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPrintAdapterTest {
    @Test
    fun `EAN 13 encoder produces standard 95 module pattern`() {
        val bits = ean13Bits("2000000000015")

        assertEquals(95, bits?.length)
        assertTrue(bits!!.startsWith("101"))
        assertTrue(bits.endsWith("101"))
    }

    @Test
    fun `EAN 13 encoder rejects non EAN input`() {
        assertNull(ean13Bits("SKU-42"))
        assertNull(ean13Bits("123456789012"))
    }
}
