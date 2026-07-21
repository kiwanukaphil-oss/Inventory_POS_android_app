package com.kline.inventorypos.feature.pos

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.kline.inventorypos.core.designsystem.MoneyFontFamily
import com.kline.inventorypos.core.designsystem.MoneyGreen50
import com.kline.inventorypos.core.designsystem.MoneyGreen700
import com.kline.inventorypos.core.designsystem.MoneyText
import com.kline.inventorypos.core.designsystem.PosCard
import com.kline.inventorypos.core.designsystem.Primary100
import com.kline.inventorypos.core.designsystem.Primary50
import com.kline.inventorypos.core.designsystem.Primary700
import com.kline.inventorypos.core.designsystem.Primary800
import com.kline.inventorypos.core.designsystem.ProductVisual
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate200
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.designsystem.Slate950
import com.kline.inventorypos.core.designsystem.StatusPill
import com.kline.inventorypos.core.model.CartLine
import com.kline.inventorypos.core.model.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(
    products: List<Product>,
    cartItemCount: Int,
    cartTotal: Long,
    onAddProduct: (Product) -> Unit,
    onOpenCart: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All items") }
    var variantProduct by remember { mutableStateOf<Product?>(null) }
    val categories = listOf("All items", "Shirts", "Trousers", "Jackets", "Accessories")
    val visible = products.filter { product ->
        (selectedCategory == "All items" || product.category == selectedCategory) &&
            (query.isBlank() || product.name.contains(query, true) || product.sku.contains(query, true))
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = { Text("Search name, SKU or barcode") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        trailingIcon = {
                            FilledIconButton(onClick = { onMessage("Camera barcode scanner is queued for the hardware milestone") }) {
                                Icon(Icons.Outlined.QrCodeScanner, contentDescription = "Scan barcode")
                            }
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category) },
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${visible.size} products", style = MaterialTheme.typography.bodySmall, color = Slate500)
                        Text("Price includes tax", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 94.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        onAdd = {
                            if (product.requiresVariantChoice) variantProduct = product else onAddProduct(product)
                        },
                    )
                }
            }
        }

        if (cartItemCount > 0) {
            Surface(
                onClick = onOpenCart,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
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
                    Text(formatUgx(cartTotal), style = MaterialTheme.typography.labelMedium.copy(fontFamily = MoneyFontFamily), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    variantProduct?.let { product ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var colour by remember { mutableStateOf("Sky blue") }
        var size by remember { mutableStateOf("M") }
        ModalBottomSheet(
            onDismissRequest = { variantProduct = null },
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductVisual(product.tone, Modifier.size(62.dp), iconSize = 32.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleLarge)
                        MoneyText(product.price, style = MaterialTheme.typography.titleSmall, color = Primary800)
                    }
                }
                Text("Colour", modifier = Modifier.padding(top = 20.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Sky blue", "White", "Sage").forEach { option ->
                        FilterChip(selected = colour == option, onClick = { colour = option }, label = { Text(option) })
                    }
                }
                Text("Size", modifier = Modifier.padding(top = 14.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("S", "M", "L", "XL").forEach { option ->
                        FilterChip(selected = size == option, onClick = { size = option }, label = { Text(option) })
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$colour · $size · Qty 1", color = Slate500)
                    MoneyText(product.price, style = MaterialTheme.typography.titleSmall)
                }
                Button(
                    onClick = {
                        onAddProduct(product.copy(variant = "$colour · $size"))
                        variantProduct = null
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = ButtonShape,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  Add to cart")
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onAdd: () -> Unit) {
    PosCard(Modifier.fillMaxWidth()) {
        Column {
            Box {
                ProductVisual(product.tone, Modifier.fillMaxWidth().height(116.dp))
                StatusPill(
                    text = if (product.stock <= 4) "Only ${product.stock} left" else "${product.stock} in stock",
                    modifier = Modifier.padding(8.dp),
                    containerColor = if (product.stock <= 4) Amber50 else Color.White.copy(alpha = 0.9f),
                    contentColor = if (product.stock <= 4) Amber700 else MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(Modifier.padding(10.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(product.sku, style = MaterialTheme.typography.bodySmall, color = Slate500)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MoneyText(product.price, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    FilledIconButton(onClick = onAdd, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add ${product.name}")
                    }
                }
            }
        }
    }
}

@Composable
fun CartScreen(
    lines: List<CartLine>,
    onBack: () -> Unit,
    onIncrease: (String) -> Unit,
    onDecrease: (String) -> Unit,
    onPayment: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val total = lines.sumOf { it.lineTotal }
    val units = lines.sumOf { it.quantity }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(
            title = "Current sale",
            subtitle = "$units items",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { onMessage("Sale options: clear, note, and no sale") }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Sale options")
                }
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PosCard(Modifier.fillMaxWidth().clickable { onMessage("Customer attached · Amina Nakato") }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(Primary100, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Badge, contentDescription = null, tint = Primary800)
                        }
                        Column(Modifier.padding(start = 11.dp).weight(1f)) {
                            Text("Add customer", style = MaterialTheme.typography.titleSmall)
                            Text("Enable loyalty, prepaid and credit", style = MaterialTheme.typography.bodySmall, color = Slate500)
                        }
                    }
                }
            }
            if (lines.isEmpty()) {
                item {
                    PosCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Slate500, modifier = Modifier.size(40.dp))
                            Text("Your cart is empty", modifier = Modifier.padding(top = 8.dp), color = Slate500)
                        }
                    }
                }
            } else {
                item {
                    PosCard(Modifier.fillMaxWidth()) {
                        lines.forEachIndexed { index, line ->
                            CartLineRow(line, onIncrease, onDecrease)
                            if (index != lines.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100))
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onMessage("Sale held locally and queued for sync") }, modifier = Modifier.weight(1f).height(48.dp), shape = ButtonShape) {
                        Icon(Icons.Outlined.Pause, contentDescription = null)
                        Text("  Hold sale")
                    }
                    OutlinedButton(onClick = { onMessage("Approval is required above the configured threshold") }, modifier = Modifier.weight(1f).height(48.dp), shape = ButtonShape) {
                        Icon(Icons.Outlined.LocalOffer, contentDescription = null)
                        Text("  Discount")
                    }
                }
            }
            item { TotalsCard(total) }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Button(
                onClick = onPayment,
                enabled = lines.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(12.dp).height(54.dp),
                shape = ButtonShape,
            ) {
                Text("Proceed to payment")
                Spacer(Modifier.weight(1f))
                Text(formatUgx(total), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CartLineRow(line: CartLine, onIncrease: (String) -> Unit, onDecrease: (String) -> Unit) {
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
private fun TotalsCard(total: Long) {
    PosCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TotalRow("Subtotal", formatUgx(total))
            TotalRow("Tax", "Included")
            TotalRow("Automatic promotions", "− UGX 0", MoneyGreen700)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Slate200))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                MoneyText(total)
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MoneyFontFamily), color = color)
    }
}

private data class PaymentMethod(val id: String, val label: String, val icon: ImageVector, val hint: String? = null)

@Composable
fun PaymentScreen(
    total: Long,
    itemCount: Int,
    onBack: () -> Unit,
    onComplete: (Long) -> Unit,
    onMessage: (String) -> Unit,
) {
    val methods = listOf(
        PaymentMethod("cash", "Cash", Icons.Outlined.LocalAtm),
        PaymentMethod("card", "Card", Icons.Outlined.CreditCard),
        PaymentMethod("mobile", "Mobile money", Icons.Outlined.PhoneAndroid),
        PaymentMethod("voucher", "Voucher", Icons.AutoMirrored.Outlined.ReceiptLong),
        PaymentMethod("prepaid", "Prepaid", Icons.Outlined.Wallet, "Customer only"),
        PaymentMethod("credit", "Credit", Icons.Outlined.AlternateEmail, "Customer only"),
    )
    var selectedMethod by remember { mutableStateOf("cash") }
    var tendered by remember(total) { mutableLongStateOf(total) }
    var tenderText by remember(total) { mutableStateOf(total.toString()) }
    val change = (tendered - total).coerceAtLeast(0L)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        FocusedHeader("Take payment", "Sale #10482 · $itemCount items", onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
        ) {
            PosCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("AMOUNT DUE", style = MaterialTheme.typography.labelSmall, color = Slate500)
                    MoneyText(total, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.headlineSmall)
                }
            }
            Text("Select payment method", modifier = Modifier.padding(top = 18.dp, bottom = 8.dp), style = MaterialTheme.typography.labelLarge)
            methods.chunked(3).forEach { rowMethods ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowMethods.forEach { method ->
                        PaymentMethodTile(
                            method = method,
                            selected = selectedMethod == method.id,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                selectedMethod = method.id
                                tendered = total
                                tenderText = total.toString()
                            },
                        )
                    }
                }
            }
            PosCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (selectedMethod == "cash") "Cash received" else "Amount allocated", style = MaterialTheme.typography.titleSmall)
                        StatusPill("Selected")
                    }
                    OutlinedTextField(
                        value = tenderText,
                        onValueChange = { value ->
                            tenderText = sanitizeAmountInput(value)
                            tendered = parseAmountInput(tenderText) ?: 0L
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        prefix = { Text("UGX  ") },
                        textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = AmountGroupingVisualTransformation,
                        shape = RoundedCornerShape(13.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(total to "Exact", 500_000L to "500,000", 600_000L to "600,000").forEach { (amount, label) ->
                            OutlinedButton(
                                onClick = { tendered = amount; tenderText = amount.toString() },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MoneyGreen50,
                        contentColor = MoneyGreen700,
                    ) {
                        Row(Modifier.padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Change to return", style = MaterialTheme.typography.labelMedium)
                            Text(formatUgx(change), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Button(
                onClick = { if (tendered >= total) onComplete(tendered) else onMessage("The tendered amount is below the amount due") },
                enabled = tendered >= total,
                modifier = Modifier.fillMaxWidth().padding(12.dp).height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Slate950),
                shape = ButtonShape,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text("  Complete ${methods.first { it.id == selectedMethod }.label.lowercase()} sale")
            }
        }
    }
}

@Composable
private fun PaymentMethodTile(method: PaymentMethod, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(84.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Primary700 else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Slate200),
    ) {
        Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(method.icon, contentDescription = null, modifier = Modifier.size(25.dp))
            Text(method.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            method.hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
fun ReceiptScreen(
    total: Long,
    itemCount: Int,
    change: Long,
    cashierName: String,
    branchName: String,
    onNewSale: () -> Unit,
    onMessage: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(88.dp).background(Primary50, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(48.dp), tint = Primary700)
        }
        Text("Sale complete", modifier = Modifier.padding(top = 18.dp), style = MaterialTheme.typography.headlineSmall)
        Text("Payment accepted · Inventory updated", style = MaterialTheme.typography.bodyMedium, color = Slate500)
        PosCard(Modifier.fillMaxWidth().padding(top = 22.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Receipt #KLM-10482", style = MaterialTheme.typography.titleSmall)
                        Text("21 Jul 2026 · 09:41", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    }
                    Text("Cash", style = MaterialTheme.typography.labelMedium, color = Slate500)
                }
                Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total paid", style = MaterialTheme.typography.bodySmall, color = Slate500)
                    MoneyText(total, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.headlineSmall)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Slate200))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    ReceiptMeta("Items", "$itemCount items", Modifier.weight(1f))
                    ReceiptMeta("Change given", formatUgx(change), Modifier.weight(1f), Alignment.End)
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    ReceiptMeta("Cashier", cashierName, Modifier.weight(1f))
                    ReceiptMeta("Branch", branchName, Modifier.weight(1f), Alignment.End)
                }
                Surface(Modifier.fillMaxWidth().padding(top = 14.dp), shape = RoundedCornerShape(11.dp), color = Slate100) {
                    Row(Modifier.padding(11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Print, contentDescription = null, modifier = Modifier.size(18.dp), tint = Slate500)
                        Text("  Receipt sent to Register Printer 02", style = MaterialTheme.typography.labelSmall, color = Slate500)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onMessage("Receipt queued for reprint") }, modifier = Modifier.weight(1f).height(48.dp), shape = ButtonShape) {
                Icon(Icons.Outlined.Print, contentDescription = null)
                Text("  Reprint")
            }
            OutlinedButton(onClick = { onMessage("Email receipt form opened") }, modifier = Modifier.weight(1f).height(48.dp), shape = ButtonShape) {
                Icon(Icons.Outlined.Email, contentDescription = null)
                Text("  Email")
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNewSale, modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(54.dp), shape = ButtonShape) {
            Icon(Icons.Filled.PointOfSale, contentDescription = null)
            Text("  Start new sale")
        }
    }
}

@Composable
private fun ReceiptMeta(label: String, value: String, modifier: Modifier, alignment: Alignment.Horizontal = Alignment.Start) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Slate500)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}
