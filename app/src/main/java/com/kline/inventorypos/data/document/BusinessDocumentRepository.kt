package com.kline.inventorypos.data.document

import android.graphics.Bitmap
import com.google.gson.Gson
import com.kline.inventorypos.core.model.BusinessDocument
import com.kline.inventorypos.core.model.BusinessDocumentItem
import com.kline.inventorypos.core.model.DerivedDocument
import com.kline.inventorypos.core.model.DocumentDraftLine
import com.kline.inventorypos.core.model.conversionTargetType
import com.kline.inventorypos.core.model.requireConversionAllowed
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.BusinessDocumentDto
import com.kline.inventorypos.data.network.BusinessDocumentItemRequest
import com.kline.inventorypos.data.network.ConvertDocumentRequest
import com.kline.inventorypos.data.network.DocumentStatusRequest
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.SaveBusinessDocumentRequest
import com.kline.inventorypos.data.network.VoidDocumentRequest
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToLong
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

interface BusinessDocumentRepository {
    suspend fun list(type: String?, status: String?, search: String): List<BusinessDocument>
    suspend fun detail(id: String): BusinessDocument
    suspend fun save(id: String?, type: String, billTo: String, address: String, date: String, validUntil: String?, dueDate: String?, paymentMethod: String?, paymentReference: String, notes: String, lines: List<DocumentDraftLine>): BusinessDocument
    suspend fun transition(id: String, status: String): BusinessDocument
    suspend fun void(id: String, reason: String): BusinessDocument
    suspend fun convert(id: String, paymentMethod: String?, paymentReference: String): BusinessDocument
    suspend fun pdf(document: BusinessDocument): GeneratedDocumentPdf
    suspend fun email(document: BusinessDocument, recipient: String, cc: List<String>, message: String): BusinessDocument
}

data class GeneratedDocumentPdf(val filename: String, val bytes: ByteArray)

class DocumentMutationUncertainException : IllegalStateException("The connection ended before confirmation. Refresh the document and linked records before attempting another change.")

class DefaultBusinessDocumentRepository(
    private val api: InventoryPosApi,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
    private val documentLogo: Bitmap? = null,
) : BusinessDocumentRepository {
    private val demo = mutableListOf(demoDocument("doc-1", "quotation", "QT-01042", "sent", "Acacia Facilities Ltd", 1_850_000), demoDocument("doc-2", "invoice", "INV-00482", "draft", "Nile Corporate Wear", 3_420_000))
    private val conversionMutex = Mutex()

    override suspend fun list(type: String?, status: String?, search: String): List<BusinessDocument> {
        if (isDemo()) return demo.filter { (type == null || it.type == type) && (status == null || it.status == status) && (search.isBlank() || it.number.contains(search, true) || it.billToName.contains(search, true)) }
        return read { api.businessDocuments(type, status, search.trim().takeIf(String::isNotBlank)).data }.map { it.toDomain() }
    }
    override suspend fun detail(id: String): BusinessDocument = if (isDemo()) demo.firstOrNull { it.id == id } ?: error("Document not found.") else read { api.businessDocument(id).data }.toDomain()

    override suspend fun save(id: String?, type: String, billTo: String, address: String, date: String, validUntil: String?, dueDate: String?, paymentMethod: String?, paymentReference: String, notes: String, lines: List<DocumentDraftLine>): BusinessDocument {
        require(type in setOf("quotation", "invoice", "receipt")) { "Select a document type." }; require(billTo.trim().isNotEmpty()) { "Bill-to name is required." }; require(runCatching { LocalDate.parse(date) }.isSuccess) { "Select a valid document date." }; require(lines.isNotEmpty()) { "Add at least one line item." }; require(lines.all { it.description.isNotBlank() && it.quantity > 0 && it.unitPrice >= 0 }) { "Each line needs a description, positive quantity, and valid price." }
        val request = SaveBusinessDocumentRequest(type, billTo.trim(), address.clean(), date, validUntil, dueDate, paymentMethod, paymentReference.clean(), notes.clean(), lines.map { BusinessDocumentItemRequest(it.description.trim(), it.quantity, it.unitPrice) })
        if (isDemo()) {
            val total = lines.sumOf { (it.quantity * it.unitPrice).roundToLong() }; val existing = id?.let { key -> demo.firstOrNull { it.id == key } }
            val saved = BusinessDocument(existing?.id ?: "doc-${UUID.randomUUID()}", type, existing?.number ?: "${type.take(3).uppercase()}-${1000 + demo.size}", existing?.status ?: if (type == "receipt") "issued" else "draft", request.billToName, request.billToAddress, date, validUntil, dueDate, paymentMethod, request.paymentReference, total, total, request.notes, null, null, "Philip Kiwanuka", existing?.createdAt ?: Instant.now().toString(), lines.mapIndexed { index, line -> BusinessDocumentItem("line-$index", line.description, line.quantity, line.unitPrice, (line.quantity * line.unitPrice).roundToLong()) }, emptyList())
            if (existing == null) demo.add(0, saved) else demo[demo.indexOf(existing)] = saved; return saved
        }
        return mutate { if (id == null) api.createBusinessDocument(request).data else api.updateBusinessDocument(id, request).data }.toDomain()
    }
    override suspend fun transition(id: String, status: String): BusinessDocument = updateDemoOrLive(
        id,
        live = { mutate { api.transitionBusinessDocument(id, DocumentStatusRequest(status)).data }.toDomain() },
        change = { it.copy(status = status) },
    )
    override suspend fun void(id: String, reason: String): BusinessDocument { require(reason.trim().isNotBlank()) { "A void reason is required." }; return updateDemoOrLive(id, { mutate { api.voidBusinessDocument(id, VoidDocumentRequest(reason.trim())).data }.toDomain() }, { it.copy(status = "void", voidReason = reason.trim()) }) }
    override suspend fun convert(id: String, paymentMethod: String?, paymentReference: String): BusinessDocument = conversionMutex.withLock {
        // Re-read inside the lock. A second rapid request then observes the first
        // conversion's linked document instead of creating another target.
        val source = detail(id)
        source.requireConversionAllowed()
        val target = requireNotNull(source.conversionTargetType())
        if (isDemo()) {
            val created = source.copy(
                id = "doc-${UUID.randomUUID()}",
                type = target,
                number = "${target.take(3).uppercase()}-${1000 + demo.size}",
                status = if (target == "receipt") "issued" else "draft",
                sourceNumber = source.number,
                paymentMethod = paymentMethod,
                paymentReference = paymentReference.clean(),
                derived = emptyList(),
            )
            demo[demo.indexOf(source)] = source.copy(
                status = "converted",
                derived = source.derived + DerivedDocument(created.id, target, created.number, created.status),
            )
            demo.add(0, created)
            created
        } else {
            mutate {
                api.convertBusinessDocument(
                    id,
                    ConvertDocumentRequest(
                        LocalDate.now().toString(),
                        paymentMethod = paymentMethod,
                        paymentReference = paymentReference.clean(),
                    ),
                ).data
            }.toDomain()
        }
    }

    override suspend fun pdf(document: BusinessDocument): GeneratedDocumentPdf {
        val store = if (isDemo()) demoStoreConfig() else read { api.storeConfig().data }
        val bytes = BusinessDocumentPdfRenderer.render(document, store, documentLogo)
        require(bytes.isNotEmpty()) { "The PDF could not be generated." }
        return GeneratedDocumentPdf(document.pdfFilename(), bytes)
    }

    override suspend fun email(document: BusinessDocument, recipient: String, cc: List<String>, message: String): BusinessDocument {
        val to = recipient.trim()
        require(EMAIL.matches(to)) { "Enter a valid recipient email address." }
        val copies = cc.map(String::trim).filter(String::isNotBlank).filterNot { it.equals(to, true) }.distinctBy { it.lowercase() }
        require(copies.size <= 10 && copies.all(EMAIL::matches)) { "Enter no more than 10 valid CC email addresses." }
        require(document.status != "void") { "Void documents cannot be emailed." }
        if (isDemo()) {
            val updated = document.copy(
                status = if (document.status == "draft") "sent" else document.status,
                emailedTo = to,
                emailedCc = copies,
                emailedAt = Instant.now().toString(),
            )
            demo.indexOfFirst { it.id == document.id }.takeIf { it >= 0 }?.let { demo[it] = updated }
            return updated
        }
        val pdf = pdf(document)
        val fields = buildMap {
            put("to", to.toRequestBody(TEXT_MEDIA_TYPE))
            message.trim().takeIf(String::isNotBlank)?.let { put("message", it.toRequestBody(TEXT_MEDIA_TYPE)) }
            if (copies.isNotEmpty()) put("cc", gson.toJson(copies).toRequestBody(TEXT_MEDIA_TYPE))
        }
        val pdfPart = MultipartBody.Part.createFormData("pdf", pdf.filename, pdf.bytes.toRequestBody(PDF_MEDIA_TYPE))
        return mutate { api.emailBusinessDocument(document.id, fields, pdfPart).data }.toDomain()
    }
    private suspend fun updateDemoOrLive(id: String, live: suspend () -> BusinessDocument, change: (BusinessDocument) -> BusinessDocument): BusinessDocument { if (!isDemo()) return live(); val old = demo.first { it.id == id }; return change(old).also { demo[demo.indexOf(old)] = it } }
    private suspend fun <T> read(block: suspend () -> T): T = try { block() } catch (e: HttpException) { throw e.failure("Documents are unavailable.") } catch (e: IOException) { throw IllegalStateException("Documents are unavailable while offline.", e) }
    private suspend fun <T> mutate(block: suspend () -> T): T = try { block() } catch (e: HttpException) { throw e.failure("The document action was rejected.") } catch (_: IOException) { throw DocumentMutationUncertainException() }
    private fun HttpException.failure(fallback: String) = IllegalStateException(response()?.errorBody()?.string()?.let { runCatching { gson.fromJson(it, ApiError::class.java).message }.getOrNull() } ?: fallback, this)
}

private fun BusinessDocumentDto.toDomain() = BusinessDocument(id, documentType, documentNumber, status, billToName, billToAddress, documentDate.take(10), validUntil?.take(10), dueDate?.take(10), paymentMethod, paymentReference, subtotal.roundToLong(), total.roundToLong(), notes, sourceDocumentNumber, voidReason, createdByName, createdAt, items.orEmpty().map { BusinessDocumentItem(it.id, it.description, it.quantity, it.unitPrice.roundToLong(), it.lineTotal.roundToLong()) }, derivedDocuments.orEmpty().map { DerivedDocument(it.id, it.documentType, it.documentNumber, it.status) }, customerEmail, emailedTo, emailedCc.orEmpty(), emailedAt)
private fun String.clean() = trim().takeIf(String::isNotBlank)
private fun demoDocument(id: String, type: String, number: String, status: String, customer: String, total: Long) = BusinessDocument(id, type, number, status, customer, "Kampala", LocalDate.now().toString(), LocalDate.now().plusDays(14).toString(), null, null, null, total, total, "Corporate uniform order", null, null, "Philip Kiwanuka", Instant.now().toString(), listOf(BusinessDocumentItem("l-$id", "Premium cotton shirts", 10.0, total / 10, total)), emptyList())
private fun BusinessDocument.pdfFilename() = number.replace(Regex("[^A-Za-z0-9._-]"), "-") + ".pdf"
private fun demoStoreConfig() = com.kline.inventorypos.data.network.StoreConfigDto(
    storeName = "K-Line Men",
    addressLine1 = "",
    city = "Kampala",
    country = "Uganda",
    phone = null,
    email = null,
    currencyCode = "UGX",
    taxEnabled = null,
    taxLabel = null,
    taxRegistrationNumber = null,
    returnWindowDays = null,
    printerBridgeUrl = null,
    defaultReceiptAction = null,
)

private val EMAIL = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)
private val TEXT_MEDIA_TYPE = "text/plain; charset=utf-8".toMediaType()
private val PDF_MEDIA_TYPE = "application/pdf".toMediaType()
