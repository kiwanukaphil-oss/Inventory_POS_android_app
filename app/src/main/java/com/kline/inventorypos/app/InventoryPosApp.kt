package com.kline.inventorypos.app

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.kline.inventorypos.MainActivity
import com.kline.inventorypos.core.session.PosSession
import com.kline.inventorypos.feature.activity.ActivityScreen
import com.kline.inventorypos.feature.activity.ActivityViewModel
import com.kline.inventorypos.feature.activity.ExchangeWorkflowScreen
import com.kline.inventorypos.feature.activity.ReturnWorkflowScreen
import com.kline.inventorypos.feature.customer.CustomerScreen
import com.kline.inventorypos.feature.customer.CustomerViewModel
import com.kline.inventorypos.feature.voucher.GiftVoucherScreen
import com.kline.inventorypos.feature.voucher.GiftVoucherViewModel
import com.kline.inventorypos.feature.cash.CashScreen
import com.kline.inventorypos.feature.cash.CashViewModel
import com.kline.inventorypos.feature.reconciliation.ReconciliationScreen
import com.kline.inventorypos.feature.reconciliation.ReconciliationViewModel
import com.kline.inventorypos.feature.operations.ApprovalScreen
import com.kline.inventorypos.feature.operations.ApprovalViewModel
import com.kline.inventorypos.feature.operations.ExpenseScreen
import com.kline.inventorypos.feature.operations.ExpenseViewModel
import com.kline.inventorypos.feature.document.DocumentScreen
import com.kline.inventorypos.feature.document.DocumentViewModel
import com.kline.inventorypos.feature.administration.AdministrationScreen
import com.kline.inventorypos.feature.administration.AdministrationViewModel
import com.kline.inventorypos.feature.report.ManagementReportScreen
import com.kline.inventorypos.feature.report.ManagementReportViewModel
import com.kline.inventorypos.feature.auth.BranchSelectionScreen
import com.kline.inventorypos.feature.auth.LoginScreen
import com.kline.inventorypos.feature.auth.OpenRegisterScreen
import com.kline.inventorypos.feature.auth.RestoringSessionScreen
import com.kline.inventorypos.feature.home.HomeScreen
import com.kline.inventorypos.feature.inventory.InventoryScreen
import com.kline.inventorypos.feature.inventory.InventoryViewModel
import com.kline.inventorypos.feature.inventory.LabelPrintScreen
import com.kline.inventorypos.feature.inventory.PriceManagementScreen
import com.kline.inventorypos.feature.inventory.ProductCatalogScreen
import com.kline.inventorypos.feature.inventory.ReceiveStockScreen
import com.kline.inventorypos.feature.inventory.TransferStockScreen
import com.kline.inventorypos.feature.more.MoreScreen
import com.kline.inventorypos.feature.pos.CheckoutPaymentScreen
import com.kline.inventorypos.feature.pos.ConfirmedReceiptScreen
import com.kline.inventorypos.feature.pos.PersistentCartScreen
import com.kline.inventorypos.feature.pos.SaleCatalogScreen
import com.kline.inventorypos.feature.pos.SaleViewModel
import kotlinx.coroutines.launch

private data class TopDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val permission: String? = null,
)

private val Destinations = listOf(
    TopDestination(HomeRoute, "Home", Icons.Outlined.Dashboard),
    TopDestination(ActivityRoute, "Activity", Icons.AutoMirrored.Outlined.ReceiptLong, "sales.view"),
    TopDestination(SellRoute, "Sell", Icons.Filled.PointOfSale, "sales.create"),
    TopDestination(InventoryRoute, "Stock", Icons.Outlined.Inventory2, "inventory.view"),
    TopDestination(MoreRoute, "More", Icons.Outlined.GridView),
)

@Composable
fun InventoryPosApp(
    viewModel: AppViewModel,
    saleViewModel: SaleViewModel,
    inventoryViewModel: InventoryViewModel,
    activityViewModel: ActivityViewModel,
    customerViewModel: CustomerViewModel,
    giftVoucherViewModel: GiftVoucherViewModel,
    cashViewModel: CashViewModel,
    reconciliationViewModel: ReconciliationViewModel,
    expenseViewModel: ExpenseViewModel,
    approvalViewModel: ApprovalViewModel,
    documentViewModel: DocumentViewModel,
    administrationViewModel: AdministrationViewModel,
    managementReportViewModel: ManagementReportViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val stage = uiState.stage) {
        SessionStage.Restoring -> RestoringSessionScreen()
        SessionStage.SignedOut -> LoginScreen(
            working = uiState.working,
            error = uiState.error,
            onLogin = viewModel::login,
            onDemo = viewModel::startDemo,
            onClearError = viewModel::clearError,
        )
        is SessionStage.ChooseBranch -> BranchSelectionScreen(
            context = stage.context,
            working = uiState.working,
            error = uiState.error,
            onSelect = viewModel::chooseBranch,
            onLogout = viewModel::logout,
        )
        is SessionStage.OpenRegister -> OpenRegisterScreen(
            branch = stage.branch,
            working = uiState.working,
            error = uiState.error,
            onOpen = viewModel::openRegister,
            onSkip = viewModel::continueWithoutRegister,
            onBack = viewModel::changeBranch,
        )
        is SessionStage.Ready -> AuthenticatedApp(
            session = stage.session,
            saleViewModel = saleViewModel,
            inventoryViewModel = inventoryViewModel,
            activityViewModel = activityViewModel,
            customerViewModel = customerViewModel,
            giftVoucherViewModel = giftVoucherViewModel,
            cashViewModel = cashViewModel,
            reconciliationViewModel = reconciliationViewModel,
            expenseViewModel = expenseViewModel,
            approvalViewModel = approvalViewModel,
            documentViewModel = documentViewModel,
            administrationViewModel = administrationViewModel,
            managementReportViewModel = managementReportViewModel,
            onChangeBranch = viewModel::changeBranch,
            onOpenRegister = viewModel::requestRegister,
            onRegisterClosed = viewModel::registerClosed,
            onLogout = viewModel::logout,
        )
    }
}

@Composable
private fun AuthenticatedApp(
    session: PosSession,
    saleViewModel: SaleViewModel,
    inventoryViewModel: InventoryViewModel,
    activityViewModel: ActivityViewModel,
    customerViewModel: CustomerViewModel,
    giftVoucherViewModel: GiftVoucherViewModel,
    cashViewModel: CashViewModel,
    reconciliationViewModel: ReconciliationViewModel,
    expenseViewModel: ExpenseViewModel,
    approvalViewModel: ApprovalViewModel,
    documentViewModel: DocumentViewModel,
    administrationViewModel: AdministrationViewModel,
    managementReportViewModel: ManagementReportViewModel,
    onChangeBranch: () -> Unit,
    onOpenRegister: () -> Unit,
    onRegisterClosed: () -> Unit,
    onLogout: () -> Unit,
) {
    val activity = LocalActivity.current as? MainActivity
    val saleState by saleViewModel.uiState.collectAsStateWithLifecycle()
    val inventoryState by inventoryViewModel.uiState.collectAsStateWithLifecycle()
    val activityState by activityViewModel.uiState.collectAsStateWithLifecycle()
    val customerState by customerViewModel.uiState.collectAsStateWithLifecycle()
    val voucherState by giftVoucherViewModel.uiState.collectAsStateWithLifecycle()
    val cashState by cashViewModel.uiState.collectAsStateWithLifecycle()
    val reconciliationState by reconciliationViewModel.uiState.collectAsStateWithLifecycle()
    val expenseState by expenseViewModel.uiState.collectAsStateWithLifecycle()
    val approvalState by approvalViewModel.uiState.collectAsStateWithLifecycle()
    val documentState by documentViewModel.uiState.collectAsStateWithLifecycle()
    val administrationState by administrationViewModel.uiState.collectAsStateWithLifecycle()
    val managementReportState by managementReportViewModel.uiState.collectAsStateWithLifecycle()
    val latestSaleState = rememberUpdatedState(saleState)
    val backStack = remember { mutableStateListOf<Any>(HomeRoute) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentRoute = backStack.lastOrNull() ?: HomeRoute
    val showBottomBar = currentRoute in TopLevelRoutes
    val destinations = Destinations.filter { destination ->
        destination.permission == null || session.user.hasPermission(destination.permission)
    }

    fun message(text: String) {
        scope.launch { snackbarHostState.showSnackbar(text) }
    }

    fun selectTopLevel(route: Any) {
        if (route == SellRoute && session.register == null) {
            message("Open a register before starting a sale")
            onOpenRegister()
            return
        }
        if (backStack.lastOrNull() == route) return
        backStack.clear()
        backStack.add(route)
    }

    LaunchedEffect(session.user.id, session.branch.id, session.register?.id) { saleViewModel.bindSession(session) }
    LaunchedEffect(session.user.id, session.branch.id, session.register?.id) { inventoryViewModel.bindSession(session) }
    LaunchedEffect(session.user.id, session.branch.id, session.register?.id) { activityViewModel.bindSession(session) }
    LaunchedEffect(session.user.id, session.branch.id, session.register?.id) { customerViewModel.bindSession(session) }
    LaunchedEffect(session.user.id, session.branch.id, session.register?.id) { giftVoucherViewModel.bindSession(session) }
    LaunchedEffect(session.user.id, session.branch.id, session.register?.id) { cashViewModel.bindSession(session) }
    LaunchedEffect(session.user.id, session.branch.id) { reconciliationViewModel.bindSession(session) }
    LaunchedEffect(session.user.id, session.branch.id) { expenseViewModel.bindSession(session) }
    LaunchedEffect(session.user.id, session.branch.id) {
        if (session.user.hasPermission("sales.approve")) approvalViewModel.bindSession(session)
    }
    LaunchedEffect(session.user.id, session.branch.id) {
        if (session.user.hasPermission("documents.view")) documentViewModel.bindSession(session)
    }
    LaunchedEffect(session.user.id, session.branch.id) { administrationViewModel.bindSession(session) }
    LaunchedEffect(session.user.id, session.branch.id) { managementReportViewModel.bindSession(session) }
    LaunchedEffect(currentRoute) {
        if (currentRoute == HomeRoute) {
            inventoryViewModel.refresh()
            managementReportViewModel.refresh()
            if (session.user.hasPermission("sales.approve")) approvalViewModel.refresh()
        }
    }
    LaunchedEffect(saleState.message) {
        saleState.message?.let { message(it); saleViewModel.consumeMessage() }
    }
    LaunchedEffect(saleState.error) {
        saleState.error?.let { message(it); saleViewModel.clearError() }
    }
    LaunchedEffect(saleState.receipt?.saleId) {
        if (saleState.receipt != null && backStack.lastOrNull() != ReceiptRoute) {
            backStack.add(ReceiptRoute)
        }
    }
    LaunchedEffect(inventoryState.message) {
        inventoryState.message?.let { message(it); inventoryViewModel.consumeMessage() }
    }
    LaunchedEffect(inventoryState.error) {
        inventoryState.error?.let { message(it); inventoryViewModel.clearError() }
    }
    LaunchedEffect(activityState.message) {
        activityState.message?.let { message(it); activityViewModel.consumeMessage() }
    }
    LaunchedEffect(activityState.error) {
        activityState.error?.let { message(it); activityViewModel.clearError() }
    }
    LaunchedEffect(customerState.message) {
        customerState.message?.let { message(it); customerViewModel.consumeMessage() }
    }
    LaunchedEffect(customerState.error) {
        customerState.error?.let { message(it); customerViewModel.clearError() }
    }
    LaunchedEffect(voucherState.message) {
        voucherState.message?.let { message(it); giftVoucherViewModel.consumeMessage() }
    }
    LaunchedEffect(voucherState.error) {
        voucherState.error?.let { message(it); giftVoucherViewModel.clearError() }
    }
    LaunchedEffect(cashState.message) {
        cashState.message?.let { message(it); cashViewModel.consumeMessage() }
    }
    LaunchedEffect(cashState.error) {
        cashState.error?.let { message(it); cashViewModel.clearError() }
    }
    LaunchedEffect(reconciliationState.message) {
        reconciliationState.message?.let { message(it); reconciliationViewModel.consumeMessage() }
    }
    LaunchedEffect(reconciliationState.error) {
        reconciliationState.error?.let { message(it); reconciliationViewModel.clearError() }
    }
    LaunchedEffect(expenseState.message) {
        expenseState.message?.let { message(it); expenseViewModel.consumeMessage() }
    }
    LaunchedEffect(expenseState.error) {
        expenseState.error?.let { message(it); expenseViewModel.clearError() }
    }
    LaunchedEffect(approvalState.message) {
        approvalState.message?.let { message(it); approvalViewModel.consumeMessage() }
    }
    LaunchedEffect(approvalState.error) {
        approvalState.error?.let { message(it); approvalViewModel.clearError() }
    }
    LaunchedEffect(documentState.message) { documentState.message?.let { message(it); documentViewModel.consumeMessage() } }
    LaunchedEffect(documentState.error) { documentState.error?.let { message(it); documentViewModel.clearError() } }
    LaunchedEffect(administrationState.error) { administrationState.error?.let { message(it); administrationViewModel.clearError() } }
    LaunchedEffect(managementReportState.error) { managementReportState.error?.let { message(it); managementReportViewModel.clearError() } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { selectTopLevel(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            onBack = {
                if (backStack.size > 1) backStack.removeLastOrNull() else activity?.finish()
            },
            entryProvider = { key ->
                when (key) {
                    HomeRoute -> NavEntry(key) {
                        HomeScreen(
                            session = session,
                            onNewSale = { selectTopLevel(SellRoute) },
                            onStock = { selectTopLevel(InventoryRoute) },
                            onActivity = { selectTopLevel(ActivityRoute) },
                            onMore = { selectTopLevel(MoreRoute) },
                            onProducts = { backStack.add(ProductCatalogRoute) },
                            onCash = { backStack.add(CashRoute) },
                            onApprovals = { backStack.add(ApprovalsRoute) },
                            sales = managementReportState.workspace.sales,
                            reportLoading = managementReportState.loading,
                            lowStockCount = inventoryState.summary?.lowStockCount
                                ?: inventoryState.products.count { it.stock <= it.reorderLevel },
                            approvalCount = approvalState.requests.size.takeIf {
                                session.user.hasPermission("sales.approve")
                            },
                        )
                    }
                    ActivityRoute -> NavEntry(key) {
                        ActivityScreen(
                            state = activityState,
                            onQueryChange = activityViewModel::setQuery,
                            onFilterChange = activityViewModel::setFilter,
                            onRefresh = activityViewModel::refresh,
                            onOpenSale = activityViewModel::openSale,
                            onCloseSale = activityViewModel::closeSale,
                            onPrint = { receipt -> activity?.printReceipt(receipt) },
                            onEmail = activityViewModel::emailReceipt,
                            onReturn = { backStack.add(ReturnSaleRoute) },
                            onExchange = { backStack.add(ExchangeSaleRoute) },
                        )
                    }
                    SellRoute -> NavEntry(key) {
                        val currentSale = latestSaleState.value
                        SaleCatalogScreen(
                            products = currentSale.products,
                            categories = currentSale.categories,
                            query = currentSale.query,
                            selectedCategoryId = currentSale.categoryId,
                            freshness = currentSale.freshness,
                            cartItemCount = currentSale.cart.sumOf { it.quantity },
                            cartTotal = currentSale.cart.sumOf { it.lineTotal } - currentSale.promotions.sumOf { it.savings },
                            onQueryChange = saleViewModel::setQuery,
                            onCategoryChange = saleViewModel::setCategory,
                            onScan = {
                                activity?.scanBarcode(saleViewModel::scan, saleViewModel::scannerFailed)
                                    ?: saleViewModel.scannerFailed("Barcode scanner is unavailable.")
                            },
                            onRefresh = saleViewModel::refreshCatalog,
                            onAddProduct = saleViewModel::addProduct,
                            onOpenCart = { backStack.add(CartRoute) },
                        )
                    }
                    InventoryRoute -> NavEntry(key) {
                        InventoryScreen(
                            branchName = session.branch.name,
                            state = inventoryState,
                            onReceiveStock = { backStack.add(ReceiveStockRoute) },
                            onTransferStock = { backStack.add(TransferStockRoute) },
                            onManagePrices = { backStack.add(PriceManagementRoute) },
                            onPrintLabels = { backStack.add(LabelPrintRoute) },
                            onRefresh = inventoryViewModel::refresh,
                            onScan = { onResult ->
                                activity?.scanBarcode(onResult, ::message)
                                    ?: message("Barcode scanner is unavailable")
                            },
                            onAdjust = inventoryViewModel::adjust,
                            onLoadGrn = inventoryViewModel::loadGrn,
                            onMessage = ::message,
                        )
                    }
                    MoreRoute -> NavEntry(key) {
                        MoreScreen(
                            session = session,
                            onChangeBranch = onChangeBranch,
                            onLogout = onLogout,
                            onMessage = ::message,
                            onCustomers = { backStack.add(CustomersRoute) },
                            onGiftVouchers = { backStack.add(GiftVouchersRoute) },
                            onCash = { backStack.add(CashRoute) },
                            onReconciliation = { backStack.add(ReconciliationRoute) },
                            onExpenses = { backStack.add(ExpensesRoute) },
                            onApprovals = { backStack.add(ApprovalsRoute) },
                            onDocuments = { backStack.add(DocumentsRoute) },
                            onAdministration = { backStack.add(AdministrationRoute) },
                            onReports = { backStack.add(ManagementReportsRoute) },
                            onProducts = { backStack.add(ProductCatalogRoute) },
                        )
                    }
                    CustomersRoute -> NavEntry(key) {
                        CustomerScreen(
                            state = customerState,
                            onBack = {
                                customerViewModel.close()
                                backStack.removeLastOrNull()
                            },
                            onQuery = customerViewModel::setQuery,
                            onView = customerViewModel::setView,
                            onRefresh = customerViewModel::refresh,
                            onOpen = customerViewModel::open,
                            onClose = customerViewModel::close,
                            onRefreshDetail = customerViewModel::refreshDetail,
                            onAddNote = customerViewModel::addNote,
                        )
                    }
                    GiftVouchersRoute -> NavEntry(key) {
                        GiftVoucherScreen(
                            state = voucherState,
                            onBack = {
                                giftVoucherViewModel.closeDetail()
                                backStack.removeLastOrNull()
                            },
                            onQuery = giftVoucherViewModel::setQuery,
                            onStatus = giftVoucherViewModel::setStatus,
                            onRefresh = giftVoucherViewModel::refresh,
                            onOpen = giftVoucherViewModel::open,
                            onCloseDetail = giftVoucherViewModel::closeDetail,
                            onRefreshDetail = giftVoucherViewModel::refreshDetail,
                            onScan = {
                                activity?.scanBarcode(giftVoucherViewModel::verify, ::message)
                                    ?: message("Barcode scanner is unavailable")
                            },
                            onVerify = giftVoucherViewModel::verify,
                            onCreate = giftVoucherViewModel::create,
                            onActivate = giftVoucherViewModel::activate,
                            onRedeem = giftVoucherViewModel::redeem,
                            onCancel = giftVoucherViewModel::cancel,
                        )
                    }
                    CashRoute -> NavEntry(key) {
                        CashScreen(
                            state = cashState,
                            onBack = { backStack.removeLastOrNull() },
                            onRefresh = cashViewModel::refresh,
                            onOpenRegister = onOpenRegister,
                            onCloseDrawer = cashViewModel::close,
                            onHandover = cashViewModel::handover,
                            onRecordMovement = cashViewModel::recordMovement,
                            onFinishClose = {
                                cashViewModel.consumeCloseResult()
                                onRegisterClosed()
                            },
                        )
                    }
                    ReconciliationRoute -> NavEntry(key) {
                        ReconciliationScreen(
                            state = reconciliationState,
                            onBack = { backStack.removeLastOrNull() },
                            onPreviousDate = reconciliationViewModel::previousDate,
                            onNextDate = reconciliationViewModel::nextDate,
                            onToday = reconciliationViewModel::today,
                            onRefresh = reconciliationViewModel::refresh,
                            onOpen = reconciliationViewModel::open,
                            onUpdateChannel = reconciliationViewModel::updateChannel,
                            onSignOff = reconciliationViewModel::signOff,
                            onCloseDay = reconciliationViewModel::closeDay,
                        )
                    }
                    ExpensesRoute -> NavEntry(key) {
                        ExpenseScreen(
                            state = expenseState,
                            onBack = { backStack.removeLastOrNull() },
                            onPeriod = expenseViewModel::setPeriod,
                            onCategory = expenseViewModel::setCategory,
                            onRefresh = expenseViewModel::refresh,
                            onSave = expenseViewModel::save,
                            onDelete = expenseViewModel::delete,
                        )
                    }
                    ApprovalsRoute -> NavEntry(key) {
                        ApprovalScreen(
                            state = approvalState,
                            onBack = { backStack.removeLastOrNull() },
                            onRefresh = approvalViewModel::refresh,
                            onDecision = approvalViewModel::decide,
                            onVerified = approvalViewModel::acknowledgeVerified,
                        )
                    }
                    DocumentsRoute -> NavEntry(key) {
                        DocumentScreen(documentState, { documentViewModel.close(); backStack.removeLastOrNull() }, documentViewModel::setType, documentViewModel::setStatus, documentViewModel::setQuery, documentViewModel::search, documentViewModel::refresh, documentViewModel::open, documentViewModel::close, documentViewModel::save, documentViewModel::transition, documentViewModel::void, documentViewModel::convert, documentViewModel::email)
                    }
                    AdministrationRoute -> NavEntry(key) { AdministrationScreen(administrationState, { backStack.removeLastOrNull() }, administrationViewModel::refresh) }
                    ManagementReportsRoute -> NavEntry(key) { ManagementReportScreen(managementReportState, { backStack.removeLastOrNull() }, managementReportViewModel::setPeriod, managementReportViewModel::refresh) }
                    ProductCatalogRoute -> NavEntry(key) {
                        ProductCatalogScreen(
                            state = inventoryState,
                            onBack = { backStack.removeLastOrNull() },
                            onLoad = inventoryViewModel::loadPricing,
                            onRefresh = inventoryViewModel::refresh,
                            onManagePrices = { backStack.add(PriceManagementRoute) },
                            onPrintLabels = { backStack.add(LabelPrintRoute) },
                        )
                    }
                    CartRoute -> NavEntry(key) {
                        val currentSale = latestSaleState.value
                        PersistentCartScreen(
                            lines = currentSale.cart,
                            customer = currentSale.customer,
                            customerResults = currentSale.customerResults,
                            promotions = currentSale.promotions,
                            discountOptions = currentSale.discountOptions,
                            appliedDiscount = currentSale.appliedDiscount,
                            heldCarts = currentSale.heldCarts,
                            working = currentSale.working,
                            onBack = { backStack.removeLastOrNull() },
                            onIncrease = saleViewModel::increase,
                            onDecrease = saleViewModel::decrease,
                            onSearchCustomers = saleViewModel::searchCustomers,
                            onAttachCustomer = saleViewModel::attachCustomer,
                            onApplyDiscount = saleViewModel::applyDiscount,
                            onRemoveDiscount = saleViewModel::removeDiscount,
                            onHold = saleViewModel::holdSale,
                            onRecall = saleViewModel::recallHeld,
                            onDeleteHeld = saleViewModel::removeHeld,
                            onPayment = { if (latestSaleState.value.cart.isNotEmpty()) backStack.add(PaymentRoute) },
                        )
                    }
                    PaymentRoute -> NavEntry(key) {
                        val currentSale = latestSaleState.value
                        CheckoutPaymentScreen(
                            quote = currentSale.checkoutQuote,
                            lines = currentSale.cart,
                            customer = currentSale.customer,
                            working = currentSale.checkoutWorking,
                            attempt = currentSale.checkoutAttempt,
                            onBack = { backStack.removeLastOrNull() },
                            onPrepare = saleViewModel::prepareCheckout,
                            onSubmit = saleViewModel::submitCheckout,
                            onAcknowledgeUncertain = saleViewModel::acknowledgeUncertainCheckout,
                        )
                    }
                    ReceiptRoute -> NavEntry(key) {
                        val currentSale = latestSaleState.value
                        currentSale.receipt?.let { receipt ->
                            ConfirmedReceiptScreen(
                                receipt = receipt,
                                working = currentSale.working,
                                onPrint = { activity?.printReceipt(receipt) },
                                onEmail = saleViewModel::emailReceipt,
                                onNewSale = {
                                    saleViewModel.startNewSale()
                                    selectTopLevel(SellRoute)
                                },
                            )
                        }
                    }
                    ReturnSaleRoute -> NavEntry(key) {
                        activityState.selectedSale?.let { sale ->
                            ReturnWorkflowScreen(
                                sale = sale,
                                state = activityState,
                                onBack = { activityViewModel.clearAftercare(); backStack.removeLastOrNull() },
                                onLoad = activityViewModel::loadReturnableItems,
                                onSubmit = activityViewModel::submitReturn,
                                onComplete = {
                                    backStack.removeLastOrNull()
                                    activityViewModel.finishAftercare()
                                },
                            )
                        }
                    }
                    ExchangeSaleRoute -> NavEntry(key) {
                        activityState.selectedSale?.let { sale ->
                            ExchangeWorkflowScreen(
                                sale = sale,
                                products = inventoryState.products,
                                state = activityState,
                                onBack = { activityViewModel.clearAftercare(); backStack.removeLastOrNull() },
                                onLoad = activityViewModel::loadReturnableItems,
                                onPreview = activityViewModel::previewExchange,
                                onSubmit = activityViewModel::submitExchange,
                                onComplete = {
                                    backStack.removeLastOrNull()
                                    activityViewModel.finishAftercare()
                                },
                            )
                        }
                    }
                    ReceiveStockRoute -> NavEntry(key) {
                        ReceiveStockScreen(
                            products = inventoryState.products,
                            suppliers = inventoryState.suppliers,
                            draft = inventoryState.receiveDraft,
                            working = inventoryState.working,
                            receiveResult = inventoryState.receiveResult,
                            onBack = { backStack.removeLastOrNull() },
                            onSubmit = inventoryViewModel::receive,
                            onSaveDraft = inventoryViewModel::saveReceiveDraft,
                            onDeleteDraft = inventoryViewModel::deleteReceiveDraft,
                            onComplete = {
                                inventoryViewModel.consumeReceiveResult()
                                backStack.removeLastOrNull()
                            },
                            onScan = { onResult ->
                                activity?.scanBarcode(onResult, ::message)
                                    ?: message("Barcode scanner is unavailable")
                            },
                            onMessage = ::message,
                        )
                    }
                    TransferStockRoute -> NavEntry(key) {
                        TransferStockScreen(
                            currentBranchId = session.branch.id,
                            products = inventoryState.products,
                            destinations = inventoryState.destinationBranches,
                            transfers = inventoryState.transfers,
                            working = inventoryState.working,
                            onBack = { backStack.removeLastOrNull() },
                            onCreate = inventoryViewModel::createTransfer,
                            onTransition = inventoryViewModel::transitionTransfer,
                        )
                    }
                    PriceManagementRoute -> NavEntry(key) {
                        PriceManagementScreen(
                            state = inventoryState,
                            onBack = { backStack.removeLastOrNull() },
                            onLoad = { inventoryViewModel.loadPricing() },
                            onUpdate = inventoryViewModel::updatePrice,
                        )
                    }
                    LabelPrintRoute -> NavEntry(key) {
                        LabelPrintScreen(
                            products = inventoryState.products,
                            onBack = { backStack.removeLastOrNull() },
                            onPrint = { items ->
                                if (activity == null) {
                                    message("Android printing is unavailable")
                                } else {
                                    activity.printLabels(items) { inventoryViewModel.recordLabelPrint(items) }
                                }
                            },
                        )
                    }
                    else -> error("Unknown navigation key: $key")
                }
            },
        )
    }
}
