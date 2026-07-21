package com.kline.inventorypos.core.model

import com.kline.inventorypos.core.session.PosBranch

data class StoreAdministration(val name: String, val address: String, val phone: String?, val email: String?, val currency: String, val taxEnabled: Boolean, val taxLabel: String, val taxRegistration: String?, val returnWindowDays: Int, val printerBridgeUrl: String?, val receiptAction: String?)
data class StaffMember(val id: String, val name: String, val employeeId: String?, val phone: String?, val email: String?, val position: String?)
data class AdministrationWorkspace(val store: StoreAdministration?, val branches: List<PosBranch>, val staff: List<StaffMember>)
