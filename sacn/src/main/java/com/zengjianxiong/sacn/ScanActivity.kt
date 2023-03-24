package com.zengjianxiong.sacn

import android.content.Intent
import android.os.Bundle
import android.util.Size
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.interfaces.Detector


class ScanActivity : BaseCameraScanActivity<List<Barcode>>() {
    private lateinit var barcodeScanner: BarcodeScanner
    override fun initConfig() {
        setPlayBeep(true)
        setVibrate(true)
    }

    override fun initDetector(): Detector<List<Barcode>> {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
            )
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)
        return barcodeScanner
    }

    override fun setScanResultCallback(result: MlKitAnalyzer.Result?) {
        val barcodeResults = result?.getValue(barcodeScanner)
        setAnalyzeImage(false)
        overlayClick(barcodeResults!![0].displayValue)
    }

    private fun overlayClick(it: String?) {
        val intent = Intent()
        val bundle = Bundle()
        bundle.putString(CODE_RESULT, it ?: "")
        intent.putExtras(bundle)
        setResult(RESULT_OK, intent);
        finish()
    }
}