package com.kline.inventorypos.feature.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kline.inventorypos.core.common.AmountGroupingVisualTransformation
import com.kline.inventorypos.core.common.formatUgx
import com.kline.inventorypos.core.common.parseAmountInput
import com.kline.inventorypos.core.common.sanitizeAmountInput
import com.kline.inventorypos.core.designsystem.Amber50
import com.kline.inventorypos.core.designsystem.Amber700
import com.kline.inventorypos.core.designsystem.ButtonShape
import com.kline.inventorypos.core.designsystem.FocusedHeader
import com.kline.inventorypos.core.designsystem.MetricCard
import com.kline.inventorypos.core.designsystem.MoneyFontFamily
import com.kline.inventorypos.core.designsystem.MoneyGreen50
import com.kline.inventorypos.core.designsystem.MoneyGreen700
import com.kline.inventorypos.core.designsystem.PosCard
import com.kline.inventorypos.core.designsystem.Primary100
import com.kline.inventorypos.core.designsystem.Primary50
import com.kline.inventorypos.core.designsystem.Primary600
import com.kline.inventorypos.core.designsystem.Primary700
import com.kline.inventorypos.core.designsystem.Primary800
import com.kline.inventorypos.core.designsystem.ProductVisual
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate200
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.model.Product
import com.kline.inventorypos.core.model.InventoryMutationResult
import com.kline.inventorypos.core.model.GrnLine
import com.kline.inventorypos.core.model.GrnSummary
import com.kline.inventorypos.core.model.ReceiveStockLine
import com.kline.inventorypos.core.model.ReceiveStockDraft
import com.kline.inventorypos.core.model.StockMovement
import com.kline.inventorypos.core.model.StockTransfer
import com.kline.inventorypos.core.model.SupplierSummary
import com.kline.inventorypos.core.session.PosBranch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val NeedsActionTab = "Needs action"
private const val AllStockTab = "All stock"
private const val MovementsTab = "Movements"
private const val ReceiptsTab = "GRNs"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    branchName: String,
    state: InventoryUiState,
    onReceiveStock: () -> Unit,
    onTransferStock: () -> Unit,
    onManagePrices: () -> Unit,
    onPrintLabels: () -> Unit,
    onRefresh: () -> Unit,
    onScan: ((String) -> Unit) -> Unit,
    onAdjust: (Product, Int, String) -> Unit,
    onLoadGrn: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(NeedsActionTab) }
    var query by rememberSaveable { mutableStateOf("") }
    var adjustmentProduct by remember { mutableStateOf<Product?>(null) }
    val summary = state.summary
    val visibleProducts = when (selectedTab) {
        NeedsActionTab -> state.products.filter { it.stock <= it.reorderLevel }
        else -> state.products
    }.filter { product ->
        query.isBlank() || listOf(product.name, product.sku, product.variant).any { it.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Stock", style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(Primary600, CircleShape))
                        Text("  $branchName inventory", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    }
                }
                IconButton(onClick = {
                    onScan { code ->
                        val product = state.products.firstOrNull {
                            it.sku.equals(code, true) || it.barcode.equals(code, true)
                        }
                        when {
                            product == null -> onMessage("No stock item matches $code")
                            state.canAdjust -> adjustmentProduct = product
                            else -> {
                                selectedTab = AllStockTab
                                query = product.sku
                            }
                        }
                    }
                }) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan stock")
                }
                IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                    if (state.refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, contentDescription = "Refresh stock")
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Units on hand", summary?.unitsOnHand?.toString() ?: "—", Modifier.weight(1f))
            MetricCard("Stock value", summary?.inventoryValue?.compactMoney() ?: "—", Modifier.weight(1f))
            MetricCard("Low / out", summary?.let { "${it.lowStockCount}/${it.outOfStockCount}" } ?: "—", Modifier.weight(1f), valueColor = Amber700)
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StockAction(
                "Receive",
                Icons.Outlined.AddBox,
                Modifier.weight(1.4f),
                primary = true,
                enabled = state.canReceive,
                onClick = onReceiveStock,
            )
            StockAction(
                "Transfer",
                Icons.Outlined.SwapHoriz,
                Modifier.weight(1f),
                enabled = state.canTransfer,
                onClick = onTransferStock,
            )
            StockAction(
                "Adjust",
                Icons.Outlined.EditNote,
                Modifier.weight(1f),
                enabled = state.canAdjust,
            ) {
                adjustmentProduct = visibleProducts.firstOrNull() ?: state.products.firstOrNull()
                if (adjustmentProduct == null) onMessage("No stock item is available to adjust")
            }
        }

        if (state.canViewProducts) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StockAction(
                    "Prices",
                    Icons.Outlined.LocalOffer,
                    Modifier.weight(1f),
                    enabled = state.canViewProducts,
                    onClick = onManagePrices,
                )
                StockAction(
                    "Print labels",
                    Icons.Outlined.Print,
                    Modifier.weight(1f),
                    enabled = state.canViewProducts,
                    onClick = onPrintLabels,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOf(NeedsActionTab, AllStockTab, MovementsTab, ReceiptsTab).forEach { tab ->
                FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab }, label = { Text(tab) })
            }
        }

        if (selectedTab !in setOf(MovementsTab, ReceiptsTab)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text("Search name, SKU or variant") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selectedTab == MovementsTab) {
                if (state.movements.isEmpty()) {
                    item { InventoryEmpty("No stock movements yet", "Receipts, sales and adjustments will appear here.") }
                } else {
                    items(state.movements, key = StockMovement::id) { movement -> MovementRow(movement) }
                }
            } else if (selectedTab == ReceiptsTab) {
                if (state.grns.isEmpty()) {
                    item { InventoryEmpty("No goods received notes", "Posted purchase receipts will appear here.") }
                } else {
                    items(state.grns, key = GrnSummary::reference) { grn ->
                        GrnRow(
                            grn = grn,
                            details = state.grnDetails[grn.reference],
                            working = state.working,
                            onLoad = { onLoadGrn(grn.reference) },
                        )
                    }
                }
            } else if (visibleProducts.isEmpty()) {
                item {
                    InventoryEmpty(
                        if (query.isNotBlank()) "No matching stock" else "Everything is well stocked",
                        if (query.isNotBlank()) "Try another name, SKU or variant." else "No variants currently need attention.",
                    )
                }
            } else {
                items(visibleProducts, key = Product::id) { product ->
                    StockRow(
                        product = product,
                        onClick = if (state.canAdjust) ({ adjustmentProduct = product }) else null,
                    )
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    adjustmentProduct?.let { product ->
        AdjustmentSheet(
            product = product,
            working = state.working,
            onDismiss = { if (!state.working) adjustmentProduct = null },
            onSubmit = { change, reason ->
                onAdjust(product, change, reason)
                adjustmentProduct = null
            },
        )
    }
}

@Composable
private fun StockAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (primary) Primary700 else MaterialTheme.colorScheme.surface,
        contentColor = if (primary) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun StockRow(product: Product, onClick: (() -> Unit)?) {
    PosCard(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductVisual(product.tone, Modifier.size(width = 46.dp, height = 54.dp), iconSize = 26.dp)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("${product.name} · ${product.variant}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("SKU ${product.sku} · Reorder at ${product.reorderLevel}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                LinearProgressIndicator(
                    progress = { (product.stock.toFloat() / product.reorderLevel.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(.38f).padding(top = 6.dp).height(4.dp),
                    color = if (product.stock <= product.reorderLevel) Amber700 else Primary700,
                    trackColor = Slate100,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(product.stock.toString(), style = MaterialTheme.typography.titleLarge, color = if (product.stock <= product.reorderLevel) Amber700 else Primary800)
                Text(if (product.stock == 0) "out of stock" else "on hand", style = MaterialTheme.typography.bodySmall, color = Slate500)
            }
        }
    }
}

@Composable
private fun MovementRow(movement: StockMovement) {
    val incoming = movement.quantityChange > 0
    PosCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(if (incoming) MoneyGreen50 else Amber50, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (incoming) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                    contentDescription = null,
                    tint = if (incoming) MoneyGreen700 else Amber700,
                )
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(movement.productName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${movement.type.displayMovement()} · ${movement.sku}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                Text("${movement.performedBy} · ${movement.createdAt.displayDate()}", style = MaterialTheme.typography.labelSmall, color = Slate500)
                movement.reason?.takeUnless { it.equals(movement.type, true) }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (incoming) "+${movement.quantityChange}" else movement.quantityChange.toString(),
                    color = if (incoming) MoneyGreen700 else Amber700,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text("${movement.previousQuantity} → ${movement.newQuantity}", style = MaterialTheme.typography.labelSmall, color = Slate500)
            }
        }
    }
}

@Composable
private fun GrnRow(
    grn: GrnSummary,
    details: List<GrnLine>?,
    working: Boolean,
    onLoad: () -> Unit,
) {
    var expanded by rememberSaveable(grn.reference) { mutableStateOf(false) }
    PosCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable {
                    expanded = !expanded
                    if (expanded && details == null) onLoad()
                }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).background(Primary50, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = Primary700)
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(grn.reference, fontWeight = FontWeight.Bold)
                    Text(grn.supplierName ?: "Supplier not recorded", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    Text("${grn.itemCount} items · ${grn.totalUnits} units · ${grn.receivedAt.displayDate()}", style = MaterialTheme.typography.labelSmall, color = Slate500)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (expanded) "Collapse" else "Show details")
            }
            if (expanded) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100))
                if (details == null && working) {
                    Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else {
                    details.orEmpty().forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(line.productName, style = MaterialTheme.typography.titleSmall)
                                Text("${line.sku} · ${line.variant}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("+${line.quantity}", color = MoneyGreen700, fontWeight = FontWeight.Bold)
                                line.unitCost?.let { Text(formatUgx(it), style = MaterialTheme.typography.labelSmall, color = Slate500) }
                            }
                        }
                    }
                    Text(
                        "Received by ${grn.receivedBy}",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustmentSheet(
    product: Product,
    working: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Int, String) -> Unit,
) {
    var direction by rememberSaveable(product.id) { mutableIntStateOf(-1) }
    var quantityText by rememberSaveable(product.id) { mutableStateOf("1") }
    var reason by rememberSaveable(product.id) { mutableStateOf("") }
    val quantity = quantityText.toIntOrNull() ?: 0
    val change = quantity * direction
    val resultingStock = product.stock + change
    val valid = quantity > 0 && reason.trim().length >= 3 && resultingStock >= 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text("Adjust stock", style = MaterialTheme.typography.titleLarge)
            Text("${product.name} · ${product.variant}", color = Slate500)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = direction < 0,
                    onClick = { direction = -1 },
                    label = { Text("Remove stock") },
                    leadingIcon = { Icon(Icons.Outlined.Remove, contentDescription = null, Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = direction > 0,
                    onClick = { direction = 1 },
                    label = { Text("Add stock") },
                    leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, Modifier.size(18.dp)) },
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it.filter(Char::isDigit).take(6) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                singleLine = true,
            )
            Text("Reason", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("Count correction", "Damaged", "Expired", "Display use").forEach { option ->
                    FilterChip(selected = reason == option, onClick = { reason = option }, label = { Text(option) })
                }
            }
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it.take(180) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                label = { Text("Audit note") },
                minLines = 2,
            )
            Surface(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                color = if (resultingStock < 0) Amber50 else Primary50,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(Modifier.padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("On hand after adjustment")
                    Text(resultingStock.coerceAtLeast(0).toString(), fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = { onSubmit(change, reason.trim()) },
                enabled = valid && !working,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp),
                shape = ButtonShape,
            ) {
                if (working) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Submit adjustment")
            }
            Text(
                "This action is recorded with your name and may require manager approval.",
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = Slate500,
            )
        }
    }
}

@Composable
private fun InventoryEmpty(title: String, subtitle: String) {
    PosCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = Slate500, modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slate500, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferStockScreen(
    currentBranchId: String,
    products: List<Product>,
    destinations: List<PosBranch>,
    transfers: List<StockTransfer>,
    working: Boolean,
    onBack: () -> Unit,
    onCreate: (PosBranch, List<ReceiveStockLine>, String?) -> Unit,
    onTransition: (StockTransfer, String) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf("open") }
    var destinationId by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val quantities = remember { mutableStateMapOf<String, Int>() }
    var pendingTransition by remember { mutableStateOf<Pair<StockTransfer, String>?>(null) }
    val destination = destinations.firstOrNull { it.id == destinationId }
    val lines = quantities.mapNotNull { (id, quantity) ->
        products.firstOrNull { it.id == id }?.let { ReceiveStockLine(it, quantity, null) }
    }
    val matches = products.filter {
        query.length >= 2 && listOf(it.name, it.sku, it.variant).any { value -> value.contains(query, true) }
    }.take(8)
    val canCreate = destination != null && lines.isNotEmpty() && lines.all { it.quantity in 1..it.product.stock }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Stock transfers", "Move stock between authorised branches", onBack)
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == "open", onClick = { mode = "open" }, label = { Text("Transfers (${transfers.size})") }, modifier = Modifier.weight(1f))
            FilterChip(selected = mode == "new", onClick = { mode = "new" }, label = { Text("New transfer") }, modifier = Modifier.weight(1f))
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (mode == "open") {
                if (transfers.isEmpty()) {
                    item { InventoryEmpty("No branch transfers", "Create a request when another branch needs stock.") }
                } else {
                    items(transfers, key = StockTransfer::id) { transfer ->
                        TransferCard(
                            transfer = transfer,
                            currentBranchId = currentBranchId,
                            working = working,
                            onAction = { action -> pendingTransition = transfer to action },
                        )
                    }
                }
            } else {
                item {
                    Text("Destination", style = MaterialTheme.typography.titleMedium)
                    Text("Only branches assigned to your account are available.", style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                if (destinations.isEmpty()) {
                    item { InventoryEmpty("No destination branches", "Ask an administrator to assign another active branch.") }
                } else {
                    items(destinations, key = PosBranch::id) { branch ->
                        Surface(
                            onClick = { destinationId = branch.id },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(13.dp),
                            color = if (destinationId == branch.id) Primary50 else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(if (destinationId == branch.id) 2.dp else 1.dp, if (destinationId == branch.id) Primary700 else Slate200),
                        ) {
                            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = Primary700)
                                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                    Text(branch.name, fontWeight = FontWeight.Bold)
                                    Text(listOf(branch.code, branch.city).filterNotNull().filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Slate500)
                                }
                                if (destinationId == branch.id) Icon(Icons.Outlined.CheckCircle, "Selected", tint = Primary700)
                            }
                        }
                    }
                }
                item {
                    Text("Items", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        placeholder = { Text("Search product, SKU or variant") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                    )
                }
                items(matches, key = Product::id) { product ->
                    if (product.id !in quantities) {
                        Surface(
                            onClick = { if (product.stock > 0) { quantities[product.id] = 1; query = "" } },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, fontWeight = FontWeight.Bold)
                                    Text("${product.sku} · ${product.stock} available", style = MaterialTheme.typography.bodySmall, color = Slate500)
                                }
                                Icon(Icons.Outlined.Add, "Add item", tint = if (product.stock > 0) Primary700 else Slate500)
                            }
                        }
                    }
                }
                items(lines, key = { it.product.id }) { line ->
                    TransferLineEditor(
                        line = line,
                        onQuantity = { value -> if (value <= 0) quantities.remove(line.product.id) else quantities[line.product.id] = value },
                    )
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(240) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        label = { Text("Transfer note (optional)") },
                        placeholder = { Text("Reason, urgency or handling note") },
                        minLines = 2,
                    )
                }
            }
        }
        if (mode == "new") {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                Button(
                    onClick = {
                        destination?.let { onCreate(it, lines, notes.takeIf(String::isNotBlank)) }
                        mode = "open"
                    },
                    enabled = canCreate && !working,
                    modifier = Modifier.fillMaxWidth().padding(12.dp).height(54.dp),
                    shape = ButtonShape,
                ) {
                    if (working) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Request transfer · ${lines.sumOf { it.quantity }} units")
                }
            }
        }
    }

    pendingTransition?.let { (transfer, action) ->
        ModalBottomSheet(
            onDismissRequest = { if (!working) pendingTransition = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = Amber700, modifier = Modifier.size(34.dp))
                Text(action.transferActionTitle(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                Text(
                    when (action) {
                        "dispatch" -> "This immediately removes the requested units from ${transfer.fromBranchName}."
                        "receive" -> "Confirm the shipment is physically present. This adds the units to ${transfer.toBranchName}."
                        else -> "This closes the request before any stock moves."
                    },
                    color = Slate500,
                )
                Text("${transfer.number} · ${transfer.itemCount} items", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pendingTransition = null }, enabled = !working, modifier = Modifier.weight(1f).height(50.dp)) { Text("Keep open") }
                    Button(
                        onClick = { onTransition(transfer, action); pendingTransition = null },
                        enabled = !working,
                        modifier = Modifier.weight(1f).height(50.dp),
                    ) { Text("Confirm") }
                }
            }
        }
    }
}

@Composable
private fun TransferCard(
    transfer: StockTransfer,
    currentBranchId: String,
    working: Boolean,
    onAction: (String) -> Unit,
) {
    val canDispatch = transfer.fromBranchId == currentBranchId && transfer.status in setOf("requested", "approved")
    val canReceive = transfer.toBranchId == currentBranchId && transfer.status == "dispatched"
    val canCancel = transfer.fromBranchId == currentBranchId && transfer.status in setOf("draft", "requested", "approved")
    PosCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(transfer.number, fontWeight = FontWeight.Bold)
                    Text("${transfer.fromBranchName} → ${transfer.toBranchName}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                Surface(color = transfer.status.statusColor(), contentColor = transfer.status.statusContentColor(), shape = CircleShape) {
                    Text(transfer.status.replaceFirstChar(Char::uppercase), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Text("${transfer.itemCount} items · requested ${transfer.requestedAt.displayDate()}", style = MaterialTheme.typography.bodySmall, color = Slate500, modifier = Modifier.padding(top = 6.dp))
            transfer.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp)) }
            if (canDispatch || canReceive || canCancel) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (canCancel) OutlinedButton(onClick = { onAction("cancel") }, enabled = !working, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    if (canDispatch) Button(onClick = { onAction("dispatch") }, enabled = !working, modifier = Modifier.weight(1f)) { Text("Dispatch") }
                    if (canReceive) Button(onClick = { onAction("receive") }, enabled = !working, modifier = Modifier.weight(1f)) { Text("Receive") }
                }
            }
        }
    }
}

@Composable
private fun TransferLineEditor(line: ReceiveStockLine, onQuantity: (Int) -> Unit) {
    PosCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.product.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${line.product.sku} · ${line.product.stock} available", style = MaterialTheme.typography.bodySmall, color = Slate500)
                if (line.quantity > line.product.stock) Text("Reduce to available stock", style = MaterialTheme.typography.labelSmall, color = Amber700)
            }
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onQuantity(line.quantity - 1) }, Modifier.size(34.dp)) { Icon(Icons.Outlined.Remove, "Remove one") }
                    Text(line.quantity.toString(), Modifier.width(28.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { onQuantity(line.quantity + 1) }, Modifier.size(34.dp)) { Icon(Icons.Outlined.Add, "Add one") }
                }
            }
        }
    }
}

@Composable
fun ReceiveStockScreen(
    products: List<Product>,
    suppliers: List<SupplierSummary>,
    draft: ReceiveStockDraft?,
    working: Boolean,
    receiveResult: InventoryMutationResult?,
    onBack: () -> Unit,
    onSubmit: (SupplierSummary, List<ReceiveStockLine>, String?) -> Unit,
    onSaveDraft: (SupplierSummary?, List<ReceiveStockLine>, String?) -> Unit,
    onDeleteDraft: () -> Unit,
    onComplete: () -> Unit,
    onScan: ((String) -> Unit) -> Unit,
    onMessage: (String) -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var supplierId by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var draftHandled by rememberSaveable { mutableStateOf(false) }
    val quantities = remember { mutableStateMapOf<String, Int>() }
    val costs = remember { mutableStateMapOf<String, String>() }
    val supplier = suppliers.firstOrNull { it.id == supplierId }
    val lines = quantities.mapNotNull { (id, quantity) ->
        products.firstOrNull { it.id == id }?.let { product ->
            ReceiveStockLine(product, quantity, costs[id]?.let(::parseAmountInput))
        }
    }
    val matchingProducts = products.filter {
        query.length >= 2 && listOf(it.name, it.sku, it.variant).any { value -> value.contains(query, true) }
    }.take(8)
    val resumeDraft = {
        supplierId = draft?.supplierId.orEmpty()
        notes = draft?.notes.orEmpty()
        quantities.clear()
        costs.clear()
        draft?.lines.orEmpty().forEach { line ->
            quantities[line.variantId] = line.quantity
            line.unitCost?.let { costs[line.variantId] = it.toString() }
        }
        step = if (draft?.supplierId != null) 1 else 0
        draftHandled = true
    }

    if (receiveResult != null) {
        ReceiveSuccess(
            result = receiveResult,
            lines = lines,
            onDone = onComplete,
        )
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(
            "Receive stock",
            "Goods received note · step ${step + 1} of 3",
            onBack,
            trailing = {
                TextButton(
                    onClick = {
                        draftHandled = true
                        onSaveDraft(supplier, lines, notes.takeIf(String::isNotBlank))
                    },
                    enabled = !working && (supplier != null || lines.isNotEmpty()),
                ) { Text("Save") }
            },
        )
        StepIndicator(step)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (draft != null && !draftHandled) {
                item {
                    Surface(color = Primary50, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Primary100)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Resume saved receipt?", fontWeight = FontWeight.Bold)
                            Text(
                                "${draft.supplierName ?: "Supplier not chosen"} · ${draft.lines.sumOf { it.quantity }} units · saved ${draft.updatedAt.displayDate()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onDeleteDraft(); draftHandled = true },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Discard") }
                                Button(onClick = resumeDraft, modifier = Modifier.weight(1f)) { Text("Resume") }
                            }
                        }
                    }
                }
            }
            when (step) {
                0 -> {
                    item {
                        Text("Choose supplier", style = MaterialTheme.typography.titleLarge)
                        Text("The supplier is recorded on the GRN and stock audit trail.", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    }
                    if (suppliers.isEmpty()) {
                        item { InventoryEmpty("No suppliers available", "Ask a manager for supplier access or create suppliers in the web app.") }
                    } else {
                        items(suppliers, key = SupplierSummary::id) { option ->
                            Surface(
                                onClick = { supplierId = option.id },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = if (supplierId == option.id) Primary50 else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(if (supplierId == option.id) 2.dp else 1.dp, if (supplierId == option.id) Primary700 else Slate200),
                            ) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = Primary700)
                                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                        Text(option.name, fontWeight = FontWeight.Bold)
                                        option.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Slate500) }
                                    }
                                    if (supplierId == option.id) Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = Primary700)
                                }
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it.take(240) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Delivery note (optional)") },
                            placeholder = { Text("Invoice number, condition or partial delivery") },
                            minLines = 2,
                        )
                    }
                }
                1 -> {
                    item {
                        Text("Add delivered items", style = MaterialTheme.typography.titleLarge)
                        Text("Search by product, SKU or variant, then set quantity and unit cost.", style = MaterialTheme.typography.bodySmall, color = Slate500)
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            placeholder = { Text("Search stock") },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = {
                                    onScan { code ->
                                        val product = products.firstOrNull {
                                            it.sku.equals(code, true) || it.barcode.equals(code, true)
                                        }
                                        if (product == null) onMessage("No delivered item matches $code")
                                        else quantities[product.id] = (quantities[product.id] ?: 0) + 1
                                    }
                                }) {
                                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan delivered item")
                                }
                            },
                            singleLine = true,
                        )
                    }
                    items(matchingProducts, key = Product::id) { product ->
                        if (product.id !in quantities) {
                            Surface(
                                onClick = { quantities[product.id] = 1; query = "" },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ProductVisual(product.tone, Modifier.size(42.dp), iconSize = 23.dp)
                                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                        Text(product.name, fontWeight = FontWeight.Bold)
                                        Text("${product.sku} · ${product.variant}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                                    }
                                    Icon(Icons.Outlined.Add, contentDescription = "Add item", tint = Primary700)
                                }
                            }
                        }
                    }
                    if (lines.isEmpty() && query.length < 2) {
                        item { InventoryEmpty("No items added", "Type at least two characters to find the first delivered item.") }
                    }
                    items(lines, key = { it.product.id }) { line ->
                        ReceiveLineEditor(
                            line = line,
                            costText = costs[line.product.id].orEmpty(),
                            onQuantity = { value -> if (value <= 0) quantities.remove(line.product.id) else quantities[line.product.id] = value },
                            onCost = { costs[line.product.id] = sanitizeAmountInput(it) },
                        )
                    }
                }
                else -> {
                    item {
                        Text("Review receipt", style = MaterialTheme.typography.titleLarge)
                        Text("Confirm physical quantities and costs before posting stock.", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    }
                    item {
                        PosCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReviewRow("Supplier", supplier?.name.orEmpty())
                                ReviewRow("Line items", lines.size.toString())
                                ReviewRow("Units received", lines.sumOf { it.quantity }.toString())
                                ReviewRow("Recorded cost", formatUgx(lines.sumOf { (it.unitCost ?: 0L) * it.quantity }))
                            }
                        }
                    }
                    items(lines, key = { it.product.id }) { line ->
                        PosCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(line.product.name, fontWeight = FontWeight.Bold)
                                    Text("${line.product.sku} · ${line.product.variant}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("+${line.quantity}", color = MoneyGreen700, fontWeight = FontWeight.Bold)
                                    Text(line.unitCost?.let(::formatUgx) ?: "No cost", style = MaterialTheme.typography.bodySmall, color = Slate500)
                                }
                            }
                        }
                    }
                    item {
                        Surface(color = Amber50, contentColor = Amber700, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Posting creates auditable purchase movements and an automatic GRN.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        enabled = !working,
                        modifier = Modifier.weight(.75f).height(54.dp),
                        shape = ButtonShape,
                    ) { Text("Back") }
                }
                Button(
                    onClick = {
                        if (step < 2) step++ else supplier?.let { onSubmit(it, lines, notes.takeIf(String::isNotBlank)) }
                    },
                    enabled = !working && when (step) {
                        0 -> supplier != null
                        else -> lines.isNotEmpty()
                    },
                    modifier = Modifier.weight(1.5f).height(54.dp),
                    shape = ButtonShape,
                ) {
                    if (working) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(if (step < 2) "Continue" else "Post stock receipt")
                }
            }
        }
    }
}

@Composable
private fun ReceiveLineEditor(
    line: ReceiveStockLine,
    costText: String,
    onQuantity: (Int) -> Unit,
    onCost: (String) -> Unit,
) {
    PosCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(line.product.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${line.product.sku} · ${line.product.variant}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onQuantity(line.quantity - 1) }, Modifier.size(34.dp)) { Icon(Icons.Outlined.Remove, "Remove one") }
                        Text(line.quantity.toString(), Modifier.width(28.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onQuantity(line.quantity + 1) }, Modifier.size(34.dp)) { Icon(Icons.Outlined.Add, "Add one") }
                    }
                }
            }
            OutlinedTextField(
                value = costText,
                onValueChange = onCost,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Unit cost (optional)") },
                prefix = { Text("UGX ") },
                supportingText = { Text("Selling price ${formatUgx(line.product.price)}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = AmountGroupingVisualTransformation,
                singleLine = true,
            )
        }
    }
}

@Composable
private fun ReceiveSuccess(result: InventoryMutationResult, lines: List<ReceiveStockLine>, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(76.dp).background(MoneyGreen50, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MoneyGreen700, modifier = Modifier.size(42.dp))
        }
        Text(
            if (result.pendingApproval) "Approval requested" else "Stock received",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            result.reference ?: result.message,
            color = Primary700,
            fontWeight = FontWeight.Bold,
            fontFamily = MoneyFontFamily,
            textAlign = TextAlign.Center,
        )
        Text(
            if (result.pendingApproval) "Stock will update after manager approval." else "${lines.sumOf { it.quantity }} units across ${lines.size} items",
            color = Slate500,
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(54.dp), shape = ButtonShape) {
            Text("Return to stock")
        }
    }
}

@Composable
private fun StepIndicator(current: Int) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { index ->
                StepCircle((index + 1).toString(), active = index <= current)
                if (index < 2) Box(Modifier.size(width = 52.dp, height = 2.dp).background(if (index < current) Primary700 else Primary100))
            }
        }
    }
}

@Composable
private fun StepCircle(label: String, active: Boolean) {
    Box(
        Modifier.size(26.dp).background(if (active) Primary700 else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (active) Color.White else Slate500, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Slate500)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private fun Long.compactMoney(): String = when {
    this >= 1_000_000_000 -> "${"%.1f".format(this / 1_000_000_000.0)}b"
    this >= 1_000_000 -> "${"%.1f".format(this / 1_000_000.0)}m"
    this >= 1_000 -> "${this / 1_000}k"
    else -> toString()
}

private fun String.displayMovement(): String = replace('_', ' ').replace('-', ' ')
    .split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

private fun String.displayDate(): String = runCatching {
    DateTimeFormatter.ofPattern("dd MMM, HH:mm")
        .format(Instant.parse(this).atZone(ZoneId.systemDefault()))
}.getOrElse { take(16).replace('T', ' ') }

private fun String.transferActionTitle(): String = when (this) {
    "dispatch" -> "Dispatch this transfer?"
    "receive" -> "Receive this transfer?"
    else -> "Cancel this transfer?"
}

private fun String.statusColor(): Color = when (this) {
    "received" -> MoneyGreen50
    "dispatched" -> Primary100
    "cancelled" -> Slate100
    else -> Amber50
}

private fun String.statusContentColor(): Color = when (this) {
    "received" -> MoneyGreen700
    "dispatched" -> Primary800
    "cancelled" -> Slate500
    else -> Amber700
}
