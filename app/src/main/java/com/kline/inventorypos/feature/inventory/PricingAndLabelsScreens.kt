package com.kline.inventorypos.feature.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kline.inventorypos.core.common.AmountGroupingVisualTransformation
import com.kline.inventorypos.core.common.formatUgx
import com.kline.inventorypos.core.common.parseAmountInput
import com.kline.inventorypos.core.common.sanitizeAmountInput
import com.kline.inventorypos.core.designsystem.Amber50
import com.kline.inventorypos.core.designsystem.Amber700
import com.kline.inventorypos.core.designsystem.FocusedHeader
import com.kline.inventorypos.core.designsystem.MoneyFontFamily
import com.kline.inventorypos.core.designsystem.MoneyGreen50
import com.kline.inventorypos.core.designsystem.MoneyGreen700
import com.kline.inventorypos.core.designsystem.PosCard
import com.kline.inventorypos.core.designsystem.Primary50
import com.kline.inventorypos.core.designsystem.Primary700
import com.kline.inventorypos.core.designsystem.ProductVisual
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate200
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.model.LabelPrintItem
import com.kline.inventorypos.core.model.PriceHistoryEntry
import com.kline.inventorypos.core.model.PricingVariant
import com.kline.inventorypos.core.model.Product
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceManagementScreen(
    state: InventoryUiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onUpdate: (PricingVariant, Long, String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<PricingVariant?>(null) }
    LaunchedEffect(Unit) { onLoad() }
    val variants = state.pricingVariants.filter {
        query.isBlank() || it.productName.contains(query, true) || it.sku.contains(query, true) || it.variant.contains(query, true)
    }
    val belowTarget = state.pricingVariants.count { it.marginPercent() != null && it.marginPercent()!! < 20 }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Price management", "Selling prices · approval-aware", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            placeholder = { Text("Search product, SKU or variant") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PriceMetric("Variants", state.pricingVariants.size.toString(), Modifier.weight(1f))
            PriceMetric("Below target", belowTarget.toString(), Modifier.weight(1f), attention = belowTarget > 0)
            PriceMetric("Recent changes", state.priceHistory.size.toString(), Modifier.weight(1f))
        }
        when {
            state.pricingLoading && state.pricingVariants.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            variants.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(if (query.isBlank()) "No pricing data is available." else "No prices match this search.", color = Slate500)
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(variants, key = PricingVariant::id) { variant ->
                    PricingRow(variant, state.canEditPrices) { editing = variant }
                }
                if (state.priceHistory.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.History, contentDescription = null, tint = Primary700)
                            Text("Recent price changes", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    items(state.priceHistory.take(5), key = PriceHistoryEntry::id) { PriceHistoryRow(it) }
                }
            }
        }
    }
    editing?.let { variant ->
        PriceEditorSheet(
            variant = variant,
            working = state.working,
            onDismiss = { if (!state.working) editing = null },
            onSubmit = { price, reason ->
                onUpdate(variant, price, reason)
                editing = null
            },
        )
    }
}

@Composable
private fun PriceMetric(label: String, value: String, modifier: Modifier, attention: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = if (attention) Amber50 else MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(10.dp)) {
            Text(value, fontWeight = FontWeight.Bold, color = if (attention) Amber700 else MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Slate500, maxLines = 1)
        }
    }
}

@Composable
private fun PricingRow(variant: PricingVariant, editable: Boolean, onClick: () -> Unit) {
    PosCard(Modifier.fillMaxWidth().clickable(enabled = editable, onClick = onClick)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Primary50, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.LocalOffer, contentDescription = null, tint = Primary700)
            }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text(variant.productName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${variant.sku} · ${variant.variant}", style = MaterialTheme.typography.bodySmall, color = Slate500, maxLines = 1)
                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    variant.costPrice?.let { Text("Cost ${formatUgx(it)}", style = MaterialTheme.typography.labelSmall, color = Slate500) }
                    variant.marginPercent()?.let { margin ->
                        Text(
                            "Margin $margin%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (margin < 20) Amber700 else MoneyGreen700,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatUgx(variant.price), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
                Text(if (editable) "Tap to change" else "View only", style = MaterialTheme.typography.labelSmall, color = Slate500)
            }
        }
    }
}

@Composable
private fun PriceHistoryRow(entry: PriceHistoryEntry) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.productName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                Text(formatUgx(entry.newPrice), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
            }
            Text("${entry.sku} · ${formatUgx(entry.oldPrice)} → ${formatUgx(entry.newPrice)}", style = MaterialTheme.typography.bodySmall, color = Slate500)
            Text("${entry.reason} · ${entry.changedBy}", style = MaterialTheme.typography.labelSmall, color = Slate500, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceEditorSheet(
    variant: PricingVariant,
    working: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Long, String) -> Unit,
) {
    var priceText by remember(variant.id) { mutableStateOf(variant.price.toString()) }
    var reason by remember(variant.id) { mutableStateOf("") }
    val price = parseAmountInput(priceText)
    val valid = price != null && price > 0 && price != variant.price && reason.length >= 3
    val reasons = listOf("Supplier cost change", "Margin review", "Market adjustment", "Promotion ended")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            Text("Change selling price", style = MaterialTheme.typography.titleLarge)
            Text("${variant.productName} · ${variant.sku}", color = Slate500, style = MaterialTheme.typography.bodySmall)
            Surface(Modifier.fillMaxWidth().padding(top = 14.dp), color = Slate100, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Current price")
                    Text(formatUgx(variant.price), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
                }
            }
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = sanitizeAmountInput(it) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                label = { Text("New selling price (UGX)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = AmountGroupingVisualTransformation,
                singleLine = true,
            )
            Text("Reason for audit trail", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp))
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reasons.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { option ->
                            FilterChip(
                                selected = reason == option,
                                onClick = { reason = option },
                                label = { Text(option, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (price != null && price != variant.price) {
                val difference = price - variant.price
                Text(
                    "${if (difference > 0) "+" else ""}${formatUgx(difference)} from current price",
                    modifier = Modifier.padding(top = 8.dp),
                    color = if (difference > 0) MoneyGreen700 else Amber700,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !working) { Text("Cancel") }
                Button(onClick = { onSubmit(price!!, reason) }, modifier = Modifier.weight(1f), enabled = valid && !working) {
                    if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Submit change")
                }
            }
        }
    }
}

@Composable
fun LabelPrintScreen(
    products: List<Product>,
    onBack: () -> Unit,
    onPrint: (List<LabelPrintItem>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val copies = remember { mutableStateMapOf<String, Int>() }
    val visible = products.filter {
        query.isBlank() || it.name.contains(query, true) || it.sku.contains(query, true) || it.variant.contains(query, true)
    }
    val selected = products.mapNotNull { product -> copies[product.id]?.let { LabelPrintItem(product, it) } }
    val totalCopies = selected.sumOf(LabelPrintItem::copies)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Print labels", "Select variants and copy counts", onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            placeholder = { Text("Search product, SKU or variant") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp), color = Primary50, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Print, contentDescription = null, tint = Primary700)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("$totalCopies labels selected", fontWeight = FontWeight.Bold)
                    Text("EAN-13 variants only · maximum 99 copies each", style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(visible, key = Product::id) { product ->
                val printable = product.barcode?.matches(Regex("\\d{13}")) == true
                val count = copies[product.id] ?: 0
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    color = if (count > 0) Primary50 else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (count > 0) 2.dp else 1.dp, if (count > 0) Primary700 else Slate200),
                ) {
                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        ProductVisual(product.tone, Modifier.size(44.dp), iconSize = 24.dp)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${product.sku} · ${product.variant}", style = MaterialTheme.typography.bodySmall, color = Slate500, maxLines = 1)
                            Text(
                                if (printable) product.barcode.orEmpty() else "No printable EAN-13 barcode",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (printable) Slate500 else Amber700,
                            )
                        }
                        if (printable) {
                            if (count == 0) {
                                OutlinedButton(onClick = { copies[product.id] = 1 }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Add") }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        if (count <= 1) copies.remove(product.id) else copies[product.id] = count - 1
                                    }) { Icon(Icons.Outlined.Remove, contentDescription = "Remove one label") }
                                    Text(count.toString(), fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { if (count < 99) copies[product.id] = count + 1 }) {
                                        Icon(Icons.Outlined.Add, contentDescription = "Add one label")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (visible.isEmpty()) item { Text("No products match this search.", color = Slate500, modifier = Modifier.padding(16.dp)) }
        }
        Surface(shadowElevation = 8.dp) {
            Button(
                onClick = { onPrint(selected) },
                modifier = Modifier.fillMaxWidth().padding(14.dp).height(52.dp),
                enabled = selected.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Print, contentDescription = null)
                Text("  Print $totalCopies ${if (totalCopies == 1) "label" else "labels"}")
            }
        }
    }
}

internal fun PricingVariant.marginPercent(): Int? = costPrice?.takeIf { it > 0 }?.let {
    (((price - it).toDouble() / it) * 100).roundToInt()
}
