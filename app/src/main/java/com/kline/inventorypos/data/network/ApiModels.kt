package com.kline.inventorypos.data.network

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.kline.inventorypos.core.session.PosBranch
import com.kline.inventorypos.core.session.PosUser
import com.kline.inventorypos.core.session.RegisterSession

data class LoginRequest(val username: String, val password: String)

data class LoginResponse(
    val success: Boolean,
    val message: String?,
    val token: String,
    val user: UserDto,
)

data class CurrentUserResponse(val success: Boolean, val user: UserDto)

data class ApiEnvelope<T>(
    val success: Boolean,
    val message: String?,
    val data: T,
)

data class ApiError(val message: String?)

data class UserDto(
    val id: String,
    val username: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("role_name") val roleName: String?,
    val role: String?,
    val permissions: List<PermissionDto>?,
    val branches: List<BranchDto>?,
    @SerializedName("default_branch_id") val defaultBranchId: String?,
)

data class PermissionDto(
    @SerializedName("permission_name") val permissionName: String?,
    val name: String?,
)

data class BranchDto(
    val id: String,
    val code: String?,
    val name: String,
    val status: String?,
    val city: String?,
    @SerializedName("is_default") val isDefault: Boolean?,
    @SerializedName("is_user_default") val isUserDefault: Boolean?,
    @SerializedName("can_switch_to") val canSwitchTo: Boolean?,
)

data class MyBranchesDto(
    val branches: List<BranchDto>,
    @SerializedName("default_branch_id") val defaultBranchId: String?,
)

data class OpenDrawerRequest(@SerializedName("opening_float") val openingFloat: Long)

data class CashDrawerDto(
    val id: String,
    @SerializedName("opening_float") val openingFloat: Double?,
    @SerializedName("opened_at") val openedAt: String?,
    @SerializedName("running_total") val runningTotal: Double?,
    @SerializedName("opened_by_name") val openedByName: String? = null,
    @SerializedName("expected_closing") val expectedClosing: Double? = null,
    @SerializedName("actual_closing") val actualClosing: Double? = null,
    val variance: Double? = null,
    @SerializedName("variance_note") val varianceNote: String? = null,
    val status: String? = null,
    @SerializedName("closed_at") val closedAt: String? = null,
)

data class CloseCashDrawerRequest(
    @SerializedName("actual_closing") val actualClosing: Long,
    val notes: String? = null,
    @SerializedName("variance_note") val varianceNote: String? = null,
)

data class HandoverCashDrawerRequest(
    @SerializedName("outgoing_session_id") val outgoingSessionId: String,
    @SerializedName("counted_amount") val countedAmount: Long,
    @SerializedName("incoming_user_id") val incomingUserId: String,
    @SerializedName("variance_note") val varianceNote: String?,
)

data class HandoverCashDrawerDto(
    @SerializedName("closed_session") val closedSession: CashDrawerDto,
    @SerializedName("new_session") val newSession: CashDrawerDto,
)

data class CashMovementTotalDto(
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("movement_type") val movementType: String,
    val count: Int,
    val total: Double,
)

data class CashSessionSummaryDto(
    val session: CashDrawerDto,
    val movements: List<CashMovementTotalDto>,
    val expected: Double,
)

data class CashMovementDto(
    val id: String,
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("movement_type") val movementType: String,
    val amount: Double,
    val category: String?,
    @SerializedName("reference_number") val referenceNumber: String?,
    @SerializedName("processed_by_name") val processedByName: String?,
    val notes: String?,
    @SerializedName("transaction_date") val transactionDate: String,
)

data class CashMovementsDataDto(val movements: List<CashMovementDto>, val total: Int)

data class CashBookSummaryDto(
    @SerializedName("total_inflows") val totalInflows: Double?,
    @SerializedName("total_outflows") val totalOutflows: Double?,
    @SerializedName("net_movement") val netMovement: Double?,
    @SerializedName("movement_count") val movementCount: Int?,
    val breakdown: List<CashMovementTotalDto>?,
)

data class RecordCashMovementRequest(
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("movement_type") val movementType: String,
    val amount: Long,
    val notes: String,
    val category: String?,
    @SerializedName("customer_id") val customerId: String? = null,
)

data class CashMovementMutationResponse(
    val success: Boolean,
    val status: String?,
    val message: String?,
    val data: JsonObject,
)

data class CashStaffDto(
    val id: String,
    val username: String,
    @SerializedName("full_name") val fullName: String?,
    @SerializedName("is_active") val isActive: Boolean?,
)

data class CatalogVariantDto(
    val id: String,
    val sku: String,
    @SerializedName("variant_attributes") val variantAttributes: Map<String, String>?,
    val price: Double,
    @SerializedName("quantity_in_stock") val quantityInStock: Double?,
    @SerializedName("reorder_level") val reorderLevel: Double?,
    val barcode: String?,
    @SerializedName("product_id") val productId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("brand_name") val brandName: String?,
)

data class CategoryDto(val id: String, val name: String)

data class CustomerDto(
    val id: String,
    @SerializedName("first_name") val firstName: String?,
    @SerializedName("last_name") val lastName: String?,
    @SerializedName("company_name") val companyName: String?,
    val phone: String?,
    val email: String?,
    @SerializedName("loyalty_points") val loyaltyPoints: Double?,
    @SerializedName("customer_type") val customerType: String? = null,
    val city: String? = null,
    @SerializedName("total_purchases") val totalPurchases: Int? = null,
    @SerializedName("total_spent") val totalSpent: Double? = null,
    @SerializedName("credit_balance") val creditBalance: Double? = null,
    @SerializedName("credit_limit") val creditLimit: Double? = null,
    @SerializedName("prepaid_balance") val prepaidBalance: Double? = null,
    @SerializedName("last_purchase_date") val lastPurchaseDate: String? = null,
    val tier: CustomerTierDto? = null,
    val segment: CustomerSegmentDto? = null,
    val tags: List<String>? = null,
)

data class CustomerTierDto(val id: String?, val name: String?)
data class CustomerSegmentDto(val segment: String?, val label: String?)

data class CustomerListResponse(
    val success: Boolean,
    val data: List<CustomerDto>,
    val pagination: SalesPaginationDto,
)

data class CustomerPurchaseHistoryDto(
    val data: List<CustomerPurchaseDto>,
)

data class CustomerPurchaseDto(
    val id: String,
    @SerializedName("receipt_number") val receiptNumber: String,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("total_returned") val totalReturned: Double?,
    @SerializedName("net_amount") val netAmount: Double?,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("sale_date") val saleDate: String,
    val status: String,
    @SerializedName("item_count") val itemCount: Int?,
    @SerializedName("product_names") val productNames: List<String>?,
)

data class CustomerAgingDto(
    @SerializedName("current_bucket") val currentBucket: Double?,
    @SerializedName("bucket_0_30") val bucket0To30: Double?,
    @SerializedName("bucket_31_60") val bucket31To60: Double?,
    @SerializedName("bucket_61_plus") val bucket61Plus: Double?,
)

data class CustomerLedgerEntryDto(
    val id: String,
    val account: String,
    val event: String,
    @SerializedName("signed_amount") val signedAmount: Double?,
    @SerializedName("running_balance") val runningBalance: Double?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("receipt_number") val receiptNumber: String?,
    val notes: String?,
)

data class CustomerNoteDto(
    val id: String,
    val body: String,
    val pinned: Boolean?,
    @SerializedName("created_by_name") val createdByName: String?,
    @SerializedName("created_at") val createdAt: String,
)

data class CreateCustomerNoteRequest(val body: String, val pinned: Boolean)

data class CustomerContactDto(
    val id: String,
    val name: String,
    val title: String?,
    val phone: String?,
    val email: String?,
    @SerializedName("is_primary") val isPrimary: Boolean?,
)

data class LoyaltyEntryDto(
    val id: String,
    val type: String,
    val points: Int,
    @SerializedName("balance_after") val balanceAfter: Int,
    val description: String?,
    @SerializedName("created_at") val createdAt: String,
)

data class StoreCreditSummaryDto(
    @SerializedName("active_balance") val activeBalance: Double?,
    @SerializedName("active_credit_count") val activeCreditCount: Int?,
    @SerializedName("next_expiry_date") val nextExpiryDate: String?,
)

data class GiftVoucherTemplateDto(
    val id: String,
    val name: String,
    val description: String?,
    val category: String?,
    @SerializedName("is_active") val isActive: Boolean?,
)

data class GiftVoucherTransactionDto(
    val id: String,
    @SerializedName("transaction_type") val transactionType: String,
    val amount: Double,
    @SerializedName("balance_after") val balanceAfter: Double,
    @SerializedName("payment_method") val paymentMethod: String?,
    val reference: String?,
    val notes: String?,
    @SerializedName("created_by_name") val createdByName: String?,
    @SerializedName("created_at") val createdAt: String,
)

data class GiftVoucherDto(
    val id: String,
    @SerializedName("voucher_code") val voucherCode: String,
    @SerializedName("template_name") val templateName: String?,
    @SerializedName("recipient_name") val recipientName: String,
    @SerializedName("from_name") val fromName: String?,
    val message: String?,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("original_amount") val originalAmount: Double,
    @SerializedName("remaining_balance") val remainingBalance: Double,
    @SerializedName("issue_date") val issueDate: String?,
    @SerializedName("expiry_date") val expiryDate: String?,
    val status: String,
    @SerializedName("payment_status") val paymentStatus: String,
    @SerializedName("created_by_name") val createdByName: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("cancel_reason") val cancelReason: String?,
    val transactions: List<GiftVoucherTransactionDto>?,
)

data class GiftVoucherListResponse(
    val success: Boolean,
    val data: List<GiftVoucherDto>,
    val pagination: SalesPaginationDto,
)

data class CreateGiftVoucherRequest(
    @SerializedName("template_id") val templateId: String,
    @SerializedName("recipient_name") val recipientName: String,
    @SerializedName("from_name") val fromName: String?,
    val message: String?,
    @SerializedName("phone_number") val phoneNumber: String?,
    @SerializedName("original_amount") val originalAmount: Long,
    @SerializedName("expiry_date") val expiryDate: String?,
    val notes: String?,
)

data class GiftVoucherPaymentRequest(
    val method: String,
    val amount: Long,
    val reference: String?,
)

data class ActivateGiftVoucherRequest(val payments: List<GiftVoucherPaymentRequest>, val notes: String? = null)
data class ValidateGiftVoucherRequest(val code: String? = null, val token: String? = null, val amount: Long? = null)
data class RedeemGiftVoucherRequest(val amount: Long, @SerializedName("sale_id") val saleId: String? = null, val notes: String? = null)
data class CancelGiftVoucherRequest(val reason: String, val refund: Boolean)

data class PromotionEvaluationRequest(
    val items: List<PromotionItemRequest>,
    val subtotal: Long,
    @SerializedName("customer_id") val customerId: String?,
)

data class PromotionItemRequest(
    @SerializedName("variant_id") val variantId: String,
    @SerializedName("product_id") val productId: String,
    @SerializedName("category_id") val categoryId: String?,
    val price: Long,
    val quantity: Int,
)

data class PromotionEvaluationDto(
    val applied: List<AppliedPromotionDto>?,
    val totalSavings: Double?,
)

data class AppliedPromotionDto(
    val name: String,
    val savings: Double?,
    @SerializedName("applicable_variant_ids") val applicableVariantIds: List<String>?,
)

data class HeldCartItemDto(
    @SerializedName("variant_id") val variantId: String,
    @SerializedName("product_name") val productName: String,
    val sku: String,
    @SerializedName("variant_attributes") val variantAttributes: Map<String, String>,
    val price: Long,
    val quantity: Int,
    @SerializedName("stock_quantity") val stockQuantity: Int,
)

data class HoldCartRequest(
    @SerializedName("customer_id") val customerId: String?,
    @SerializedName("cart_items") val cartItems: List<HeldCartItemDto>,
    val notes: String?,
)

data class HeldCartDto(
    val id: String,
    @SerializedName("customer_id") val customerId: String?,
    @SerializedName("customer_name") val customerName: String?,
    @SerializedName("cart_items") val cartItems: List<HeldCartItemDto>,
    @SerializedName("item_count") val itemCount: Int?,
    val notes: String?,
    @SerializedName("held_at") val heldAt: String?,
)

data class CartItemRequest(
    @SerializedName("variant_id") val variantId: String,
    val quantity: Int,
)

data class ValidateCartRequest(val items: List<CartItemRequest>)

data class CartValidationResponse(
    val success: Boolean,
    val valid: Boolean,
    val items: List<CartValidationItemDto>,
)

data class CartValidationItemDto(
    @SerializedName("variant_id") val variantId: String,
    val valid: Boolean,
    val error: String?,
    @SerializedName("available_quantity") val availableQuantity: Double?,
)

data class TaxPreviewRequest(val items: List<TaxPreviewItemRequest>)

data class TaxPreviewItemRequest(
    @SerializedName("variant_id") val variantId: String,
    @SerializedName("product_id") val productId: String,
    @SerializedName("category_id") val categoryId: String?,
    @SerializedName("unit_price") val unitPrice: Long,
    val quantity: Int,
)

data class TaxPreviewDto(val items: List<TaxPreviewItemDto>, val totals: TaxPreviewTotalsDto)

data class TaxPreviewItemDto(
    @SerializedName("variant_id") val variantId: String,
    @SerializedName("tax_rate") val taxRate: Double,
    @SerializedName("tax_type") val taxType: String?,
)

data class TaxPreviewTotalsDto(
    val subtotal: Double,
    val tax: Double,
    val total: Double,
)

data class SalePaymentRequest(
    val method: String,
    val amount: Long,
    val reference: String?,
)

data class DiscountDto(
    val id: String,
    val name: String,
    @SerializedName("discount_type") val discountType: String,
    val value: Double,
    val scope: String,
    @SerializedName("scope_id") val scopeId: String?,
    val conditions: DiscountConditionsDto?,
    @SerializedName("requires_approval") val requiresApproval: Boolean,
    @SerializedName("approval_threshold") val approvalThreshold: Double?,
    @SerializedName("max_uses") val maxUses: Int?,
    @SerializedName("current_uses") val currentUses: Int?,
)

data class DiscountConditionsDto(
    @SerializedName("min_spend") val minimumSpend: Double?,
    @SerializedName("min_quantity") val minimumQuantity: Int?,
)

data class InventorySummaryDto(
    @SerializedName("total_products") val totalProducts: Int,
    @SerializedName("total_variants") val totalVariants: Int,
    @SerializedName("total_stock_units") val totalStockUnits: Int,
    @SerializedName("low_stock_count") val lowStockCount: Int,
    @SerializedName("out_of_stock_count") val outOfStockCount: Int,
    @SerializedName("total_inventory_value") val totalInventoryValue: Double,
    @SerializedName("dead_stock_count") val deadStockCount: Int,
    @SerializedName("stale_price_count") val stalePriceCount: Int,
)

data class StockMovementDto(
    val id: String,
    @SerializedName("variant_id") val variantId: String,
    @SerializedName("product_name") val productName: String?,
    val sku: String,
    @SerializedName("movement_type") val movementType: String,
    @SerializedName("quantity_change") val quantityChange: Int,
    @SerializedName("previous_quantity") val previousQuantity: Int,
    @SerializedName("new_quantity") val newQuantity: Int,
    val reason: String?,
    @SerializedName("reference_id") val referenceId: String?,
    @SerializedName("performed_by_name") val performedByName: String?,
    @SerializedName("created_at") val createdAt: String,
)

data class PaginationDto(val total: Int, val limit: Int, val offset: Int)

data class StockMovementsResponse(
    val success: Boolean,
    val data: List<StockMovementDto>,
    val pagination: PaginationDto,
)

data class StockAdjustmentRequest(
    @SerializedName("variant_id") val variantId: String,
    @SerializedName("quantity_change") val quantityChange: Int,
    val reason: String,
    @SerializedName("movement_type") val movementType: String = "adjustment",
)

data class BulkStockAdjustmentRequest(
    val adjustments: List<BulkStockAdjustmentItemRequest>,
    val reason: String,
    @SerializedName("movement_type") val movementType: String = "purchase",
    @SerializedName("supplier_id") val supplierId: String?,
)

data class BulkStockAdjustmentItemRequest(
    @SerializedName("variant_id") val variantId: String,
    @SerializedName("quantity_change") val quantityChange: Int,
    @SerializedName("cost_price") val costPrice: Long?,
)

data class InventoryMutationResponseDto(
    val success: Boolean,
    val status: String?,
    val message: String?,
    @SerializedName("grn_reference") val grnReference: String?,
    val data: JsonElement?,
)

data class SupplierDto(
    val id: String,
    val name: String,
    val phone: String?,
    @SerializedName("is_active") val isActive: Boolean?,
)

data class PricingVariantDto(
    val id: String,
    val sku: String,
    @SerializedName("variant_attributes") val variantAttributes: Map<String, String>?,
    val price: Double,
    @SerializedName("cost_price") val costPrice: Double?,
    @SerializedName("stock_quantity") val stockQuantity: Double,
    @SerializedName("product_name") val productName: String,
    @SerializedName("category_name") val categoryName: String?,
)

data class PriceHistoryEntryDto(
    val id: String,
    val sku: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("old_price") val oldPrice: Double,
    @SerializedName("new_price") val newPrice: Double,
    val reason: String?,
    @SerializedName("changed_by_name") val changedByName: String?,
    @SerializedName("changed_by_username") val changedByUsername: String?,
    @SerializedName("created_at") val createdAt: String,
)

data class PriceUpdateRequest(val variantId: String, val newPrice: Long)

data class BulkPriceUpdateRequest(
    val updates: List<PriceUpdateRequest>,
    val reason: String,
    val effectiveDate: String,
)

data class LabelPrintRunItemRequest(val variantId: String, val copies: Int)

data class LabelPrintRunRequest(
    val items: List<LabelPrintRunItemRequest>,
    val note: String? = null,
)

data class LabelPrintRunDto(
    val id: String,
    val printedAt: String,
    val totalCopies: Int,
)

data class StockTransferDto(
    val id: String,
    @SerializedName("transfer_number") val transferNumber: String,
    @SerializedName("from_branch_id") val fromBranchId: String,
    @SerializedName("to_branch_id") val toBranchId: String,
    val status: String,
    val notes: String?,
    @SerializedName("from_branch_name") val fromBranchName: String,
    @SerializedName("to_branch_name") val toBranchName: String,
    @SerializedName("requested_by_name") val requestedByName: String?,
    @SerializedName("requested_at") val requestedAt: String,
    @SerializedName("item_count") val itemCount: Int?,
)

data class StockTransfersResponse(
    val success: Boolean,
    val data: List<StockTransferDto>,
    val pagination: PaginationDto,
)

data class CreateStockTransferRequest(
    @SerializedName("to_branch_id") val toBranchId: String,
    val notes: String?,
    val items: List<CreateStockTransferItemRequest>,
)

data class CreateStockTransferItemRequest(
    @SerializedName("variant_id") val variantId: String,
    @SerializedName("requested_quantity") val requestedQuantity: Int,
)

data class ReceiveDraftItemDto(
    @SerializedName("variant_id") val variantId: String,
    val sku: String?,
    @SerializedName("product_name") val productName: String?,
    @SerializedName("variant_attributes") val variantAttributes: Map<String, String>?,
    val quantity: Int,
    @SerializedName("cost_price") val costPrice: Double?,
)

data class ReceiveStockDraftDto(
    val id: String,
    @SerializedName("supplier_id") val supplierId: String?,
    @SerializedName("supplier_name") val supplierName: String?,
    val items: List<ReceiveDraftItemDto>?,
    val notes: String?,
    @SerializedName("updated_at") val updatedAt: String,
)

data class SaveReceiveStockDraftRequest(
    @SerializedName("supplier_id") val supplierId: String?,
    @SerializedName("grn_reference") val grnReference: String? = null,
    val items: List<ReceiveDraftItemDto>,
    val notes: String?,
    @SerializedName("is_partial") val isPartial: Boolean = false,
)

data class GrnSummaryDto(
    @SerializedName("grn_reference") val grnReference: String,
    @SerializedName("received_at") val receivedAt: String,
    @SerializedName("supplier_name") val supplierName: String?,
    @SerializedName("received_by") val receivedBy: String?,
    @SerializedName("item_count") val itemCount: Int,
    @SerializedName("total_units") val totalUnits: Int,
)

data class GrnListResponse(
    val success: Boolean,
    val data: List<GrnSummaryDto>,
    val total: Int,
)

data class GrnLineDto(
    @SerializedName("movement_id") val movementId: String,
    @SerializedName("variant_id") val variantId: String,
    @SerializedName("quantity_change") val quantityChange: Int,
    @SerializedName("previous_quantity") val previousQuantity: Int,
    @SerializedName("new_quantity") val newQuantity: Int,
    val sku: String,
    @SerializedName("variant_attributes") val variantAttributes: Map<String, String>?,
    @SerializedName("current_cost_price") val currentCostPrice: Double?,
    @SerializedName("product_name") val productName: String,
)

data class CreateSaleRequest(
    @SerializedName("customer_id") val customerId: String?,
    val items: List<CartItemRequest>,
    val payments: List<SalePaymentRequest>,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("amount_paid") val amountPaid: Long,
    @SerializedName("discount_amount") val discountAmount: Long = 0,
    @SerializedName("discount_id") val discountId: String? = null,
    @SerializedName("payment_reference") val paymentReference: String?,
    @SerializedName("confirm_oversell") val confirmOversell: Boolean = false,
)

data class SaleMutationResponseDto(
    val success: Boolean,
    val status: String?,
    val message: String?,
    val data: JsonObject,
)

data class SaleDto(
    val id: String,
    @SerializedName("receipt_number") val receiptNumber: String,
    @SerializedName("sale_date") val saleDate: String?,
    @SerializedName("created_at") val createdAt: String?,
    val subtotal: Double,
    @SerializedName("discount_amount") val discountAmount: Double,
    @SerializedName("tax_amount") val taxAmount: Double,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("amount_paid") val amountPaid: Double,
    @SerializedName("change_amount") val changeAmount: Double,
    val items: List<SaleItemDto>?,
)

data class SaleItemDto(
    @SerializedName("product_name") val productName: String,
    @SerializedName("variant_attributes") val variantAttributes: Map<String, String>?,
    val sku: String,
    @SerializedName("unit_price") val unitPrice: Double,
    val quantity: Int,
    @SerializedName("line_total") val lineTotal: Double,
)

data class SaleHistoryDto(
    val id: String,
    @SerializedName("receipt_number") val receiptNumber: String,
    @SerializedName("customer_first_name") val customerFirstName: String?,
    @SerializedName("customer_last_name") val customerLastName: String?,
    @SerializedName("total_amount") val totalAmount: Double,
    @SerializedName("payment_method") val paymentMethod: String,
    val status: String,
    @SerializedName("sale_date") val saleDate: String,
    @SerializedName("processed_by_name") val processedByName: String?,
    @SerializedName("staff_name") val staffName: String?,
    @SerializedName("item_count") val itemCount: Int?,
    @SerializedName("return_status") val returnStatus: String?,
    @SerializedName("total_returned") val totalReturned: Double?,
)

data class SalesPaginationDto(
    val total: Int,
    val page: Int,
    val limit: Int,
    val totalPages: Int,
)

data class SalesListResponse(
    val success: Boolean,
    val data: List<SaleHistoryDto>,
    val pagination: SalesPaginationDto,
)

data class PendingApprovalDto(
    @SerializedName("approval_request_id") val approvalRequestId: String,
)

data class ReceiptDto(
    val receiptNumber: String,
    val saleId: String,
    val date: String,
    val customer: ReceiptCustomerDto?,
    val items: List<ReceiptItemDto>,
    val subtotal: Double,
    val discountAmount: Double,
    val taxAmount: Double,
    val totalAmount: Double,
    val amountPaid: Double,
    val changeAmount: Double,
    val payments: List<ReceiptPaymentDto>,
    val processedBy: String,
)

data class ReceiptCustomerDto(val id: String, val name: String, val email: String?, val phone: String?)

data class ReceiptItemDto(
    val productName: String,
    val variantAttributes: Map<String, String>?,
    val sku: String,
    val unitPrice: Double,
    val quantity: Int,
    val lineTotal: Double,
)

data class ReceiptPaymentDto(val method: String, val amount: Double, val reference: String?)

data class EmailReceiptRequest(
    val email: String,
    val saveToCustomer: Boolean = false,
    val customerId: String? = null,
    val customerName: String? = null,
)

data class ReturnableItemDto(
    val id: String,
    @SerializedName("variant_id") val variantId: String,
    @SerializedName("product_name") val productName: String,
    @SerializedName("variant_attributes") val variantAttributes: Map<String, String>?,
    val sku: String,
    @SerializedName("unit_price") val unitPrice: Double,
    @SerializedName("tax_amount") val taxAmount: Double?,
    val quantity: Int,
    @SerializedName("max_returnable") val maxReturnable: Int,
)

data class ReturnItemRequest(
    @SerializedName("original_sale_item_id") val originalSaleItemId: String,
    @SerializedName("variant_id") val variantId: String,
    @SerializedName("quantity_returned") val quantityReturned: Int,
    val condition: String,
)

data class CreateReturnRequest(
    @SerializedName("original_sale_id") val originalSaleId: String,
    @SerializedName("return_type") val returnType: String,
    @SerializedName("return_reason") val returnReason: String,
    @SerializedName("return_notes") val returnNotes: String?,
    @SerializedName("refund_method") val refundMethod: String?,
    val items: List<ReturnItemRequest>,
)

data class ReturnMutationResponse(
    val success: Boolean,
    @SerializedName("return_number") val returnNumber: String?,
    @SerializedName("total_refund") val totalRefund: Double?,
    val message: String?,
)

data class ExchangeNewItemRequest(
    @SerializedName("variant_id") val variantId: String,
    val quantity: Int,
)

data class ExchangePreviewRequest(
    @SerializedName("sale_id") val saleId: String,
    @SerializedName("returned_items") val returnedItems: List<ReturnItemRequest>,
    @SerializedName("new_items") val newItems: List<ExchangeNewItemRequest>,
    @SerializedName("exchange_mode") val exchangeMode: String,
)

data class ExchangePreviewDto(
    @SerializedName("returned_value") val returnedValue: Double,
    @SerializedName("new_items_value") val newItemsValue: Double,
    @SerializedName("net_amount") val netAmount: Double,
)

data class CreateExchangeRequest(
    @SerializedName("original_sale_id") val originalSaleId: String,
    @SerializedName("exchange_mode") val exchangeMode: String,
    @SerializedName("return_reason") val returnReason: String,
    @SerializedName("return_notes") val returnNotes: String?,
    @SerializedName("returned_items") val returnedItems: List<ReturnItemRequest>,
    @SerializedName("new_items") val newItems: List<ExchangeNewItemRequest>,
    @SerializedName("settlement_method") val settlementMethod: String?,
)

data class ExchangeMutationResponse(
    val success: Boolean,
    @SerializedName("return_number") val returnNumber: String?,
    @SerializedName("new_receipt_number") val newReceiptNumber: String?,
    @SerializedName("net_amount") val netAmount: Double?,
    val message: String?,
)

fun UserDto.toDomain(): PosUser = PosUser(
    id = id,
    username = username,
    fullName = fullName?.takeIf(String::isNotBlank) ?: username,
    roleName = roleName ?: role ?: "Team member",
    permissions = permissions.orEmpty().mapNotNull { it.permissionName ?: it.name }.toSet(),
)

fun BranchDto.toDomain(): PosBranch = PosBranch(
    id = id,
    code = code.orEmpty(),
    name = name,
    status = status ?: "active",
    city = city,
    isDefault = isDefault == true,
    isUserDefault = isUserDefault == true,
    canSwitchTo = canSwitchTo != false,
)

fun CashDrawerDto.toDomain(): RegisterSession = RegisterSession(
    id = id,
    openingFloat = openingFloat?.toLong() ?: 0L,
    openedAt = openedAt.orEmpty(),
    runningTotal = runningTotal?.toLong(),
)
