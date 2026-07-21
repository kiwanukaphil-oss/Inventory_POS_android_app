package com.kline.inventorypos

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import com.kline.inventorypos.core.common.formatUgx
import com.kline.inventorypos.core.model.LabelPrintItem
import java.io.FileOutputStream

class LabelPrintAdapter(
    private val context: Context,
    items: List<LabelPrintItem>,
) : PrintDocumentAdapter() {
    private val labels = items.flatMap { item -> List(item.copies.coerceIn(1, 99)) { item.product } }
    private var attributes = PrintAttributes.Builder().build()
    private var pageCount = 1

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) return callback.onLayoutCancelled()
        attributes = newAttributes
        val document = PrintedPdfDocument(context, attributes)
        val content = document.pageContentRect
        document.close()
        val columns = 3
        val labelHeight = 92f
        val rows = (content.height() / labelHeight).toInt().coerceAtLeast(1)
        pageCount = ((labels.size + (columns * rows) - 1) / (columns * rows)).coerceAtLeast(1)
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder("inventory-labels.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pageCount)
                .build(),
            oldAttributes != newAttributes,
        )
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        if (cancellationSignal.isCanceled) return callback.onWriteCancelled()
        val document = PrintedPdfDocument(context, attributes)
        try {
            var labelIndex = 0
            for (pageIndex in 0 until pageCount) {
                if (cancellationSignal.isCanceled) return callback.onWriteCancelled()
                val page = document.startPage(pageIndex)
                val canvas = page.canvas
                val content = document.pageContentRect
                val columns = 3
                val cellWidth = content.width().toFloat() / columns
                val labelHeight = 92f
                val rows = (content.height() / labelHeight).toInt().coerceAtLeast(1)
                repeat(rows) { row ->
                    repeat(columns) { column ->
                        if (labelIndex >= labels.size) return@repeat
                        val left = content.left + column * cellWidth
                        val top = content.top + row * labelHeight
                        drawLabel(canvas, RectF(left, top, left + cellWidth, top + labelHeight), labels[labelIndex])
                        labelIndex++
                    }
                }
                document.finishPage(page)
            }
            FileOutputStream(destination.fileDescriptor).use(document::writeTo)
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Exception) {
            callback.onWriteFailed(error.localizedMessage)
        } finally {
            document.close()
        }
    }

    private fun drawLabel(canvas: android.graphics.Canvas, bounds: RectF, product: com.kline.inventorypos.core.model.Product) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.LTGRAY
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 7.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val bold = Paint(text).apply {
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawRect(bounds, border)
        val left = bounds.left + 7f
        canvas.drawText(product.name.take(28), left, bounds.top + 13f, bold)
        canvas.drawText(product.variant.take(30), left, bounds.top + 24f, text)
        canvas.drawText(formatUgx(product.price), left, bounds.top + 36f, bold)
        canvas.drawText(product.sku.take(22), bounds.right - 7f - text.measureText(product.sku.take(22)), bounds.top + 36f, text)
        product.barcode?.takeIf { it.matches(Regex("\\d{13}")) }?.let { barcode ->
            drawEan13(canvas, barcode, RectF(left, bounds.top + 42f, bounds.right - 7f, bounds.bottom - 14f))
            val width = text.measureText(barcode)
            canvas.drawText(barcode, bounds.centerX() - width / 2f, bounds.bottom - 4f, text)
        }
    }

    private fun drawEan13(canvas: android.graphics.Canvas, value: String, bounds: RectF) {
        val bits = ean13Bits(value) ?: return
        val moduleWidth = bounds.width() / bits.length
        val paint = Paint().apply { color = android.graphics.Color.BLACK }
        bits.forEachIndexed { index, bit ->
            if (bit == '1') {
                canvas.drawRect(
                    bounds.left + index * moduleWidth,
                    bounds.top,
                    bounds.left + (index + 1) * moduleWidth + 0.1f,
                    bounds.bottom,
                    paint,
                )
            }
        }
    }
}

internal fun ean13Bits(value: String): String? {
    if (!value.matches(Regex("\\d{13}"))) return null
    val left = arrayOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
    val alternate = arrayOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
    val right = arrayOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")
    val parity = arrayOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")
    val digits = value.map(Char::digitToInt)
    return buildString(95) {
        append("101")
        digits.slice(1..6).forEachIndexed { index, digit ->
            append(if (parity[digits.first()][index] == 'L') left[digit] else alternate[digit])
        }
        append("01010")
        digits.slice(7..12).forEach { append(right[it]) }
        append("101")
    }
}
