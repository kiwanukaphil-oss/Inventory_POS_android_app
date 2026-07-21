package com.kline.inventorypos

import android.app.Application
import com.kline.inventorypos.app.AppContainer

class InventoryPosApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
