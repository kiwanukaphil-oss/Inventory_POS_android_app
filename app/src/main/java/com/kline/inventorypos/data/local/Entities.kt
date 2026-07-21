package com.kline.inventorypos.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "catalog_variants", primaryKeys = ["branch_id", "variant_id"])
data class CatalogVariantEntity(
    @ColumnInfo(name = "branch_id") val branchId: String,
    @ColumnInfo(name = "variant_id") val variantId: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "product_name") val productName: String,
    val sku: String,
    @ColumnInfo(name = "attributes_json") val attributesJson: String,
    val price: Long,
    val stock: Int,
    @ColumnInfo(name = "reorder_level") val reorderLevel: Int,
    val barcode: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "brand_name") val brandName: String?,
)

@Entity(tableName = "catalog_categories", primaryKeys = ["branch_id", "category_id"])
data class CategoryEntity(
    @ColumnInfo(name = "branch_id") val branchId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val name: String,
)

@Entity(tableName = "catalog_sync", primaryKeys = ["branch_id"])
data class CatalogSyncEntity(
    @ColumnInfo(name = "branch_id") val branchId: String,
    @ColumnInfo(name = "synced_at") val syncedAt: Long,
)

@Entity(tableName = "cart_lines", primaryKeys = ["session_key", "variant_id"])
data class CartLineEntity(
    @ColumnInfo(name = "session_key") val sessionKey: String,
    @ColumnInfo(name = "variant_id") val variantId: String,
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "product_name") val productName: String,
    val sku: String,
    val price: Long,
    val stock: Int,
    val category: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val variant: String,
    val barcode: String?,
    @ColumnInfo(name = "reorder_level") val reorderLevel: Int,
    val quantity: Int,
)

@Entity(tableName = "held_carts", primaryKeys = ["id"])
data class HeldCartEntity(
    val id: String,
    @ColumnInfo(name = "session_key") val sessionKey: String,
    @ColumnInfo(name = "items_json") val itemsJson: String,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "customer_id") val customerId: String?,
    @ColumnInfo(name = "customer_name") val customerName: String?,
    val notes: String?,
    @ColumnInfo(name = "held_at") val heldAt: Long,
    @ColumnInfo(name = "server_id") val serverId: String?,
    @ColumnInfo(name = "sync_state") val syncState: String,
)

@Entity(tableName = "checkout_attempts", primaryKeys = ["attempt_id"])
data class CheckoutAttemptEntity(
    @ColumnInfo(name = "attempt_id") val attemptId: String,
    @ColumnInfo(name = "session_key") val sessionKey: String,
    @ColumnInfo(name = "request_json") val requestJson: String,
    val status: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "receipt_json") val receiptJson: String?,
    val message: String?,
)
