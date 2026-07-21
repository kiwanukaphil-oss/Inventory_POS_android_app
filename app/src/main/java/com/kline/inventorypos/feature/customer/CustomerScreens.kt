package com.kline.inventorypos.feature.customer

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Grade
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.kline.inventorypos.core.designsystem.Slate100
import com.kline.inventorypos.core.designsystem.Slate500
import com.kline.inventorypos.core.model.CustomerAccount
import com.kline.inventorypos.core.model.CustomerWorkspace
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CustomerScreen(
    state: CustomerUiState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onView: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (CustomerAccount) -> Unit,
    onClose: () -> Unit,
    onRefreshDetail: () -> Unit,
    onAddNote: (String, Boolean) -> Unit,
) {
    BackHandler(enabled = state.selected != null, onBack = onClose)
    state.selected?.let { workspace ->
        CustomerDetailScreen(workspace, state, onClose, onRefreshDetail, onAddNote)
        return
    }
    if (state.detailLoading) {
        Column(Modifier.fillMaxSize()) {
            FocusedHeader("Customer profile", "Loading account workspace", onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader("Customers", "Accounts and relationships", onBack)
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text("Search name, phone or email") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { IconButton(onRefresh, enabled = !state.loading) { Icon(Icons.Outlined.Refresh, "Refresh") } },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        ChoiceRow(listOf("all" to "All", "with_credit" to "Owes credit", "with_prepaid" to "Has prepaid"), state.view, onView)
        when {
            state.loading && state.customers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.customers.isEmpty() -> EmptyCustomers()
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.customers, key = CustomerAccount::id) { customer -> CustomerCard(customer) { onOpen(customer) } }
            }
        }
    }
}

@Composable
private fun CustomerCard(customer: CustomerAccount, onClick: () -> Unit) {
    PosCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Primary50, CircleShape), contentAlignment = Alignment.Center) {
                Text(customer.initials(), color = Primary700, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text(customer.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(customer.phone ?: customer.email ?: "No contact details", color = Slate500, style = MaterialTheme.typography.bodySmall)
                Text(listOfNotNull(customer.tier, customer.segment).joinToString(" · ").ifBlank { "${customer.totalPurchases} purchases" }, color = Slate500, style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatUgx(customer.totalSpent), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold)
                if (customer.creditBalance > 0) Text("Owes ${formatUgx(customer.creditBalance)}", color = Error700, style = MaterialTheme.typography.labelSmall)
                else if (customer.prepaidBalance > 0) Text("Prepaid ${formatUgx(customer.prepaidBalance)}", color = MoneyGreen700, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CustomerDetailScreen(
    workspace: CustomerWorkspace,
    state: CustomerUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddNote: (String, Boolean) -> Unit,
) {
    var tab by rememberSaveable(workspace.customer.id) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(workspace.customer.name, listOfNotNull(workspace.customer.tier, workspace.customer.segment).joinToString(" · ").ifBlank { "Customer account" }, onBack)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Overview", "Purchases", "Accounts", "Notes").forEachIndexed { index, label -> FilterChip(tab == index, { tab = index }, { Text(label) }) }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (tab) {
                0 -> overviewItems(workspace)
                1 -> purchaseItems(workspace)
                2 -> accountItems(workspace)
                else -> noteItems(workspace, state, onAddNote)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        Surface(shadowElevation = 6.dp) { OutlinedButton(onRefresh, Modifier.fillMaxWidth().padding(12.dp), enabled = !state.detailLoading) {
            Icon(Icons.Outlined.Refresh, null); Text("  Refresh customer")
        } }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.overviewItems(workspace: CustomerWorkspace) {
    val customer = workspace.customer
    item {
        PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ContactRow(Icons.Outlined.Phone, "Phone", customer.phone ?: "Not provided")
            ContactRow(Icons.Outlined.Email, "Email", customer.email ?: "Not provided")
            customer.city?.let { SummaryText("Location", it) }
            SummaryText("Customer type", customer.type.replaceFirstChar(Char::uppercase))
            if (customer.tags.isNotEmpty()) SummaryText("Tags", customer.tags.joinToString(" · "))
        } }
    }
    item { Text("Relationship", style = MaterialTheme.typography.titleSmall) }
    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Lifetime spend", formatUgx(customer.totalSpent), Modifier.weight(1f))
        MetricCard("Purchases", customer.totalPurchases.toString(), Modifier.weight(1f))
    } }
    if (workspace.contacts.isNotEmpty()) {
        item { Text("Contacts", style = MaterialTheme.typography.titleSmall) }
        items(workspace.contacts, key = { it.id }) { contact ->
            PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(contact.name, fontWeight = FontWeight.Bold); if (contact.primary) Text("Primary", color = Primary700, style = MaterialTheme.typography.labelSmall) }
                contact.title?.let { Text(it, color = Slate500, style = MaterialTheme.typography.bodySmall) }
                contact.phone?.let { ContactRow(Icons.Outlined.Phone, "Phone", it) }
                contact.email?.let { ContactRow(Icons.Outlined.Email, "Email", it) }
            } }
        }
    }
    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Loyalty points", customer.loyaltyPoints.toString(), Modifier.weight(1f))
        MetricCard("Last visit", customer.lastPurchaseDate?.displayDate() ?: "Never", Modifier.weight(1f))
    } }
    if (workspace.loyalty.isNotEmpty()) {
        item { Text("Recent loyalty", style = MaterialTheme.typography.titleSmall) }
        items(workspace.loyalty.take(5), key = { it.id }) { entry ->
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Grade, null, tint = Primary700)
                    Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(entry.description ?: entry.type.label(), fontWeight = FontWeight.Bold); Text(entry.date.displayDate(), color = Slate500, style = MaterialTheme.typography.bodySmall) }
                    Text("${if (entry.points > 0) "+" else ""}${entry.points}", color = if (entry.points >= 0) MoneyGreen700 else Error700, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.purchaseItems(workspace: CustomerWorkspace) {
    item { Text("Branch purchase history", style = MaterialTheme.typography.titleMedium) }
    if (workspace.purchases.isEmpty()) item { EmptySection("No purchases at this branch") }
    items(workspace.purchases, key = { it.id }) { purchase ->
        PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(purchase.receiptNumber, fontWeight = FontWeight.Bold); Text(formatUgx(purchase.net), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) }
            Text("${purchase.date.displayDate()} · ${purchase.itemCount} items · ${purchase.paymentMethod.label()}", color = Slate500, style = MaterialTheme.typography.bodySmall)
            if (purchase.productNames.isNotEmpty()) Text(purchase.productNames.joinToString(", "), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            if (purchase.returned > 0) Text("Returned ${formatUgx(purchase.returned)}", color = Error700, style = MaterialTheme.typography.labelSmall)
        } }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.accountItems(workspace: CustomerWorkspace) {
    val customer = workspace.customer
    item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AccountCard("Credit owed", customer.creditBalance, customer.creditLimit?.let { "Limit ${formatUgx(it)}" }, true, Modifier.weight(1f))
        AccountCard("Prepaid", customer.prepaidBalance, "Available balance", false, Modifier.weight(1f))
    } }
    item { AccountCard("Store credit", workspace.storeCredit.activeBalance, "${workspace.storeCredit.activeCount} active credit${if (workspace.storeCredit.activeCount == 1) "" else "s"}", false, Modifier.fillMaxWidth()) }
    if (customer.creditBalance > 0) {
        item { Text("Credit aging", style = MaterialTheme.typography.titleSmall) }
        item { PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryMoney("Current", workspace.aging.current); SummaryMoney("0–30 days", workspace.aging.days0To30); SummaryMoney("31–60 days", workspace.aging.days31To60); SummaryMoney("61+ days", workspace.aging.days61Plus)
        } } }
    }
    item { Text("Account statement", style = MaterialTheme.typography.titleSmall) }
    if (workspace.ledger.isEmpty()) item { EmptySection("No account activity") }
    items(workspace.ledger, key = { "${it.account}-${it.id}" }) { entry ->
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AccountBalanceWallet, null, tint = Primary700)
            Column(Modifier.padding(start = 10.dp).weight(1f)) { Text(entry.event.label(), fontWeight = FontWeight.Bold); Text(listOfNotNull(entry.receiptNumber, entry.date.displayDate()).joinToString(" · "), color = Slate500, style = MaterialTheme.typography.bodySmall); entry.notes?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Slate500) } }
            Column(horizontalAlignment = Alignment.End) { Text(formatUgx(entry.signedAmount), color = if (entry.signedAmount < 0) Error700 else MoneyGreen700, fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold); Text("Bal ${formatUgx(entry.runningBalance)}", color = Slate500, style = MaterialTheme.typography.labelSmall) }
        } }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.noteItems(workspace: CustomerWorkspace, state: CustomerUiState, onAddNote: (String, Boolean) -> Unit) {
    if (state.canEdit) item { NoteComposer(state.working, state.noteUncertain, onAddNote) }
    if (workspace.notes.isEmpty()) item { EmptySection("No customer notes") }
    items(workspace.notes, key = { it.id }) { note ->
        PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { if (note.pinned) Icon(Icons.Outlined.PushPin, null, Modifier.size(16.dp), tint = Primary700); Text(if (note.pinned) "  Pinned note" else "Customer note", color = Slate500, style = MaterialTheme.typography.labelSmall) }
            Text(note.body)
            Text(listOfNotNull(note.author, note.date.displayDate()).joinToString(" · "), color = Slate500, style = MaterialTheme.typography.labelSmall)
        } }
    }
}

@Composable private fun NoteComposer(working: Boolean, uncertain: Boolean, onAdd: (String, Boolean) -> Unit) {
    var body by remember { mutableStateOf("") }; var pinned by remember { mutableStateOf(false) }
    PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add internal note", fontWeight = FontWeight.Bold)
        OutlinedTextField(body, { body = it.take(500) }, Modifier.fillMaxWidth(), placeholder = { Text("Customer preferences or follow-up…") }, minLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(pinned, { pinned = it }); Text("Pin this note", Modifier.weight(1f)); Button({ onAdd(body, pinned) }, enabled = body.isNotBlank() && !working && !uncertain) { if (working) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Text("Save") } }
        if (uncertain) Text("Note status is unknown. Refresh before trying again.", color = Error700, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    } }
}

@Composable private fun ContactRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(18.dp), tint = Slate500); Text("  $label", Modifier.weight(1f), color = Slate500, style = MaterialTheme.typography.bodySmall); Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
@Composable private fun MetricCard(label: String, value: String, modifier: Modifier) { Surface(modifier, color = Primary50, shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(12.dp)) { Text(label, color = Primary700, style = MaterialTheme.typography.labelSmall); Text(value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
@Composable private fun AccountCard(label: String, amount: Long, subtitle: String?, danger: Boolean, modifier: Modifier) { Surface(modifier, color = if (danger && amount > 0) Error50 else MoneyGreen50, shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(12.dp)) { Text(label, color = if (danger && amount > 0) Error700 else MoneyGreen700, style = MaterialTheme.typography.labelSmall); Text(formatUgx(amount), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold, maxLines = 1); subtitle?.let { Text(it, color = Slate500, style = MaterialTheme.typography.labelSmall, maxLines = 1) } } } }
@Composable private fun SummaryText(label: String, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Slate500); Text(value, fontWeight = FontWeight.Bold) } }
@Composable private fun SummaryMoney(label: String, amount: Long) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(formatUgx(amount), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } }
@Composable private fun EmptySection(message: String) { Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) { Text(message, Modifier.padding(20.dp), color = Slate500, textAlign = TextAlign.Center) } }
@Composable private fun EmptyCustomers() { Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.People, null, Modifier.size(42.dp), tint = Slate500); Text("No matching customers", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)); Text("Try another search or account filter.", color = Slate500, textAlign = TextAlign.Center) } } }
@Composable private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) { options.forEach { (value, label) -> FilterChip(selected == value, { onSelect(value) }, { Text(label) }) } } }

private fun CustomerAccount.initials(): String = name.split(' ').filter(String::isNotBlank).take(2).joinToString("") { it.take(1) }.uppercase()
private fun String.label(): String = replace('_', ' ').replaceFirstChar(Char::uppercase)
private val CustomerDate = DateTimeFormatter.ofPattern("dd MMM yyyy")
private fun String.displayDate(): String = runCatching { Instant.parse(this).atZone(ZoneId.systemDefault()).format(CustomerDate) }.getOrDefault(take(10))
