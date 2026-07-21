package com.kline.inventorypos.data.catalog

import com.google.gson.Gson
import com.kline.inventorypos.core.model.Product
import com.kline.inventorypos.core.model.ProductTone
import com.kline.inventorypos.core.model.SampleProducts
import com.kline.inventorypos.data.local.CatalogVariantEntity
import com.kline.inventorypos.data.local.CategoryEntity
import com.kline.inventorypos.data.local.InventoryPosDao
import com.kline.inventorypos.data.network.CatalogVariantDto
import com.kline.inventorypos.data.network.InventoryPosApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import kotlin.math.roundToLong

interface CatalogRepository {
    fun observeProducts(branchId: String, query: String, categoryId: String?): Flow<List<Product>>
    fun observeCategories(branchId: String): Flow<List<Pair<String, String>>>
    fun observeSyncedAt(branchId: String): Flow<Long?>
    suspend fun refresh(branchId: String)
    suspend fun findByCode(branchId: String, code: String): Product?
}

class DefaultCatalogRepository(
    private val dao: InventoryPosDao,
    private val api: InventoryPosApi,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
) : CatalogRepository {
    override fun observeProducts(branchId: String, query: String, categoryId: String?): Flow<List<Product>> =
        dao.observeCatalog(branchId, query.trim(), categoryId).map { entities ->
            val counts = entities.groupingBy { it.productId }.eachCount()
            entities.map { it.toProduct(requiresVariantChoice = counts.getValue(it.productId) > 1) }
        }

    override fun observeCategories(branchId: String): Flow<List<Pair<String, String>>> =
        dao.observeCategories(branchId).map { categories -> categories.map { it.categoryId to it.name } }

    override fun observeSyncedAt(branchId: String): Flow<Long?> = dao.observeSyncedAt(branchId)

    override suspend fun refresh(branchId: String) {
        val now = System.currentTimeMillis()
        if (isDemo()) {
            val variants = SampleProducts.map { product ->
                CatalogVariantEntity(
                    branchId = branchId,
                    variantId = product.id,
                    productId = product.productId,
                    productName = product.name,
                    sku = product.sku,
                    attributesJson = gson.toJson(mapOf("selection" to product.variant)),
                    price = product.price,
                    stock = product.stock,
                    reorderLevel = product.reorderLevel,
                    barcode = when (product.id) {
                        "v1" -> "2000000000015"
                        "v2" -> "2000000000022"
                        else -> null
                    },
                    categoryId = product.category.lowercase(),
                    categoryName = product.category,
                    brandName = "K-Line",
                )
            }
            val categories = variants.distinctBy { it.categoryId }.mapNotNull { variant ->
                variant.categoryId?.let { CategoryEntity(branchId, it, variant.categoryName) }
            }
            dao.replaceCatalog(branchId, variants, categories, now)
            return
        }

        try {
            val variants = api.catalogVariants().data.map { it.toEntity(branchId, gson) }
            val categories = api.categories().data.map { CategoryEntity(branchId, it.id, it.name) }
            dao.replaceCatalog(branchId, variants, categories, now)
        } catch (error: HttpException) {
            throw CatalogException("Catalog refresh was rejected by the server.", error)
        } catch (error: IOException) {
            throw CatalogException("Offline · showing the last saved catalog", error)
        }
    }

    override suspend fun findByCode(branchId: String, code: String): Product? =
        dao.findByCode(branchId, code.trim())?.toProduct(false)
}

class CatalogException(message: String, cause: Throwable? = null) : Exception(message, cause)

private fun CatalogVariantDto.toEntity(branchId: String, gson: Gson) = CatalogVariantEntity(
    branchId = branchId,
    variantId = id,
    productId = productId,
    productName = productName,
    sku = sku,
    attributesJson = gson.toJson(variantAttributes.orEmpty()),
    price = price.roundToLong(),
    stock = quantityInStock?.toInt() ?: 0,
    reorderLevel = reorderLevel?.toInt() ?: 5,
    barcode = barcode,
    categoryId = categoryId,
    categoryName = categoryName ?: "Uncategorized",
    brandName = brandName,
)

private fun CatalogVariantEntity.toProduct(requiresVariantChoice: Boolean): Product {
    val attributes = runCatching {
        Gson().fromJson(attributesJson, Map::class.java).values.joinToString(" · ") { it.toString() }
    }.getOrDefault("").ifBlank { "Standard" }
    return Product(
        id = variantId,
        productId = productId,
        name = productName,
        sku = sku,
        price = price,
        stock = stock,
        category = categoryName,
        categoryId = categoryId,
        tone = ProductTone.entries[Math.floorMod(productId.hashCode(), ProductTone.entries.size)],
        variant = attributes,
        barcode = barcode,
        reorderLevel = reorderLevel,
        requiresVariantChoice = requiresVariantChoice,
    )
}
