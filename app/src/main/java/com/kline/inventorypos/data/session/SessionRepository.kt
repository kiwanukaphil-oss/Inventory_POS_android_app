package com.kline.inventorypos.data.session

import com.google.gson.Gson
import com.kline.inventorypos.core.session.AuthenticatedContext
import com.kline.inventorypos.core.session.PosBranch
import com.kline.inventorypos.core.session.PosUser
import com.kline.inventorypos.core.session.RegisterSession
import com.kline.inventorypos.data.network.ApiError
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.LoginRequest
import com.kline.inventorypos.data.network.OpenDrawerRequest
import com.kline.inventorypos.data.network.toDomain
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.util.UUID

interface SessionRepository {
    suspend fun restore(): AuthenticatedContext?
    suspend fun login(username: String, password: String): AuthenticatedContext
    suspend fun startDemo(): AuthenticatedContext
    suspend fun selectBranch(branchId: String)
    suspend fun activeRegister(): RegisterSession?
    suspend fun openRegister(openingFloat: Long): RegisterSession
    suspend fun markRegisterClosed()
    suspend fun logout()
    fun isDemo(): Boolean
}

class DefaultSessionRepository(
    private val api: InventoryPosApi,
    private val store: SessionStore,
    private val headers: SessionHeaders,
    private val gson: Gson,
) : SessionRepository {
    private var demoMode = false
    private val demoRegisters = mutableMapOf<String, RegisterSession>()

    override suspend fun restore(): AuthenticatedContext? {
        val token = store.readToken() ?: return null
        headers.token = token
        headers.branchId = store.readBranchId()
        demoMode = token == DEMO_TOKEN
        return if (demoMode) {
            demoContext(headers.branchId)
        } else {
            apiCall {
                val userDto = api.currentUser().user
                val branchData = api.myBranches().data
                AuthenticatedContext(
                    user = userDto.toDomain(),
                    branches = branchData.branches.map { it.toDomain() }.filter { it.status == "active" },
                    defaultBranchId = branchData.defaultBranchId ?: userDto.defaultBranchId,
                    selectedBranchId = headers.branchId,
                )
            }
        }
    }

    override suspend fun login(username: String, password: String): AuthenticatedContext = apiCall {
        val response = api.login(LoginRequest(username.trim(), password))
        store.writeToken(response.token)
        headers.token = response.token
        headers.branchId = null
        demoMode = false
        val embedded = response.user.branches.orEmpty()
        val branchData = if (embedded.isNotEmpty()) {
            embedded to response.user.defaultBranchId
        } else {
            api.myBranches().data.let { it.branches to it.defaultBranchId }
        }
        AuthenticatedContext(
            user = response.user.toDomain(),
            branches = branchData.first.map { it.toDomain() }.filter { it.status == "active" },
            defaultBranchId = branchData.second,
        )
    }

    override suspend fun startDemo(): AuthenticatedContext {
        logout()
        store.writeToken(DEMO_TOKEN)
        headers.token = DEMO_TOKEN
        demoMode = true
        return demoContext(null)
    }

    override suspend fun selectBranch(branchId: String) {
        store.writeBranchId(branchId)
        headers.branchId = branchId
    }

    override suspend fun activeRegister(): RegisterSession? =
        if (demoMode) headers.branchId?.let(demoRegisters::get) else apiCall { api.activeDrawer().data?.toDomain() }

    override suspend fun openRegister(openingFloat: Long): RegisterSession {
        require(openingFloat >= 0) { "Opening float cannot be negative." }
        if (demoMode) {
            return RegisterSession(
                id = "demo-${UUID.randomUUID()}",
                openingFloat = openingFloat,
                openedAt = Instant.now().toString(),
                runningTotal = openingFloat,
            ).also { register ->
                val branchId = checkNotNull(headers.branchId) { "Choose a branch before opening a register." }
                demoRegisters[branchId] = register
            }
        }
        return apiCall { api.openDrawer(OpenDrawerRequest(openingFloat)).data.toDomain() }
    }

    override suspend fun markRegisterClosed() {
        if (demoMode) headers.branchId?.let(demoRegisters::remove)
    }

    override suspend fun logout() {
        store.clear()
        headers.token = null
        headers.branchId = null
        demoMode = false
        demoRegisters.clear()
    }

    override fun isDemo(): Boolean = demoMode

    private fun demoContext(selectedBranchId: String?) = AuthenticatedContext(
        user = DemoUser,
        branches = DemoBranches,
        defaultBranchId = DemoBranches.first().id,
        selectedBranchId = selectedBranchId,
    )

    private suspend fun <T> apiCall(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        val message = runCatching {
            gson.fromJson(error.response()?.errorBody()?.string(), ApiError::class.java)?.message
        }.getOrNull()
        throw SessionException(message ?: "The server could not complete that request.", error)
    } catch (error: IOException) {
        throw SessionException("Cannot reach the server. Check the connection and try again.", error)
    }

    private companion object {
        const val DEMO_TOKEN = "inventory-pos-local-demo"

        val DemoUser = PosUser(
            id = "demo-admin",
            username = "philip",
            fullName = "Philip Kiwanuka",
            roleName = "Store administrator",
            permissions = setOf("system.admin"),
        )

        val DemoBranches = listOf(
            PosBranch("main", "MAIN", "Main Branch", "active", "Kampala", true, true, true),
            PosBranch("acacia", "ACA", "Acacia Mall", "active", "Kampala", false, false, true),
            PosBranch("village", "VLG", "Village Mall", "active", "Bugolobi", false, false, true),
        )
    }
}

class SessionException(message: String, cause: Throwable? = null) : Exception(message, cause)
