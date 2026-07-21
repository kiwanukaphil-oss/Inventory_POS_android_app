package com.kline.inventorypos.feature.activity

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.kline.inventorypos.core.designsystem.Primary800
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.designsystem.StatusPill
import com.kline.inventorypos.core.model.ConfirmedReceipt
import com.kline.inventorypos.core.model.PaymentLeg
import com.kline.inventorypos.core.model.SaleSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenSale: (SaleSummary) -> Unit,
    onCloseSale: () -> Unit,
    onPrint: (ConfirmedReceipt) -> Unit,
    onEmail: (String) -> Unit,
) {
    if (state.selectedSale != null) {
        SaleDetailScreen(
            sale = state.selectedSale,
            receipt = state.receipt,
            loading = state.detailLoading,
            working = state.working,
            onBack = onCloseSale,
            onPrint = onPrint,
            onEmail = onEmail,
        )
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sales activity", style = MaterialTheme.typography.titleLarge)
                    Text("${state.sales.size} recent receipts · ${state.branchName}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                }
                IconButton(onClick = onRefresh, enabled = !state.loading) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, contentDescription = "Refresh sales")
                }
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search receipt, customer or cashier") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOf("All", "Completed", "Returns").forEach { option ->
                FilterChip(selected = state.filter == option, onClick = { onFilterChange(option) }, label = { Text(option) })
            }
        }
        when {
            state.loading && state.sales.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.visibleSales.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, tint = Slate500, modifier = Modifier.size(40.dp))
                    Text("No matching receipts", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    Text("Try another search or filter.", color = Slate500, textAlign = TextAlign.Center)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.visibleSales, key = SaleSummary::id) { sale -> SaleCard(sale) { onOpenSale(sale) } }
                item { Spacer(Modifier.height(10.dp)) }
            }
        }
    }
}

@Composable
private fun SaleCard(sale: SaleSummary, onClick: () -> Unit) {
    val hasReturn = sale.returnStatus != "none" || sale.status.contains("refund")
    PosCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = if (hasReturn) Error50 else Primary50, contentColor = if (hasReturn) Error700 else Primary800) {
                Icon(if (hasReturn) Icons.Outlined.Refresh else Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("${sale.receiptNumber} · ${sale.customerName ?: "Walk-in"}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${sale.saleDate.displayDate()} · ${sale.cashierName} · ${sale.itemCount} ${if (sale.itemCount == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate500,
                    maxLines = 1,
                )
                sale.staffName?.let { Text("Salesperson: $it", style = MaterialTheme.typography.labelSmall, color = Slate500) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatUgx(sale.total), style = MaterialTheme.typography.labelMedium.copy(fontFamily = MoneyFontFamily), fontWeight = FontWeight.Bold)
                StatusPill(
                    if (hasReturn) sale.returnStatus.replaceFirstChar(Char::uppercase) else sale.status.replace('_', ' ').replaceFirstChar(Char::uppercase),
                    modifier = Modifier.padding(top = 5.dp),
                    containerColor = if (hasReturn) Error50 else MoneyGreen50,
                    contentColor = if (hasReturn) Error700 else MoneyGreen700,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleDetailScreen(
    sale: SaleSummary,
    receipt: ConfirmedReceipt?,
    loading: Boolean,
    working: Boolean,
    onBack: () -> Unit,
    onPrint: (ConfirmedReceipt) -> Unit,
    onEmail: (String) -> Unit,
) {
    var showEmail by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Receipt ${sale.receiptNumber}", sale.saleDate.displayDate(), onBack)
        if (loading || receipt == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    PosCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(sale.customerName ?: "Walk-in customer", fontWeight = FontWeight.Bold)
                                    Text(sale.paymentMethod.friendlyPayment(), style = MaterialTheme.typography.bodySmall, color = Slate500)
                                }
                                Text(formatUgx(receipt.total), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp).height(1.dp).background(Slate100))
                            AttributionRow("Cashier", receipt.cashierName)
                            sale.staffName?.let { AttributionRow("Salesperson", it) }
                            AttributionRow("Branch", receipt.branchName)
                        }
                    }
                }
                item { Text("Items", style = MaterialTheme.typography.titleSmall) }
                items(receipt.lines, key = { "${it.sku}-${it.variant}" }) { line ->
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).background(Primary50, CircleShape), contentAlignment = Alignment.Center) {
                                Text(line.quantity.toString(), color = Primary700, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                Text(line.name, fontWeight = FontWeight.Bold)
                                Text("${line.variant} · ${line.sku}", style = MaterialTheme.typography.bodySmall, color = Slate500)
                            }
                            Text(formatUgx(line.lineTotal), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    PosCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            DetailTotal("Subtotal", receipt.subtotal)
                            if (receipt.discountAmount > 0) DetailTotal("Discounts", -receipt.discountAmount)
                            DetailTotal("Tax", receipt.taxAmount)
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100))
                            DetailTotal("Total", receipt.total, strong = true)
                            receipt.payments.forEach { PaymentRow(it) }
                            if (receipt.change > 0) DetailTotal("Change returned", receipt.change)
                            if (sale.totalReturned > 0) DetailTotal("Returned", -sale.totalReturned)
                        }
                    }
                }
            }
            Surface(shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPrint(receipt) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Print, contentDescription = null)
                        Text("  Reprint")
                    }
                    Button(onClick = { showEmail = true }, modifier = Modifier.weight(1f), enabled = !working) {
                        Icon(Icons.Outlined.AlternateEmail, contentDescription = null)
                        Text("  Email")
                    }
                }
            }
        }
    }
    if (showEmail && receipt != null) {
        EmailReceiptSheet(
            working = working,
            onDismiss = { if (!working) showEmail = false },
            onSend = { email -> onEmail(email); showEmail = false },
        )
    }
}

@Composable
private fun AttributionRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Person, contentDescription = null, tint = Slate500, modifier = Modifier.size(17.dp))
        Text("  $label", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Slate500)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailTotal(label: String, amount: Long, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
        Text(
            formatUgx(amount),
            fontFamily = MoneyFontFamily,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            color = if (amount < 0) Error700 else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PaymentRow(payment: PaymentLeg) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(payment.method.friendlyPayment(), style = MaterialTheme.typography.bodySmall, color = Slate500)
            payment.reference?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Slate500) }
        }
        Text(formatUgx(payment.amount), fontFamily = MoneyFontFamily, style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailReceiptSheet(working: Boolean, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    val valid = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp)) {
            Text("Email receipt", style = MaterialTheme.typography.titleLarge)
            Text("The confirmed receipt will be sent without changing the sale.", color = Slate500, style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.trim().take(120) },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                label = { Text("Email address") },
                singleLine = true,
            )
            Button(
                onClick = { onSend(email.trim()) },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(50.dp),
                enabled = valid && !working,
                shape = RoundedCornerShape(14.dp),
            ) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Send receipt")
            }
        }
    }
}

private fun String.friendlyPayment(): String = replace('_', ' ').replaceFirstChar(Char::uppercase)

private val ActivityDateFormatter = DateTimeFormatter.ofPattern("dd MMM · HH:mm")

private fun String.displayDate(): String = runCatching {
    Instant.parse(this).atZone(ZoneId.systemDefault()).format(ActivityDateFormatter)
}.getOrDefault(take(16).replace('T', ' '))
