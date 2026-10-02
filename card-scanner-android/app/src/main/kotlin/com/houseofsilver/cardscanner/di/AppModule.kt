package com.houseofsilver.cardscanner.di

import android.content.Context
import com.houseofsilver.cardscanner.data.local.ScanDatabase
import com.houseofsilver.cardscanner.data.prefs.OwnCardPrefs
import com.houseofsilver.cardscanner.data.prefs.TemplatePrefs
import com.houseofsilver.cardscanner.ocr.OcrProcessor
import com.houseofsilver.cardscanner.qr.BarcodeReader

/**
 * Manual DI container — singletons held here, accessed via [CardScannerApp.appModule].
 */
class AppModule(context: Context) {

    val templatePrefs: TemplatePrefs by lazy { TemplatePrefs(context) }

    val ownCardPrefs: OwnCardPrefs by lazy { OwnCardPrefs(context) }

    val database: ScanDatabase by lazy { ScanDatabase.getInstance(context) }

    val scanDao by lazy { database.scanDao() }

    val ocrProcessor: OcrProcessor by lazy { OcrProcessor() }

    val barcodeReader: BarcodeReader by lazy { BarcodeReader() }
}
