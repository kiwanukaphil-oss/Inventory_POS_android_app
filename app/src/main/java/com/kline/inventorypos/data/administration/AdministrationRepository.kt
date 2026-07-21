package com.kline.inventorypos.data.administration

import com.google.gson.Gson
import com.kline.inventorypos.core.model.*
import com.kline.inventorypos.core.session.PosBranch
import com.kline.inventorypos.data.network.*
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

interface AdministrationRepository { suspend fun workspace(canStore: Boolean, canBranches: Boolean, canUsers: Boolean): AdministrationWorkspace }
class DefaultAdministrationRepository(private val api: InventoryPosApi, private val gson: Gson, private val isDemo: () -> Boolean) : AdministrationRepository {
    override suspend fun workspace(canStore: Boolean, canBranches: Boolean, canUsers: Boolean): AdministrationWorkspace {
        if (isDemo()) return AdministrationWorkspace(demoStore().takeIf { canStore }, if(canBranches) demoBranches() else emptyList(), if(canUsers) demoStaff() else emptyList())
        return coroutineScope {
            val store = async { if(canStore) read { api.storeConfig().data }.domain() else null }
            val branches = async { if(canBranches) read { api.branches().data }.map { it.toDomain() } else emptyList() }
            val staff = async { if(canUsers) read { api.activeStaff().data }.map { StaffMember(it.id,it.fullName,it.employeeId,it.phone,it.email,it.position) } else emptyList() }
            AdministrationWorkspace(store.await(),branches.await(),staff.await())
        }
    }
    private suspend fun <T> read(block:suspend()->T):T=try{block()}catch(e:HttpException){val m=e.response()?.errorBody()?.string()?.let{runCatching{gson.fromJson(it,ApiError::class.java).message}.getOrNull()};throw IllegalStateException(m?:"Administration data is unavailable.",e)}catch(e:IOException){throw IllegalStateException("Administration data is unavailable while offline.",e)}
}
private fun StoreConfigDto.domain()=StoreAdministration(storeName?:"Inventory POS",listOfNotNull(addressLine1,city,country).joinToString(", "),phone,email,currencyCode?:"UGX",taxEnabled==true,taxLabel?:"Tax",taxRegistrationNumber,returnWindowDays?:0,printerBridgeUrl,defaultReceiptAction)
private fun demoStore()=StoreAdministration("K-Line Men","Kampala, Uganda","+256 700 000000","sales@kline.example","UGX",true,"VAT","TIN-100042",14,"http://127.0.0.1:9100","print")
private fun demoBranches()=listOf(PosBranch("main","MAIN","Main Branch","active","Kampala",true,true,true),PosBranch("acacia","ACA","Acacia Mall","active","Kampala",false,false,true))
private fun demoStaff()=listOf(StaffMember("u2","Sarah Namusoke","EMP-002","+256 701 000002","sarah@example.com","Cashier"),StaffMember("u3","Grace Akello","EMP-003","+256 701 000003","grace@example.com","Supervisor"))
