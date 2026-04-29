package com.parkingscanner

import android.app.Application
import com.parkingscanner.data.repository.CatalogStorage

class ParkingScannerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CatalogStorage(this).load()
    }
}
