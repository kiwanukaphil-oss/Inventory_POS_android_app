package com.kline.inventorypos.feature.operations

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
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
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.model.ApprovalRequest
import com.kline.inventorypos.core.model.Expense
import com.kline.inventorypos.core.model.ExpenseCategory
import com.kline.inventorypos.core.model.ExpenseSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExpenseScreen(
    state: ExpenseUiState,
    onBack: () -> Unit,
    onPeriod: (ExpensePeriod) -> Unit,
    onCategory: (String?) -> Unit,
    onRefresh: () -> Unit,
    onSave: (String?, String, String, Long, String, String, String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = state.workspace.expenses.firstOrNull { it.id == editingId }
    LaunchedEffect(state.message) { if (state.message != null) { editorOpen = false; editingId = null; deleteId = null } }
    LaunchedEffect(state.uncertain) { if (state.uncertain) deleteId = null }
    BackHandler(enabled = editorOpen) { if (!state.working) { editorOpen = false; editingId = null } }
    if (editorOpen) {
        ExpenseEditor(state, editing, { editorOpen = false; editingId = null }, onSave)
    } else {
        ExpenseDashboard(
            state, onBack, onPeriod, onCategory, onRefresh,
            onNew = { editingId = null; editorOpen = true },
            onEdit = { editingId = it.id; editorOpen = true },
            onDelete = { deleteId = it.id },
        )
    }
    val deleteTarget = state.workspace.expenses.firstOrNull { it.id == deleteId }
    if (deleteTarget != null) AlertDialog(
        onDismissRequest = { if (!state.working) deleteId = null },
        icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = Error700) },
        title = { Text("Delete this expense?") },
        text = { Text("${deleteTarget.categoryName} · ${formatUgx(deleteTarget.amount)} will be permanently removed from financial reporting.") },
        confirmButton = { Button({ onDelete(deleteTarget.id) }, enabled = !state.working && !state.uncertain) { Text("Delete") } },
        dismissButton = { TextButton({ deleteId = null }, enabled = !state.working) { Text("Cancel") } },
    )
}

@Composable
private fun ExpenseDashboard(
    state: ExpenseUiState,
    onBack: () -> Unit,
    onPeriod: (ExpensePeriod) -> Unit,
    onCategory: (String?) -> Unit,
    onRefresh: () -> Unit,
    onNew: () -> Unit,
    onEdit: (Expense) -> Unit,
    onDelete: (Expense) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Expenses", "Branch operating expense ledger", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ChipRow(ExpensePeriod.entries.map { it.name to it.label }, state.period.name) { name -> onPeriod(ExpensePeriod.valueOf(name)) } }
            if (state.workspace.categories.isNotEmpty()) item { CategoryFilters(state.workspace.categories, state.categoryId, onCategory) }
            if (state.loading && state.workspace.expenses.isEmpty()) item { LoadingBox() }
            else {
                item { ExpenseSummaryCard(state.workspace.summary) }
                if (state.uncertain) item { WarningCard("A save or delete may already have completed. Refresh the selected ledger view before trying another change.") }
                item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Recorded expenses", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); if (state.canCreate) Button(onNew, enabled = !state.working && !state.uncertain) { Text("New expense") } } }
                if (state.workspace.expenses.isEmpty()) item { EmptyCard("No expenses recorded in this period.") }
                items(state.workspace.expenses, key = { it.id }) { expense -> ExpenseCard(expense, state.canEdit, state.canDelete, { onEdit(expense) }, { onDelete(expense) }) }
            }
        }
        Surface(shadowElevation = 6.dp) { OutlinedButton(onRefresh, Modifier.fillMaxWidth().padding(12.dp), enabled = !state.loading && !state.working) { Icon(Icons.Outlined.Refresh, null); Text("  Refresh expense ledger") } }
    }
}

@Composable
private fun ExpenseSummaryCard(summary: ExpenseSummary) {
    PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).background(Primary50, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, tint = Primary700) }; Column(Modifier.padding(start = 10.dp)) { Text("Period spend", color = Slate500, style = MaterialTheme.typography.labelSmall); Text(formatUgx(summary.total), style = MaterialTheme.typography.headlineSmall, fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SmallMetric("Entries", summary.count.toString(), Modifier.weight(1f)); SmallMetric("Average", formatUgx(summary.average), Modifier.weight(1f)) }
        summary.topCategory?.let { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Top category · $it", color = Slate500); Text(formatUgx(summary.topCategoryTotal), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } }
    } }
}

@Composable private fun SmallMetric(label: String, value: String, modifier: Modifier) { Surface(modifier, color = Primary50, shape = RoundedCornerShape(10.dp)) { Column(Modifier.padding(10.dp)) { Text(label, color = Primary700, style = MaterialTheme.typography.labelSmall); Text(value, fontWeight = FontWeight.Bold, fontFamily = if (value.startsWith("UGX")) MoneyFontFamily else null) } } }

@Composable
private fun ExpenseCard(expense: Expense, canEdit: Boolean, canDelete: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(13.dp)) { Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Payments, null, tint = Primary700); Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(expense.categoryName, fontWeight = FontWeight.Bold); Text(listOfNotNull(expense.payee, expense.reference).joinToString(" · ").ifBlank { expense.paymentMethod.label() }, color = Slate500, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Column(horizontalAlignment = Alignment.End) { Text(formatUgx(expense.amount), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold); Text(expense.date, color = Slate500, style = MaterialTheme.typography.labelSmall) } }
        expense.notes?.let { Text(it, color = Slate500, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { if (canEdit) TextButton(onEdit) { Icon(Icons.Outlined.Edit, null, Modifier.size(17.dp)); Text(" Edit") }; if (canDelete) TextButton(onDelete) { Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(17.dp), tint = Error700); Text(" Delete", color = Error700) } }
    } }
}

@Composable
private fun ExpenseEditor(
    state: ExpenseUiState,
    expense: Expense?,
    onBack: () -> Unit,
    onSave: (String?, String, String, Long, String, String, String, String) -> Unit,
) {
    var date by rememberSaveable(expense?.id) { mutableStateOf(expense?.date ?: LocalDate.now().toString()) }
    var category by rememberSaveable(expense?.id) { mutableStateOf(expense?.categoryId.orEmpty()) }
    var amount by rememberSaveable(expense?.id) { mutableStateOf(expense?.amount?.toString().orEmpty()) }
    var payee by rememberSaveable(expense?.id) { mutableStateOf(expense?.payee.orEmpty()) }
    var method by rememberSaveable(expense?.id) { mutableStateOf(expense?.paymentMethod ?: "cash") }
    var reference by rememberSaveable(expense?.id) { mutableStateOf(expense?.reference.orEmpty()) }
    var notes by rememberSaveable(expense?.id) { mutableStateOf(expense?.notes.orEmpty()) }
    val value = amount.toLongOrNull()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(if (expense == null) "New expense" else "Edit expense", "Operating expense record", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DateField(date) { date = it } }
            item { Text("Category", fontWeight = FontWeight.Bold); ChipRow(state.workspace.categories.map { it.id to it.name }, category) { category = it } }
            item { OutlinedTextField(amount, { amount = it.filter(Char::isDigit).take(12) }, Modifier.fillMaxWidth(), label = { Text("Amount") }, prefix = { Text("UGX ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = AmountGroupingVisualTransformation) }
            item { Text("Payment method", fontWeight = FontWeight.Bold); ChipRow(listOf("cash" to "Cash", "mobile_money" to "Mobile money", "bank" to "Bank", "credit" to "On credit"), method) { method = it } }
            if (method == "cash") item { Surface(Modifier.fillMaxWidth(), color = Primary50, shape = RoundedCornerShape(11.dp)) { Text("This records the operating expense ledger only. Record the physical drawer outflow separately in Cash book when required.", Modifier.padding(11.dp), color = Primary700, style = MaterialTheme.typography.bodySmall) } }
            item { OutlinedTextField(payee, { payee = it.take(120) }, Modifier.fillMaxWidth(), label = { Text("Payee (optional)") }, singleLine = true) }
            item { OutlinedTextField(reference, { reference = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("Receipt / reference (optional)") }, singleLine = true) }
            item { OutlinedTextField(notes, { notes = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 3) }
            if (state.uncertain) item { WarningCard("The previous change may already be saved. Return and refresh before submitting again.") }
        }
        Surface(shadowElevation = 8.dp) { Button({ onSave(expense?.id, date, category, value ?: 0, payee, method, reference, notes) }, Modifier.fillMaxWidth().padding(12.dp).height(52.dp), enabled = category.isNotBlank() && (value ?: 0) > 0 && !state.working && !state.uncertain) { if (state.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (expense == null) "Record expense" else "Save changes") } }
    }
}

@Composable
private fun DateField(value: String, onValue: (String) -> Unit) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
    OutlinedTextField(value, {}, Modifier.fillMaxWidth(), readOnly = true, label = { Text("Expense date") }, trailingIcon = { IconButton({ DatePickerDialog(context, { _, year, month, day -> onValue(LocalDate.of(year, month + 1, day).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }) { Icon(Icons.Outlined.CalendarMonth, "Choose date") } }, singleLine = true)
}

@Composable
fun ApprovalScreen(
    state: ApprovalUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDecision: (ApprovalRequest, Boolean, String) -> Unit,
    onVerified: () -> Unit,
) {
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var decisionId by rememberSaveable { mutableStateOf<String?>(null) }
    var approve by rememberSaveable { mutableStateOf(false) }
    val detail = state.requests.firstOrNull { it.id == detailId }
    val decision = state.requests.firstOrNull { it.id == decisionId }
    LaunchedEffect(state.message) { if (state.message != null) { decisionId = null; detailId = null } }
    LaunchedEffect(state.uncertain) { if (state.uncertain) { decisionId = null; detailId = null } }
    BackHandler(enabled = detail != null) { detailId = null }
    if (detail != null) ApprovalDetailScreen(detail, state.currentUserId, { detailId = null }, { approve = it; decisionId = detail.id })
    else ApprovalDashboard(state, onBack, onRefresh, { detailId = it.id }, { request, mode -> approve = mode; decisionId = request.id }, onVerified)
    if (decision != null) DecisionDialog(decision, approve, state.working, { decisionId = null }) { note -> onDecision(decision, approve, note) }
}

@Composable
private fun ApprovalDashboard(state: ApprovalUiState, onBack: () -> Unit, onRefresh: () -> Unit, onOpen: (ApprovalRequest) -> Unit, onDecision: (ApprovalRequest, Boolean) -> Unit, onVerified: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Approvals", "Manager review and execution queue", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PosCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).background(if (state.requests.isEmpty()) MoneyGreen50 else Primary50, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AssignmentTurnedIn, null, tint = if (state.requests.isEmpty()) MoneyGreen700 else Primary700) }; Column(Modifier.padding(start = 11.dp)) { Text(if (state.requests.isEmpty()) "Queue clear" else "${state.requests.size} pending request${if (state.requests.size == 1) "" else "s"}", fontWeight = FontWeight.Bold); Text("Only requests for the selected branch are shown", color = Slate500, style = MaterialTheme.typography.bodySmall) } } } }
            if (state.uncertain) item { PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { WarningCard("A decision may have executed its underlying business action. Verify the affected sale, cash, stock, or price record before clearing this safety lock."); OutlinedButton(onVerified, Modifier.fillMaxWidth()) { Text("I verified the affected record") } } } }
            if (state.loading && state.requests.isEmpty()) item { LoadingBox() }
            else if (state.requests.isEmpty()) item { EmptyCard("No pending approval requests. All clear.") }
            else items(state.requests, key = { it.id }) { request -> ApprovalCard(request, request.requestedById == state.currentUserId, state.working || state.loading || state.uncertain, { onOpen(request) }, { onDecision(request, false) }, { onDecision(request, true) }) }
        }
        Surface(shadowElevation = 6.dp) { OutlinedButton(onRefresh, Modifier.fillMaxWidth().padding(12.dp), enabled = !state.loading && !state.working) { Icon(Icons.Outlined.Refresh, null); Text("  Refresh approval queue") } }
    }
}

@Composable
private fun ApprovalCard(request: ApprovalRequest, own: Boolean, actionsDisabled: Boolean, onOpen: () -> Unit, onReject: () -> Unit, onApprove: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(13.dp)) { Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Surface(color = Primary50, shape = CircleShape) { Text(request.typeLabel, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Primary700, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }; Text(request.createdAt.displayTime(), Modifier.weight(1f), color = Slate500, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End) }
        Text("Requested by ${request.requestedByName}", color = Slate500, style = MaterialTheme.typography.bodySmall)
        request.reference?.let { Text(it, fontWeight = FontWeight.Bold) }
        request.amount?.let { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Financial impact", color = Slate500); Text(formatUgx(it), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } }
        request.reason?.let { Text(it, color = Slate500, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        if (own) Text("You cannot resolve your own request.", color = Error700, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onOpen) { Text("Details") }; OutlinedButton(onReject, enabled = !actionsDisabled && !own) { Text("Reject") }; Button(onApprove, Modifier.padding(start = 7.dp), enabled = !actionsDisabled && !own) { Text("Approve") } }
    } }
}

@Composable
private fun ApprovalDetailScreen(request: ApprovalRequest, currentUserId: String, onBack: () -> Unit, onDecision: (Boolean) -> Unit) {
    val own = request.requestedById == currentUserId
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(request.typeLabel, "Requested by ${request.requestedByName}", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            request.amount?.let { item { PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("Financial impact", color = Slate500); Text(formatUgx(it), style = MaterialTheme.typography.headlineSmall, fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold); request.threshold?.let { threshold -> Text("Policy threshold ${formatUgx(threshold)}", color = Error700) } } } } }
            request.reason?.let { item { DetailRow("Reason", it, false) } }
            items(request.details, key = { "${it.label}:${it.value}" }) { detail -> DetailRow(detail.label, detail.value, detail.money) }
            if (own) item { WarningCard("You requested this action. A different manager must approve or reject it.") }
        }
        if (!own) Surface(shadowElevation = 8.dp) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ onDecision(false) }, Modifier.weight(1f).height(52.dp)) { Text("Reject") }; Button({ onDecision(true) }, Modifier.weight(1f).height(52.dp)) { Text("Approve") } } }
    }
}

@Composable private fun DetailRow(label: String, value: String, money: Boolean) { PosCard(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f), color = Slate500); Text(if (money) value.toDoubleOrNull()?.toLong()?.let(::formatUgx) ?: value else value, fontFamily = if (money) MoneyFontFamily else null, fontWeight = FontWeight.Bold, textAlign = TextAlign.End) } } }

@Composable
private fun DecisionDialog(request: ApprovalRequest, approve: Boolean, working: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var note by remember(request.id, approve) { mutableStateOf("") }
    val valid = approve || note.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        icon = { Icon(if (approve) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = if (approve) MoneyGreen700 else Error700) },
        title = { Text(if (approve) "Approve and execute?" else "Reject request?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(if (approve) "This immediately executes the underlying ${request.typeLabel.lowercase()} action." else "The requester will need your reason before correcting or resubmitting."); OutlinedTextField(note, { note = it.take(500) }, Modifier.fillMaxWidth(), label = { Text(if (approve) "Decision note (optional)" else "Rejection reason") }, minLines = 3) } },
        confirmButton = { Button({ onConfirm(note) }, enabled = valid && !working) { Text(if (approve) "Approve and execute" else "Reject") } },
        dismissButton = { TextButton(onDismiss, enabled = !working) { Text("Cancel") } },
    )
}

@Composable private fun CategoryFilters(categories: List<ExpenseCategory>, selected: String?, onSelect: (String?) -> Unit) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { FilterChip(selected == null, { onSelect(null) }, { Text("All categories") }); categories.forEach { category -> FilterChip(selected == category.id, { onSelect(category.id) }, { Text(category.name) }) } } }
@Composable private fun ChipRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) { options.forEach { (value, label) -> FilterChip(selected == value, { onSelect(value) }, { Text(label) }) } } }
@Composable private fun LoadingBox() { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
@Composable private fun WarningCard(text: String) { Surface(Modifier.fillMaxWidth(), color = Error50, contentColor = Error700, shape = RoundedCornerShape(12.dp)) { Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) } }
@Composable private fun EmptyCard(text: String) { Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) { Text(text, Modifier.padding(20.dp), color = Slate500, textAlign = TextAlign.Center) } }
private fun String.label() = replace('_', ' ').replaceFirstChar(Char::uppercase)
private val OperationTime = DateTimeFormatter.ofPattern("dd MMM · HH:mm")
private fun String.displayTime() = runCatching { Instant.parse(this).atZone(ZoneId.systemDefault()).format(OperationTime) }.getOrDefault(take(16).replace('T', ' '))
