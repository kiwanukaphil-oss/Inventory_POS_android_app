package com.kline.inventorypos.data.network

import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
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

    @GET("products/variants")
    suspend fun catalogVariants(): ApiEnvelope<List<CatalogVariantDto>>

    @GET("products/variants/by-barcode/{barcode}")
    suspend fun variantByBarcode(@Path("barcode") barcode: String): ApiEnvelope<CatalogVariantDto>

    @GET("categories")
    suspend fun categories(): ApiEnvelope<List<CategoryDto>>

    @GET("customers/search")
    suspend fun searchCustomers(@Query("q") query: String): ApiEnvelope<List<CustomerDto>>

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
