package com.zengjianxiong.sacn

import android.content.Intent
import android.os.Bundle
import android.util.Size
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.interfaces.Detector
import java.util.concurrent.*


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
        setAnalyzeImage(false)
        val barcodeResults = result?.getValue(barcodeScanner)
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

    override fun initCameraExecutor(): ExecutorService {
        val queue = LinkedBlockingQueue<Runnable>(10)
        return ThreadPoolExecutor(
            5, 10, 1,
            TimeUnit.MINUTES, queue, ThreadPoolExecutor.DiscardPolicy()
        )
    }
}