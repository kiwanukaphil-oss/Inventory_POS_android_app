package com.kline.inventorypos.data.document

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kline.inventorypos.R
import com.kline.inventorypos.core.model.BusinessDocument
import com.kline.inventorypos.core.model.BusinessDocumentItem
import com.kline.inventorypos.data.network.StoreConfigDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BusinessDocumentPdfRendererTest {
    @Test
    fun rendersAValidPdfForEmailAttachment() {
        val document = BusinessDocument(
            id = "doc-1",
            type = "invoice",
            number = "INV-00482",
            status = "sent",
            billToName = "Acacia Facilities Ltd",
            billToAddress = "Kampala, Uganda",
            date = "2026-07-21",
            validUntil = null,
            dueDate = "2026-08-04",
            paymentMethod = null,
            paymentReference = null,
            subtotal = 1_850_000,
            total = 1_850_000,
            notes = "Thank you for your business.",
            sourceNumber = null,
            voidReason = null,
            createdBy = "Philip Kiwanuka",
            createdAt = "2026-07-21T10:00:00Z",
            items = listOf(BusinessDocumentItem("line-1", "Premium cotton shirts", 10.0, 185_000, 1_850_000)),
            derived = emptyList(),
        )
        val store = StoreConfigDto("K-Line Men", "Kampala Road", "Kampala", "Uganda", "+256 700 000000", "sales@example.com", "UGX", true, "VAT", null, 14, null, "print")

        val context = ApplicationProvider.getApplicationContext<Context>()
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.kline_logo)
        val bytes = BusinessDocumentPdfRenderer.render(document, store, logo)

        assertTrue(bytes.size > 1_000)
        assertEquals("%PDF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
    }
}
