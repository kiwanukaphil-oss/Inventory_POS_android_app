package com.kline.inventorypos.feature.voucher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kline.inventorypos.core.common.AmountGroupingVisualTransformation
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
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.model.CreateGiftVoucher
import com.kline.inventorypos.core.model.GiftVoucher
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GiftVoucherScreen(
    state: GiftVoucherUiState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onStatus: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (GiftVoucher) -> Unit,
    onCloseDetail: () -> Unit,
    onRefreshDetail: () -> Unit,
    onScan: () -> Unit,
    onVerify: (String) -> Unit,
    onCreate: (CreateGiftVoucher) -> Unit,
    onActivate: (String, String?) -> Unit,
    onRedeem: (Long, String) -> Unit,
    onCancel: (String, Boolean) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.selected?.id) { if (state.selected != null) creating = false }
    BackHandler(enabled = creating || state.selected != null) { if (creating) creating = false else onCloseDetail() }
    when {
        state.selected != null -> VoucherDetailScreen(state, onCloseDetail, onRefreshDetail, onActivate, onRedeem, onCancel)
        creating -> CreateVoucherScreen(state, { creating = false }, onCreate)
        state.detailLoading -> LoadingVoucher(onBack)
        else -> VoucherListScreen(state, onBack, onQuery, onStatus, onRefresh, onOpen, onScan, onVerify, { creating = true })
    }
}

@Composable
private fun VoucherListScreen(
    state: GiftVoucherUiState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onStatus: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (GiftVoucher) -> Unit,
    onScan: () -> Unit,
    onVerify: (String) -> Unit,
    onNew: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Gift vouchers", "Issue, verify and manage liability", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(state.query, onQuery, Modifier.weight(1f), placeholder = { Text("Search code or recipient") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp))
            IconButton(onRefresh, enabled = !state.loading) { Icon(Icons.Outlined.Refresh, "Refresh") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(code, { code = it.take(180) }, Modifier.weight(1f), label = { Text("Verify voucher code") }, singleLine = true)
            IconButton(onScan, enabled = state.canRedeem) { Icon(Icons.Outlined.QrCodeScanner, "Scan voucher QR") }
            Button({ onVerify(code) }, enabled = code.isNotBlank() && state.canRedeem) { Text("Verify") }
        }
        ChoiceRow(listOf("all" to "All", "active" to "Active", "partially_redeemed" to "Part used", "pending_payment" to "Unpaid", "cancelled" to "Cancelled"), state.status, onStatus)
        when {
            state.loading && state.vouchers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.vouchers.isEmpty() -> EmptyVouchers()
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.vouchers, key = GiftVoucher::id) { voucher -> VoucherCard(voucher) { onOpen(voucher) } }
            }
        }
        if (state.canIssue) Surface(shadowElevation = 8.dp) { Button(onNew, Modifier.fillMaxWidth().padding(12.dp).height(50.dp)) { Icon(Icons.Outlined.Add, null); Text("  New gift voucher") } }
    }
}

@Composable
private fun VoucherCard(voucher: GiftVoucher, onClick: () -> Unit) {
    val good = voucher.status == "active" || voucher.status == "partially_redeemed"
    PosCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(if (good) MoneyGreen50 else Primary50, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CardGiftcard, null, tint = if (good) MoneyGreen700 else Primary700) }
        Column(Modifier.padding(start = 11.dp).weight(1f)) { Text("${voucher.code} · ${voucher.recipientName}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(listOfNotNull(voucher.templateName, voucher.expiryDate?.let { "Expires ${it.displayDate()}" }).joinToString(" · "), color = Slate500, style = MaterialTheme.typography.bodySmall); Text(voucher.status.label(), color = if (good) MoneyGreen700 else Slate500, style = MaterialTheme.typography.labelSmall) }
        Column(horizontalAlignment = Alignment.End) { Text(formatUgx(voucher.remainingBalance), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold); Text("of ${formatUgx(voucher.originalAmount)}", color = Slate500, style = MaterialTheme.typography.labelSmall) }
    } }
}

@Composable
private fun CreateVoucherScreen(state: GiftVoucherUiState, onBack: () -> Unit, onCreate: (CreateGiftVoucher) -> Unit) {
    var template by rememberSaveable { mutableStateOf("") }; var recipient by rememberSaveable { mutableStateOf("") }; var from by rememberSaveable { mutableStateOf("") }; var phone by rememberSaveable { mutableStateOf("") }; var amount by rememberSaveable { mutableStateOf("") }; var expiry by rememberSaveable { mutableStateOf("") }; var message by rememberSaveable { mutableStateOf("") }; var notes by rememberSaveable { mutableStateOf("") }
    val request = CreateGiftVoucher(template, recipient, from, message, phone, amount.toLongOrNull() ?: 0, expiry, notes)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("New gift voucher", "Create an unpaid draft first", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Choose design", style = MaterialTheme.typography.titleMedium) }
            item { ChoiceRow(state.templates.map { it.id to it.name }, template) { template = it } }
            item { OutlinedTextField(recipient, { recipient = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Recipient name") }, singleLine = true) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(from, { from = it.take(100) }, Modifier.weight(1f), label = { Text("From (optional)") }, singleLine = true); OutlinedTextField(phone, { phone = it.take(30) }, Modifier.weight(1f), label = { Text("Recipient phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)) } }
            item { OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(), label = { Text("Voucher amount") }, prefix = { Text("UGX ") }, supportingText = { if (amount.isNotBlank()) Text(formatUgx(amount.toLongOrNull() ?: 0)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = AmountGroupingVisualTransformation) }
            item { OutlinedTextField(expiry, { expiry = it.take(10) }, Modifier.fillMaxWidth(), label = { Text("Expiry date (optional)") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true) }
            item { OutlinedTextField(message, { message = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Gift message") }, minLines = 3) }
            item { OutlinedTextField(notes, { notes = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Internal notes") }, minLines = 2) }
            if (state.uncertain) item { Warning("Draft status is unknown. Verify the voucher list before creating another.") }
        }
        Surface(shadowElevation = 8.dp) { Button({ onCreate(request) }, Modifier.fillMaxWidth().padding(12.dp).height(52.dp), enabled = template.isNotBlank() && recipient.isNotBlank() && request.amount > 0 && !state.working && !state.uncertain) { if (state.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Create unpaid draft") } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoucherDetailScreen(
    state: GiftVoucherUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onActivate: (String, String?) -> Unit,
    onRedeem: (Long, String) -> Unit,
    onCancel: (String, Boolean) -> Unit,
) {
    val voucher = state.selected ?: return
    var action by rememberSaveable(voucher.id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(voucher.code, voucher.status.label(), onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { VoucherHero(voucher) }
            item { PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) { SummaryText("Recipient", voucher.recipientName); voucher.fromName?.let { SummaryText("From", it) }; voucher.phone?.let { SummaryText("Phone", it) }; voucher.templateName?.let { SummaryText("Design", it) }; SummaryText("Payment", voucher.paymentStatus.label()); SummaryText("Issued", voucher.issueDate?.displayDate() ?: "Not activated"); SummaryText("Expires", voucher.expiryDate?.displayDate() ?: "Set on activation"); voucher.message?.let { Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100)); Text(it) } } } }
            if (state.uncertain) item { Warning("This action may already have completed. Refresh this voucher before any retry.") }
            item { Text("Voucher activity", style = MaterialTheme.typography.titleSmall) }
            if (voucher.transactions.isEmpty()) item { EmptySection("No transaction history loaded") }
            items(voucher.transactions, key = { it.id }) { tx -> PosCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Payments, null, tint = Primary700); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(tx.type.label(), fontWeight = FontWeight.Bold); Text(listOfNotNull(tx.reference, tx.date.displayDate()).joinToString(" · "), color = Slate500, style = MaterialTheme.typography.bodySmall); tx.notes?.let { Text(it, color = Slate500, style = MaterialTheme.typography.labelSmall) } }; Column(horizontalAlignment = Alignment.End) { Text(formatUgx(tx.amount), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold); Text("Bal ${formatUgx(tx.balanceAfter)}", color = Slate500, style = MaterialTheme.typography.labelSmall) } } } }
        }
        Surface(shadowElevation = 8.dp) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canIssue && voucher.status == "pending_payment" && voucher.paymentStatus == "unpaid") Button({ action = "activate" }, Modifier.weight(1f), enabled = !state.uncertain) { Text("Activate") }
                if (state.canRedeem && voucher.status in listOf("active", "partially_redeemed")) Button({ action = "redeem" }, Modifier.weight(1f), enabled = !state.uncertain) { Text("Redeem") }
                if (state.canManage && voucher.status !in listOf("cancelled", "fully_redeemed")) OutlinedButton({ action = "cancel" }, Modifier.weight(1f), enabled = !state.uncertain) { Text("Cancel") }
            }
            OutlinedButton(onRefresh, Modifier.fillMaxWidth(), enabled = !state.detailLoading && !state.working) { Icon(Icons.Outlined.Refresh, null); Text("  Refresh and verify") }
        } }
    }
    when (action) {
        "activate" -> ActivateSheet(voucher, state, { action = null }, onActivate)
        "redeem" -> RedeemSheet(voucher, state, { action = null }, onRedeem)
        "cancel" -> CancelSheet(voucher, state, { action = null }, onCancel)
    }
}

@Composable private fun VoucherHero(voucher: GiftVoucher) { Surface(Modifier.fillMaxWidth(), color = Primary50, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.CardGiftcard, null, Modifier.size(38.dp), tint = Primary700); Text(voucher.recipientName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(voucher.code, color = Primary700, fontWeight = FontWeight.Bold); Text(formatUgx(voucher.remainingBalance), fontFamily = MoneyFontFamily, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)); Text("remaining of ${formatUgx(voucher.originalAmount)}", color = Slate500) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ActivateSheet(voucher: GiftVoucher, state: GiftVoucherUiState, onDismiss: () -> Unit, onSubmit: (String, String?) -> Unit) { var method by rememberSaveable { mutableStateOf("cash") }; var reference by rememberSaveable { mutableStateOf("") }; ActionSheet("Activate voucher", onDismiss) { Text("Collect exactly ${formatUgx(voucher.originalAmount)} before activation.", color = Slate500); ChoiceRow(listOf("cash" to "Cash", "visa" to "Visa", "mastercard" to "Mastercard", "airtel_money" to "Airtel", "mtn_mobile_money" to "MTN MoMo", "bank_transfer" to "Bank transfer"), method) { method = it }; OutlinedTextField(reference, { reference = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Payment reference (optional)") }, singleLine = true); if (method == "cash" && !state.hasOpenRegister) Warning("Open a register before accepting cash for this voucher."); Button({ onSubmit(method, reference); onDismiss() }, Modifier.fillMaxWidth().height(50.dp), enabled = !state.working && !state.uncertain && !(method == "cash" && !state.hasOpenRegister)) { Text("Confirm payment and activate") } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun RedeemSheet(voucher: GiftVoucher, state: GiftVoucherUiState, onDismiss: () -> Unit, onSubmit: (Long, String) -> Unit) { var amount by rememberSaveable { mutableStateOf("") }; var notes by rememberSaveable { mutableStateOf("") }; val value = amount.toLongOrNull() ?: 0; ActionSheet("Redeem voucher", onDismiss) { Text("Available ${formatUgx(voucher.remainingBalance)}", color = MoneyGreen700, fontWeight = FontWeight.Bold); OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(), label = { Text("Redemption amount") }, prefix = { Text("UGX ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = AmountGroupingVisualTransformation); OutlinedTextField(notes, { notes = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("Redemption note (optional)") }, minLines = 2); Text("Confirm only after matching this voucher to the customer and transaction.", color = Slate500, style = MaterialTheme.typography.bodySmall); Button({ onSubmit(value, notes); onDismiss() }, Modifier.fillMaxWidth().height(50.dp), enabled = value in 1..voucher.remainingBalance && !state.working && !state.uncertain) { Text("Redeem ${if (value > 0) formatUgx(value) else "voucher"}") } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CancelSheet(voucher: GiftVoucher, state: GiftVoucherUiState, onDismiss: () -> Unit, onSubmit: (String, Boolean) -> Unit) { var reason by rememberSaveable { mutableStateOf("") }; var refund by rememberSaveable { mutableStateOf(false) }; val canRefund = voucher.paymentStatus == "paid" && voucher.remainingBalance > 0; ActionSheet("Cancel voucher", onDismiss) { OutlinedTextField(reason, { reason = it.take(250) }, Modifier.fillMaxWidth(), label = { Text("Cancellation reason") }, minLines = 3); if (canRefund) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(refund, { refund = it }); Column { Text("Refund remaining balance as cash", fontWeight = FontWeight.Bold); Text(formatUgx(voucher.remainingBalance), color = Slate500, style = MaterialTheme.typography.bodySmall) } }; if (refund && !state.hasOpenRegister) Warning("Open a register before paying this cash refund."); Warning(if (refund) "This cancels the voucher and records a cash outflow." else "This cancels redemption access without paying cash; the remaining value stays recorded on the cancelled voucher."); Button({ onSubmit(reason, refund); onDismiss() }, Modifier.fillMaxWidth().height(50.dp), enabled = reason.isNotBlank() && !state.working && !state.uncertain && !(refund && !state.hasOpenRegister)) { Text(if (refund) "Cancel and refund" else "Cancel voucher") } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ActionSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) { ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); content() } } }

@Composable private fun LoadingVoucher(onBack: () -> Unit) { Column(Modifier.fillMaxSize()) { FocusedHeader("Gift voucher", "Loading verified details", onBack); Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } } }
@Composable private fun EmptyVouchers() { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.CardGiftcard, null, Modifier.size(44.dp), tint = Slate500); Text("No matching vouchers", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)); Text("Try another code or status filter.", color = Slate500, textAlign = TextAlign.Center) } } }
@Composable private fun EmptySection(text: String) { Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) { Text(text, Modifier.padding(18.dp), color = Slate500, textAlign = TextAlign.Center) } }
@Composable private fun Warning(text: String) { Surface(Modifier.fillMaxWidth(), color = Error50, contentColor = Error700, shape = RoundedCornerShape(12.dp)) { Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) } }
@Composable private fun SummaryText(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Slate500); Text(value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
@Composable private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { options.forEach { (value, label) -> FilterChip(selected == value, { onSelect(value) }, { Text(label) }) } } }
private fun String.label() = replace('_', ' ').replaceFirstChar(Char::uppercase)
private val VoucherDate = DateTimeFormatter.ofPattern("dd MMM yyyy")
private fun String.displayDate(): String = runCatching { Instant.parse(this).atZone(ZoneId.systemDefault()).format(VoucherDate) }.getOrElse { take(10) }
