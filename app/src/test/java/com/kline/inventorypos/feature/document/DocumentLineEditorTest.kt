package com.kline.inventorypos.feature.document

import com.kline.inventorypos.core.model.DocumentDraftLine
import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentLineEditorTest {
    @Test
    fun addingAnotherItemPreservesEveryExistingLine() {
        val first = DocumentDraftLine("Shirt", 2.0, 125_000)
        val second = DocumentDraftLine("Trouser", 3.0, 175_000)

        val result = upsertDocumentLine(listOf(first), null, second)

        assertEquals(listOf(first, second), result)
    }

    @Test
    fun editingFirstItemDoesNotRemoveLaterItems() {
        val first = DocumentDraftLine("Shirt", 2.0, 125_000)
        val second = DocumentDraftLine("Trouser", 3.0, 175_000)
        val corrected = DocumentDraftLine("Premium shirt", 4.0, 150_000)

        val result = upsertDocumentLine(listOf(first, second), 0, corrected)

        assertEquals(listOf(corrected, second), result)
    }
}
