package com.kline.inventorypos.feature.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
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
import com.kline.inventorypos.core.designsystem.MoneyFontFamily
import com.kline.inventorypos.core.designsystem.MoneyGreen700
import com.kline.inventorypos.core.designsystem.MoneyText
import com.kline.inventorypos.core.designsystem.PosCard
import com.kline.inventorypos.core.designsystem.Primary100
import com.kline.inventorypos.core.designsystem.Primary700
import com.kline.inventorypos.core.designsystem.Primary800
import com.kline.inventorypos.core.designsystem.ProductVisual
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate200
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.designsystem.Slate950
import com.kline.inventorypos.core.designsystem.StatusPill
import com.kline.inventorypos.core.model.CartLine
import com.kline.inventorypos.core.model.AppliedDiscount
import com.kline.inventorypos.core.model.CatalogFreshness
import com.kline.inventorypos.core.model.CustomerSummary
import com.kline.inventorypos.core.model.DiscountOption
import com.kline.inventorypos.core.model.HeldCart
import com.kline.inventorypos.core.model.Product
import com.kline.inventorypos.core.model.PromotionSummary
import com.kline.inventorypos.data.checkout.DiscountRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleCatalogScreen(
    products: List<Product>,
    categories: List<Pair<String, String>>,
    query: String,
    selectedCategoryId: String?,
    freshness: CatalogFreshness,
    cartItemCount: Int,
    cartTotal: Long,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onScan: () -> Unit,
    onRefresh: () -> Unit,
    onAddProduct: (Product) -> Unit,
    onOpenCart: () -> Unit,
) {
    var variantsToChoose by remember { mutableStateOf<List<Product>?>(null) }
    val grouped = products.groupBy(Product::productId).values.map { variants ->
        variants.first().copy(
            price = variants.minOf(Product::price),
            stock = variants.sumOf(Product::stock),
            requiresVariantChoice = variants.size > 1,
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("Search name, SKU or barcode") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        FilledIconButton(onClick = onScan) {
                            Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan barcode")
                        }
                    },
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onCategoryChange(null) },
                        label = { Text("All items") },
                    )
                    categories.forEach { (id, name) ->
                        FilterChip(
                            selected = selectedCategoryId == id,
                            onClick = { onCategoryChange(id) },
                            label = { Text(name) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${grouped.size} products · offline ready", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    if (freshness.refreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh catalog")
                        }
                    }
                }
                freshness.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            if (grouped.isEmpty() && !freshness.refreshing) {
                Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (query.isBlank()) "No products available" else "No matching products", fontWeight = FontWeight.Bold)
                    Text("Pull the latest catalog or adjust your search.", color = Slate500)
                    OutlinedButton(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) { Text("Refresh catalog") }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 94.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(grouped, key = Product::productId) { product ->
                        CatalogProductCard(product) {
                            val variants = products.filter { it.productId == product.productId }
                            if (variants.size > 1) variantsToChoose = variants
                            else variants.firstOrNull()?.let(onAddProduct)
                        }
                    }
                }
            }
        }

        if (cartItemCount > 0) {
            Surface(
                onClick = onOpenCart,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                shape = RoundedCornerShape(18.dp),
                color = Slate950,
                contentColor = Color.White,
                shadowElevation = 10.dp,
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(Primary700, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                        Text(cartItemCount.toString(), fontWeight = FontWeight.ExtraBold)
                    }
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("View cart", style = MaterialTheme.typography.labelLarge)
                        Text("$cartItemCount items in current sale", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.62f))
                    }
                    Text(formatUgx(cartTotal), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    variantsToChoose?.let { variants ->
        ModalBottomSheet(
            onDismissRequest = { variantsToChoose = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
                Text(variants.first().name, style = MaterialTheme.typography.titleLarge)
                Text("Choose a variant", modifier = Modifier.padding(top = 4.dp, bottom = 10.dp), color = Slate500)
                variants.forEach { variant ->
                    Surface(
                        onClick = {
                            onAddProduct(variant)
                            variantsToChoose = null
                        },
                        enabled = variant.stock > 0,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(variant.variant.ifBlank { variant.sku }, fontWeight = FontWeight.Bold)
                                Text("${variant.sku} · ${variant.stock} in stock", style = MaterialTheme.typography.bodySmall, color = Slate500)
                            }
                            MoneyText(variant.price, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogProductCard(product: Product, onAdd: () -> Unit) {
    PosCard(Modifier.fillMaxWidth()) {
        Column {
            Box {
                ProductVisual(product.tone, Modifier.fillMaxWidth().height(116.dp))
                StatusPill(
                    text = if (product.stock <= 0) "Out of stock" else if (product.stock <= 4) "Only ${product.stock} left" else "${product.stock} in stock",
                    modifier = Modifier.padding(8.dp),
                    containerColor = if (product.stock <= 4) Amber50 else Color.White.copy(alpha = 0.9f),
                    contentColor = if (product.stock <= 4) Amber700 else MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(Modifier.padding(10.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (product.requiresVariantChoice) "Multiple variants" else product.sku, style = MaterialTheme.typography.bodySmall, color = Slate500)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MoneyText(product.price, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    FilledIconButton(onClick = onAdd, enabled = product.stock > 0, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add ${product.name}")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersistentCartScreen(
    lines: List<CartLine>,
    customer: CustomerSummary?,
    customerResults: List<CustomerSummary>,
    promotions: List<PromotionSummary>,
    discountOptions: List<DiscountOption>,
    appliedDiscount: AppliedDiscount?,
    heldCarts: List<HeldCart>,
    working: Boolean,
    onBack: () -> Unit,
    onIncrease: (String) -> Unit,
    onDecrease: (String) -> Unit,
    onSearchCustomers: (String) -> Unit,
    onAttachCustomer: (CustomerSummary?) -> Unit,
    onApplyDiscount: (DiscountOption) -> Unit,
    onRemoveDiscount: () -> Unit,
    onHold: (String?) -> Unit,
    onRecall: (String) -> Unit,
    onDeleteHeld: (String) -> Unit,
    onPayment: () -> Unit,
) {
    var showCustomers by remember { mutableStateOf(false) }
    var showHold by remember { mutableStateOf(false) }
    var showDiscount by remember { mutableStateOf(false) }
    var customerQuery by remember { mutableStateOf("") }
    var holdNote by remember { mutableStateOf("") }
    val subtotal = lines.sumOf(CartLine::lineTotal)
    val savings = promotions.sumOf(PromotionSummary::savings).coerceAtMost(subtotal)
    val discountAmount = (appliedDiscount?.amount ?: 0L).coerceAtMost(subtotal - savings)
    val total = subtotal - savings - discountAmount
    val units = lines.sumOf(CartLine::quantity)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Current sale", "$units items · saved automatically", onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PosCard(Modifier.fillMaxWidth().clickable { showCustomers = true }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(Primary100, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Badge, contentDescription = null, tint = Primary800)
                        }
                        Column(Modifier.padding(start = 11.dp).weight(1f)) {
                            Text(customer?.name ?: "Add customer", style = MaterialTheme.typography.titleSmall)
                            Text(
                                customer?.let { "${it.loyaltyPoints} loyalty points · tap to change" }
                                    ?: "Enable loyalty, prepaid and credit",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500,
                            )
                        }
                    }
                }
            }
            if (lines.isEmpty()) {
                item {
                    PosCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Slate500, modifier = Modifier.size(40.dp))
                            Text("Your cart is empty", modifier = Modifier.padding(top = 8.dp), color = Slate500)
                        }
                    }
                }
            } else {
                item {
                    PosCard(Modifier.fillMaxWidth()) {
                        lines.forEachIndexed { index, line ->
                            PersistentCartLine(line, onIncrease, onDecrease)
                            if (index != lines.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100))
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showHold = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = ButtonShape,
                        ) {
                            Icon(Icons.Outlined.Pause, contentDescription = null)
                            Text("  Hold")
                        }
                        OutlinedButton(
                            onClick = { showDiscount = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = ButtonShape,
                        ) {
                            Icon(Icons.Outlined.LocalOffer, contentDescription = null)
                            Text(if (appliedDiscount == null) "  Discount" else "  Edit discount")
                        }
                    }
                }
                if (promotions.isNotEmpty()) {
                    item {
                        PosCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.LocalOffer, contentDescription = null, tint = MoneyGreen700)
                                    Text("  Automatic promotions", fontWeight = FontWeight.Bold)
                                }
                                promotions.forEach { promotion ->
                                    Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(promotion.name, style = MaterialTheme.typography.bodySmall)
                                        Text("− ${formatUgx(promotion.savings)}", color = MoneyGreen700, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                item { PersistentTotals(subtotal, savings, discountAmount, appliedDiscount?.option?.name, total) }
            }
            if (heldCarts.isNotEmpty()) {
                item { Text("Held sales", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
                items(heldCarts, key = HeldCart::id) { held ->
                    PosCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(held.customerName ?: "Walk-in sale", fontWeight = FontWeight.Bold)
                                Text("${held.itemCount} items${if (held.pendingSync) " · sync pending" else ""}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                                held.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                            OutlinedButton(onClick = { onRecall(held.id) }) { Text("Recall") }
                            IconButton(onClick = { onDeleteHeld(held.id) }) { Icon(Icons.Outlined.Delete, contentDescription = "Delete held sale") }
                        }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Button(
                onClick = onPayment,
                enabled = lines.isNotEmpty() && !working,
                modifier = Modifier.fillMaxWidth().padding(12.dp).height(54.dp),
                shape = ButtonShape,
            ) {
                Text("Proceed to payment")
                Spacer(Modifier.weight(1f))
                Text(formatUgx(total), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showCustomers) {
        ModalBottomSheet(onDismissRequest = { showCustomers = false }) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
                Text("Attach customer", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = customerQuery,
                    onValueChange = {
                        customerQuery = it
                        onSearchCustomers(it)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true,
                    placeholder = { Text("Name or phone") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                )
                if (customer != null) {
                    OutlinedButton(onClick = { onAttachCustomer(null); showCustomers = false }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Remove ${customer.name}")
                    }
                }
                customerResults.forEach { result ->
                    Surface(
                        onClick = { onAttachCustomer(result); showCustomers = false },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(result.name, fontWeight = FontWeight.Bold)
                            Text(result.phone ?: result.email.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Slate500)
                        }
                    }
                }
            }
        }
    }

    if (showHold) {
        ModalBottomSheet(onDismissRequest = { showHold = false }) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
                Text("Hold this sale", style = MaterialTheme.typography.titleLarge)
                Text("The cart is saved on this device and synced when the server is available.", color = Slate500)
                OutlinedTextField(
                    value = holdNote,
                    onValueChange = { holdNote = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    label = { Text("Note (optional)") },
                )
                Button(
                    onClick = { onHold(holdNote.takeIf(String::isNotBlank)); showHold = false },
                    enabled = lines.isNotEmpty() && !working,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Hold sale") }
            }
        }
    }

    if (showDiscount) {
        DiscountSheet(
            lines = lines,
            options = discountOptions,
            applied = appliedDiscount,
            onDismiss = { showDiscount = false },
            onApply = {
                onApplyDiscount(it)
                showDiscount = false
            },
            onRemove = {
                onRemoveDiscount()
                showDiscount = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscountSheet(
    lines: List<CartLine>,
    options: List<DiscountOption>,
    applied: AppliedDiscount?,
    onDismiss: () -> Unit,
    onApply: (DiscountOption) -> Unit,
    onRemove: () -> Unit,
) {
    var mode by remember { mutableStateOf(if (options.isEmpty()) "manual" else "saved") }
    var selectedId by remember(applied?.option?.id) { mutableStateOf(applied?.option?.id) }
    var manualType by remember { mutableStateOf("percentage") }
    var manualInput by remember { mutableStateOf("") }
    val subtotal = lines.sumOf(CartLine::lineTotal)
    val entered = if (manualType == "fixed") {
        parseAmountInput(manualInput)?.toDouble()
    } else {
        manualInput.toDoubleOrNull()
    }
    val previewOption = if (mode == "saved") {
        options.firstOrNull { it.id == selectedId }
    } else {
        entered?.takeIf { it > 0 && (manualType != "percentage" || it <= 100) }?.let { value ->
            DiscountOption(
                id = null,
                name = if (manualType == "percentage") "${value.cleanNumber()}% manual discount" else "Manual discount",
                type = if (manualType == "percentage") "percentage" else "fixed_amount",
                value = value,
            )
        }
    }
    val previewAmount = previewOption?.let { DiscountRules.calculate(it, lines) } ?: 0L

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text("Apply discount", style = MaterialTheme.typography.titleLarge)
            Text("Subtotal ${formatUgx(subtotal)}", color = Slate500, modifier = Modifier.padding(top = 2.dp))

            if (options.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = mode == "saved",
                        onClick = { mode = "saved" },
                        label = { Text("Saved (${options.size})") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = mode == "manual",
                        onClick = { mode = "manual" },
                        label = { Text("Manual") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (mode == "saved") {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(options, key = { it.id.orEmpty() }) { option ->
                        val selected = selectedId == option.id
                        Surface(
                            onClick = { selectedId = option.id },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) Primary100 else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (selected) Primary700 else Slate200),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(option.name, fontWeight = FontWeight.Bold)
                                    Text(option.description(), style = MaterialTheme.typography.bodySmall, color = Slate500)
                                }
                                if (option.requiresApproval) StatusPill("May need approval", containerColor = Amber50, contentColor = Amber700)
                            }
                        }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = manualType == "percentage",
                        onClick = {
                            if (manualType != "percentage") manualInput = ""
                            manualType = "percentage"
                        },
                        label = { Text("Percentage") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = manualType == "fixed",
                        onClick = {
                            if (manualType != "fixed") manualInput = ""
                            manualType = "fixed"
                        },
                        label = { Text("Fixed amount") },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { value ->
                        manualInput = if (manualType == "fixed") {
                            sanitizeAmountInput(value)
                        } else {
                            value.filter { it.isDigit() || it == '.' }.take(10)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = { Text(if (manualType == "percentage") "Discount percentage" else "Discount amount") },
                    suffix = { Text(if (manualType == "percentage") "%" else "UGX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (manualType == "fixed") KeyboardType.Number else KeyboardType.Decimal,
                    ),
                    visualTransformation = if (manualType == "fixed") {
                        AmountGroupingVisualTransformation
                    } else {
                        VisualTransformation.None
                    },
                    supportingText = {
                        if (manualType == "percentage" && (entered ?: 0.0) > 100) Text("Enter a value from 0 to 100")
                    },
                )
                if (manualType == "percentage") {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(5, 10, 15, 20, 25, 50).forEach { percentage ->
                            FilterChip(
                                selected = manualInput == percentage.toString(),
                                onClick = { manualInput = percentage.toString() },
                                label = { Text("$percentage%") },
                            )
                        }
                    }
                }
            }

            if (previewAmount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    color = Primary100,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Discount preview", style = MaterialTheme.typography.labelMedium, color = Primary800)
                            Text("New total ${formatUgx((subtotal - previewAmount).coerceAtLeast(0))}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("− ${formatUgx(previewAmount)}", color = Primary700, fontWeight = FontWeight.Bold, fontFamily = MoneyFontFamily)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (applied != null) {
                    OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f).height(50.dp), shape = ButtonShape) {
                        Text("Remove")
                    }
                }
                Button(
                    onClick = { previewOption?.let(onApply) },
                    enabled = previewAmount > 0,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = ButtonShape,
                ) { Text(if (applied == null) "Apply discount" else "Update discount") }
            }
        }
    }
}

private fun DiscountOption.description(): String {
    val valueText = if (type == "percentage") "${value.cleanNumber()}% off" else "${formatUgx(value.toLong())} off"
    val scopeText = when (scope) {
        "transaction" -> "entire sale"
        "category" -> "selected category"
        "brand" -> "selected brand"
        "item" -> "specific item"
        else -> scope
    }
    val minimum = minimumSpend?.let { " · min ${formatUgx(it)}" }.orEmpty()
    return "$valueText · $scopeText$minimum"
}

private fun Double.cleanNumber(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()

@Composable
private fun PersistentCartLine(line: CartLine, onIncrease: (String) -> Unit, onDecrease: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ProductVisual(line.product.tone, Modifier.size(width = 48.dp, height = 58.dp), iconSize = 28.dp)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(line.product.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line.product.variant, style = MaterialTheme.typography.bodySmall, color = Slate500)
            MoneyText(line.lineTotal, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium)
        }
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDecrease(line.product.id) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Decrease quantity", tint = Primary800)
                }
                Text(line.quantity.toString(), modifier = Modifier.width(24.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                IconButton(onClick = { onIncrease(line.product.id) }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = "Increase quantity", tint = Primary800)
                }
            }
        }
    }
}

@Composable
private fun PersistentTotals(
    subtotal: Long,
    savings: Long,
    discountAmount: Long,
    discountName: String?,
    total: Long,
) {
    PosCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryRow("Subtotal", formatUgx(subtotal))
            SummaryRow("Tax", "Included")
            SummaryRow("Promotions", "− ${formatUgx(savings)}", MoneyGreen700)
            if (discountAmount > 0) {
                SummaryRow(discountName ?: "Discount", "- ${formatUgx(discountAmount)}", Primary700)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Slate200))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                MoneyText(total)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = color)
        Text(value, color = color, fontFamily = MoneyFontFamily)
    }
}
