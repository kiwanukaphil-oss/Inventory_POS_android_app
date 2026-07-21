package com.kline.inventorypos.feature.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kline.inventorypos.core.common.formatUgx
import com.kline.inventorypos.core.designsystem.Error50
import com.kline.inventorypos.core.designsystem.Error700
import com.kline.inventorypos.core.designsystem.FocusedHeader
import com.kline.inventorypos.core.designsystem.MoneyFontFamily
import com.kline.inventorypos.core.designsystem.MoneyGreen50
import com.kline.inventorypos.core.designsystem.MoneyGreen700
import com.kline.inventorypos.core.designsystem.PosCard
import com.kline.inventorypos.core.designsystem.Primary50
import com.kline.inventorypos.core.designsystem.Primary700
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.model.AftercareResult
import com.kline.inventorypos.core.model.AftercareRules
import com.kline.inventorypos.core.model.ExchangeNewLine
import com.kline.inventorypos.core.model.ExchangeRequest
import com.kline.inventorypos.core.model.Product
import com.kline.inventorypos.core.model.ReturnLine
import com.kline.inventorypos.core.model.ReturnRequest
import com.kline.inventorypos.core.model.ReturnableItem
import com.kline.inventorypos.core.model.SaleSummary

private val Reasons = listOf(
    "wrong_size" to "Wrong size",
    "defective" to "Defective",
    "customer_request" to "Customer request",
    "changed_mind" to "Changed mind",
    "other" to "Other",
)
private val Conditions = listOf("sellable" to "Sellable", "damaged" to "Damaged", "defective" to "Defective")

@Composable
fun ReturnWorkflowScreen(
    sale: SaleSummary,
    state: ActivityUiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onSubmit: (ReturnRequest) -> Unit,
    onComplete: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val quantities = remember { mutableStateMapOf<String, Int>() }
    val conditions = remember { mutableStateMapOf<String, String>() }
    var type by rememberSaveable { mutableStateOf("refund") }
    var reason by rememberSaveable { mutableStateOf("customer_request") }
    var notes by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf("original_payment") }
    LaunchedEffect(sale.id) { onLoad() }
    val lines = state.returnableItems.mapNotNull { item ->
        quantities[item.saleItemId]?.takeIf { it > 0 }?.let { ReturnLine(item, it, conditions[item.saleItemId] ?: "sellable") }
    }
    val request = ReturnRequest(sale.id, type, reason, notes, if (type == "refund") method else null, lines)

    AftercareScaffold(
        title = "Return ${sale.receiptNumber}",
        step = step,
        steps = listOf("Items", "Details", "Confirm"),
        onBack = if (step == 0) onBack else ({ step -= 1 }),
        bottom = {
            if (state.aftercareResult == null) {
                Button(
                    onClick = { if (step < 2) step++ else onSubmit(request) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = lines.isNotEmpty() && !state.aftercareWorking && !state.aftercareUncertain &&
                        !(step >= 1 && type == "refund" && method == "cash" && !state.hasOpenRegister) &&
                        !(step >= 1 && type == "store_credit" && sale.customerName == null),
                ) {
                    if (state.aftercareWorking) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                    else Text(if (step == 2) "Confirm return" else "Continue")
                }
            }
        },
    ) {
        when {
            state.aftercareResult != null -> ResultCard(state.aftercareResult, onComplete)
            state.aftercareLoading -> LoadingBlock()
            state.returnableItems.isEmpty() -> EmptyBlock("Nothing left to return", "All items on this receipt have already been returned or exchanged.")
            step == 0 -> {
                Instruction("Select returned items", "Only remaining eligible quantities are shown.")
                state.returnableItems.forEach { item ->
                    ReturnItemSelector(item, quantities[item.saleItemId] ?: 0, conditions[item.saleItemId] ?: "sellable", { quantities[item.saleItemId] = it }, { conditions[item.saleItemId] = it })
                }
            }
            step == 1 -> {
                Instruction("How should this be resolved?", "The server recalculates the final amount from the original sale.")
                ChoiceRow(listOf("refund" to "Refund", "store_credit" to "Store credit"), type) { type = it }
                if (type == "store_credit" && sale.customerName == null) WarningCard("Store credit needs a customer attached to the original sale.")
                Text("Reason", fontWeight = FontWeight.Bold)
                ChoiceRow(Reasons, reason) { reason = it }
                if (type == "refund") {
                    Text("Refund method", fontWeight = FontWeight.Bold)
                    ChoiceRow(listOf("original_payment" to "Original payment", "cash" to "Cash"), method) { method = it }
                    if (method == "cash" && !state.hasOpenRegister) WarningCard("Open a register before issuing a cash refund.")
                }
                OutlinedTextField(notes, { notes = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 2)
            }
            else -> {
                Instruction("Review return", "This action updates stock and sale totals and cannot be undone here.")
                ReviewLines(lines.map { "${it.quantity} × ${it.item.productName}" to it.estimatedValue })
                SummaryMoney("Estimated return", AftercareRules.estimatedReturn(lines))
                SummaryText("Resolution", if (type == "store_credit") "Store credit" else method.label())
                SummaryText("Reason", reason.label())
                if (state.aftercareUncertain) WarningCard("Submission status is unknown. Verify this receipt in Activity before doing anything else.")
            }
        }
    }
}

@Composable
fun ExchangeWorkflowScreen(
    sale: SaleSummary,
    products: List<Product>,
    state: ActivityUiState,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onPreview: (ExchangeRequest) -> Unit,
    onSubmit: (ExchangeRequest) -> Unit,
    onComplete: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val returned = remember { mutableStateMapOf<String, Int>() }
    val conditions = remember { mutableStateMapOf<String, String>() }
    val replacements = remember { mutableStateMapOf<String, Int>() }
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("return_and_resale") }
    var reason by rememberSaveable { mutableStateOf("wrong_size") }
    var notes by rememberSaveable { mutableStateOf("") }
    var settlement by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(sale.id) { onLoad() }
    val returnLines = state.returnableItems.mapNotNull { item -> returned[item.saleItemId]?.takeIf { it > 0 }?.let { ReturnLine(item, it, conditions[item.saleItemId] ?: "sellable") } }
    val newLines = products.mapNotNull { product -> replacements[product.id]?.takeIf { it > 0 }?.let { ExchangeNewLine(product, it) } }
    fun request(settlementMethod: String? = settlement.takeIf(String::isNotBlank)) = ExchangeRequest(sale.id, mode, reason, notes, returnLines, newLines, settlementMethod)
    val preview = state.exchangePreview
    val needsSettlement = preview?.netAmount != 0L
    val validSettlement = !needsSettlement || (
        settlement.isNotBlank() &&
            !(settlement == "cash" && !state.hasOpenRegister) &&
            !(preview?.netAmount ?: 0L < 0 && settlement == "store_credit" && sale.customerName == null)
        )

    AftercareScaffold(
        title = "Exchange ${sale.receiptNumber}",
        step = step,
        steps = listOf("Return", "Replace", "Settle"),
        onBack = if (step == 0) onBack else ({ step -= 1 }),
        bottom = {
            if (state.aftercareResult == null) Button(
                onClick = {
                    when (step) {
                        0 -> step = 1
                        1 -> { onPreview(request(null)); step = 2 }
                        else -> onSubmit(request())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = returnLines.isNotEmpty() && (step < 1 || newLines.isNotEmpty()) &&
                    (step < 2 || (preview != null && validSettlement)) && !state.aftercareWorking && !state.aftercareUncertain,
            ) {
                if (state.aftercareWorking) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                else Text(if (step == 2) "Confirm exchange" else if (step == 1) "Calculate difference" else "Choose replacements")
            }
        },
    ) {
        when {
            state.aftercareResult != null -> ResultCard(state.aftercareResult, onComplete)
            state.aftercareLoading -> LoadingBlock()
            state.returnableItems.isEmpty() -> EmptyBlock("Nothing left to exchange", "No eligible quantities remain on this receipt.")
            step == 0 -> {
                Instruction("What is coming back?", "Record the condition carefully; sellable stock is returned to availability.")
                state.returnableItems.forEach { item -> ReturnItemSelector(item, returned[item.saleItemId] ?: 0, conditions[item.saleItemId] ?: "sellable", { returned[item.saleItemId] = it }, { conditions[item.saleItemId] = it }) }
            }
            step == 1 -> {
                Instruction("Choose replacements", "Available stock is checked again when the exchange is committed.")
                ChoiceRow(listOf("return_and_resale" to "Different item", "same_item_swap" to "Same-item swap"), mode) { mode = it }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search products or SKU") }, singleLine = true)
                products.filter { query.isBlank() || it.name.contains(query, true) || it.sku.contains(query, true) }.take(30).forEach { product ->
                    ProductSelector(product, replacements[product.id] ?: 0) { replacements[product.id] = it }
                }
            }
            else -> {
                Instruction("Settle the difference", "Amounts below are calculated by the server from the original sale and current replacement prices.")
                if (state.aftercareWorking || preview == null) LoadingBlock() else {
                    SummaryMoney("Returned value", preview.returnedValue)
                    SummaryMoney("Replacement value", preview.newItemsValue)
                    SummaryMoney(if (preview.netAmount > 0) "Customer pays" else if (preview.netAmount < 0) "Store refunds" else "Even exchange", kotlin.math.abs(preview.netAmount), strong = true)
                    if (preview.netAmount > 0) {
                        ChoiceRow(listOf("cash" to "Cash", "visa" to "Visa", "mastercard" to "Mastercard", "airtel_money" to "Airtel", "mtn_mobile_money" to "MTN MoMo"), settlement) { settlement = it }
                    } else if (preview.netAmount < 0) {
                        ChoiceRow(listOf("original_payment" to "Original payment", "cash" to "Cash", "store_credit" to "Store credit"), settlement) { settlement = it }
                        if (settlement == "cash" && !state.hasOpenRegister) WarningCard("A cash refund requires an open register.")
                        if (settlement == "store_credit" && sale.customerName == null) WarningCard("Store credit requires a customer on the original sale.")
                    }
                    Text("Reason", fontWeight = FontWeight.Bold)
                    ChoiceRow(Reasons, reason) { reason = it }
                    OutlinedTextField(notes, { notes = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 2)
                    if (state.aftercareUncertain) WarningCard("Submission status is unknown. Verify Activity before attempting another exchange.")
                }
            }
        }
    }
}

@Composable
private fun AftercareScaffold(title: String, step: Int, steps: List<String>, onBack: () -> Unit, bottom: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(title, "Financial aftercare", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { index, label ->
                Surface(Modifier.weight(1f), color = if (index <= step) Primary50 else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small) {
                    Text("${index + 1}. $label", Modifier.padding(vertical = 8.dp), color = if (index <= step) Primary700 else Slate500, textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content(); Spacer(Modifier.height(6.dp)) }
        Surface(shadowElevation = 8.dp) { Box(Modifier.fillMaxWidth().padding(12.dp)) { bottom() } }
    }
}

@Composable private fun Instruction(title: String, subtitle: String) { Column { Text(title, style = MaterialTheme.typography.titleLarge); Text(subtitle, color = Slate500, style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun ReturnItemSelector(item: ReturnableItem, quantity: Int, condition: String, onQuantity: (Int) -> Unit, onCondition: (String) -> Unit) {
    PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(item.productName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${item.variant} · ${item.sku}", color = Slate500, style = MaterialTheme.typography.bodySmall); Text("Up to ${item.maxReturnable} · ${formatUgx(item.unitPrice)} each", color = Slate500, style = MaterialTheme.typography.labelSmall) }
            QuantityControl(quantity, item.maxReturnable, onQuantity)
        }
        if (quantity > 0) ChoiceRow(Conditions, condition, onCondition)
    } }
}

@Composable
private fun ProductSelector(product: Product, quantity: Int, onQuantity: (Int) -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(product.name, fontWeight = FontWeight.Bold); Text("${product.variant} · ${product.sku}", color = Slate500, style = MaterialTheme.typography.bodySmall); Text("${formatUgx(product.price)} · ${product.stock} available", color = Slate500, style = MaterialTheme.typography.labelSmall) }
        QuantityControl(quantity, product.stock, onQuantity)
    } }
}

@Composable private fun QuantityControl(value: Int, max: Int, onChange: (Int) -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { IconButton({ onChange((value - 1).coerceAtLeast(0)) }, enabled = value > 0) { Icon(Icons.Outlined.Remove, "Decrease") }; Text(value.toString(), fontWeight = FontWeight.Bold); IconButton({ onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) { Icon(Icons.Outlined.Add, "Increase") } } }

@Composable private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { options.forEach { (value, label) -> FilterChip(selected == value, { onSelect(value) }, { Text(label) }) } } }

@Composable private fun ReviewLines(lines: List<Pair<String, Long>>) { PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { lines.forEach { (label, amount) -> SummaryMoney(label, amount) } } } }
@Composable private fun SummaryMoney(label: String, amount: Long, strong: Boolean = false) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal); Text(formatUgx(amount), fontFamily = MoneyFontFamily, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal) } }
@Composable private fun SummaryText(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Slate500); Text(value, fontWeight = FontWeight.Bold) } }
@Composable private fun WarningCard(message: String) { Surface(Modifier.fillMaxWidth(), color = Error50, contentColor = Error700, shape = MaterialTheme.shapes.medium) { Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) } }
@Composable private fun LoadingBlock() { Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
@Composable private fun EmptyBlock(title: String, message: String) { Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, fontWeight = FontWeight.Bold); Text(message, color = Slate500, style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun ResultCard(result: AftercareResult, onComplete: () -> Unit) { Column(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(58.dp), tint = MoneyGreen700); Text(result.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(result.reference, color = Slate500); Text(formatUgx(kotlin.math.abs(result.amount)), fontFamily = MoneyFontFamily, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Surface(color = MoneyGreen50, contentColor = MoneyGreen700, shape = MaterialTheme.shapes.medium) { Text(result.message, Modifier.padding(12.dp)) }; Button(onComplete, Modifier.fillMaxWidth().height(52.dp)) { Text("Back to activity") } } }

private fun String.label(): String = (Reasons + Conditions + listOf("original_payment" to "Original payment", "cash" to "Cash", "store_credit" to "Store credit")).firstOrNull { it.first == this }?.second ?: replace('_', ' ').replaceFirstChar(Char::uppercase)
