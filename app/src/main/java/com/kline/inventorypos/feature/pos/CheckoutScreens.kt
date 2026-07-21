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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Wallet
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import com.kline.inventorypos.core.common.AmountGroupingVisualTransformation
import com.kline.inventorypos.core.common.formatUgx
import com.kline.inventorypos.core.common.parseAmountInput
import com.kline.inventorypos.core.common.sanitizeAmountInput
import com.kline.inventorypos.core.designsystem.ButtonShape
import com.kline.inventorypos.core.designsystem.FocusedHeader
import com.kline.inventorypos.core.designsystem.MoneyFontFamily
import com.kline.inventorypos.core.designsystem.MoneyGreen50
import com.kline.inventorypos.core.designsystem.MoneyGreen700
import com.kline.inventorypos.core.designsystem.MoneyText
import com.kline.inventorypos.core.designsystem.PosCard
import com.kline.inventorypos.core.designsystem.Primary50
import com.kline.inventorypos.core.designsystem.Primary700
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate200
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.model.CartLine
import com.kline.inventorypos.core.model.CheckoutAttempt
import com.kline.inventorypos.core.model.CheckoutAttemptStatus
import com.kline.inventorypos.core.model.CheckoutQuote
import com.kline.inventorypos.core.model.ConfirmedReceipt
import com.kline.inventorypos.core.model.CustomerSummary
import com.kline.inventorypos.core.model.PaymentLeg
import com.kline.inventorypos.data.checkout.PaymentRules

private data class PaymentChoice(val id: String, val label: String, val icon: ImageVector, val customerOnly: Boolean = false)
private data class PaymentDraft(val method: String, val amount: String, val reference: String = "")

private val PaymentChoices = listOf(
    PaymentChoice("cash", "Cash", Icons.Outlined.LocalAtm),
    PaymentChoice("card", "Card", Icons.Outlined.CreditCard),
    PaymentChoice("mobile_money", "Mobile money", Icons.Outlined.PhoneAndroid),
    PaymentChoice("prepaid", "Prepaid", Icons.Outlined.Wallet, true),
    PaymentChoice("credit", "Credit", Icons.Outlined.AlternateEmail, true),
)

@Composable
fun CheckoutPaymentScreen(
    quote: CheckoutQuote?,
    lines: List<CartLine>,
    customer: CustomerSummary?,
    working: Boolean,
    attempt: CheckoutAttempt?,
    onBack: () -> Unit,
    onPrepare: () -> Unit,
    onSubmit: (List<PaymentLeg>) -> Unit,
    onAcknowledgeUncertain: () -> Unit,
) {
    LaunchedEffect(lines, customer?.id) { onPrepare() }
    if (quote == null) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            FocusedHeader("Take payment", "Validating stock, tax and promotions", onBack)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                if (working) {
                    CircularProgressIndicator()
                    Text("Preparing server-verified totals…", modifier = Modifier.padding(top = 14.dp), color = Slate500)
                } else {
                    Text("The checkout preview is not ready.", color = Slate500)
                    Button(onClick = onPrepare, modifier = Modifier.padding(top = 12.dp)) { Text("Try again") }
                }
            }
        }
        return
    }

    var drafts by remember(quote.total) { mutableStateOf(listOf(PaymentDraft("cash", quote.total.toString()))) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    val payments = drafts.mapNotNull { draft ->
        parseAmountInput(draft.amount)?.takeIf { it > 0 }?.let { PaymentLeg(draft.method, it, draft.reference.takeIf(String::isNotBlank)) }
    }
    val validationError = runCatching { PaymentRules.requireValid(payments, quote.total, customer != null) }.exceptionOrNull()?.message
    val allocated = payments.sumOf { it.amount }
    val remaining = (quote.total - allocated).coerceAtLeast(0)
    val change = (allocated - quote.total).coerceAtLeast(0)
    val blocked = attempt?.status in setOf(CheckoutAttemptStatus.UNCERTAIN, CheckoutAttemptStatus.PENDING_APPROVAL)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Take payment", "${lines.sumOf { it.quantity }} items · server checked", onBack)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PosCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AMOUNT DUE", style = MaterialTheme.typography.labelSmall, color = Slate500)
                        MoneyText(quote.total, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.headlineSmall)
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal ${formatUgx(quote.subtotal)}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                            Text("Tax ${formatUgx(quote.taxAmount)}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                        }
                        if (quote.promotionSavings > 0) {
                            Text("Promotions − ${formatUgx(quote.promotionSavings)}", color = MoneyGreen700, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                if (quote.discountAmount > 0) {
                    Text(
                        "Discount - ${formatUgx(quote.discountAmount)}",
                        color = MoneyGreen700,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("Payment methods", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaymentChoices.forEach { choice ->
                        val existing = drafts.indexOfFirst { it.method == choice.id }
                        FilterChip(
                            selected = existing >= 0,
                            enabled = !choice.customerOnly || customer != null,
                            onClick = {
                                if (existing >= 0) selectedIndex = existing
                                else if (remaining > 0) {
                                    drafts = drafts + PaymentDraft(choice.id, remaining.toString())
                                    selectedIndex = drafts.lastIndex
                                }
                            },
                            leadingIcon = { Icon(choice.icon, contentDescription = null, Modifier.size(18.dp)) },
                            label = { Text(choice.label) },
                        )
                    }
                }
                if (customer == null) Text("Attach a customer to use prepaid or credit.", style = MaterialTheme.typography.bodySmall, color = Slate500)
            }
            items(drafts.indices.toList(), key = { drafts[it].method }) { index ->
                val draft = drafts[index]
                val choice = PaymentChoices.first { it.id == draft.method }
                PosCard(
                    Modifier.fillMaxWidth().clickable { selectedIndex = index },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(choice.icon, contentDescription = null, tint = Primary700)
                            Text(choice.label, Modifier.padding(start = 8.dp).weight(1f), fontWeight = FontWeight.Bold)
                            if (drafts.size > 1) {
                                IconButton(onClick = {
                                    drafts = drafts.filterIndexed { itemIndex, _ -> itemIndex != index }
                                    selectedIndex = 0
                                }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove ${choice.label}") }
                            }
                        }
                        OutlinedTextField(
                            value = draft.amount,
                            onValueChange = { value -> drafts = drafts.updated(index, draft.copy(amount = sanitizeAmountInput(value))) },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            label = { Text("Amount (UGX)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            visualTransformation = AmountGroupingVisualTransformation,
                            singleLine = true,
                        )
                        if (draft.method != "cash") {
                            OutlinedTextField(
                                value = draft.reference,
                                onValueChange = { value -> drafts = drafts.updated(index, draft.copy(reference = value)) },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                label = { Text("Reference (optional)") },
                                singleLine = true,
                            )
                        } else if (selectedIndex == index) {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(quote.total, quote.total + 10_000, quote.total + 50_000).distinct().forEach { amount ->
                                    OutlinedButton(
                                        onClick = { drafts = drafts.updated(index, draft.copy(amount = amount.toString())) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                    ) { Text(if (amount == quote.total) "Exact" else formatUgx(amount).removePrefix("UGX ")) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                PosCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        PaymentSummaryRow("Allocated", formatUgx(allocated))
                        PaymentSummaryRow("Remaining", formatUgx(remaining))
                        PaymentSummaryRow("Change", formatUgx(change), if (change > 0) MoneyGreen700 else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            if (blocked) {
                item {
                    val isUncertain = attempt?.status == CheckoutAttemptStatus.UNCERTAIN
                    Surface(
                        color = if (isUncertain) MaterialTheme.colorScheme.errorContainer else Primary50,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                attempt?.message ?: "This sale is awaiting approval.",
                                color = if (isUncertain) MaterialTheme.colorScheme.onErrorContainer else Primary700,
                            )
                            if (isUncertain) {
                                OutlinedButton(
                                    onClick = onAcknowledgeUncertain,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                ) { Text("I checked Activity · allow retry") }
                            }
                        }
                    }
                }
            } else if (validationError != null) {
                item { Text(validationError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Button(
                onClick = { onSubmit(payments) },
                enabled = validationError == null && !working && !blocked,
                modifier = Modifier.fillMaxWidth().padding(12.dp).height(54.dp),
                shape = ButtonShape,
            ) {
                if (working) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    Text("Complete sale")
                    Spacer(Modifier.weight(1f))
                    Text(formatUgx(quote.total), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmedReceiptScreen(
    receipt: ConfirmedReceipt,
    working: Boolean,
    onPrint: () -> Unit,
    onEmail: (String) -> Unit,
    onNewSale: () -> Unit,
) {
    var showEmail by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    LaunchedEffect(receipt.saleId) {
        if (!receipt.receiptNumber.startsWith("DEMO-")) onPrint()
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(68.dp).background(MoneyGreen50, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MoneyGreen700, modifier = Modifier.size(42.dp))
                    }
                    Text("Sale complete", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 10.dp))
                    Text(receipt.receiptNumber, fontFamily = MoneyFontFamily, color = Slate500)
                }
            }
            item {
                PosCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(receipt.branchName, fontWeight = FontWeight.Bold)
                        Text("Cashier: ${receipt.cashierName}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                        receipt.customerName?.let { Text("Customer: $it", style = MaterialTheme.typography.bodySmall, color = Slate500) }
                        Box(Modifier.fillMaxWidth().height(1.dp).padding(vertical = 8.dp).background(Slate200))
                        receipt.lines.forEach { line ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("${line.quantity} × ${line.name}", fontWeight = FontWeight.SemiBold)
                                    Text(line.variant, style = MaterialTheme.typography.bodySmall, color = Slate500)
                                }
                                Text(formatUgx(line.lineTotal), fontFamily = MoneyFontFamily)
                            }
                        }
                    }
                }
            }
            item {
                PosCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        PaymentSummaryRow("Subtotal", formatUgx(receipt.subtotal))
                        PaymentSummaryRow("Discounts", "− ${formatUgx(receipt.discountAmount)}", MoneyGreen700)
                        PaymentSummaryRow("Tax", formatUgx(receipt.taxAmount))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Slate200))
                        PaymentSummaryRow("Total", formatUgx(receipt.total), fontWeight = FontWeight.Bold)
                        receipt.payments.forEach { payment ->
                            PaymentSummaryRow(payment.method.replace('_', ' ').replaceFirstChar(Char::uppercase), formatUgx(payment.amount))
                        }
                        if (receipt.change > 0) PaymentSummaryRow("Change", formatUgx(receipt.change), MoneyGreen700)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPrint, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.Outlined.Print, contentDescription = null)
                        Text("  Print")
                    }
                    OutlinedButton(onClick = { showEmail = true }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null)
                        Text("  Email")
                    }
                }
            }
        }
        Button(onClick = onNewSale, modifier = Modifier.fillMaxWidth().padding(12.dp).height(54.dp), shape = ButtonShape) {
            Text("Start new sale")
        }
    }

    if (showEmail) {
        ModalBottomSheet(onDismissRequest = { showEmail = false }) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
                Text("Email receipt", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    label = { Text("Email address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                )
                Button(
                    onClick = { onEmail(email); showEmail = false },
                    enabled = email.isNotBlank() && !working,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Send receipt") }
            }
        }
    }
}

@Composable
private fun PaymentSummaryRow(
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = color, fontWeight = fontWeight)
        Text(value, color = color, fontWeight = fontWeight, fontFamily = MoneyFontFamily, textAlign = TextAlign.End)
    }
}

private fun <T> List<T>.updated(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
