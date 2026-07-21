package com.kline.inventorypos.data.network

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiModelsTest {
    @Test
    fun currentBackendPermissionShapeMapsToDomain() {
        val dto = UserDto(
            id = "u1",
            username = "pat",
            fullName = "Pat Cashier",
            roleName = "cashier",
            role = null,
            permissions = listOf(PermissionDto(permissionName = "sales.create", name = null)),
            branches = null,
            defaultBranchId = null,
        )

        val user = dto.toDomain()

        assertEquals("Pat Cashier", user.fullName)
        assertTrue(user.hasPermission("sales.create"))
    }

    @Test
    fun userBranchFlagsRemainIntact() {
        val branch = BranchDto(
            id = "b1",
            code = "MAIN",
            name = "Main Branch",
            status = "active",
            city = "Kampala",
            isDefault = true,
            isUserDefault = true,
            canSwitchTo = false,
        ).toDomain()

        assertTrue(branch.isDefault)
        assertTrue(branch.isUserDefault)
        assertEquals(false, branch.canSwitchTo)
    }

    @Test
    fun salesHistoryParsesPostgresCountAndStaffAttribution() {
        val json = """{
            "success":true,
            "data":[{
                "id":"sale-1","receipt_number":"KLM-42",
                "customer_first_name":"Amina","customer_last_name":"Nakato",
                "total_amount":125000,"payment_method":"mobile_money","status":"completed",
                "sale_date":"2026-07-21T09:30:00Z","processed_by_name":"Pat Cashier",
                "staff_name":"Grace Akello","item_count":"3","return_status":"none","total_returned":0
            }],
            "pagination":{"total":1,"page":1,"limit":50,"totalPages":1}
        }"""

        val response = Gson().fromJson(json, SalesListResponse::class.java)

        assertEquals(3, response.data.single().itemCount)
        assertEquals("Grace Akello", response.data.single().staffName)
        assertEquals("KLM-42", response.data.single().receiptNumber)
    }
}
