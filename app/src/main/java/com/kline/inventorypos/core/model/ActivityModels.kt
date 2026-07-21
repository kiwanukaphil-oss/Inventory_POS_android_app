package com.kline.inventorypos.core.model

data class SaleSummary(
    val id: String,
    val receiptNumber: String,
    val customerName: String?,
    val total: Long,
    val paymentMethod: String,
    val status: String,
    val saleDate: String,
    val cashierName: String,
    val staffName: String?,
    val itemCount: Int,
    val returnStatus: String,
    val totalReturned: Long,
)
