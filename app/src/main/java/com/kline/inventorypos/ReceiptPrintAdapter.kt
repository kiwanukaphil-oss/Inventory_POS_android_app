package com.kline.inventorypos

import android.content.Context
import android.graphics.Paint
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
import com.kline.inventorypos.core.model.ConfirmedReceipt
import java.io.FileOutputStream

class ReceiptPrintAdapter(
    private val context: Context,
    private val receipt: ConfirmedReceipt,
) : PrintDocumentAdapter() {
    private var attributes = PrintAttributes.Builder().build()

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) return callback.onLayoutCancelled()
        attributes = newAttributes
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder("${receipt.receiptNumber}.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
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
            val page = document.startPage(0)
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = 11f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
            val bold = Paint(paint).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
            val left = 28f
            var y = 36f
            fun line(text: String, strong: Boolean = false, gap: Float = 17f) {
                canvas.drawText(text.take(72), left, y, if (strong) bold else paint)
                y += gap
            }
            line(receipt.branchName, true)
            line("Receipt ${receipt.receiptNumber}")
            line(receipt.saleDate)
            line("Cashier: ${receipt.cashierName}")
            receipt.customerName?.let { line("Customer: $it") }
            line("-".repeat(48))
            receipt.lines.forEach { item ->
                line("${item.quantity} x ${item.name}", true)
                line("  ${item.variant}  ${formatUgx(item.lineTotal)}")
            }
            line("-".repeat(48))
            line("Subtotal  ${formatUgx(receipt.subtotal)}")
            line("Discounts ${formatUgx(receipt.discountAmount)}")
            line("Tax       ${formatUgx(receipt.taxAmount)}")
            line("TOTAL     ${formatUgx(receipt.total)}", true, 22f)
            receipt.payments.forEach { line("${it.method}: ${formatUgx(it.amount)}") }
            if (receipt.change > 0) line("Change: ${formatUgx(receipt.change)}")
            line("Thank you.", true, 22f)
            document.finishPage(page)
            FileOutputStream(destination.fileDescriptor).use(document::writeTo)
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Exception) {
            callback.onWriteFailed(error.localizedMessage)
        } finally {
            document.close()
        }
    }
}
