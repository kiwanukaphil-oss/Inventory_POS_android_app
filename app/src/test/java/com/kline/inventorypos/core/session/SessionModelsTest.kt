package com.kline.inventorypos.core.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionModelsTest {
    @Test
    fun exactPermissionAllowsAction() {
        val user = userWith(setOf("sales.create"))

        assertTrue(user.hasPermission("sales.create"))
        assertFalse(user.hasPermission("inventory.adjust"))
    }

    @Test
    fun systemAdminPermissionAllowsEveryAction() {
        val user = userWith(setOf("system.admin"))

        assertTrue(user.hasPermission("sales.create"))
        assertTrue(user.hasPermission("inventory.adjust"))
    }

    private fun userWith(permissions: Set<String>) = PosUser(
        id = "user-1",
        username = "cashier",
        fullName = "Test Cashier",
        roleName = "Cashier",
        permissions = permissions,
    )
}
