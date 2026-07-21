package com.kline.inventorypos.feature.reconciliation

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kline.inventorypos.core.model.DailySalesSummary
import com.kline.inventorypos.core.model.PaymentMethodSummary
import com.kline.inventorypos.core.model.Reconciliation
import com.kline.inventorypos.core.model.ReconciliationChannel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReconciliationScreen(
    state: ReconciliationUiState,
    onBack: () -> Unit,
    onPreviousDate: () -> Unit,
    onNextDate: () -> Unit,
    onToday: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
    onUpdateChannel: (String, Long, Long, String) -> Unit,
    onSignOff: (Long, String) -> Unit,
    onCloseDay: () -> Unit,
) {
    var channelMethod by rememberSaveable { mutableStateOf<String?>(null) }
    var showSignOff by rememberSaveable { mutableStateOf(false) }
    var showClose by rememberSaveable { mutableStateOf(false) }
    val selectedChannel = state.workspace.reconciliation?.channels?.firstOrNull { it.method == channelMethod }
    LaunchedEffect(state.message) { if (state.message != null) { channelMethod = null; showSignOff = false; showClose = false } }
    BackHandler(enabled = selectedChannel != null) { channelMethod = null }

    if (selectedChannel != null) {
        ChannelCountScreen(selectedChannel, state.working, state.uncertain, { channelMethod = null }) { external, charges, note ->
            onUpdateChannel(selectedChannel.method, external, charges, note)
        }
    } else {
        ReconciliationDashboard(
            state = state,
            onBack = onBack,
            onPreviousDate = onPreviousDate,
            onNextDate = onNextDate,
            onToday = onToday,
            onRefresh = onRefresh,
            onOpen = onOpen,
            onChannel = { channelMethod = it },
            onSignOff = { showSignOff = true },
            onClose = { showClose = true },
        )
    }

    val mine = state.workspace.reconciliation?.pendingStaff?.firstOrNull { it.userId == state.currentUserId }
    if (showSignOff && mine != null) {
        SignOffDialog(mine.salesTotal, state.working, { showSignOff = false }, onSignOff)
    }
    if (showClose) {
        AlertDialog(
            onDismissRequest = { if (!state.working) showClose = false },
            icon = { Icon(Icons.Outlined.Lock, null) },
            title = { Text("Close and lock this day?") },
            text = { Text("Verified channels and staff sign-offs become the final branch record. This action cannot be edited from the app.") },
            confirmButton = { Button(onCloseDay, enabled = !state.working) { Text("Close day") } },
            dismissButton = { TextButton({ showClose = false }, enabled = !state.working) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReconciliationDashboard(
    state: ReconciliationUiState,
    onBack: () -> Unit,
    onPreviousDate: () -> Unit,
    onNextDate: () -> Unit,
    onToday: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
    onChannel: (String) -> Unit,
    onSignOff: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("End of day", "Sales review and daily reconciliation", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { DateSelector(state.date, onPreviousDate, onNextDate, onToday) }
            if (state.loading && state.workspace.sales == null && state.workspace.reconciliation == null) {
                item { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else {
                state.workspace.sales?.let { item { SalesOverviewCard(it) } }
                if (state.workspace.payments.isNotEmpty()) item { PaymentMixCard(state.workspace.payments) }
                if (state.canViewReconciliation) {
                    item { ReconciliationSummary(state, onOpen) }
                    state.workspace.reconciliation?.let { recon ->
                        item { SectionTitle("Payment channels", "${recon.channels.count { it.externalTotal != null }}/${recon.channels.size} verified") }
                        items(recon.channels, key = { it.id }) { ChannelCard(it, state.canReconcile && recon.status != "closed") { onChannel(it.method) } }
                        item { StaffSignoffCard(recon, state.currentUserId, onSignOff) }
                        if (state.canReconcile) item { CloseDayCard(recon, state.working, state.uncertain, onClose) }
                    }
                }
                if (!state.canViewReports && !state.canViewReconciliation) item { EmptyCard("You do not have access to sales reports or daily reconciliation.") }
                if (state.uncertain) item { WarningCard("An action may already have completed. Refresh this date before attempting another end-of-day action.") }
            }
        }
        Surface(shadowElevation = 6.dp) {
            OutlinedButton(onRefresh, Modifier.fillMaxWidth().padding(12.dp), enabled = !state.loading && !state.working) {
                Icon(Icons.Outlined.Refresh, null); Text("  Refresh server records")
            }
        }
    }
}

@Composable
private fun DateSelector(date: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    PosCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onPrevious) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous day") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(date.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")), fontWeight = FontWeight.Bold)
                if (date == LocalDate.now()) Text("Today", color = Primary700, style = MaterialTheme.typography.labelSmall)
                else TextButton(onToday, contentPadding = PaddingValues(0.dp)) { Text("Return to today") }
            }
            IconButton(onNext, enabled = date.isBefore(LocalDate.now())) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Next day") }
        }
    }
}

@Composable
private fun SalesOverviewCard(sales: DailySalesSummary) {
    PosCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Assessment, null, tint = Primary700); Text("  Management summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            Text(formatUgx(sales.netRevenue), style = MaterialTheme.typography.headlineMedium, fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
            Text("Net revenue from ${sales.transactions} completed sales", color = Slate500)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Gross", sales.grossRevenue, Modifier.weight(1f)); MetricTile("Average", sales.averageSale, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Returns", color = Slate500); Text(formatUgx(sales.returns), fontFamily = MoneyFontFamily) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Discounts", color = Slate500); Text(formatUgx(sales.discounts), fontFamily = MoneyFontFamily) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Tax", color = Slate500); Text(formatUgx(sales.tax), fontFamily = MoneyFontFamily) }
        }
    }
}

@Composable private fun MetricTile(label: String, amount: Long, modifier: Modifier) { Surface(modifier, color = Primary50, shape = RoundedCornerShape(10.dp)) { Column(Modifier.padding(10.dp)) { Text(label, color = Primary700, style = MaterialTheme.typography.labelSmall); Text(formatUgx(amount), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } } }

@Composable
private fun PaymentMixCard(payments: List<PaymentMethodSummary>) {
    PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Payment mix", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        payments.forEach { payment -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(payment.method.label(), Modifier.weight(1f)); Column(horizontalAlignment = Alignment.End) { Text(formatUgx(payment.total), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold); Text("${payment.transactions} sales · ${String.format(Locale.US, "%.1f", payment.percentage)}%", color = Slate500, style = MaterialTheme.typography.labelSmall) }
        } }
    } }
}

@Composable
private fun ReconciliationSummary(state: ReconciliationUiState, onOpen: () -> Unit) {
    val recon = state.workspace.reconciliation
    PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Payments, null, tint = Primary700); Text("  Daily reconciliation", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); recon?.let { StatusPill(it.status) } }
        if (recon == null) {
            Text("No reconciliation has been started for this branch and date.", color = Slate500)
            if (state.canReconcile) Button(onOpen, Modifier.fillMaxWidth(), enabled = !state.working && !state.uncertain) { Text("Start reconciliation") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricTile("System", recon.systemTotal, Modifier.weight(1f)); MetricTile("External", recon.externalTotal, Modifier.weight(1f)) }
            if (recon.channels.any { it.externalTotal != null }) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Current variance", color = Slate500); Text(recon.variance.signedMoney(), color = if (recon.variance == 0L) MoneyGreen700 else Error700, fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) }
            recon.closedBy?.let { Text("Closed by $it", color = Slate500, style = MaterialTheme.typography.bodySmall) }
        }
    } }
}

@Composable private fun StatusPill(status: String) { val closed = status == "closed"; Surface(color = if (closed) MoneyGreen50 else Primary50, shape = CircleShape) { Text(status.label(), Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = if (closed) MoneyGreen700 else Primary700, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) } }
@Composable private fun SectionTitle(title: String, supporting: String) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Text(supporting, color = Slate500, style = MaterialTheme.typography.labelSmall) } }

@Composable
private fun ChannelCard(channel: ReconciliationChannel, editable: Boolean, onClick: () -> Unit) {
    val verified = channel.externalTotal != null
    Surface(Modifier.fillMaxWidth().then(if (editable) Modifier.clickable(onClick = onClick) else Modifier), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(34.dp).background(if (verified) MoneyGreen50 else Primary50, CircleShape), contentAlignment = Alignment.Center) { Icon(if (verified) Icons.Outlined.CheckCircle else Icons.Outlined.Payments, null, Modifier.size(19.dp), tint = if (verified) MoneyGreen700 else Primary700) }; Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(channel.method.label(), fontWeight = FontWeight.Bold); Text(if (verified) "Verified${channel.verifiedBy?.let { " by $it" } ?: ""}" else "External total required", color = Slate500, style = MaterialTheme.typography.bodySmall) }; Text(formatUgx(channel.systemTotal), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) }
            if (verified) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("External ${formatUgx(channel.externalTotal)}", color = Slate500); Text(channel.variance.signedMoney(), color = if (channel.variance == 0L) MoneyGreen700 else Error700, fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) }; channel.discrepancyNote?.let { Text(it, color = Slate500, style = MaterialTheme.typography.bodySmall) } }
        }
    }
}

@Composable
private fun StaffSignoffCard(recon: Reconciliation, currentUserId: String, onSignOff: () -> Unit) {
    val mine = recon.pendingStaff.firstOrNull { it.userId == currentUserId }
    PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Staff sign-off", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        recon.signoffs.forEach { staff -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = MoneyGreen700); Text("  ${staff.staffName}", Modifier.weight(1f)); Text(formatUgx(staff.confirmedTotal), fontFamily = MoneyFontFamily) } }
        recon.pendingStaff.forEach { staff -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = Primary700); Text("  ${staff.staffName}", Modifier.weight(1f)); Text("${staff.transactionCount} sales", color = Slate500) } }
        if (recon.pendingStaff.isEmpty()) Text("Everyone with sales has signed off.", color = MoneyGreen700)
        if (mine != null && recon.status != "closed") Button(onSignOff, Modifier.fillMaxWidth()) { Text("Confirm my ${formatUgx(mine.salesTotal)}") }
        else if (recon.pendingStaff.isNotEmpty()) Text("Each team member must sign in and confirm their own sales.", color = Slate500, style = MaterialTheme.typography.bodySmall)
    } }
}

@Composable
private fun CloseDayCard(recon: Reconciliation, working: Boolean, uncertain: Boolean, onClose: () -> Unit) {
    if (recon.status == "closed") return
    val unverified = recon.channels.filter { it.externalTotal == null }
    val missingNotes = recon.channels.filter { it.variance != 0L && it.discrepancyNote.isNullOrBlank() }
    val ready = unverified.isEmpty() && missingNotes.isEmpty() && recon.pendingStaff.isEmpty()
    PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (ready) "Ready to close" else "Close-day checks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (unverified.isNotEmpty()) CheckMessage("Verify ${unverified.joinToString { it.method.label() }}")
        if (missingNotes.isNotEmpty()) CheckMessage("Explain variance for ${missingNotes.joinToString { it.method.label() }}")
        if (recon.pendingStaff.isNotEmpty()) CheckMessage("Await ${recon.pendingStaff.size} staff sign-off${if (recon.pendingStaff.size == 1) "" else "s"}")
        if (ready) Text("All channel counts, discrepancy notes, and staff confirmations are complete.", color = MoneyGreen700, style = MaterialTheme.typography.bodySmall)
        Button(onClose, Modifier.fillMaxWidth(), enabled = ready && !working && !uncertain) { Icon(Icons.Outlined.Lock, null); Text("  Submit and close day") }
    } }
}

@Composable private fun CheckMessage(text: String) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(18.dp), tint = Error700); Text("  $text", color = Error700, style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun ChannelCountScreen(channel: ReconciliationChannel, working: Boolean, uncertain: Boolean, onBack: () -> Unit, onSubmit: (Long, Long, String) -> Unit) {
    var external by rememberSaveable(channel.method) { mutableStateOf(channel.externalTotal?.toString().orEmpty()) }
    var charges by rememberSaveable(channel.method) { mutableStateOf(channel.charges.takeIf { it > 0 }?.toString().orEmpty()) }
    var note by rememberSaveable(channel.method) { mutableStateOf(channel.discrepancyNote.orEmpty()) }
    val externalValue = external.toLongOrNull()
    val chargesValue = charges.toLongOrNull() ?: 0
    val variance = externalValue?.minus(chargesValue)?.minus(channel.systemTotal)
    val valid = externalValue != null && chargesValue >= 0 && (variance == 0L || note.isNotBlank())
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Verify ${channel.method.label()}", "External settlement against system total", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("System total", color = Slate500); Text(formatUgx(channel.systemTotal), style = MaterialTheme.typography.headlineSmall, fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold); Text("Use the independent drawer, terminal, or provider statement—not this figure—to enter the external total.", color = Slate500, style = MaterialTheme.typography.bodySmall) } } }
            item { OutlinedTextField(external, { external = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(), label = { Text("External total") }, prefix = { Text("UGX ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = AmountGroupingVisualTransformation) }
            if (channel.method != "cash") item { OutlinedTextField(charges, { charges = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(), label = { Text("Provider charges (optional)") }, prefix = { Text("UGX ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = AmountGroupingVisualTransformation) }
            variance?.let { item { Surface(Modifier.fillMaxWidth(), color = if (it == 0L) MoneyGreen50 else Error50, shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("Expected variance", color = if (it == 0L) MoneyGreen700 else Error700); Text(it.signedMoney(), color = if (it == 0L) MoneyGreen700 else Error700, fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } } } }
            if (variance != null && variance != 0L) item { OutlinedTextField(note, { note = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Discrepancy explanation") }, supportingText = { Text("Required for a non-zero variance") }, minLines = 3) }
            if (uncertain) item { WarningCard("The previous verification may already be saved. Return and refresh instead of submitting again.") }
        }
        Surface(shadowElevation = 8.dp) { Button({ onSubmit(externalValue ?: 0, chargesValue, note) }, Modifier.fillMaxWidth().padding(12.dp).height(52.dp), enabled = valid && !working && !uncertain) { if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Verify channel") } }
    }
}

@Composable
private fun SignOffDialog(total: Long, working: Boolean, onDismiss: () -> Unit, onConfirm: (Long, String) -> Unit) {
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = { Icon(Icons.Outlined.CheckCircle, null) },
        title = { Text("Confirm your sales") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("You are confirming ${formatUgx(total)} as your sales total for this date."); OutlinedTextField(notes, { notes = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") }, minLines = 2) } },
        confirmButton = { Button({ onConfirm(total, notes) }, enabled = !working) { Text("Sign off") } },
        dismissButton = { TextButton(onDismiss, enabled = !working) { Text("Cancel") } },
    )
}

@Composable private fun WarningCard(text: String) { Surface(Modifier.fillMaxWidth(), color = Error50, contentColor = Error700, shape = RoundedCornerShape(12.dp)) { Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) } }
@Composable private fun EmptyCard(text: String) { Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) { Text(text, Modifier.padding(18.dp), color = Slate500, textAlign = TextAlign.Center) } }
private fun String.label() = replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun Long.signedMoney() = if (this > 0) "+${formatUgx(this)}" else formatUgx(this)
