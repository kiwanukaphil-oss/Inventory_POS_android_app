package com.kline.inventorypos.feature.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kline.inventorypos.core.common.formatUgx
import com.kline.inventorypos.core.designsystem.Amber50
import com.kline.inventorypos.core.designsystem.Amber700
import com.kline.inventorypos.core.designsystem.Error50
import com.kline.inventorypos.core.designsystem.Error700
import com.kline.inventorypos.core.designsystem.FocusedHeader
import com.kline.inventorypos.core.designsystem.MoneyFontFamily
import com.kline.inventorypos.core.designsystem.MoneyGreen700
import com.kline.inventorypos.core.designsystem.PosCard
import com.kline.inventorypos.core.designsystem.Primary50
import com.kline.inventorypos.core.designsystem.Primary700
import com.kline.inventorypos.core.designsystem.ProductVisual
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.model.PricingVariant
import com.kline.inventorypos.core.model.Product

private enum class CatalogStockFilter(val label: String) {
    All("All stock"),
    Low("Low stock"),
    Out("Out of stock"),
}

@Composable
fun ProductCatalogScreen(
    state: InventoryUiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onRefresh: () -> Unit,
    onManagePrices: () -> Unit,
    onPrintLabels: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var stockFilter by rememberSaveable { mutableStateOf(CatalogStockFilter.All) }
    LaunchedEffect(Unit) { onLoad() }

    val categories = state.products.map(Product::category).filter(String::isNotBlank).distinct().sorted()
    val pricing = state.pricingVariants.associateBy(PricingVariant::id)
    val visible = state.products.filter { product ->
        (query.isBlank() || listOf(product.name, product.sku, product.variant, product.barcode.orEmpty())
            .any { it.contains(query.trim(), ignoreCase = true) }) &&
            (category == null || product.category == category) &&
            when (stockFilter) {
                CatalogStockFilter.All -> true
                CatalogStockFilter.Low -> product.stock in 1..product.reorderLevel
                CatalogStockFilter.Out -> product.stock <= 0
            }
    }
    val productCount = state.products.map(Product::productId).distinct().size
    val lowStockCount = state.products.count { it.stock in 1..it.reorderLevel }
    val outOfStockCount = state.products.count { it.stock <= 0 }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Product catalog", "Products, variants and stock", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            placeholder = { Text("Search product, SKU or barcode") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CatalogMetric("Products", productCount.toString(), Modifier.weight(1f))
            CatalogMetric("Variants", state.products.size.toString(), Modifier.weight(1f))
            CatalogMetric("Low / out", "$lowStockCount / $outOfStockCount", Modifier.weight(1f), attention = lowStockCount + outOfStockCount > 0)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            CatalogStockFilter.entries.forEach { option ->
                FilterChip(selected = stockFilter == option, onClick = { stockFilter = option }, label = { Text(option.label) })
            }
        }
        if (categories.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All categories") })
                categories.forEach { name ->
                    FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) })
                }
            }
        }
        when {
            state.refreshing && state.products.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            visible.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(if (state.products.isEmpty()) "No catalog products are available." else "No products match these filters.", color = Slate500)
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(visible, key = Product::id) { product -> CatalogProductRow(product, pricing[product.id]) }
            }
        }
        Surface(shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onManagePrices, modifier = Modifier.weight(1f).height(48.dp), enabled = state.canEditPrices) {
                        Icon(Icons.Outlined.LocalOffer, contentDescription = null)
                        Text("  Prices")
                    }
                    OutlinedButton(onClick = onPrintLabels, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Outlined.Print, contentDescription = null)
                        Text("  Labels")
                    }
                }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), enabled = !state.refreshing) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Text("  Refresh catalog")
                }
            }
        }
    }
}

@Composable
private fun CatalogMetric(label: String, value: String, modifier: Modifier, attention: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = if (attention) Amber50 else MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(10.dp)) {
            Text(value, fontWeight = FontWeight.Bold, color = if (attention) Amber700 else MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Slate500, maxLines = 1)
        }
    }
}

@Composable
private fun CatalogProductRow(product: Product, pricing: PricingVariant?) {
    val stockColor = when {
        product.stock <= 0 -> Error700
        product.stock <= product.reorderLevel -> Amber700
        else -> MoneyGreen700
    }
    val stockBackground = when {
        product.stock <= 0 -> Error50
        product.stock <= product.reorderLevel -> Amber50
        else -> Primary50
    }
    PosCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductVisual(product.tone, Modifier.size(48.dp), iconSize = 26.dp)
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text(product.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${product.sku} · ${product.variant}", style = MaterialTheme.typography.bodySmall, color = Slate500, maxLines = 1)
                Text(
                    listOfNotNull(product.category.takeIf(String::isNotBlank), product.barcode?.let { "EAN $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate500,
                    maxLines = 1,
                )
                pricing?.marginPercent()?.let { margin ->
                    Text("Margin $margin%", style = MaterialTheme.typography.labelSmall, color = if (margin < 20) Amber700 else MoneyGreen700)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(formatUgx(pricing?.price ?: product.price), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
                Surface(color = stockBackground, shape = RoundedCornerShape(20.dp)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = stockColor, modifier = Modifier.size(14.dp))
                        Text(" ${product.stock} in stock", style = MaterialTheme.typography.labelSmall, color = stockColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100))
    }
}
