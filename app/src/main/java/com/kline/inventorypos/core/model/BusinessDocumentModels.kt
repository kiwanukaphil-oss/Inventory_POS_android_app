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
