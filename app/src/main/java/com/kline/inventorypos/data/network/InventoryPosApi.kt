package com.kline.inventorypos.data.network

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Header

interface InventoryPosApi {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("auth/me")
    suspend fun currentUser(): CurrentUserResponse

    @GET("branches/my")
    suspend fun myBranches(): ApiEnvelope<MyBranchesDto>

    @GET("cash-drawer/active")
    suspend fun activeDrawer(): ApiEnvelope<CashDrawerDto?>

    @POST("cash-drawer/open")
    suspend fun openDrawer(@Body request: OpenDrawerRequest): ApiEnvelope<CashDrawerDto>

    @POST("cash-drawer/{id}/close")
    suspend fun closeDrawer(@Path("id") id: String, @Body request: CloseCashDrawerRequest): ApiEnvelope<CashDrawerDto>

    @GET("cash-drawer/{id}/summary")
    suspend fun cashDrawerSummary(@Path("id") id: String): ApiEnvelope<CashSessionSummaryDto>

    @POST("cash-drawer/handover")
    suspend fun handoverDrawer(@Body request: HandoverCashDrawerRequest): ApiEnvelope<HandoverCashDrawerDto>

    @GET("cash-book/movements")
    suspend fun cashMovements(
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100,
    ): ApiEnvelope<CashMovementsDataDto>

    @GET("cash-book/summary")
    suspend fun cashBookSummary(@Query("start_date") startDate: String, @Query("end_date") endDate: String): ApiEnvelope<CashBookSummaryDto>

    @POST("cash-book/movements")
    suspend fun recordCashMovement(@Body request: RecordCashMovementRequest): CashMovementMutationResponse

    @GET("users")
    suspend fun cashStaff(@Query("include_inactive") includeInactive: Boolean = false): ApiEnvelope<List<CashStaffDto>>

    @GET("reconciliation/{date}")
    suspend fun reconciliation(@Path("date") date: String): ApiEnvelope<ReconciliationDto>

    @POST("reconciliation/open")
    suspend fun openReconciliation(@Query("date") date: String, @Body body: Map<String, String> = emptyMap()): ApiEnvelope<ReconciliationDto>

    @PUT("reconciliation/{date}/channel/{method}")
    suspend fun updateReconciliationChannel(
        @Path("date") date: String,
        @Path("method") method: String,
        @Body request: UpdateReconciliationChannelRequest,
    ): ApiEnvelope<ReconciliationDto>

    @POST("reconciliation/{date}/signoff")
    suspend fun signOffReconciliation(@Path("date") date: String, @Body request: ReconciliationSignoffRequest): ApiEnvelope<ReconciliationDto>

    @POST("reconciliation/{date}/close")
    suspend fun closeReconciliation(@Path("date") date: String, @Body body: Map<String, String> = emptyMap()): ApiEnvelope<ReconciliationDto>

    @GET("reports/sales/daily")
    suspend fun dailySalesSummary(@Query("date") date: String): ApiEnvelope<DailySalesSummaryDto>

    @GET("reports/sales/payment-methods")
    suspend fun paymentMethodReport(@Query("start_date") startDate: String, @Query("end_date") endDate: String): ApiEnvelope<PaymentMethodReportDto>

    @GET("expense-categories")
    suspend fun expenseCategories(): ApiEnvelope<List<ExpenseCategoryDto>>

    @GET("expenses")
    suspend fun expenses(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("category_id") categoryId: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): ApiEnvelope<List<ExpenseDto>>

    @POST("expenses")
    suspend fun createExpense(@Body request: SaveExpenseRequest): ApiEnvelope<ExpenseDto>

    @PUT("expenses/{id}")
    suspend fun updateExpense(@Path("id") id: String, @Body request: SaveExpenseRequest): ApiEnvelope<ExpenseDto>

    @DELETE("expenses/{id}")
    suspend fun deleteExpense(@Path("id") id: String): ApiEnvelope<Any>

    @GET("approvals/pending")
    suspend fun pendingApprovals(): ApiEnvelope<List<ApprovalRequestDto>>

    @POST("approvals/{id}/approve")
    suspend fun approveRequest(@Path("id") id: String, @Body request: ApprovalDecisionRequest): ApiEnvelope<ApprovalRequestDto>

    @POST("approvals/{id}/reject")
    suspend fun rejectRequest(@Path("id") id: String, @Body request: ApprovalDecisionRequest): ApiEnvelope<ApprovalRequestDto>

    @GET("documents")
    suspend fun businessDocuments(
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): BusinessDocumentListResponse

    @GET("documents/{id}")
    suspend fun businessDocument(@Path("id") id: String): ApiEnvelope<BusinessDocumentDto>

    @POST("documents")
    suspend fun createBusinessDocument(@Body request: SaveBusinessDocumentRequest): ApiEnvelope<BusinessDocumentDto>

    @PUT("documents/{id}")
    suspend fun updateBusinessDocument(@Path("id") id: String, @Body request: SaveBusinessDocumentRequest): ApiEnvelope<BusinessDocumentDto>

    @POST("documents/{id}/status")
    suspend fun transitionBusinessDocument(@Path("id") id: String, @Body request: DocumentStatusRequest): ApiEnvelope<BusinessDocumentDto>

    @POST("documents/{id}/void")
    suspend fun voidBusinessDocument(@Path("id") id: String, @Body request: VoidDocumentRequest): ApiEnvelope<BusinessDocumentDto>

    @POST("documents/{id}/convert")
    suspend fun convertBusinessDocument(@Path("id") id: String, @Body request: ConvertDocumentRequest): ApiEnvelope<BusinessDocumentDto>

    @GET("settings/store")
    suspend fun storeConfig(): ApiEnvelope<StoreConfigDto>

    @GET("branches")
    suspend fun branches(@Query("include_inactive") includeInactive: Boolean = true): ApiEnvelope<List<BranchDto>>

    @GET("staff/active")
    suspend fun activeStaff(): ApiEnvelope<List<StaffDto>>

    @GET("reports/sales/period")
    suspend fun periodSales(@Query("start_date") startDate: String, @Query("end_date") endDate: String, @Query("group_by") groupBy: String = "day"): ApiEnvelope<PeriodSalesDto>

    @GET("reports/financial/income-statement")
    suspend fun incomeStatement(@Query("from") from: String, @Query("to") to: String): ApiEnvelope<IncomeStatementDto>

    @GET("reports/financial/cash-flow")
    suspend fun cashFlow(@Query("from") from: String, @Query("to") to: String): ApiEnvelope<CashFlowDto>

    @GET("products/variants")
    suspend fun catalogVariants(): ApiEnvelope<List<CatalogVariantDto>>

    @GET("products/variants/by-barcode/{barcode}")
    suspend fun variantByBarcode(@Path("barcode") barcode: String): ApiEnvelope<CatalogVariantDto>

    @GET("categories")
    suspend fun categories(): ApiEnvelope<List<CategoryDto>>

    @GET("customers/search")
    suspend fun searchCustomers(@Query("q") query: String): ApiEnvelope<List<CustomerDto>>

    @GET("customers")
    suspend fun customers(
        @Query("search") search: String? = null,
        @Query("view") view: String = "all",
        @Query("sort") sort: String = "recent_activity",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): CustomerListResponse

    @GET("customers/{id}")
    suspend fun customer(@Path("id") id: String): ApiEnvelope<CustomerDto>

    @GET("customers/{id}/purchases")
    suspend fun customerPurchases(@Path("id") id: String, @Query("limit") limit: Int = 100): ApiEnvelope<CustomerPurchaseHistoryDto>

    @GET("customers/{id}/aging")
    suspend fun customerAging(@Path("id") id: String): ApiEnvelope<CustomerAgingDto>

    @GET("customers/{id}/activity-ledger")
    suspend fun customerLedger(@Path("id") id: String, @Query("filter") filter: String = "all", @Query("limit") limit: Int = 100): ApiEnvelope<List<CustomerLedgerEntryDto>>

    @GET("customers/{id}/notes")
    suspend fun customerNotes(@Path("id") id: String): ApiEnvelope<List<CustomerNoteDto>>

    @POST("customers/{id}/notes")
    suspend fun createCustomerNote(@Path("id") id: String, @Body request: CreateCustomerNoteRequest): ApiEnvelope<CustomerNoteDto>

    @GET("customers/{id}/contacts")
    suspend fun customerContacts(@Path("id") id: String): ApiEnvelope<List<CustomerContactDto>>

    @GET("customers/{id}/loyalty-history")
    suspend fun customerLoyalty(@Path("id") id: String, @Query("limit") limit: Int = 50): ApiEnvelope<List<LoyaltyEntryDto>>

    @GET("customers/{id}/store-credit-summary")
    suspend fun customerStoreCredit(@Path("id") id: String): ApiEnvelope<StoreCreditSummaryDto>

    @GET("gift-vouchers/templates")
    suspend fun giftVoucherTemplates(@Query("active") active: Boolean = true): ApiEnvelope<List<GiftVoucherTemplateDto>>

    @GET("gift-vouchers")
    suspend fun giftVouchers(
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): GiftVoucherListResponse

    @GET("gift-vouchers/{id}")
    suspend fun giftVoucher(@Path("id") id: String): ApiEnvelope<GiftVoucherDto>

    @POST("gift-vouchers")
    suspend fun createGiftVoucher(@Body request: CreateGiftVoucherRequest): ApiEnvelope<GiftVoucherDto>

    @POST("gift-vouchers/{id}/activate")
    suspend fun activateGiftVoucher(@Path("id") id: String, @Body request: ActivateGiftVoucherRequest): ApiEnvelope<GiftVoucherDto>

    @POST("gift-vouchers/validate")
    suspend fun validateGiftVoucher(@Body request: ValidateGiftVoucherRequest): ApiEnvelope<GiftVoucherDto>

    @POST("gift-vouchers/{id}/redeem")
    suspend fun redeemGiftVoucher(@Path("id") id: String, @Body request: RedeemGiftVoucherRequest): ApiEnvelope<GiftVoucherDto>

    @POST("gift-vouchers/{id}/cancel")
    suspend fun cancelGiftVoucher(@Path("id") id: String, @Body request: CancelGiftVoucherRequest): ApiEnvelope<GiftVoucherDto>

    @POST("promotions/evaluate")
    suspend fun evaluatePromotions(@Body request: PromotionEvaluationRequest): ApiEnvelope<PromotionEvaluationDto>

    @GET("discounts")
    suspend fun discounts(@Query("active") activeOnly: Boolean = true): ApiEnvelope<List<DiscountDto>>

    @GET("held-transactions")
    suspend fun heldCarts(): ApiEnvelope<List<HeldCartDto>>

    @POST("held-transactions")
    suspend fun holdCart(@Body request: HoldCartRequest): ApiEnvelope<HeldCartDto>

    @POST("held-transactions/{id}/recall")
    suspend fun recallHeldCart(@Path("id") id: String): ApiEnvelope<HeldCartDto>

    @DELETE("held-transactions/{id}")
    suspend fun deleteHeldCart(@Path("id") id: String): ApiEnvelope<Any>

    @POST("sales/validate-cart")
    suspend fun validateCart(@Body request: ValidateCartRequest): CartValidationResponse

    @POST("tax/preview")
    suspend fun previewTax(@Body request: TaxPreviewRequest): ApiEnvelope<TaxPreviewDto>

    @POST("sales")
    suspend fun createSale(
        @Header("X-Idempotency-Key") attemptId: String,
        @Body request: CreateSaleRequest,
    ): SaleMutationResponseDto

    @GET("sales")
    suspend fun sales(
        @Query("search") search: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): SalesListResponse

    @GET("receipts/{id}")
    suspend fun receipt(@Path("id") id: String): ApiEnvelope<ReceiptDto>

    @POST("receipts/{saleId}/email")
    suspend fun emailReceipt(
        @Path("saleId") saleId: String,
        @Body request: EmailReceiptRequest,
    ): ApiEnvelope<JsonObject>

    @GET("sales/{saleId}/returnable-items")
    suspend fun returnableItems(@Path("saleId") saleId: String): ApiEnvelope<List<ReturnableItemDto>>

    @POST("returns")
    suspend fun createReturn(@Body request: CreateReturnRequest): ReturnMutationResponse

    @POST("exchanges/preview")
    suspend fun previewExchange(@Body request: ExchangePreviewRequest): ApiEnvelope<ExchangePreviewDto>

    @POST("exchanges")
    suspend fun createExchange(@Body request: CreateExchangeRequest): ExchangeMutationResponse

    @GET("inventory/summary")
    suspend fun inventorySummary(): ApiEnvelope<InventorySummaryDto>

    @GET("inventory/movements")
    suspend fun stockMovements(
        @Query("movement_type") movementType: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): StockMovementsResponse

    @POST("inventory/adjust")
    suspend fun adjustStock(@Body request: StockAdjustmentRequest): InventoryMutationResponseDto

    @POST("inventory/bulk-adjust")
    suspend fun receiveStock(@Body request: BulkStockAdjustmentRequest): InventoryMutationResponseDto

    @GET("suppliers")
    suspend fun suppliers(): ApiEnvelope<List<SupplierDto>>

    @GET("pricing/variants")
    suspend fun pricingVariants(): ApiEnvelope<List<PricingVariantDto>>

    @GET("pricing/history")
    suspend fun priceHistory(@Query("limit") limit: Int = 100): ApiEnvelope<List<PriceHistoryEntryDto>>

    @POST("pricing/bulk-update")
    suspend fun bulkUpdatePrices(@Body request: BulkPriceUpdateRequest): InventoryMutationResponseDto

    @POST("products/labels/print-runs")
    suspend fun recordLabelPrintRun(@Body request: LabelPrintRunRequest): ApiEnvelope<LabelPrintRunDto>

    @GET("inventory/transfers")
    suspend fun stockTransfers(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): StockTransfersResponse

    @POST("inventory/transfers")
    suspend fun createStockTransfer(@Body request: CreateStockTransferRequest): ApiEnvelope<StockTransferDto>

    @POST("inventory/transfers/{id}/dispatch")
    suspend fun dispatchStockTransfer(@Path("id") id: String): ApiEnvelope<StockTransferDto>

    @POST("inventory/transfers/{id}/receive")
    suspend fun receiveStockTransfer(@Path("id") id: String): ApiEnvelope<StockTransferDto>

    @POST("inventory/transfers/{id}/cancel")
    suspend fun cancelStockTransfer(@Path("id") id: String): ApiEnvelope<StockTransferDto>

    @GET("inventory/receive/draft")
    suspend fun receiveStockDraft(): ApiEnvelope<ReceiveStockDraftDto?>

    @POST("inventory/receive/draft")
    suspend fun saveReceiveStockDraft(@Body request: SaveReceiveStockDraftRequest): ApiEnvelope<ReceiveStockDraftDto>

    @DELETE("inventory/receive/draft/{id}")
    suspend fun deleteReceiveStockDraft(@Path("id") id: String): ApiEnvelope<Any>

    @GET("inventory/grn")
    suspend fun grnHistory(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): GrnListResponse

    @GET("inventory/grn/{reference}")
    suspend fun grnDetail(@Path("reference") reference: String): ApiEnvelope<List<GrnLineDto>>
}
