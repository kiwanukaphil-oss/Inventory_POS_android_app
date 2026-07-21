package com.kline.inventorypos.core.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Displays grouping separators without changing the editable digit string. */
object AmountGroupingVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val formatted = formatAmountInput(original, maxDigits = Int.MAX_VALUE)
        val originalOffsets = IntArray(original.length + 1)

        var transformedOffset = 0
        original.indices.forEach { originalOffset ->
            if (originalOffset > 0 && (original.length - originalOffset) % 3 == 0) {
                transformedOffset++
            }
            originalOffsets[originalOffset] = transformedOffset
            transformedOffset++
        }
        originalOffsets[original.length] = transformedOffset

        val transformedOffsets = IntArray(formatted.length + 1)
        var originalOffset = 0
        for (offset in 0..formatted.length) {
            transformedOffsets[offset] = originalOffset
            if (offset < formatted.length && formatted[offset].isDigit()) originalOffset++
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalOffsets[offset.coerceIn(0, originalOffsets.lastIndex)]

            override fun transformedToOriginal(offset: Int): Int =
                transformedOffsets[offset.coerceIn(0, transformedOffsets.lastIndex)]
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}
