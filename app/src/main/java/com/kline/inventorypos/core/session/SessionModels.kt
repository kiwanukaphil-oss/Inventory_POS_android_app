package com.kline.inventorypos.core.session

data class PosPermission(val name: String)

data class PosUser(
    val id: String,
    val username: String,
    val fullName: String,
    val roleName: String,
    val permissions: Set<String>,
) {
    val initials: String
        get() = fullName.split(' ').filter(String::isNotBlank).take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { username.take(2).uppercase() }

    fun hasPermission(permission: String): Boolean =
        "system.admin" in permissions || permission in permissions
}

data class PosBranch(
    val id: String,
    val code: String,
    val name: String,
    val status: String,
    val city: String?,
    val isDefault: Boolean,
    val isUserDefault: Boolean,
    val canSwitchTo: Boolean,
)

data class RegisterSession(
    val id: String,
    val openingFloat: Long,
    val openedAt: String,
    val runningTotal: Long?,
)

data class AuthenticatedContext(
    val user: PosUser,
    val branches: List<PosBranch>,
    val defaultBranchId: String?,
    val selectedBranchId: String? = null,
)

data class PosSession(
    val user: PosUser,
    val branch: PosBranch,
    val register: RegisterSession?,
    val isDemo: Boolean,
)
