package com.kline.inventorypos.core.model

enum class ProductTone { SKY, NAVY, SAND, WINE, FOREST, CHARCOAL }

data class Product(
    val id: String,
    val productId: String,
    val name: String,
    val sku: String,
    val price: Long,
    val stock: Int,
    val category: String,
    val categoryId: String? = null,
    val tone: ProductTone,
    val variant: String,
    val barcode: String? = null,
    val reorderLevel: Int = 5,
    val requiresVariantChoice: Boolean = false,
)

data class CartLine(
    val product: Product,
    val quantity: Int,
) {
    val lineTotal: Long get() = product.price * quantity
}

data class StockAlert(
    val product: Product,
    val reorderLevel: Int,
)

val SampleProducts = listOf(
    Product("v1", "p1", "Premium Linen Shirt", "LIN-SKY-M", 165_000, 8, "Shirts", tone = ProductTone.SKY, variant = "Sky blue · M", requiresVariantChoice = true),
    Product("v2", "p2", "Oxford Cotton Shirt", "OXF-NV-L", 145_000, 2, "Shirts", tone = ProductTone.NAVY, variant = "Navy · L"),
    Product("v3", "p3", "Tailored Chino Trouser", "CHI-ST-34", 180_000, 12, "Trousers", tone = ProductTone.SAND, variant = "Stone · 34"),
    Product("v4", "p4", "Classic Piqué Polo", "POL-BG-M", 95_000, 4, "Shirts", tone = ProductTone.WINE, variant = "Burgundy · M"),
    Product("v5", "p5", "Field Jacket", "JKT-OL-M", 285_000, 6, "Jackets", tone = ProductTone.FOREST, variant = "Olive · M"),
    Product("v6", "p6", "Leather Dress Belt", "BLT-BK-36", 75_000, 15, "Accessories", tone = ProductTone.CHARCOAL, variant = "Black · 36"),
)

val InitialCart = listOf(
    CartLine(SampleProducts[0], 1),
    CartLine(SampleProducts[2], 1),
    CartLine(SampleProducts[3], 1),
)
