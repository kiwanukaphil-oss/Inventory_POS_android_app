package com.kline.inventorypos.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kline.inventorypos.core.session.AuthenticatedContext
import com.kline.inventorypos.core.session.PosBranch
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.data.session.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SessionStage {
    data object Restoring : SessionStage
    data object SignedOut : SessionStage
    data class ChooseBranch(val context: AuthenticatedContext) : SessionStage
    data class OpenRegister(val context: AuthenticatedContext, val branch: PosBranch) : SessionStage
    data class Ready(val session: PosSession) : SessionStage
}

data class AppUiState(
    val stage: SessionStage = SessionStage.Restoring,
    val working: Boolean = false,
    val error: String? = null,
)

class AppViewModel(private val repository: SessionRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var context: AuthenticatedContext? = null

    init {
        viewModelScope.launch {
            runCatching { repository.restore() }
                .onSuccess { restored ->
                    if (restored == null) {
                        _uiState.value = AppUiState(SessionStage.SignedOut)
                    } else {
                        context = restored
                        val selected = restored.branches.find { it.id == restored.selectedBranchId }
                        if (selected != null) prepareBranch(restored, selected)
                        else showBranchChoice(restored)
                    }
                }
                .onFailure {
                    repository.logout()
                    _uiState.value = AppUiState(SessionStage.SignedOut, error = it.userMessage())
                }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Enter both username and password.") }
            return
        }
        launchWork({ authenticated -> showBranchChoice(authenticated) }) {
            repository.login(username, password)
        }
    }

    fun startDemo() = launchWork({ authenticated -> showBranchChoice(authenticated) }) {
        repository.startDemo()
    }

    fun chooseBranch(branch: PosBranch) {
        val authenticated = context ?: return
        viewModelScope.launch { prepareBranch(authenticated, branch) }
    }

    fun openRegister(openingFloat: Long) {
        val stage = _uiState.value.stage as? SessionStage.OpenRegister ?: return
        launchWork({ register ->
            _uiState.value = AppUiState(
                SessionStage.Ready(
                    PosSession(stage.context.user, stage.branch, register, repository.isDemo()),
                ),
            )
        }) { repository.openRegister(openingFloat) }
    }

    fun continueWithoutRegister() {
        val stage = _uiState.value.stage as? SessionStage.OpenRegister ?: return
        _uiState.value = AppUiState(
            SessionStage.Ready(PosSession(stage.context.user, stage.branch, null, repository.isDemo())),
        )
    }

    fun changeBranch() {
        context?.let { _uiState.value = AppUiState(SessionStage.ChooseBranch(it)) }
    }

    fun requestRegister() {
        val ready = _uiState.value.stage as? SessionStage.Ready ?: return
        val authenticated = context ?: return
        _uiState.value = AppUiState(SessionStage.OpenRegister(authenticated, ready.session.branch))
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            context = null
            _uiState.value = AppUiState(SessionStage.SignedOut)
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun showBranchChoice(authenticated: AuthenticatedContext) {
        context = authenticated
        when {
            authenticated.branches.isEmpty() -> {
                _uiState.value = AppUiState(
                    SessionStage.SignedOut,
                    error = "This account has no active branch assignment. Contact an administrator.",
                )
            }
            authenticated.branches.size == 1 -> chooseBranch(authenticated.branches.first())
            else -> _uiState.value = AppUiState(SessionStage.ChooseBranch(authenticated))
        }
    }

    private suspend fun prepareBranch(authenticated: AuthenticatedContext, branch: PosBranch) {
        _uiState.update { it.copy(working = true, error = null) }
        var selectedContext = authenticated
        runCatching {
            repository.selectBranch(branch.id)
            selectedContext = authenticated.copy(selectedBranchId = branch.id)
            context = selectedContext
            if (authenticated.user.hasPermission("sales.create")) repository.activeRegister() else null
        }.onSuccess { register ->
            _uiState.value = when {
                register != null || !authenticated.user.hasPermission("sales.create") -> AppUiState(
                    SessionStage.Ready(
                        PosSession(selectedContext.user, branch, register, repository.isDemo()),
                    ),
                )
                else -> AppUiState(SessionStage.OpenRegister(selectedContext, branch))
            }
        }.onFailure { error ->
            _uiState.value = AppUiState(SessionStage.ChooseBranch(authenticated), error = error.userMessage())
        }
    }

    private fun <T> launchWork(onSuccess: (T) -> Unit, block: suspend () -> T) {
        viewModelScope.launch {
            _uiState.update { it.copy(working = true, error = null) }
            runCatching { block() }
                .onSuccess(onSuccess)
                .onFailure { error -> _uiState.update { it.copy(working = false, error = error.userMessage()) } }
        }
    }

    class Factory(container: AppContainer) : ViewModelProvider.Factory {
        private val repository = container.sessionRepository

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppViewModel(repository) as T
    }
}

private fun Throwable.userMessage(): String = message?.takeIf(String::isNotBlank)
    ?: "Something went wrong. Please try again."
