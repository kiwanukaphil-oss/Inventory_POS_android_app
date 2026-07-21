package com.kline.inventorypos.feature.document

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kline.inventorypos.core.common.AmountGroupingVisualTransformation
import com.kline.inventorypos.core.common.formatUgx
import com.kline.inventorypos.core.designsystem.*
import com.kline.inventorypos.core.model.BusinessDocument
import com.kline.inventorypos.core.model.DocumentDraftLine
import com.kline.inventorypos.core.model.canConvert
import com.kline.inventorypos.core.model.conversionTargetType
import com.kline.inventorypos.data.document.GeneratedDocumentPdf
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToLong

@Composable fun DocumentScreen(state: DocumentUiState, onBack: () -> Unit, onType: (String?) -> Unit, onStatus: (String?) -> Unit, onQuery: (String) -> Unit, onSearch: () -> Unit, onRefresh: () -> Unit, onOpen: (String) -> Unit, onClose: () -> Unit, onSave: (String?, String, String, String, String, String?, String?, String?, String, String, List<DocumentDraftLine>) -> Unit, onTransition: (String, String) -> Unit, onVoid: (String, String) -> Unit, onConvert: (String, String?, String) -> Unit, onEmail: (BusinessDocument, String, List<String>, String) -> Unit, onPdf: (BusinessDocument, DocumentPdfAction) -> Unit, onPdfConsumed: () -> Unit, onPdfResult: (String?, String?) -> Unit) {
    var editor by rememberSaveable { mutableStateOf(false) }; var lifecycle by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var pendingSave by remember { mutableStateOf<GeneratedDocumentPdf?>(null) }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val pdf = pendingSave
        pendingSave = null
        if (uri != null && pdf != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(pdf.bytes) }
                    ?: error("The selected file could not be opened.")
            }.onSuccess { onPdfResult("${pdf.filename} saved", null) }
                .onFailure { onPdfResult(null, it.message ?: "The PDF could not be saved.") }
        }
    }
    LaunchedEffect(state.preparedPdf?.requestId) {
        val prepared = state.preparedPdf ?: return@LaunchedEffect
        onPdfConsumed()
        when (prepared.action) {
            DocumentPdfAction.SAVE -> {
                pendingSave = prepared.file
                savePdf.launch(prepared.file.filename)
            }
            DocumentPdfAction.VIEW -> runCatching {
                val directory = File(context.cacheDir, "documents").apply { mkdirs() }
                val file = File(directory, prepared.file.filename).apply { writeBytes(prepared.file.bytes) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent, "View ${prepared.file.filename}"))
            }.onSuccess { onPdfResult("PDF ready to view", null) }
                .onFailure { onPdfResult(null, "No PDF viewer is available on this device.") }
        }
    }
    LaunchedEffect(state.message) { if (state.message != null) { editor = false; lifecycle = null } }
    BackHandler(enabled = editor || state.detail != null) { if (editor) editor = false else onClose() }
    when { editor -> DocumentEditor(state, state.detail?.takeIf { it.status == "draft" }, { editor = false }, onSave); state.detail != null -> DocumentDetail(state, { onClose() }, { editor = true }, { lifecycle = it }, onTransition, onConvert, onEmail, onPdf); else -> DocumentList(state, onBack, onType, onStatus, onQuery, onSearch, onRefresh, onOpen) { editor = true } }
    val doc = state.detail
    if (lifecycle == "void" && doc != null) ReasonDialog("Void ${doc.number}?", true, { lifecycle = null }) { onVoid(doc.id, it) }
}

@Composable private fun DocumentList(state: DocumentUiState, onBack: () -> Unit, onType: (String?) -> Unit, onStatus: (String?) -> Unit, onQuery: (String) -> Unit, onSearch: () -> Unit, onRefresh: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit) { Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { FocusedHeader("Business documents", "Quotes, invoices and receipts", onBack); LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { OutlinedTextField(state.query, onQuery, Modifier.fillMaxWidth(), label={Text("Number or client")}, trailingIcon={IconButton(onSearch){Icon(Icons.Outlined.Search,"Search")}}, singleLine=true) }; item { Choices(listOf(null to "All", "quotation" to "Quotes", "invoice" to "Invoices", "receipt" to "Receipts"), state.type, onType) }; item { Choices(listOf(null to "Any status", "draft" to "Draft", "sent" to "Sent", "paid" to "Paid", "void" to "Void"), state.status, onStatus) }; item { Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically){ Text("Documents",Modifier.weight(1f),fontWeight=FontWeight.Bold); if(state.canCreate) Button(onNew){Text("New")}} }; if(state.loading&&state.documents.isEmpty()) item { Box(Modifier.fillMaxWidth().height(180.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()} } else if(state.documents.isEmpty()) item{Empty("No documents match these filters.")} else items(state.documents,key={it.id}){ d-> DocumentCard(d){onOpen(d.id)} }; if(state.uncertain)item{Warn("A document action may already have completed. Refresh the document and linked records before retrying.")} }; Surface(shadowElevation=6.dp){OutlinedButton(onRefresh,Modifier.fillMaxWidth().padding(12.dp),enabled=!state.loading&&!state.working){Icon(Icons.Outlined.Refresh,null);Text("  Refresh documents")}} } }
@Composable private fun DocumentCard(doc: BusinessDocument,onOpen:()->Unit){Surface(Modifier.fillMaxWidth().clickable(onClick=onOpen),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(13.dp)){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Icon(when(doc.type){"invoice"->Icons.Outlined.RequestQuote;"receipt"->Icons.AutoMirrored.Outlined.ReceiptLong;else->Icons.Outlined.Description},null,tint=Primary700);Column(Modifier.padding(start=10.dp).weight(1f)){Text(doc.number,fontWeight=FontWeight.Bold);Text("${doc.billToName} · ${doc.status.label()}",color=Slate500,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};Column(horizontalAlignment=Alignment.End){Text(formatUgx(doc.total),fontFamily=MoneyFontFamily,fontWeight=FontWeight.Bold);Text(doc.date,color=Slate500,style=MaterialTheme.typography.labelSmall)}}}}

@Composable
private fun DocumentDetail(state: DocumentUiState, onBack: () -> Unit, onEdit: () -> Unit, onLifecycle: (String) -> Unit, onTransition: (String, String) -> Unit, onConvert: (String, String?, String) -> Unit, onEmail: (BusinessDocument, String, List<String>, String) -> Unit, onPdf: (BusinessDocument, DocumentPdfAction) -> Unit) {
    val document = state.detail!!
    var convert by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(document.id) { convert = false }
    LaunchedEffect(state.message) { if (state.message?.contains("emailed to", ignoreCase = true) == true) email = false }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(document.number, "${document.type.label()} · ${document.status.label()}", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { PosCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp)) { Text(document.billToName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); document.billToAddress?.let { Text(it, color = Slate500) }; Spacer(Modifier.height(10.dp)); Text(formatUgx(document.total), style = MaterialTheme.typography.headlineMedium, fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold); Text("Document date ${document.date}", color = Slate500) } } }
            items(document.items, key = { it.id }) { line -> Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(11.dp)) { Row(Modifier.padding(12.dp)) { Column(Modifier.weight(1f)) { Text(line.description, fontWeight = FontWeight.Bold); Text("${line.quantity.cleanQuantity()} × ${formatUgx(line.unitPrice)}", color = Slate500) }; Text(formatUgx(line.lineTotal), fontFamily = MoneyFontFamily, fontWeight = FontWeight.Bold) } } }
            document.notes?.let { item { Text(it, color = Slate500) } }
            document.emailedAt?.let { item { Text("Last emailed to ${document.emailedTo.orEmpty()} · ${it.take(16).replace('T', ' ')}", color = MoneyGreen700, style = MaterialTheme.typography.bodySmall) } }
            if (document.derived.isNotEmpty()) item { Text("Linked: ${document.derived.joinToString { it.number }}", color = Primary700) }
            document.voidReason?.let { item { Warn("Void reason: $it") } }
            item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (document.status == "draft" && state.canEdit) OutlinedButton(onEdit, Modifier.fillMaxWidth()) { Text("Edit draft") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ onPdf(document, DocumentPdfAction.VIEW) }, Modifier.weight(1f), enabled = !state.working) { Icon(Icons.Outlined.Visibility, null); Text("  View PDF") }
                    OutlinedButton({ onPdf(document, DocumentPdfAction.SAVE) }, Modifier.weight(1f), enabled = !state.working) { Icon(Icons.Outlined.Download, null); Text("  Save PDF") }
                }
                if (document.status != "void" && state.canSend) Button({ email = true }, Modifier.fillMaxWidth(), enabled = !state.working && !state.uncertain) { Icon(Icons.Outlined.Email, null); Text("  Email PDF") }
                nextStatuses(document).forEach { status -> OutlinedButton({ onTransition(document.id, status) }, Modifier.fillMaxWidth(), enabled = !state.working && !state.uncertain) { Text("Mark ${status.label()}") } }
                val conversionTarget = document.conversionTargetType()
                if (conversionTarget != null && document.canConvert() && state.canCreate) {
                    Button(
                        onClick = { convert = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.working && !state.uncertain,
                    ) { Text("Convert to $conversionTarget") }
                }
                if (document.status !in setOf("converted", "void") && state.canVoid) TextButton({ onLifecycle("void") }, Modifier.fillMaxWidth()) { Text("Void document", color = Error700) }
            } }
        }
    }
    if (convert) ConvertDialog(document.type == "invoice", state.working, { convert = false }) { method, reference -> onConvert(document.id, method, reference) }
    if (email) EmailDocumentDialog(document, state.working, { if (!state.working) email = false }) { to, cc, message -> onEmail(document, to, cc, message) }
}

@Composable
private fun DocumentEditor(state: DocumentUiState, document: BusinessDocument?, onBack: () -> Unit, onSave: (String?, String, String, String, String, String?, String?, String?, String, String, List<DocumentDraftLine>) -> Unit) {
    var type by rememberSaveable { mutableStateOf(document?.type ?: "quotation") }; var bill by rememberSaveable { mutableStateOf(document?.billToName.orEmpty()) }; var address by rememberSaveable { mutableStateOf(document?.billToAddress.orEmpty()) }; var notes by rememberSaveable { mutableStateOf(document?.notes.orEmpty()) }
    var lines by remember(document?.id) { mutableStateOf<List<DocumentDraftLine>>(document?.items?.map { DocumentDraftLine(it.description, it.quantity, it.unitPrice) }.orEmpty()) }
    var lineDialog by rememberSaveable { mutableStateOf(false) }
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusedHeader(if (document == null) "New document" else "Edit ${document.number}", "Client and line items", onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (document == null) item { Choices(listOf("quotation" to "Quote", "invoice" to "Invoice", "receipt" to "Receipt"), type) { type = it } }
            item { OutlinedTextField(bill, { bill = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("Bill to") }, singleLine = true) }
            item { OutlinedTextField(address, { address = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Address (optional)") }, minLines = 2) }
            item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Line items", fontWeight = FontWeight.Bold); Text("${lines.size} ${if (lines.size == 1) "item" else "items"} · ${formatUgx(lines.sumOf { (it.quantity * it.unitPrice).roundToLong() })}", color = Slate500, style = MaterialTheme.typography.bodySmall) }; Button({ editingIndex = null; lineDialog = true }) { Icon(Icons.Outlined.Add, null); Text("  Add item") } } }
            if (lines.isEmpty()) item { Empty("Add every product or service that should appear on this document.") }
            itemsIndexed(lines, key = { index, _ -> index }) { index, line -> DocumentDraftLineCard(line, index + 1, { editingIndex = index; lineDialog = true }) { lines = lines.filterIndexed { position, _ -> position != index } } }
            item { OutlinedTextField(notes, { notes = it.take(1000) }, Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 3) }
            if (state.uncertain) item { Warn("Refresh before retrying this document change.") }
        }
        Surface(shadowElevation = 8.dp) { Button({ onSave(document?.id, type, bill, address, document?.date ?: LocalDate.now().toString(), document?.validUntil, document?.dueDate, document?.paymentMethod, document?.paymentReference.orEmpty(), notes, lines) }, Modifier.fillMaxWidth().padding(12.dp).height(52.dp), enabled = bill.isNotBlank() && lines.isNotEmpty() && !state.working && !state.uncertain) { if (state.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (document == null) "Create document" else "Save draft") } }
    }
    if (lineDialog) LineItemDialog(lines.getOrNull(editingIndex ?: -1), { lineDialog = false }) { line ->
        lines = upsertDocumentLine(lines, editingIndex, line)
        lineDialog = false
        editingIndex = null
    }
}

@Composable private fun DocumentDraftLineCard(line: DocumentDraftLine, number: Int, onEdit: () -> Unit, onRemove: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onEdit), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Primary700, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(8.dp)) { Text(number.toString(), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontWeight = FontWeight.Bold) }
            Column(Modifier.padding(horizontal = 10.dp).weight(1f)) { Text(line.description, fontWeight = FontWeight.Bold); Text("${line.quantity.cleanQuantity()} × ${formatUgx(line.unitPrice)}  ·  ${formatUgx((line.quantity * line.unitPrice).roundToLong())}", color = Slate500, style = MaterialTheme.typography.bodySmall) }
            IconButton(onEdit) { Icon(Icons.Outlined.Edit, "Edit item") }
            IconButton(onRemove) { Icon(Icons.Outlined.DeleteOutline, "Remove item", tint = Error700) }
        }
    }
}

@Composable private fun LineItemDialog(initial: DocumentDraftLine?, onDismiss: () -> Unit, onConfirm: (DocumentDraftLine) -> Unit) {
    var description by rememberSaveable(initial?.description) { mutableStateOf(initial?.description.orEmpty()) }
    var quantity by rememberSaveable(initial?.quantity) { mutableStateOf(initial?.quantity?.cleanQuantity() ?: "1") }
    var price by rememberSaveable(initial?.unitPrice) { mutableStateOf(initial?.unitPrice?.toString().orEmpty()) }
    val qty = quantity.toDoubleOrNull()
    val amount = price.toLongOrNull()
    val valid = description.isNotBlank() && qty != null && qty > 0 && amount != null && amount >= 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add item" else "Edit item") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(description, { description = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(quantity, { quantity = it.filter { char -> char.isDigit() || char == '.' }.take(8) }, Modifier.weight(1f), label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(price, { price = it.filter(Char::isDigit).take(12) }, Modifier.weight(2f), label = { Text("Unit price") }, prefix = { Text("UGX ") }, visualTransformation = AmountGroupingVisualTransformation, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
            if (valid) Text("Line total ${formatUgx((qty * amount).roundToLong())}", color = MoneyGreen700, fontWeight = FontWeight.Bold)
            else Text("Enter a description, positive quantity, and valid unit price.", color = Error700, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button({ onConfirm(DocumentDraftLine(description.trim(), requireNotNull(qty), requireNotNull(amount))) }, enabled = valid) { Text(if (initial == null) "Add item" else "Save changes") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

internal fun upsertDocumentLine(lines: List<DocumentDraftLine>, index: Int?, line: DocumentDraftLine): List<DocumentDraftLine> =
    if (index == null || index !in lines.indices) lines + line else lines.toMutableList().apply { this[index] = line }

@Composable
private fun ConvertDialog(
    receipt: Boolean,
    working: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?, String) -> Unit,
) {
    var method by remember { mutableStateOf(if (receipt) "cash" else "") }
    var reference by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(if (receipt) "Create receipt" else "Create invoice") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (receipt) {
                    Text("This invoice can create one receipt. Once created, it will remain linked for matching and audit purposes.")
                    Text("Payment method")
                    Choices(
                        listOf("cash" to "Cash", "mobile_money" to "Mobile money", "bank" to "Bank", "cheque" to "Cheque"),
                        method,
                    ) { method = it }
                    OutlinedTextField(
                        reference,
                        { reference = it.take(100) },
                        label = { Text("Payment reference (optional)") },
                        enabled = !working,
                    )
                } else {
                    Text("The quote’s client, line items, and totals will be copied into one linked draft invoice.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(method.takeIf(String::isNotBlank), reference) },
                enabled = !working,
            ) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(if (receipt) "Create receipt" else "Create invoice")
            }
        },
        dismissButton = { TextButton(onDismiss, enabled = !working) { Text("Cancel") } },
    )
}
@Composable private fun ReasonDialog(title:String,required:Boolean,onDismiss:()->Unit,onConfirm:(String)->Unit){var text by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={OutlinedTextField(text,{text=it.take(500)},label={Text("Reason")},minLines=3)},confirmButton={Button({onConfirm(text)},enabled=!required||text.isNotBlank()){Text("Confirm")}},dismissButton={TextButton(onDismiss){Text("Cancel")}})}
@Composable private fun EmailDocumentDialog(document:BusinessDocument,working:Boolean,onDismiss:()->Unit,onConfirm:(String,List<String>,String)->Unit){var to by rememberSaveable{mutableStateOf(document.emailedTo?:document.customerEmail.orEmpty())};var cc by rememberSaveable{mutableStateOf(document.emailedCc.joinToString(", "))};var message by rememberSaveable{mutableStateOf("")};val copies=cc.split(',').map(String::trim).filter(String::isNotBlank);val valid=to.matches(Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",RegexOption.IGNORE_CASE))&&copies.size<=10&&copies.all{it.matches(Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",RegexOption.IGNORE_CASE))};AlertDialog(onDismissRequest=onDismiss,title={Text("Email ${document.number}")},text={Column(verticalArrangement=Arrangement.spacedBy(9.dp)){OutlinedTextField(to,{to=it.take(254)},Modifier.fillMaxWidth(),label={Text("Recipient email")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email));OutlinedTextField(cc,{cc=it.take(1000)},Modifier.fillMaxWidth(),label={Text("CC (optional)")},supportingText={Text("Separate up to 10 addresses with commas")},minLines=2,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email));OutlinedTextField(message,{message=it.take(1000)},Modifier.fillMaxWidth(),label={Text("Cover note (optional)")},minLines=3);Text("A crisp A4 PDF is generated on this device and attached securely.",color=Slate500,style=MaterialTheme.typography.bodySmall);if(document.status=="draft")Text("Sending will mark this draft as sent.",color=Primary700,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Bold)}},confirmButton={Button({onConfirm(to,copies,message)},enabled=valid&&!working){if(working)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp)else Text("Send PDF")}},dismissButton={TextButton(onDismiss,enabled=!working){Text("Cancel")}})}
@Composable private fun <T> Choices(options:List<Pair<T,String>>,selected:T,onSelect:(T)->Unit){Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){options.forEach{(v,l)->FilterChip(selected==v,{onSelect(v)},{Text(l)})}}}
@Composable private fun Empty(text:String){Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(12.dp)){Text(text,Modifier.padding(20.dp),color=Slate500)}}
@Composable private fun Warn(text:String){Surface(Modifier.fillMaxWidth(),color=Error50,contentColor=Error700,shape=RoundedCornerShape(12.dp)){Text(text,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Bold)}}
private fun String.label()=replace('_',' ').replaceFirstChar(Char::uppercase);private fun Double.cleanQuantity()=if(this%1.0==0.0)toLong().toString() else toString();private fun nextStatuses(d:BusinessDocument)=when(d.type){"quotation"->when(d.status){"draft"->listOf("sent","accepted","declined");"sent"->listOf("accepted","declined");else->emptyList()};"invoice"->when(d.status){"draft"->listOf("sent","paid");"sent"->listOf("paid");else->emptyList()};else->emptyList()}
