package com.kline.inventorypos.data.document

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.kline.inventorypos.core.model.BusinessDocument
import com.kline.inventorypos.data.network.StoreConfigDto
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.util.Locale

object BusinessDocumentPdfRenderer {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 46f
    private const val RIGHT = 549f
    private const val CONTENT_BOTTOM = 748f

    fun render(document: BusinessDocument, store: StoreConfigDto, logo: Bitmap? = null): ByteArray {
        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val money = NumberFormat.getNumberInstance(Locale.US)
        var pageNumber = 0
        var page = startPage(pdf, ++pageNumber)
        var canvas = page.canvas

        fun text(value: String, x: Float, y: Float, size: Float = 10f, bold: Boolean = false, color: Int = Color.rgb(55, 55, 55)) {
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.color = color
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(value, x, y, paint)
        }
        fun right(value: String, x: Float, y: Float, size: Float = 10f, bold: Boolean = false, color: Int = Color.rgb(55, 55, 55)) {
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            paint.color = color
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText(value, x, y, paint)
            paint.textAlign = Paint.Align.LEFT
        }
        fun footer() {
            paint.color = Color.rgb(210, 210, 210)
            paint.strokeWidth = 1f
            canvas.drawLine(LEFT, 782f, RIGHT, 782f, paint)
            val contact = listOfNotNull(store.phone?.takeIf(String::isNotBlank), store.email?.takeIf(String::isNotBlank)).joinToString(" · ")
            text("For clarification or queries${if (contact.isBlank()) "" else ": $contact"}", LEFT, 800f, 8.5f, color = Color.DKGRAY)
            right("Page $pageNumber", RIGHT, 800f, 8.5f)
        }
        fun finishPage() {
            if (document.status == "void") {
                paint.color = Color.argb(35, 130, 130, 130)
                paint.textSize = 72f
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                canvas.save()
                canvas.rotate(-28f, PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f)
                canvas.drawText("VOID", PAGE_WIDTH / 2f, PAGE_HEIGHT / 2f, paint)
                canvas.restore()
                paint.textAlign = Paint.Align.LEFT
            }
            footer()
            pdf.finishPage(page)
        }
        fun tableHeader(y: Float): Float {
            paint.color = Color.rgb(60, 60, 60)
            canvas.drawRect(LEFT, y, RIGHT, y + 24f, paint)
            text("#", LEFT + 8f, y + 16f, 8.5f, color = Color.WHITE)
            text("Item & description", LEFT + 28f, y + 16f, 8.5f, color = Color.WHITE)
            right("Qty", 375f, y + 16f, 8.5f, color = Color.WHITE)
            right("Rate (UGX)", 456f, y + 16f, 8.5f, color = Color.WHITE)
            right("Amount (UGX)", RIGHT - 4f, y + 16f, 8.5f, color = Color.WHITE)
            return y + 24f
        }
        fun continuationPage(): Float {
            finishPage()
            page = startPage(pdf, ++pageNumber)
            canvas = page.canvas
            text(document.type.uppercase(), LEFT, 48f, 18f, bold = true)
            right(document.number, RIGHT, 48f, 10f, bold = true)
            return tableHeader(68f)
        }

        val storeTextLeft = if (logo != null) {
            val source = Rect(0, 0, logo.width, logo.height)
            canvas.drawBitmap(logo, source, RectF(LEFT, 24f, 116f, 94f), paint)
            132f
        } else LEFT
        text(store.storeName?.takeIf(String::isNotBlank) ?: "K-Line Men", storeTextLeft, 50f, 17f, bold = true)
        val storeAddress = listOfNotNull(store.addressLine1, store.city, store.country).filter(String::isNotBlank).joinToString(", ")
        if (storeAddress.isNotBlank()) text(storeAddress, storeTextLeft, 70f, 9f, color = Color.DKGRAY)
        right(document.type.uppercase(), RIGHT, 52f, 26f)
        right("# ${document.number}", RIGHT, 72f, 10f, bold = true)

        text("BILL TO", LEFT, 116f, 8f, color = Color.GRAY)
        text(document.billToName, LEFT, 135f, 11f, bold = true)
        document.billToAddress?.lineSequence()?.filter(String::isNotBlank)?.take(3)?.forEachIndexed { index, line ->
            text(line.trim().take(70), LEFT, 151f + index * 14f, 9f)
        }
        right("Date: ${document.date}", RIGHT, 123f, 9f)
        document.validUntil?.let { right("Valid until: $it", RIGHT, 139f, 9f) }
        document.dueDate?.let { right("Due date: $it", RIGHT, 139f, 9f) }
        document.paymentMethod?.let { right("Payment: ${it.replace('_', ' ')}", RIGHT, 155f, 9f) }

        var y = tableHeader(198f)
        document.items.forEachIndexed { index, item ->
            val lines = wrap(item.description, paint, 210f).take(2)
            val rowHeight = 28f + (lines.size - 1) * 12f
            if (y + rowHeight > CONTENT_BOTTOM) y = continuationPage()
            text((index + 1).toString(), LEFT + 8f, y + 18f, 9f)
            lines.forEachIndexed { lineIndex, line -> text(line, LEFT + 28f, y + 18f + lineIndex * 12f, 9f) }
            right(cleanQuantity(item.quantity), 375f, y + 18f, 9f)
            right(money.format(item.unitPrice), 456f, y + 18f, 9f)
            right(money.format(item.lineTotal), RIGHT - 4f, y + 18f, 9f)
            paint.color = Color.LTGRAY
            canvas.drawLine(LEFT, y + rowHeight, RIGHT, y + rowHeight, paint)
            y += rowHeight
        }

        if (y + 86f > CONTENT_BOTTOM) y = continuationPage()
        y += 24f
        right("Sub total", 456f, y, 9.5f)
        right(money.format(document.subtotal), RIGHT - 4f, y, 9.5f)
        paint.color = Color.rgb(242, 242, 242)
        canvas.drawRect(350f, y + 10f, RIGHT, y + 44f, paint)
        text("TOTAL", 370f, y + 32f, 11f, bold = true)
        right("UGX ${money.format(document.total)}", RIGHT - 5f, y + 32f, 11f, bold = true)
        y += 62f
        document.notes?.takeIf(String::isNotBlank)?.let { note ->
            text("Notes", LEFT, y, 9f, bold = true)
            wrap(note, paint, RIGHT - LEFT).take(4).forEachIndexed { index, line -> text(line, LEFT, y + 15f + index * 12f, 8.5f) }
        }

        finishPage()
        return ByteArrayOutputStream().use { output ->
            pdf.writeTo(output)
            pdf.close()
            output.toByteArray()
        }
    }

    private fun startPage(pdf: PdfDocument, number: Int) = pdf.startPage(
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create(),
    )

    private fun wrap(value: String, paint: Paint, width: Float): List<String> {
        paint.textSize = 9f
        paint.typeface = Typeface.DEFAULT
        val result = mutableListOf<String>()
        var line = ""
        value.trim().split(Regex("\\s+")).forEach { word ->
            val candidate = if (line.isBlank()) word else "$line $word"
            if (paint.measureText(candidate) <= width || line.isBlank()) line = candidate
            else { result += line; line = word }
        }
        if (line.isNotBlank()) result += line
        return result.ifEmpty { listOf("") }
    }

    private fun cleanQuantity(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
