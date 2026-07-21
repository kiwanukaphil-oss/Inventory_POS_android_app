package com.kline.inventorypos.app

data object HomeRoute
data object ActivityRoute
data object SellRoute
data object InventoryRoute
data object MoreRoute

data object CartRoute
data object PaymentRoute
data object ReceiptRoute
data object ReturnSaleRoute
data object ExchangeSaleRoute
data object CustomersRoute
data object GiftVouchersRoute
data object ReceiveStockRoute
data object TransferStockRoute
data object PriceManagementRoute
data object LabelPrintRoute

val TopLevelRoutes = setOf(HomeRoute, ActivityRoute, SellRoute, InventoryRoute, MoreRoute)
