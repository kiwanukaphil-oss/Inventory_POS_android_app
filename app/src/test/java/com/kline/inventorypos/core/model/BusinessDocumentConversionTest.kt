package com.kline.inventorypos.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessDocumentConversionTest {
    @Test
    fun invoiceWithoutLinkedReceiptCanConvertOnce() {
        val invoice = document(type = "invoice", status = "paid")

        assertEquals("receipt", invoice.conversionTargetType())
        assertTrue(invoice.canConvert())
        assertNull(invoice.conversionBlockReason())
    }

    @Test
    fun invoiceWithLinkedReceiptCannotCreateAnotherReceipt() {
        val invoice = document(
            type = "invoice",
            status = "paid",
            derived = listOf(DerivedDocument("receipt-1", "receipt", "RCT-001024", "issued")),
        )

        assertFalse(invoice.canConvert())
        assertEquals(
            "Receipt RCT-001024 already exists for INV-001023.",
            invoice.conversionBlockReason(),
        )
    }

    @Test
    fun convertedSourceCannotBeConvertedAgainEvenWhenLinkIsMissing() {
        val invoice = document(type = "invoice", status = "converted")

        assertFalse(invoice.canConvert())
        assertEquals("INV-001023 can no longer be converted.", invoice.conversionBlockReason())
    }

    private fun document(
        type: String,
        status: String,
        derived: List<DerivedDocument> = emptyList(),
    ) = BusinessDocument(
        id = "invoice-1",
        type = type,
        number = "INV-001023",
        status = status,
        billToName = "Nile Corporate Wear",
        billToAddress = "Kampala",
        date = "2026-07-21",
        validUntil = null,
        dueDate = null,
        paymentMethod = null,
        paymentReference = null,
        subtotal = 100_000,
        total = 100_000,
        notes = null,
        sourceNumber = null,
        voidReason = null,
        createdBy = "Philip Kiwanuka",
        createdAt = "2026-07-21T00:00:00Z",
        items = listOf(BusinessDocumentItem("line-1", "Shirt", 1.0, 100_000, 100_000)),
        derived = derived,
    )
}
