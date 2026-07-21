package com.kline.inventorypos.core.model

data class BusinessDocument(
    val id: String,
    val type: String,
    val number: String,
    val status: String,
    val billToName: String,
    val billToAddress: String?,
    val date: String,
    val validUntil: String?,
    val dueDate: String?,
    val paymentMethod: String?,
    val paymentReference: String?,
    val subtotal: Long,
    val total: Long,
    val notes: String?,
    val sourceNumber: String?,
    val voidReason: String?,
    val createdBy: String?,
    val createdAt: String,
    val items: List<BusinessDocumentItem>,
    val derived: List<DerivedDocument>,
    val customerEmail: String? = null,
    val emailedTo: String? = null,
    val emailedCc: List<String> = emptyList(),
    val emailedAt: String? = null,
)

data class BusinessDocumentItem(val id: String, val description: String, val quantity: Double, val unitPrice: Long, val lineTotal: Long)
data class DerivedDocument(val id: String, val type: String, val number: String, val status: String)
data class DocumentDraftLine(val description: String, val quantity: Double, val unitPrice: Long)

internal fun BusinessDocument.conversionTargetType(): String? = when (type) {
    "quotation" -> "invoice"
    "invoice" -> "receipt"
    else -> null
}

internal fun BusinessDocument.linkedConversion(): DerivedDocument? {
    val target = conversionTargetType() ?: return null
    return derived.firstOrNull { it.type == target }
}

internal fun BusinessDocument.conversionBlockReason(): String? {
    val target = conversionTargetType() ?: return "Receipts cannot be converted further."
    linkedConversion()?.let { linked ->
        return "${target.replaceFirstChar(Char::uppercase)} ${linked.number} already exists for $number."
    }
    val allowedStatuses = when (type) {
        "quotation" -> setOf("draft", "sent", "accepted")
        "invoice" -> setOf("draft", "sent", "paid")
        else -> emptySet()
    }
    return if (status in allowedStatuses) null else "$number can no longer be converted."
}

internal fun BusinessDocument.canConvert(): Boolean = conversionBlockReason() == null

internal fun BusinessDocument.requireConversionAllowed() {
    conversionBlockReason()?.let { throw IllegalStateException(it) }
}
