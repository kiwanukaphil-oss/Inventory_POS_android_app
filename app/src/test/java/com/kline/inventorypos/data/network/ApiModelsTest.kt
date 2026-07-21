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

    @Test
    fun returnRequestUsesBackendSnakeCaseContract() {
        val request = CreateReturnRequest(
            "sale-1", "refund", "defective", "Seam split", "original_payment",
            listOf(ReturnItemRequest("item-1", "variant-1", 1, "defective")),
        )

        val json = Gson().toJson(request)

        assertTrue(json.contains("\"original_sale_id\":\"sale-1\""))
        assertTrue(json.contains("\"quantity_returned\":1"))
        assertTrue(json.contains("\"refund_method\":\"original_payment\""))
    }

    @Test
    fun exchangeRequestPreservesExplicitSettlementCode() {
        val request = CreateExchangeRequest(
            "sale-1", "return_and_resale", "wrong_size", null,
            listOf(ReturnItemRequest("item-1", "variant-1", 1, "sellable")),
            listOf(ExchangeNewItemRequest("variant-2", 1)),
            "mtn_mobile_money",
        )

        val json = Gson().toJson(request)

        assertTrue(json.contains("\"exchange_mode\":\"return_and_resale\""))
        assertTrue(json.contains("\"settlement_method\":\"mtn_mobile_money\""))
        assertTrue(json.contains("\"new_items\""))
    }

    @Test
    fun customerAccountResponseParsesBalancesAndRelationshipMetadata() {
        val json = """{
            "success":true,
            "data":[{
                "id":"c1","first_name":"Amina","last_name":"Nakato",
                "phone":"+256700000001","email":"amina@example.com",
                "customer_type":"individual","total_purchases":14,"total_spent":3420000,
                "credit_balance":180000,"credit_limit":500000,"prepaid_balance":240000,
                "loyalty_points":1240,"last_purchase_date":"2026-07-16T10:00:00Z",
                "tier":{"id":"gold","name":"Gold"},
                "segment":{"segment":"vip","label":"VIP"},"tags":["Tailoring"]
            }],
            "pagination":{"total":1,"page":1,"limit":50,"totalPages":1}
        }"""

        val customer = Gson().fromJson(json, CustomerListResponse::class.java).data.single()

        assertEquals(14, customer.totalPurchases)
        assertEquals(180000.0, customer.creditBalance)
        assertEquals("Gold", customer.tier?.name)
        assertEquals("VIP", customer.segment?.label)
    }

    @Test
    fun customerPurchaseHistoryParsesServerNestedDataShape() {
        val json = """{
            "success":true,
            "data":{"data":[{
                "id":"s1","receipt_number":"KLM-42","total_amount":620000,
                "total_returned":95000,"net_amount":525000,"payment_method":"visa",
                "sale_date":"2026-07-16T10:00:00Z","status":"completed",
                "item_count":4,"product_names":["Linen Shirt"]
            }]}
        }"""

        val response = Gson().fromJson(json, CustomerPurchasesEnvelopeForTest::class.java)

        assertEquals("KLM-42", response.data.data.single().receiptNumber)
        assertEquals(525000.0, response.data.data.single().netAmount)
    }

    @Test
    fun giftVoucherIssueAndActivationUseExactBackendFields() {
        val issue = CreateGiftVoucherRequest(
            templateId = "template-1",
            recipientName = "Amina Nakato",
            fromName = "David",
            message = "Enjoy",
            phoneNumber = "+256700000001",
            originalAmount = 500_000,
            expiryDate = "2027-01-01",
            notes = null,
        )
        val activation = ActivateGiftVoucherRequest(
            listOf(GiftVoucherPaymentRequest("mtn_mobile_money", 500_000, "MM-42")),
        )

        val issueJson = Gson().toJson(issue)
        val activationJson = Gson().toJson(activation)

        assertTrue(issueJson.contains("\"template_id\":\"template-1\""))
        assertTrue(issueJson.contains("\"original_amount\":500000"))
        assertTrue(activationJson.contains("\"method\":\"mtn_mobile_money\""))
        assertTrue(activationJson.contains("\"amount\":500000"))
    }

    @Test
    fun voucherCancellationRefundFlagIsExplicit() {
        val json = Gson().toJson(CancelGiftVoucherRequest("Duplicate issue", true))

        assertTrue(json.contains("\"reason\":\"Duplicate issue\""))
        assertTrue(json.contains("\"refund\":true"))
    }

    @Test
    fun drawerCloseAndHandoverPreserveBlindCountContract() {
        val closeJson = Gson().toJson(CloseCashDrawerRequest(645_000, varianceNote = "Counted twice"))
        val handoverJson = Gson().toJson(HandoverCashDrawerRequest("drawer-1", 645_000, "user-2", "Shift change"))

        assertTrue(closeJson.contains("\"actual_closing\":645000"))
        assertTrue(closeJson.contains("\"variance_note\":\"Counted twice\""))
        assertTrue(handoverJson.contains("\"outgoing_session_id\":\"drawer-1\""))
        assertTrue(handoverJson.contains("\"counted_amount\":645000"))
        assertTrue(handoverJson.contains("\"incoming_user_id\":\"user-2\""))
    }

    @Test
    fun manualCashMovementUsesAuditedDirectionAndTypeFields() {
        val json = Gson().toJson(RecordCashMovementRequest("petty_cash", "outflow", 15_000, "Courier fare", "Transport"))

        assertTrue(json.contains("\"transaction_type\":\"petty_cash\""))
        assertTrue(json.contains("\"movement_type\":\"outflow\""))
        assertTrue(json.contains("\"amount\":15000"))
    }
}

private data class CustomerPurchasesEnvelopeForTest(val success: Boolean, val data: CustomerPurchaseHistoryDto)
