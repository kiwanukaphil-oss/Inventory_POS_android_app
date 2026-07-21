package com.kline.inventorypos.feature.cash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
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
import com.kline.inventorypos.core.model.CashBookSummary
import com.kline.inventorypos.core.model.CashMovement
import com.kline.inventorypos.core.model.CashSession
import com.kline.inventorypos.core.model.CashSessionSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CashScreen(
    state: CashUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenRegister: () -> Unit,
    onCloseDrawer: (Long, String) -> Unit,
    onHandover: (Long, String, String) -> Unit,
    onRecordMovement: (String, String, Long, String, String) -> Unit,
    onFinishClose: () -> Unit,
) {
    var countMode by rememberSaveable { mutableStateOf<String?>(null) }
    var recordMovement by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) { if (state.message != null) recordMovement = false }
    BackHandler(enabled = state.closeResult != null || countMode != null || recordMovement) {
        if (state.closeResult != null) onFinishClose()
        else { countMode = null; recordMovement = false }
    }
    when {
        state.closeResult != null -> CloseResultScreen(state.closeResult, onFinishClose)
        countMode != null -> BlindCountScreen(countMode == "handover", state, { countMode = null }, onCloseDrawer, onHandover)
        recordMovement -> RecordMovementScreen(state, { recordMovement = false }, onRecordMovement)
        else -> CashDashboard(state, onBack, onRefresh, onOpenRegister, { countMode = "close" }, { countMode = "handover" }, { recordMovement = true })
    }
}

@Composable
private fun CashDashboard(state: CashUiState, onBack: () -> Unit, onRefresh: () -> Unit, onOpenRegister: () -> Unit, onClose: () -> Unit, onHandover: () -> Unit, onRecord: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Cash management", "Drawer, movements and today’s Z-summary", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.loading && state.workspace.activeSession == null && state.workspace.bookSummary == null) item { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else {
                item { DrawerCard(state.workspace.activeSession, state.canOperateDrawer, onOpenRegister, onClose, onHandover, state.canHandover && state.workspace.staff.isNotEmpty()) }
                state.workspace.bookSummary?.let { summary -> item { TodaySummary(summary) } }
                if (state.uncertain) item { Warning("A cash action may already have completed. Refresh the drawer and cash book before attempting another action.") }
                if (state.canViewBook) {
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Today’s movements", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); if (state.canManage && state.workspace.activeSession != null) Button(onRecord) { Icon(Icons.Outlined.Add, null); Text(" Record") } } }
                    if (state.workspace.movements.isEmpty()) item { EmptySection("No cash movements today") }
                    items(state.workspace.movements, key = { it.id }) { MovementCard(it) }
                }
            }
        }
        Surface(shadowElevation = 6.dp) { OutlinedButton(onRefresh, Modifier.fillMaxWidth().padding(12.dp), enabled = !state.loading && !state.working) { Icon(Icons.Outlined.Refresh, null); Text("  Refresh cash records") } }
    }
}

@Composable
private fun DrawerCard(session: CashSession?, canOperate: Boolean, onOpen: () -> Unit, onClose: () -> Unit, onHandover: () -> Unit, canHandover: Boolean) {
    PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).background(if (session != null) MoneyGreen50 else Primary50, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AccountBalanceWallet, null, tint = if (session != null) MoneyGreen700 else Primary700) }; Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(if (session != null) "Drawer open" else "No open drawer", fontWeight = FontWeight.Bold); Text(session?.let { "Opened ${it.openedAt.displayTime()}${it.openedByName?.let { name -> " · $name" } ?: ""}" } ?: "Open a register to accept or pay cash", color = Slate500, style = MaterialTheme.typography.bodySmall) }; session?.let { Text(formatUgx(it.openingFloat), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } }
        if (session != null) {
            Surface(Modifier.fillMaxWidth(), color = Primary50, shape = RoundedCornerShape(10.dp)) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = Primary700); Text("  Closing uses a blind physical count. Expected cash remains hidden until submission.", color = Primary700, style = MaterialTheme.typography.bodySmall) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClose, Modifier.weight(1f)) { Text("Close drawer") }; if (canHandover) Button(onHandover, Modifier.weight(1f)) { Text("Hand over") } }
        } else if (canOperate) Button(onOpen, Modifier.fillMaxWidth()) { Text("Open register") }
    } }
}

@Composable
private fun TodaySummary(summary: CashBookSummary) { PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Today’s cash book", style = MaterialTheme.typography.titleSmall); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SummaryTile("Cash in", summary.inflows, false, Modifier.weight(1f)); SummaryTile("Cash out", summary.outflows, true, Modifier.weight(1f)) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Net movement · ${summary.count} entries", color = Slate500); Text(formatUgx(summary.net), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } } } }
@Composable private fun SummaryTile(label: String, amount: Long, danger: Boolean, modifier: Modifier) { Surface(modifier, color = if (danger) Error50 else MoneyGreen50, shape = RoundedCornerShape(10.dp)) { Column(Modifier.padding(10.dp)) { Text(label, color = if (danger) Error700 else MoneyGreen700, style = MaterialTheme.typography.labelSmall); Text(formatUgx(amount), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } } }

@Composable
private fun MovementCard(movement: CashMovement) { val inflow = movement.direction == "inflow"; Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Payments, null, tint = if (inflow) MoneyGreen700 else Error700); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(movement.type.label(), fontWeight = FontWeight.Bold); Text(listOfNotNull(movement.reference, movement.category, movement.date.displayTime()).joinToString(" · "), color = Slate500, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis); movement.notes?.let { Text(it, color = Slate500, style = MaterialTheme.typography.labelSmall, maxLines = 1) } }; Text("${if (inflow) "+" else "−"}${formatUgx(movement.amount)}", color = if (inflow) MoneyGreen700 else Error700, fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } } }

@Composable
private fun BlindCountScreen(handover: Boolean, state: CashUiState, onBack: () -> Unit, onClose: (Long, String) -> Unit, onHandover: (Long, String, String) -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }; var note by rememberSaveable { mutableStateOf("") }; var incoming by rememberSaveable { mutableStateOf("") }; val counted = amount.toLongOrNull()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(if (handover) "Hand over drawer" else "Close drawer", "Blind cash count", onBack)
        Column(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(42.dp), tint = Primary700)
            Text("Count all physical cash", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("The expected amount is intentionally hidden. Count notes and coins independently, then enter only the physical total.", color = Slate500)
            OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(), label = { Text("Total cash counted") }, prefix = { Text("UGX ") }, supportingText = { counted?.let { Text(formatUgx(it)) } }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = AmountGroupingVisualTransformation)
            if (handover) { Text("Incoming cashier", fontWeight = FontWeight.Bold); ChoiceRow(state.workspace.staff.map { it.id to "${it.name} (${it.username})" }, incoming) { incoming = it } }
            OutlinedTextField(note, { note = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Count or variance note (optional)") }, minLines = 2)
            if (state.uncertain) Warning("The previous count may already be committed. Return and refresh instead of submitting again.")
        }
        Surface(shadowElevation = 8.dp) { Button({ if (handover) onHandover(counted ?: 0, incoming, note) else onClose(counted ?: 0, note) }, Modifier.fillMaxWidth().padding(12.dp).height(52.dp), enabled = counted != null && counted >= 0 && (!handover || incoming.isNotBlank()) && !state.working && !state.uncertain) { if (state.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (handover) "Submit count and hand over" else "Submit count and close") } }
    }
}

@Composable
private fun CloseResultScreen(result: CashSessionSummary, onDone: () -> Unit) { val variance = result.session.variance ?: 0; Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { FocusedHeader("Drawer closed", "Final server-authoritative Z-summary", onDone); LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Surface(Modifier.fillMaxWidth(), color = if (variance == 0L) MoneyGreen50 else Error50, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(44.dp), tint = if (variance == 0L) MoneyGreen700 else Error700); Text(if (variance == 0L) "Exact match" else if (variance > 0) "Cash over" else "Cash short", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if (variance > 0) "+${formatUgx(variance)}" else formatUgx(variance), fontFamily = MoneyFontFamily, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) } } }; item { PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { SummaryRow("Opening float", result.session.openingFloat); SummaryRow("Expected", result.expected); SummaryRow("Counted", result.session.actualClosing ?: 0); Box(Modifier.fillMaxWidth().height(1.dp).background(Slate100)); result.movements.filter { it.type !in listOf("opening_float", "variance_writeoff") }.forEach { SummaryRow("${it.type.label()} (${it.count}×)", if (it.direction == "outflow") -it.total else it.total) } } } } }; Surface(shadowElevation = 8.dp) { Button(onDone, Modifier.fillMaxWidth().padding(12.dp).height(52.dp)) { Text("Done") } } } }

@Composable
private fun RecordMovementScreen(state: CashUiState, onBack: () -> Unit, onSubmit: (String, String, Long, String, String) -> Unit) { var direction by rememberSaveable { mutableStateOf("inflow") }; var type by rememberSaveable { mutableStateOf("") }; var amount by rememberSaveable { mutableStateOf("") }; var category by rememberSaveable { mutableStateOf("") }; var notes by rememberSaveable { mutableStateOf("") }; val types = if (direction == "inflow") listOf("cash_topup" to "Cash top-up", "adjustment" to "Adjustment in") else listOf("petty_cash" to "Petty cash", "banking" to "Bank / safe drop", "adjustment" to "Adjustment out"); Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { FocusedHeader("Record cash movement", "Audited manual drawer entry", onBack); LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { ChoiceRow(listOf("inflow" to "Cash in", "outflow" to "Cash out"), direction) { direction = it; type = "" } }; item { Text("Movement type", fontWeight = FontWeight.Bold); ChoiceRow(types, type) { type = it } }; item { OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(), label = { Text("Amount") }, prefix = { Text("UGX ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = AmountGroupingVisualTransformation) }; item { OutlinedTextField(category, { category = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Category (optional)") }, singleLine = true) }; item { OutlinedTextField(notes, { notes = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Reason / notes") }, minLines = 3) }; if (state.uncertain) item { Warning("This movement may already exist. Refresh before recording anything else.") } }; Surface(shadowElevation = 8.dp) { Button({ onSubmit(type, direction, amount.toLongOrNull() ?: 0, category, notes) }, Modifier.fillMaxWidth().padding(12.dp).height(52.dp), enabled = type.isNotBlank() && (amount.toLongOrNull() ?: 0) > 0 && notes.isNotBlank() && !state.working && !state.uncertain) { if (state.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Record movement") } } } }

@Composable private fun SummaryRow(label: String, amount: Long) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(formatUgx(amount), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold, color = if (amount < 0) Error700 else MaterialTheme.colorScheme.onSurface) } }
@Composable private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { options.forEach { (value, label) -> FilterChip(selected == value, { onSelect(value) }, { Text(label) }) } } }
@Composable private fun Warning(text: String) { Surface(Modifier.fillMaxWidth(), color = Error50, contentColor = Error700, shape = RoundedCornerShape(12.dp)) { Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) } }
@Composable private fun EmptySection(text: String) { Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) { Text(text, Modifier.padding(18.dp), color = Slate500, textAlign = TextAlign.Center) } }
private fun String.label() = replace('_', ' ').replaceFirstChar(Char::uppercase)
private val CashTime = DateTimeFormatter.ofPattern("dd MMM · HH:mm")
private fun String.displayTime(): String = runCatching { Instant.parse(this).atZone(ZoneId.systemDefault()).format(CashTime) }.getOrDefault(take(16).replace('T', ' '))
