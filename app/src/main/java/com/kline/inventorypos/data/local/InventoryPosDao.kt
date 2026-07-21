package com.kline.inventorypos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryPosDao {
    @Query(
        """SELECT * FROM catalog_variants
           WHERE branch_id = :branchId
             AND (:categoryId IS NULL OR category_id = :categoryId)
             AND (:query = '' OR product_name LIKE '%' || :query || '%' COLLATE NOCASE
                  OR sku LIKE '%' || :query || '%' COLLATE NOCASE
                  OR IFNULL(barcode, '') LIKE '%' || :query || '%')
           ORDER BY product_name, sku""",
    )
    fun observeCatalog(branchId: String, query: String, categoryId: String?): Flow<List<CatalogVariantEntity>>

    @Query("SELECT * FROM catalog_variants WHERE branch_id = :branchId AND (barcode = :code OR sku = :code) LIMIT 1")
    suspend fun findByCode(branchId: String, code: String): CatalogVariantEntity?

    @Query("SELECT * FROM catalog_categories WHERE branch_id = :branchId ORDER BY name")
    fun observeCategories(branchId: String): Flow<List<CategoryEntity>>

    @Query("SELECT synced_at FROM catalog_sync WHERE branch_id = :branchId")
    fun observeSyncedAt(branchId: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(items: List<CatalogVariantEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSync(sync: CatalogSyncEntity)

    @Query("DELETE FROM catalog_variants WHERE branch_id = :branchId")
    suspend fun deleteVariants(branchId: String)

    @Query("DELETE FROM catalog_categories WHERE branch_id = :branchId")
    suspend fun deleteCategories(branchId: String)

    @Transaction
    suspend fun replaceCatalog(
        branchId: String,
        variants: List<CatalogVariantEntity>,
        categories: List<CategoryEntity>,
        syncedAt: Long,
    ) {
        deleteVariants(branchId)
        deleteCategories(branchId)
        insertVariants(variants)
        insertCategories(categories)
        insertSync(CatalogSyncEntity(branchId, syncedAt))
    }

    @Query("SELECT * FROM cart_lines WHERE session_key = :sessionKey ORDER BY product_name, variant")
    fun observeCart(sessionKey: String): Flow<List<CartLineEntity>>

    @Query("SELECT * FROM cart_lines WHERE session_key = :sessionKey AND variant_id = :variantId")
    suspend fun getCartLine(sessionKey: String, variantId: String): CartLineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCartLine(line: CartLineEntity)

    @Query("DELETE FROM cart_lines WHERE session_key = :sessionKey AND variant_id = :variantId")
    suspend fun deleteCartLine(sessionKey: String, variantId: String)

    @Query("DELETE FROM cart_lines WHERE session_key = :sessionKey")
    suspend fun clearCart(sessionKey: String)

    @Query("SELECT * FROM cart_lines WHERE session_key = :sessionKey ORDER BY product_name")
    suspend fun getCart(sessionKey: String): List<CartLineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putHeldCart(held: HeldCartEntity)

    @Query("SELECT * FROM held_carts WHERE session_key = :sessionKey ORDER BY held_at DESC")
    fun observeHeldCarts(sessionKey: String): Flow<List<HeldCartEntity>>

    @Query("SELECT * FROM held_carts WHERE session_key = :sessionKey ORDER BY held_at DESC")
    suspend fun getHeldCarts(sessionKey: String): List<HeldCartEntity>

    @Query("DELETE FROM held_carts WHERE session_key = :sessionKey AND sync_state = 'synced'")
    suspend fun deleteSyncedHeldCarts(sessionKey: String)

    @Query("SELECT * FROM held_carts WHERE id = :id")
    suspend fun getHeldCart(id: String): HeldCartEntity?

    @Query("DELETE FROM held_carts WHERE id = :id")
    suspend fun deleteHeldCart(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCheckoutAttempt(attempt: CheckoutAttemptEntity)

    @Query("SELECT * FROM checkout_attempts WHERE session_key = :sessionKey ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestCheckoutAttempt(sessionKey: String): CheckoutAttemptEntity?
}
