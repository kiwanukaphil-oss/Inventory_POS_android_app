package com.kline.inventorypos.data.session

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kline.inventorypos.data.network.ApiEnvelope
import com.kline.inventorypos.data.network.CashDrawerDto
import com.kline.inventorypos.data.network.CatalogVariantDto
import com.kline.inventorypos.data.network.CategoryDto
import com.kline.inventorypos.data.network.CurrentUserResponse
import com.kline.inventorypos.data.network.CartValidationResponse
import com.kline.inventorypos.data.network.CreateSaleRequest
import com.kline.inventorypos.data.network.CreateReturnRequest
import com.kline.inventorypos.data.network.CreateExchangeRequest
import com.kline.inventorypos.data.network.ReturnMutationResponse
import com.kline.inventorypos.data.network.ExchangeMutationResponse
import com.kline.inventorypos.data.network.ReturnableItemDto
import com.kline.inventorypos.data.network.ExchangePreviewRequest
import com.kline.inventorypos.data.network.ExchangePreviewDto
import com.kline.inventorypos.data.network.EmailReceiptRequest
import com.kline.inventorypos.data.network.CustomerDto
import com.kline.inventorypos.data.network.CustomerListResponse
import com.kline.inventorypos.data.network.CustomerPurchaseHistoryDto
import com.kline.inventorypos.data.network.CustomerAgingDto
import com.kline.inventorypos.data.network.CustomerLedgerEntryDto
import com.kline.inventorypos.data.network.CustomerNoteDto
import com.kline.inventorypos.data.network.CustomerContactDto
import com.kline.inventorypos.data.network.CreateCustomerNoteRequest
import com.kline.inventorypos.data.network.LoyaltyEntryDto
import com.kline.inventorypos.data.network.StoreCreditSummaryDto
import com.kline.inventorypos.data.network.DiscountDto
import com.kline.inventorypos.data.network.HeldCartDto
import com.kline.inventorypos.data.network.HoldCartRequest
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.network.InventorySummaryDto
import com.kline.inventorypos.data.network.StockMovementsResponse
import com.kline.inventorypos.data.network.StockAdjustmentRequest
import com.kline.inventorypos.data.network.BulkStockAdjustmentRequest
import com.kline.inventorypos.data.network.InventoryMutationResponseDto
import com.kline.inventorypos.data.network.SupplierDto
import com.kline.inventorypos.data.network.PricingVariantDto
import com.kline.inventorypos.data.network.PriceHistoryEntryDto
import com.kline.inventorypos.data.network.BulkPriceUpdateRequest
import com.kline.inventorypos.data.network.LabelPrintRunRequest
import com.kline.inventorypos.data.network.LabelPrintRunDto
import com.kline.inventorypos.data.network.SalesListResponse
import com.kline.inventorypos.data.network.StockTransfersResponse
import com.kline.inventorypos.data.network.StockTransferDto
import com.kline.inventorypos.data.network.CreateStockTransferRequest
import com.kline.inventorypos.data.network.ReceiveStockDraftDto
import com.kline.inventorypos.data.network.SaveReceiveStockDraftRequest
import com.kline.inventorypos.data.network.GrnListResponse
import com.kline.inventorypos.data.network.GrnLineDto
import com.kline.inventorypos.data.network.LoginRequest
import com.kline.inventorypos.data.network.LoginResponse
import com.kline.inventorypos.data.network.MyBranchesDto
import com.kline.inventorypos.data.network.OpenDrawerRequest
import com.kline.inventorypos.data.network.PromotionEvaluationDto
import com.kline.inventorypos.data.network.PromotionEvaluationRequest
import com.kline.inventorypos.data.network.ReceiptDto
import com.kline.inventorypos.data.network.SaleMutationResponseDto
import com.kline.inventorypos.data.network.TaxPreviewDto
import com.kline.inventorypos.data.network.TaxPreviewRequest
import com.kline.inventorypos.data.network.ValidateCartRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SessionRepositoryTest {
    @Test
    fun demoRegistersAreScopedToSelectedBranch() = runBlocking {
        val headers = SessionHeaders()
        val repository = DefaultSessionRepository(NoNetworkApi, MemoryStore(), headers, Gson())
        val context = repository.startDemo()
        val main = context.branches.first { it.id == "main" }
        val acacia = context.branches.first { it.id == "acacia" }

        repository.selectBranch(main.id)
        val mainRegister = repository.openRegister(200_000)
        assertSame(mainRegister, repository.activeRegister())

        repository.selectBranch(acacia.id)
        assertNull(repository.activeRegister())
        val acaciaRegister = repository.openRegister(100_000)
        assertEquals(100_000L, acaciaRegister.openingFloat)

        repository.selectBranch(main.id)
        assertEquals(200_000L, repository.activeRegister()?.openingFloat)
    }
}

private class MemoryStore : SessionStore {
    private var token: String? = null
    private var branch: String? = null
    override suspend fun readToken() = token
    override suspend fun readBranchId() = branch
    override suspend fun writeToken(token: String) { this.token = token }
    override suspend fun writeBranchId(branchId: String) { branch = branchId }
    override suspend fun clear() { token = null; branch = null }
}

private object NoNetworkApi : InventoryPosApi {
    override suspend fun login(request: LoginRequest): LoginResponse = error("Network should not be used")
    override suspend fun currentUser(): CurrentUserResponse = error("Network should not be used")
    override suspend fun myBranches(): ApiEnvelope<MyBranchesDto> = error("Network should not be used")
    override suspend fun activeDrawer(): ApiEnvelope<CashDrawerDto?> = error("Network should not be used")
    override suspend fun openDrawer(request: OpenDrawerRequest): ApiEnvelope<CashDrawerDto> = error("Network should not be used")
    override suspend fun catalogVariants(): ApiEnvelope<List<CatalogVariantDto>> = error("Network should not be used")
    override suspend fun variantByBarcode(barcode: String): ApiEnvelope<CatalogVariantDto> = error("Network should not be used")
    override suspend fun categories(): ApiEnvelope<List<CategoryDto>> = error("Network should not be used")
    override suspend fun searchCustomers(query: String): ApiEnvelope<List<CustomerDto>> = error("Network should not be used")
    override suspend fun customers(search: String?, view: String, sort: String, page: Int, limit: Int): CustomerListResponse = error("Network should not be used")
    override suspend fun customer(id: String): ApiEnvelope<CustomerDto> = error("Network should not be used")
    override suspend fun customerPurchases(id: String, limit: Int): ApiEnvelope<CustomerPurchaseHistoryDto> = error("Network should not be used")
    override suspend fun customerAging(id: String): ApiEnvelope<CustomerAgingDto> = error("Network should not be used")
    override suspend fun customerLedger(id: String, filter: String, limit: Int): ApiEnvelope<List<CustomerLedgerEntryDto>> = error("Network should not be used")
    override suspend fun customerNotes(id: String): ApiEnvelope<List<CustomerNoteDto>> = error("Network should not be used")
    override suspend fun createCustomerNote(id: String, request: CreateCustomerNoteRequest): ApiEnvelope<CustomerNoteDto> = error("Network should not be used")
    override suspend fun customerContacts(id: String): ApiEnvelope<List<CustomerContactDto>> = error("Network should not be used")
    override suspend fun customerLoyalty(id: String, limit: Int): ApiEnvelope<List<LoyaltyEntryDto>> = error("Network should not be used")
    override suspend fun customerStoreCredit(id: String): ApiEnvelope<StoreCreditSummaryDto> = error("Network should not be used")
    override suspend fun evaluatePromotions(request: PromotionEvaluationRequest): ApiEnvelope<PromotionEvaluationDto> = error("Network should not be used")
    override suspend fun discounts(activeOnly: Boolean): ApiEnvelope<List<DiscountDto>> = error("Network should not be used")
    override suspend fun heldCarts(): ApiEnvelope<List<HeldCartDto>> = error("Network should not be used")
    override suspend fun holdCart(request: HoldCartRequest): ApiEnvelope<HeldCartDto> = error("Network should not be used")
    override suspend fun recallHeldCart(id: String): ApiEnvelope<HeldCartDto> = error("Network should not be used")
    override suspend fun deleteHeldCart(id: String): ApiEnvelope<Any> = error("Network should not be used")
    override suspend fun validateCart(request: ValidateCartRequest): CartValidationResponse = error("Network should not be used")
    override suspend fun previewTax(request: TaxPreviewRequest): ApiEnvelope<TaxPreviewDto> = error("Network should not be used")
    override suspend fun createSale(attemptId: String, request: CreateSaleRequest): SaleMutationResponseDto = error("Network should not be used")
    override suspend fun sales(search: String?, page: Int, limit: Int): SalesListResponse = error("Network should not be used")
    override suspend fun receipt(id: String): ApiEnvelope<ReceiptDto> = error("Network should not be used")
    override suspend fun emailReceipt(saleId: String, request: EmailReceiptRequest): ApiEnvelope<JsonObject> = error("Network should not be used")
    override suspend fun returnableItems(saleId: String): ApiEnvelope<List<ReturnableItemDto>> = error("Network should not be used")
    override suspend fun createReturn(request: CreateReturnRequest): ReturnMutationResponse = error("Network should not be used")
    override suspend fun previewExchange(request: ExchangePreviewRequest): ApiEnvelope<ExchangePreviewDto> = error("Network should not be used")
    override suspend fun createExchange(request: CreateExchangeRequest): ExchangeMutationResponse = error("Network should not be used")
    override suspend fun inventorySummary(): ApiEnvelope<InventorySummaryDto> = error("Network should not be used")
    override suspend fun stockMovements(movementType: String?, limit: Int, offset: Int): StockMovementsResponse = error("Network should not be used")
    override suspend fun adjustStock(request: StockAdjustmentRequest): InventoryMutationResponseDto = error("Network should not be used")
    override suspend fun receiveStock(request: BulkStockAdjustmentRequest): InventoryMutationResponseDto = error("Network should not be used")
    override suspend fun suppliers(): ApiEnvelope<List<SupplierDto>> = error("Network should not be used")
    override suspend fun pricingVariants(): ApiEnvelope<List<PricingVariantDto>> = error("Network should not be used")
    override suspend fun priceHistory(limit: Int): ApiEnvelope<List<PriceHistoryEntryDto>> = error("Network should not be used")
    override suspend fun bulkUpdatePrices(request: BulkPriceUpdateRequest): InventoryMutationResponseDto = error("Network should not be used")
    override suspend fun recordLabelPrintRun(request: LabelPrintRunRequest): ApiEnvelope<LabelPrintRunDto> = error("Network should not be used")
    override suspend fun stockTransfers(status: String?, limit: Int, offset: Int): StockTransfersResponse = error("Network should not be used")
    override suspend fun createStockTransfer(request: CreateStockTransferRequest): ApiEnvelope<StockTransferDto> = error("Network should not be used")
    override suspend fun dispatchStockTransfer(id: String): ApiEnvelope<StockTransferDto> = error("Network should not be used")
    override suspend fun receiveStockTransfer(id: String): ApiEnvelope<StockTransferDto> = error("Network should not be used")
    override suspend fun cancelStockTransfer(id: String): ApiEnvelope<StockTransferDto> = error("Network should not be used")
    override suspend fun receiveStockDraft(): ApiEnvelope<ReceiveStockDraftDto?> = error("Network should not be used")
    override suspend fun saveReceiveStockDraft(request: SaveReceiveStockDraftRequest): ApiEnvelope<ReceiveStockDraftDto> = error("Network should not be used")
    override suspend fun deleteReceiveStockDraft(id: String): ApiEnvelope<Any> = error("Network should not be used")
    override suspend fun grnHistory(limit: Int, offset: Int): GrnListResponse = error("Network should not be used")
    override suspend fun grnDetail(reference: String): ApiEnvelope<List<GrnLineDto>> = error("Network should not be used")
}
