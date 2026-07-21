package com.kline.inventorypos.feature.document

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kline.inventorypos.core.common.AmountGroupingVisualTransformation
import com.kline.inventorypos.core.common.formatUgx
import com.kline.inventorypos.core.designsystem.*
import com.kline.inventorypos.core.model.BusinessDocument
import com.kline.inventorypos.core.model.DocumentDraftLine
import java.time.LocalDate

@Composable fun DocumentScreen(state: DocumentUiState, onBack: () -> Unit, onType: (String?) -> Unit, onStatus: (String?) -> Unit, onQuery: (String) -> Unit, onSearch: () -> Unit, onRefresh: () -> Unit, onOpen: (String) -> Unit, onClose: () -> Unit, onSave: (String?, String, String, String, String, String?, String?, String?, String, String, List<DocumentDraftLine>) -> Unit, onTransition: (String, String) -> Unit, onVoid: (String, String) -> Unit, onConvert: (String, String?, String) -> Unit) {
    var editor by rememberSaveable { mutableStateOf(false) }; var lifecycle by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.message) { if (state.message != null) { editor = false; lifecycle = null } }
    BackHandler(enabled = editor || state.detail != null) { if (editor) editor = false else onClose() }
    when { editor -> DocumentEditor(state, state.detail?.takeIf { it.status == "draft" }, { editor = false }, onSave); state.detail != null -> DocumentDetail(state, { onClose() }, { editor = true }, { lifecycle = it }, onTransition, onConvert); else -> DocumentList(state, onBack, onType, onStatus, onQuery, onSearch, onRefresh, onOpen) { editor = true } }
    val doc = state.detail
    if (lifecycle == "void" && doc != null) ReasonDialog("Void ${doc.number}?", true, { lifecycle = null }) { onVoid(doc.id, it) }
}

@Composable private fun DocumentList(state: DocumentUiState, onBack: () -> Unit, onType: (String?) -> Unit, onStatus: (String?) -> Unit, onQuery: (String) -> Unit, onSearch: () -> Unit, onRefresh: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit) { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { FocusedHeader("Business documents", "Quotes, invoices and receipts", onBack); LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { OutlinedTextField(state.query, onQuery, Modifier.fillMaxWidth(), label={Text("Number or client")}, trailingIcon={IconButton(onSearch){Icon(Icons.Outlined.Search,"Search")}}, singleLine=true) }; item { Choices(listOf(null to "All", "quotation" to "Quotes", "invoice" to "Invoices", "receipt" to "Receipts"), state.type, onType) }; item { Choices(listOf(null to "Any status", "draft" to "Draft", "sent" to "Sent", "paid" to "Paid", "void" to "Void"), state.status, onStatus) }; item { Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically){ Text("Documents",Modifier.weight(1f),fontWeight=FontWeight.Bold); if(state.canCreate) Button(onNew){Text("New")}} }; if(state.loading&&state.documents.isEmpty()) item { Box(Modifier.fillMaxWidth().height(180.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()} } else if(state.documents.isEmpty()) item{Empty("No documents match these filters.")} else items(state.documents,key={it.id}){ d-> DocumentCard(d){onOpen(d.id)} }; if(state.uncertain)item{Warn("A document action may already have completed. Refresh the document and linked records before retrying.")} }; Surface(shadowElevation=6.dp){OutlinedButton(onRefresh,Modifier.fillMaxWidth().padding(12.dp),enabled=!state.loading&&!state.working){Icon(Icons.Outlined.Refresh,null);Text("  Refresh documents")}} } }
@Composable private fun DocumentCard(doc: BusinessDocument,onOpen:()->Unit){Surface(Modifier.fillMaxWidth().clickable(onClick=onOpen),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(13.dp)){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Icon(when(doc.type){"invoice"->Icons.Outlined.RequestQuote;"receipt"->Icons.AutoMirrored.Outlined.ReceiptLong;else->Icons.Outlined.Description},null,tint=Primary700);Column(Modifier.padding(start=10.dp).weight(1f)){Text(doc.number,fontWeight=FontWeight.Bold);Text("${doc.billToName} · ${doc.status.label()}",color=Slate500,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};Column(horizontalAlignment=Alignment.End){Text(formatUgx(doc.total),fontFamily=MoneyFontFamily,fontWeight=FontWeight.Bold);Text(doc.date,color=Slate500,style=MaterialTheme.typography.labelSmall)}}}}

@Composable
private fun DocumentDetail(state: DocumentUiState, onBack: () -> Unit, onEdit: () -> Unit, onLifecycle: (String) -> Unit, onTransition: (String, String) -> Unit, onConvert: (String, String?, String) -> Unit) {
    val document = state.detail!!
    var convert by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(document.number, "${document.type.label()} · ${document.status.label()}", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp)) { Text(document.billToName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); document.billToAddress?.let { Text(it, color = Slate500) }; Spacer(Modifier.height(10.dp)); Text(formatUgx(document.total), style = MaterialTheme.typography.headlineMedium, fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold); Text("Document date ${document.date}", color = Slate500) } } }
            items(document.items, key = { it.id }) { line -> Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(11.dp)) { Row(Modifier.padding(12.dp)) { Column(Modifier.weight(1f)) { Text(line.description, fontWeight = FontWeight.Bold); Text("${line.quantity.cleanQuantity()} × ${formatUgx(line.unitPrice)}", color = Slate500) }; Text(formatUgx(line.lineTotal), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } } }
            document.notes?.let { item { Text(it, color = Slate500) } }
            if (document.derived.isNotEmpty()) item { Text("Linked: ${document.derived.joinToString { it.number }}", color = Primary700) }
            document.voidReason?.let { item { Warn("Void reason: $it") } }
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (document.status == "draft" && state.canEdit) OutlinedButton(onEdit, Modifier.fillMaxWidth()) { Text("Edit draft") }
                nextStatuses(document).forEach { status -> OutlinedButton({ onTransition(document.id, status) }, Modifier.fillMaxWidth(), enabled = !state.working && !state.uncertain) { Text("Mark ${status.label()}") } }
                if (document.type != "receipt" && document.status in setOf("draft", "sent", "accepted", "paid") && state.canCreate) Button({ convert = true }, Modifier.fillMaxWidth()) { Text("Convert to ${if (document.type == "quotation") "invoice" else "receipt"}") }
                if (document.status != "void" && state.canVoid) TextButton({ onLifecycle("void") }, Modifier.fillMaxWidth()) { Text("Void document", color = Error700) }
            } }
        }
    }
    if (convert) ConvertDialog(document.type == "invoice", { convert = false }) { method, reference -> onConvert(document.id, method, reference) }
}

@Composable
private fun DocumentEditor(state: DocumentUiState, document: BusinessDocument?, onBack: () -> Unit, onSave: (String?, String, String, String, String, String?, String?, String?, String, String, List<DocumentDraftLine>) -> Unit) {
    var type by rememberSaveable { mutableStateOf(document?.type ?: "quotation") }; var bill by rememberSaveable { mutableStateOf(document?.billToName.orEmpty()) }; var address by rememberSaveable { mutableStateOf(document?.billToAddress.orEmpty()) }; var notes by rememberSaveable { mutableStateOf(document?.notes.orEmpty()) }; var description by rememberSaveable { mutableStateOf("") }; var quantity by rememberSaveable { mutableStateOf("1") }; var price by rememberSaveable { mutableStateOf("") }
    var lines by remember(document?.id) { mutableStateOf<List<DocumentDraftLine>>(document?.items?.map { DocumentDraftLine(it.description, it.quantity, it.unitPrice) }.orEmpty()) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(if (document == null) "New document" else "Edit ${document.number}", "Client and line items", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (document == null) item { Choices(listOf("quotation" to "Quote", "invoice" to "Invoice", "receipt" to "Receipt"), type) { type = it } }
            item { OutlinedTextField(bill, { bill = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("Bill to") }, singleLine = true) }
            item { OutlinedTextField(address, { address = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Address (optional)") }, minLines = 2) }
            item { Text("Line items", fontWeight = FontWeight.Bold) }
            items(lines.indices.toList()) { index -> val line = lines[index]; Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(line.description, fontWeight = FontWeight.Bold); Text("${line.quantity.cleanQuantity()} × ${formatUgx(line.unitPrice)}", color = Slate500) }; IconButton({ lines = lines.filterIndexed { position, _ -> position != index } }) { Icon(Icons.Outlined.DeleteOutline, "Remove", tint = Error700) } } }
            item { OutlinedTextField(description, { description = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Item description") }, singleLine = true) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(quantity, { quantity = it.filter { char -> char.isDigit() || char == '.' }.take(8) }, Modifier.weight(1f), label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true); OutlinedTextField(price, { price = it.filter(Char::isDigit).take(12) }, Modifier.weight(2f), label = { Text("Unit price") }, prefix = { Text("UGX ") }, visualTransformation = AmountGroupingVisualTransformation, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true) } }
            item { OutlinedButton({ val qty = quantity.toDoubleOrNull(); val amount = price.toLongOrNull(); if (description.isNotBlank() && qty != null && qty > 0 && amount != null) { lines = lines + DocumentDraftLine(description, qty, amount); description = ""; quantity = "1"; price = "" } }, Modifier.fillMaxWidth()) { Text("Add line") } }
            item { OutlinedTextField(notes, { notes = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 3) }
            if (state.uncertain) item { Warn("Refresh before retrying this document change.") }
        }
        Surface(shadowElevation = 8.dp) { Button({ onSave(document?.id, type, bill, address, document?.date ?: LocalDate.now().toString(), document?.validUntil, document?.dueDate, document?.paymentMethod, document?.paymentReference.orEmpty(), notes, lines) }, Modifier.fillMaxWidth().padding(12.dp).height(52.dp), enabled = bill.isNotBlank() && lines.isNotEmpty() && !state.working && !state.uncertain) { if (state.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (document == null) "Create document" else "Save draft") } }
    }
}

@Composable private fun ConvertDialog(receipt:Boolean,onDismiss:()->Unit,onConfirm:(String?,String)->Unit){var method by remember{mutableStateOf(if(receipt)"cash" else "")};var ref by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text(if(receipt)"Create receipt" else "Create invoice")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){if(receipt){Text("Payment method");Choices(listOf("cash" to "Cash","mobile_money" to "Mobile money","bank" to "Bank","cheque" to "Cheque"),method){method=it};OutlinedTextField(ref,{ref=it.take(100)},label={Text("Payment reference (optional)")})}else Text("The quote’s client, line items, and totals will be copied into a new draft invoice.")}},confirmButton={Button({onConfirm(method.takeIf{it.isNotBlank()},ref)}){Text("Convert")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})}
@Composable private fun ReasonDialog(title:String,required:Boolean,onDismiss:()->Unit,onConfirm:(String)->Unit){var text by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(text,{text=it.take(500)},label={Text("Reason")},minLines=3)},confirmButton={Button({onConfirm(text)},enabled=!required||text.isNotBlank()){Text("Confirm")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})}
@Composable private fun <T> Choices(options:List<Pair<T,String>>,selected:T,onSelect:(T)->Unit){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){options.forEach{(v,l)->FilterChip(selected==v,{onSelect(v)},{Text(l)})}}}
@Composable private fun Empty(text:String){Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(12.dp)){Text(text,Modifier.padding(20.dp),color=Slate500)}}
@Composable private fun Warn(text:String){Surface(Modifier.fillMaxWidth(),color=Error50,contentColor=Error700,shape=RoundedCornerShape(12.dp)){Text(text,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Bold)}}
private fun String.label()=replace('_',' ').replaceFirstChar(Char::uppercase);private fun Double.cleanQuantity()=if(this%1.0==0.0)toLong().toString() else toString();private fun nextStatuses(d:BusinessDocument)=when(d.type){"quotation"->when(d.status){"draft"->listOf("sent","accepted","declined");"sent"->listOf("accepted","declined");else->emptyList()};"invoice"->when(d.status){"draft"->listOf("sent","paid");"sent"->listOf("paid");else->emptyList()};else->emptyList()}
