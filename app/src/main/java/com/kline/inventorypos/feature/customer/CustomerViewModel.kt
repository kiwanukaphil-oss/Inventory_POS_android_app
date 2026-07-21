package com.kline.inventorypos.feature.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.app.AppContainer
import com.kline.inventorypos.core.model.CustomerAccount
import com.kline.inventorypos.core.model.CustomerWorkspace
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.data.customer.CustomerNoteUncertainException
import com.kline.inventorypos.data.customer.CustomerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CustomerUiState(
    val customers: List<CustomerAccount> = emptyList(),
    val query: String = "",
    val view: String = "all",
    val selected: CustomerWorkspace? = null,
    val loading: Boolean = false,
    val detailLoading: Boolean = false,
    val working: Boolean = false,
    val canEdit: Boolean = false,
    val noteUncertain: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class CustomerViewModel(private val repository: CustomerRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CustomerUiState())
    val uiState: StateFlow<CustomerUiState> = _uiState.asStateFlow()
    private var sessionKey: String? = null
    private var searchJob: Job? = null

    fun bindSession(session: PosSession) {
        val key = "${session.user.id}:${session.branch.id}"
        if (sessionKey == key) return
        sessionKey = key
        _uiState.value = CustomerUiState(canEdit = session.user.hasPermission("customers.edit"))
        refresh()
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(350); refresh() }
    }

    fun setView(value: String) {
        _uiState.update { it.copy(view = value) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.customers(_uiState.value.query, _uiState.value.view) }
                .onSuccess { customers -> _uiState.update { it.copy(customers = customers, loading = false) } }
                .onFailure { error -> _uiState.update { it.copy(loading = false, error = error.userMessage()) } }
        }
    }

    fun open(customer: CustomerAccount) {
        if (_uiState.value.detailLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(detailLoading = true, selected = null, noteUncertain = false, error = null) }
            runCatching { repository.workspace(customer.id) }
                .onSuccess { workspace -> _uiState.update { it.copy(selected = workspace, detailLoading = false) } }
                .onFailure { error -> _uiState.update { it.copy(detailLoading = false, error = error.userMessage()) } }
        }
    }

    fun close() = _uiState.update { it.copy(selected = null, noteUncertain = false) }

    fun addNote(body: String, pinned: Boolean) {
        val workspace = _uiState.value.selected ?: return
        if (_uiState.value.working || _uiState.value.noteUncertain) return
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { repository.addNote(workspace.customer.id, body, pinned) }
                .onSuccess { note -> _uiState.update { state ->
                    state.copy(working = false, selected = state.selected?.copy(notes = listOf(note) + state.selected.notes), message = "Note saved")
                } }
                .onFailure { error -> _uiState.update { it.copy(working = false, noteUncertain = error is CustomerNoteUncertainException, error = error.userMessage()) } }
        }
    }

    fun refreshDetail() = _uiState.value.selected?.customer?.let(::open)
    fun clearError() = _uiState.update { it.copy(error = null) }
    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CustomerViewModel(container.customerRepository) as T
    }
}

private fun Throwable.userMessage() = message?.takeIf(String::isNotBlank) ?: "Something went wrong. Please try again."
