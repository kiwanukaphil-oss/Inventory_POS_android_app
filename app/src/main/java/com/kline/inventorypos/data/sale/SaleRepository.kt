package com.kline.inventorypos.data.sale

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kline.inventorypos.core.model.CartLine
import com.kline.inventorypos.core.model.CustomerSummary
import com.kline.inventorypos.core.model.HeldCart
import com.kline.inventorypos.core.model.Product
import com.kline.inventorypos.core.model.ProductTone
import com.kline.inventorypos.core.model.PromotionSummary
import com.kline.inventorypos.data.local.CartLineEntity
import com.kline.inventorypos.data.local.HeldCartEntity
import com.kline.inventorypos.data.local.InventoryPosDao
import com.kline.inventorypos.data.network.HeldCartItemDto
import com.kline.inventorypos.data.network.HoldCartRequest
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.PromotionEvaluationRequest
import com.kline.inventorypos.data.network.PromotionItemRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToLong

interface SaleRepository {
    fun observeCart(sessionKey: String): Flow<List<CartLine>>
    fun observeHeldCarts(sessionKey: String): Flow<List<HeldCart>>
    suspend fun add(sessionKey: String, product: Product)
    suspend fun increase(sessionKey: String, variantId: String)
    suspend fun decrease(sessionKey: String, variantId: String)
    suspend fun clear(sessionKey: String)
    suspend fun refreshHeldCarts(sessionKey: String)
    suspend fun hold(sessionKey: String, customer: CustomerSummary?, notes: String?): String
    suspend fun recall(sessionKey: String, id: String)
    suspend fun removeHeld(id: String)
    suspend fun searchCustomers(query: String): List<CustomerSummary>
    suspend fun evaluatePromotions(lines: List<CartLine>, customerId: String?): List<PromotionSummary>
}

class DefaultSaleRepository(
    private val dao: InventoryPosDao,
    private val api: InventoryPosApi,
    private val gson: Gson,
    private val isDemo: () -> Boolean,
) : SaleRepository {
    override fun observeCart(sessionKey: String): Flow<List<CartLine>> =
        dao.observeCart(sessionKey).map { rows -> rows.map(CartLineEntity::toDomain) }

    override fun observeHeldCarts(sessionKey: String): Flow<List<HeldCart>> =
        dao.observeHeldCarts(sessionKey).map { rows ->
            rows.map { HeldCart(it.id, it.itemCount, it.customerName, it.notes, it.heldAt, it.syncState == "pending") }
        }

    override suspend fun add(sessionKey: String, product: Product) {
        val existing = dao.getCartLine(sessionKey, product.id)
        val next = (existing?.quantity ?: 0) + 1
        check(product.stock > 0 && next <= product.stock) { "Only ${product.stock} available at this branch." }
        dao.putCartLine(product.toEntity(sessionKey, next))
    }

    override suspend fun increase(sessionKey: String, variantId: String) {
        val existing = dao.getCartLine(sessionKey, variantId) ?: return
        check(existing.stock > 0 && existing.quantity < existing.stock) { "Only ${existing.stock} available at this branch." }
        dao.putCartLine(existing.copy(quantity = existing.quantity + 1))
    }

    override suspend fun decrease(sessionKey: String, variantId: String) {
        val existing = dao.getCartLine(sessionKey, variantId) ?: return
        if (existing.quantity <= 1) dao.deleteCartLine(sessionKey, variantId)
        else dao.putCartLine(existing.copy(quantity = existing.quantity - 1))
    }

    override suspend fun clear(sessionKey: String) = dao.clearCart(sessionKey)

    override suspend fun refreshHeldCarts(sessionKey: String) {
        if (isDemo()) return
        val remote = api.heldCarts().data
        dao.deleteSyncedHeldCarts(sessionKey)
        remote.forEach { held ->
            val lines = held.cartItems.map { item ->
                CartLineEntity(
                    sessionKey = sessionKey,
                    variantId = item.variantId,
                    productId = item.variantId,
                    productName = item.productName,
                    sku = item.sku,
                    price = item.price,
                    stock = item.stockQuantity,
                    category = "Held sale",
                    categoryId = null,
                    variant = item.variantAttributes.values.joinToString(" · ").ifBlank { "Standard" },
                    barcode = null,
                    reorderLevel = 0,
                    quantity = item.quantity,
                )
            }
            dao.putHeldCart(
                HeldCartEntity(
                    id = held.id,
                    sessionKey = sessionKey,
                    itemsJson = gson.toJson(lines),
                    itemCount = lines.sumOf { it.quantity },
                    customerId = held.customerId,
                    customerName = held.customerName,
                    notes = held.notes,
                    heldAt = held.heldAt?.let { value ->
                        runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
                    } ?: System.currentTimeMillis(),
                    serverId = held.id,
                    syncState = "synced",
                ),
            )
        }
    }

    override suspend fun hold(sessionKey: String, customer: CustomerSummary?, notes: String?): String {
        val lines = dao.getCart(sessionKey)
        check(lines.isNotEmpty()) { "Add an item before holding this sale." }
        check(dao.getHeldCarts(sessionKey).size < 5) { "You can keep up to 5 held sales." }
        val localId = UUID.randomUUID().toString()
        var serverId: String? = null
        var syncState = "pending"
        if (!isDemo()) {
            runCatching {
                api.holdCart(
                    HoldCartRequest(
                        customerId = customer?.id,
                        cartItems = lines.map { line ->
                            HeldCartItemDto(
                                variantId = line.variantId,
                                productName = line.productName,
                                sku = line.sku,
                                variantAttributes = mapOf("selection" to line.variant),
                                price = line.price,
                                quantity = line.quantity,
                                stockQuantity = line.stock,
                            )
                        },
                        notes = notes,
                    ),
                ).data.id
            }.onSuccess { serverId = it; syncState = "synced" }
        } else {
            syncState = "local"
        }
        dao.putHeldCart(
            HeldCartEntity(
                id = localId,
                sessionKey = sessionKey,
                itemsJson = gson.toJson(lines),
                itemCount = lines.sumOf { it.quantity },
                customerId = customer?.id,
                customerName = customer?.name,
                notes = notes?.takeIf(String::isNotBlank),
                heldAt = System.currentTimeMillis(),
                serverId = serverId,
                syncState = syncState,
            ),
        )
        dao.clearCart(sessionKey)
        return syncState
    }

    override suspend fun recall(sessionKey: String, id: String) {
        val held = dao.getHeldCart(id) ?: return
        val type = object : TypeToken<List<CartLineEntity>>() {}.type
        val lines: List<CartLineEntity> = gson.fromJson(held.itemsJson, type)
        dao.clearCart(sessionKey)
        lines.forEach { dao.putCartLine(it.copy(sessionKey = sessionKey)) }
        held.serverId?.let { serverId -> runCatching { api.recallHeldCart(serverId) } }
        dao.deleteHeldCart(id)
    }

    override suspend fun removeHeld(id: String) {
        val held = dao.getHeldCart(id) ?: return
        held.serverId?.let { serverId -> runCatching { api.deleteHeldCart(serverId) } }
        dao.deleteHeldCart(id)
    }

    override suspend fun searchCustomers(query: String): List<CustomerSummary> {
        if (query.isBlank()) return emptyList()
        if (isDemo()) {
            return DemoCustomers.filter { customer ->
                customer.name.contains(query, true) || customer.phone?.contains(query) == true
            }
        }
        return api.searchCustomers(query.trim()).data.map { customer ->
            CustomerSummary(
                id = customer.id,
                name = customer.companyName?.takeIf(String::isNotBlank)
                    ?: listOfNotNull(customer.firstName, customer.lastName).joinToString(" "),
                phone = customer.phone,
                email = customer.email,
                loyaltyPoints = customer.loyaltyPoints?.toInt() ?: 0,
            )
        }
    }

    override suspend fun evaluatePromotions(lines: List<CartLine>, customerId: String?): List<PromotionSummary> {
        if (lines.isEmpty()) return emptyList()
        if (isDemo()) {
            val units = lines.sumOf { it.quantity }
            return if (units >= 3) listOf(PromotionSummary("Three-item basket · 5%", lines.sumOf { it.lineTotal } / 20)) else emptyList()
        }
        val response = api.evaluatePromotions(
            PromotionEvaluationRequest(
                items = lines.map { line ->
                    PromotionItemRequest(
                        variantId = line.product.id,
                        productId = line.product.productId,
                        categoryId = line.product.categoryId,
                        price = line.product.price,
                        quantity = line.quantity,
                    )
                },
                subtotal = lines.sumOf { it.lineTotal },
                customerId = customerId,
            ),
        ).data
        return response.applied.orEmpty().map {
            PromotionSummary(
                name = it.name,
                savings = it.savings?.roundToLong() ?: 0L,
                applicableVariantIds = it.applicableVariantIds.orEmpty().toSet(),
            )
        }
    }

    private companion object {
        val DemoCustomers = listOf(
            CustomerSummary("c1", "Amina Nakato", "+256 700 555 014", "amina@example.com", 1_240),
            CustomerSummary("c2", "David Okello", "+256 772 555 209", null, 420),
            CustomerSummary("c3", "Nile Corporate Wear", "+256 312 555 880", "buying@nile.example", 0),
        )
    }
}

private fun Product.toEntity(sessionKey: String, quantity: Int) = CartLineEntity(
    sessionKey = sessionKey,
    variantId = id,
    productId = productId,
    productName = name,
    sku = sku,
    price = price,
    stock = stock,
    category = category,
    categoryId = categoryId,
    variant = variant,
    barcode = barcode,
    reorderLevel = reorderLevel,
    quantity = quantity,
)

private fun CartLineEntity.toDomain() = CartLine(
    product = Product(
        id = variantId,
        productId = productId,
        name = productName,
        sku = sku,
        price = price,
        stock = stock,
        category = category,
        categoryId = categoryId,
        tone = ProductTone.entries[Math.floorMod(productId.hashCode(), ProductTone.entries.size)],
        variant = variant,
        barcode = barcode,
        reorderLevel = reorderLevel,
    ),
    quantity = quantity,
)
