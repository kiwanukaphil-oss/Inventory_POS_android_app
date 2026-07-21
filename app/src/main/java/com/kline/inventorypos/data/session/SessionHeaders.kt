package com.kline.inventorypos.data.session

class SessionHeaders {
    @Volatile var token: String? = null
    @Volatile var branchId: String? = null
}
