package com.kline.inventorypos.app

import android.content.Context
import com.google.gson.Gson
import com.kline.inventorypos.BuildConfig
import com.kline.inventorypos.data.network.InventoryPosApi
import com.kline.inventorypos.data.catalog.CatalogRepository
import com.kline.inventorypos.data.catalog.DefaultCatalogRepository
import com.kline.inventorypos.data.local.InventoryPosDatabase
import com.kline.inventorypos.data.inventory.DefaultInventoryRepository
import com.kline.inventorypos.data.inventory.InventoryRepository
import com.kline.inventorypos.data.checkout.CheckoutRepository
import com.kline.inventorypos.data.checkout.DefaultCheckoutRepository
import com.kline.inventorypos.data.activity.ActivityRepository
import com.kline.inventorypos.data.activity.DefaultActivityRepository
import com.kline.inventorypos.data.customer.CustomerRepository
import com.kline.inventorypos.data.customer.DefaultCustomerRepository
import com.kline.inventorypos.data.sale.DefaultSaleRepository
import com.kline.inventorypos.data.sale.SaleRepository
import com.kline.inventorypos.data.session.DefaultSessionRepository
import com.kline.inventorypos.data.session.KeystoreSessionStore
import com.kline.inventorypos.data.session.SessionHeaders
import com.kline.inventorypos.data.session.SessionRepository
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(context: Context) {
    private val gson = Gson()
    private val headers = SessionHeaders()
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().apply {
                headers.token?.takeUnless { it == "inventory-pos-local-demo" }?.let {
                    header("Authorization", "Bearer $it")
                }
                headers.branchId?.let { header("X-Branch-Id", it) }
            }.build()
            chain.proceed(request)
        }
        .build()

    private val api = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL.ensureTrailingSlash())
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(InventoryPosApi::class.java)

    val sessionRepository: SessionRepository = DefaultSessionRepository(
        api = api,
        store = KeystoreSessionStore(context.applicationContext),
        headers = headers,
        gson = gson,
    )

    private val dao = InventoryPosDatabase.create(context).dao()

    val catalogRepository: CatalogRepository = DefaultCatalogRepository(
        dao = dao,
        api = api,
        gson = gson,
        isDemo = sessionRepository::isDemo,
    )

    val saleRepository: SaleRepository = DefaultSaleRepository(
        dao = dao,
        api = api,
        gson = gson,
        isDemo = sessionRepository::isDemo,
    )

    val checkoutRepository: CheckoutRepository = DefaultCheckoutRepository(
        dao = dao,
        api = api,
        saleRepository = saleRepository,
        gson = gson,
        isDemo = sessionRepository::isDemo,
    )

    val inventoryRepository: InventoryRepository = DefaultInventoryRepository(
        api = api,
        gson = gson,
        isDemo = sessionRepository::isDemo,
    )

    val activityRepository: ActivityRepository = DefaultActivityRepository(
        api = api,
        gson = gson,
        isDemo = sessionRepository::isDemo,
    )

    val customerRepository: CustomerRepository = DefaultCustomerRepository(
        api = api,
        gson = gson,
        isDemo = sessionRepository::isDemo,
    )
}

private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"
