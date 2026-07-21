package com.kline.inventorypos.core.model

data class GiftVoucherTemplate(
    val id: String,
    val name: String,
    val description: String?,
    val category: String?,
)

data class GiftVoucher(
    val id: String,
    val code: String,
    val templateName: String?,
    val recipientName: String,
    val fromName: String?,
    val message: String?,
    val phone: String?,
    val originalAmount: Long,
    val remainingBalance: Long,
    val issueDate: String?,
    val expiryDate: String?,
    val status: String,
    val paymentStatus: String,
    val createdBy: String?,
    val createdAt: String,
    val cancelReason: String?,
    val transactions: List<GiftVoucherTransaction> = emptyList(),
)

data class GiftVoucherTransaction(
    val id: String,
    val type: String,
    val amount: Long,
    val balanceAfter: Long,
    val paymentMethod: String?,
    val reference: String?,
    val notes: String?,
    val createdBy: String?,
    val date: String,
)

data class CreateGiftVoucher(
    val templateId: String,
    val recipientName: String,
    val fromName: String,
    val message: String,
    val phone: String,
    val amount: Long,
    val expiryDate: String,
    val notes: String,
)
