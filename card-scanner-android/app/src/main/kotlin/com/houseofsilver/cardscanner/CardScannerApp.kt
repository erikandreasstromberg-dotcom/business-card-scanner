package com.houseofsilver.cardscanner

import android.app.Application
import com.houseofsilver.cardscanner.di.AppModule

class CardScannerApp : Application() {

    lateinit var appModule: AppModule
        private set

    override fun onCreate() {
        super.onCreate()
        appModule = AppModule(this)
    }
}
