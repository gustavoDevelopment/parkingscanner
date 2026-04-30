package com.parkingscanner

import android.app.Application
import com.parkingscanner.data.repository.CatalogStorage
import kotlin.concurrent.thread

class ParkingScannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        thread { CatalogStorage(this).load() }
    }
}
