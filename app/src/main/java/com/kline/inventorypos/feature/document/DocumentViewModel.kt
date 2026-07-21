package com.kline.inventorypos.feature.document

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.BusinessDocument
import com.kline.inventorypos.core.model.DerivedDocument
import com.kline.inventorypos.core.model.DocumentDraftLine
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.data.document.BusinessDocumentRepository
import com.kline.inventorypos.data.document.DocumentMutationUncertainException
import com.kline.inventorypos.data.document.GeneratedDocumentPdf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DocumentPdfAction { VIEW, SAVE }
data class PreparedDocumentPdf(val requestId: Long, val action: DocumentPdfAction, val file: GeneratedDocumentPdf)
data class DocumentUiState(val documents: List<BusinessDocument> = emptyList(), val detail: BusinessDocument? = null, val type: String? = null, val status: String? = null, val query: String = "", val canCreate: Boolean = false, val canEdit: Boolean = false, val canVoid: Boolean = false, val canSend: Boolean = false, val loading: Boolean = false, val working: Boolean = false, val uncertain: Boolean = false, val preparedPdf: PreparedDocumentPdf? = null, val error: String? = null, val message: String? = null)
class DocumentViewModel(private val repository: BusinessDocumentRepository) : ViewModel() {
    private val state = MutableStateFlow(DocumentUiState()); val uiState = state.asStateFlow(); private var key: String? = null
    private val mutationGate = Any()
    fun bindSession(session: PosSession) { val newKey = "${session.user.id}:${session.branch.id}"; if (newKey == key) return; key = newKey; state.value = DocumentUiState(canCreate = session.user.hasPermission("documents.create"), canEdit = session.user.hasPermission("documents.edit"), canVoid = session.user.hasPermission("documents.void"), canSend = session.user.hasPermission("documents.send")); refresh() }
    fun setType(value: String?) { state.update { it.copy(type = value) }; refresh() }; fun setStatus(value: String?) { state.update { it.copy(status = value) }; refresh() }; fun setQuery(value: String) { state.update { it.copy(query = value.take(80)) } }; fun search() = refresh()
    fun refresh() { val s = state.value; if (s.loading || s.working) return; viewModelScope.launch { state.update { it.copy(loading = true, error = null) }; runCatching { repository.list(s.type, s.status, s.query) }.onSuccess { rows -> state.update { it.copy(documents = rows, loading = false, uncertain = false) } }.onFailure(::failLoad) } }
    fun open(id: String) { viewModelScope.launch { state.update { it.copy(loading = true) }; runCatching { repository.detail(id) }.onSuccess { doc -> state.update { it.copy(detail = doc, loading = false) } }.onFailure(::failLoad) } }; fun close() = state.update { it.copy(detail = null) }
    fun save(id: String?, type: String, billTo: String, address: String, date: String, valid: String?, due: String?, method: String?, reference: String, notes: String, lines: List<DocumentDraftLine>) = mutate(if (id == null) "Document created" else "Draft updated") { repository.save(id, type, billTo, address, date, valid, due, method, reference, notes, lines) }
    fun transition(id: String, status: String) = mutate("Status updated") { repository.transition(id, status) }; fun void(id: String, reason: String) = mutate("Document voided") { repository.void(id, reason) }; fun convert(id: String, method: String?, reference: String) = mutate("Document converted", convertedSourceId = id) { repository.convert(id, method, reference) }
    fun preparePdf(document: BusinessDocument, action: DocumentPdfAction) {
        if (state.value.working) return
        viewModelScope.launch {
            state.update { it.copy(working = true, error = null) }
            runCatching { repository.pdf(document) }
                .onSuccess { file -> state.update { it.copy(working = false, preparedPdf = PreparedDocumentPdf(System.nanoTime(), action, file)) } }
                .onFailure { error -> state.update { it.copy(working = false, error = error.message ?: "The PDF could not be generated.") } }
        }
    }
    fun consumePdf() = state.update { it.copy(preparedPdf = null) }
    fun reportPdfResult(message: String? = null, error: String? = null) = state.update { it.copy(message = message, error = error) }
    fun email(document: BusinessDocument, recipient: String, cc: List<String>, message: String) = mutate("${document.number} emailed to ${recipient.trim()}") { repository.email(document, recipient, cc, message) }
    private fun mutate(
        message: String,
        convertedSourceId: String? = null,
        block: suspend () -> BusinessDocument,
    ) {
        val acquired = synchronized(mutationGate) {
            val current = state.value
            if (current.working || current.uncertain) false
            else {
                state.value = current.copy(working = true, error = null)
                true
            }
        }
        if (!acquired) return
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { doc ->
                    state.update { current ->
                        val updatedRows = current.documents
                            .filterNot { it.id == doc.id }
                            .map { existing ->
                                if (existing.id != convertedSourceId) existing
                                else existing.copy(
                                    status = "converted",
                                    derived = existing.derived + DerivedDocument(doc.id, doc.type, doc.number, doc.status),
                                )
                            }
                        current.copy(
                            working = false,
                            detail = doc,
                            documents = listOf(doc) + updatedRows,
                            message = message,
                        )
                    }
                }
                .onFailure { e -> state.update { it.copy(working = false, uncertain = e is DocumentMutationUncertainException, error = e.message) } }
        }
    }
    private fun failLoad(error: Throwable) = state.update { it.copy(loading = false, error = error.message ?: "Documents are unavailable") }; fun consumeMessage() = state.update { it.copy(message = null) }; fun clearError() = state.update { it.copy(error = null) }
    class Factory(private val c: AppContainer) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = DocumentViewModel(c.businessDocumentRepository) as T }
}
