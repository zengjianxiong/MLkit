package com.zengjianxiong.sacn

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.OnScaleGestureListener
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import androidx.annotation.FloatRange
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.interfaces.Detector
import com.zengjianxiong.sacn.manager.AmbientLightManager
import com.zengjianxiong.sacn.manager.BeepManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

abstract class BaseCameraScanActivity<T> : AppCompatActivity() {


    open var beepManager: BeepManager? = null
    private var ambientLightManager: AmbientLightManager? = null
    private var flashlightView: View? = null
    private var mLastHoveTapTime: Long = 0
    private var isClickTap = false
    private var mDownX = 0f
    private var mDownY = 0f

    private var analyzer: ImageAnalysis.Analyzer? = null

    private val cameraController by lazy { LifecycleCameraController(baseContext) }

    private lateinit var detector: Detector<T>
    lateinit var previewView: PreviewView


    /**
     * 缩放手势检测
     */
    private val mOnScaleGestureListener: OnScaleGestureListener =
        object : SimpleOnScaleGestureListener() {
            @SuppressLint("RestrictedApi")
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                val ratio = previewView.controller?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1.0f
                zoomTo(ratio * scale)
                return true
            }
        }


    /**
     * 是否需要支持触摸缩放
     */
    open var isNeedTouchZoom = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isContentView()) {
            setContentView(layoutId())
        }
        initUI()
    }

    /**
     * 设置是否分析图像，通过此方法可以动态控制是否分析图像，常用于中断扫码识别。如：连扫时，扫到结果，然后停止分析图像
     * @param analyze Boolean
     */
    open fun setAnalyzeImage(b: Boolean) {
        if (!b) {
            previewView.controller?.clearImageAnalysisAnalyzer()
        } else {
            analyzer?.let {
                cameraController.setImageAnalysisAnalyzer(
                    ContextCompat.getMainExecutor(this),
                    it
                )
            }

        }
    }

    /**
     * 初始化
     */
    private fun initUI() {
        previewView = findViewById(previewViewId())
        beepManager = BeepManager(this)
        ambientLightManager = AmbientLightManager(this)


        initConfig()
        registerListener()
        detector = initDetector()
        initAnalyzer()
        if (allPermissionsGranted()) {
            bindCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }


    private fun registerListener() {
        val scaleGestureDetector = ScaleGestureDetector(this, mOnScaleGestureListener)
        previewView.setOnTouchListener { _, event ->
            handlePreviewViewClickTap(event)
            if (isNeedTouchZoom) {
                return@setOnTouchListener scaleGestureDetector.onTouchEvent(event)
            }
            return@setOnTouchListener false
        }


        ambientLightManager?.apply {
            register()
            setOnLightSensorEventListener { dark: Boolean, lightLux: Float ->
                if (flashlightView != null) {
                    if (dark) {
                        if (flashlightView!!.visibility != View.VISIBLE) {
                            flashlightView!!.visibility = View.VISIBLE
                            flashlightView!!.isSelected = isTorchEnabled()
                        }
                    } else if (flashlightView!!.visibility == View.VISIBLE && !isTorchEnabled()) {
                        flashlightView!!.visibility = View.INVISIBLE
                        flashlightView!!.isSelected = false
                    }
                }
            }
        }
    }


    /**
     * 放大
     */
    open fun zoomIn() {
        val ratio =
            (previewView.controller?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f) + ZOOM_STEP_SIZE
        val maxRatio: Float =
            previewView.controller?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f
        if (ratio <= maxRatio) {
            previewView.controller?.setZoomRatio(ratio)
        }
    }

    /**
     * 缩小
     */
    open fun zoomOut() {
        val ratio =
            (previewView.controller?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f) - ZOOM_STEP_SIZE
        val minRatio: Float =
            previewView.controller?.cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f
        if (ratio >= minRatio) {
            previewView.controller?.setZoomRatio(ratio)
        }
    }

    /**
     * 缩放到指定比例
     *
     * @param ratio
     */
    private fun zoomTo(ratio: Float) {
        val zoomState = previewView.controller?.cameraInfo?.zoomState?.value
        val maxRatio = zoomState?.maxZoomRatio ?: 1f
        val minRatio = zoomState?.minZoomRatio ?: 1f
        val zoom = max(min(ratio, maxRatio), minRatio)
        previewView.controller?.cameraControl?.setZoomRatio(zoom)
    }

    /**
     * 线性放大
     */
    open fun lineZoomIn() {
        val zoom = (previewView.controller?.cameraInfo?.zoomState?.value?.linearZoom
            ?: 0f) + ZOOM_STEP_SIZE
        if (zoom <= 1f) {
            previewView.controller?.setLinearZoom(zoom)
        }
    }

    /**
     * 线性缩小
     */
    open fun lineZoomOut() {
        val zoom = (previewView.controller?.cameraInfo?.zoomState?.value?.linearZoom
            ?: 0f) - ZOOM_STEP_SIZE
        if (zoom >= 0f) {
            previewView.controller?.setLinearZoom(zoom)
        }
    }

    /**
     * 线性缩放到指定比例
     *
     * @param linearZoom
     */
    open fun lineZoomTo(@FloatRange(from = 0.0, to = 1.0) linearZoom: Float) {
        previewView.controller?.setLinearZoom(linearZoom)
    }

    /**
     * 设置闪光灯（手电筒）是否开启
     *
     * @param torch
     */
    open fun enableTorch(torch: Boolean) {
        previewView.controller?.cameraControl?.enableTorch(torch)
    }


    /**
     * 是否支持闪光灯
     *
     * @return
     */
    open fun hasFlashUnit(): Boolean {
        return previewView.controller?.cameraInfo?.hasFlashUnit()
            ?: packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }


    /**
     * 闪光灯（手电筒）是否开启
     *
     * @return
     */
    open fun isTorchEnabled(): Boolean {
        return previewView.controller?.torchState?.value == TorchState.ON
    }

    /**
     * 处理预览视图点击事件；如果触发的点击事件被判定对焦操作，则开始自动对焦
     *
     * @param event
     */
    private fun handlePreviewViewClickTap(event: MotionEvent) {
        if (event.pointerCount == 1) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isClickTap = true
                    mDownX = event.x
                    mDownY = event.y
                    mLastHoveTapTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_MOVE -> isClickTap =
                    distance(mDownX, mDownY, event.x, event.y) < HOVER_TAP_SLOP
                MotionEvent.ACTION_UP -> if (isClickTap && mLastHoveTapTime + HOVER_TAP_TIMEOUT > System.currentTimeMillis()) {
                    // 开始对焦和测光
                    startFocusAndMetering(event.x, event.y)
                }
            }
        }
    }

    /**
     * 计算两点的距离
     *
     * @param aX
     * @param aY
     * @param bX
     * @param bY
     * @return
     */
    private fun distance(aX: Float, aY: Float, bX: Float, bY: Float): Float {
        val xDiff = aX - bX
        val yDiff = aY - bY
        return sqrt((xDiff * xDiff + yDiff * yDiff).toDouble()).toFloat()
    }

    /**
     * 设置是否震动
     *
     * @param vibrate
     */
    open fun setVibrate(vibrate: Boolean) {

        beepManager?.setVibrate(vibrate)
    }

    /**
     * 设置是否播放提示音
     *
     * @param playBeep
     */
    open fun setPlayBeep(playBeep: Boolean) {
        beepManager?.setPlayBeep(playBeep)
    }

    /**
     * 开始对焦和测光
     *
     * @param x
     * @param y
     */
    private fun startFocusAndMetering(x: Float, y: Float) {
        val point: MeteringPoint = previewView.meteringPointFactory.createPoint(x, y)
        val focusMeteringAction = FocusMeteringAction.Builder(point).build()
        val supported =
            previewView.controller?.cameraInfo?.isFocusMeteringSupported(focusMeteringAction)
                ?: false
        if (supported) {
            previewView.controller?.cameraControl?.startFocusAndMetering(focusMeteringAction)
        }
    }

    /**
     * 绑定手电筒，绑定后可根据光线传感器，动态显示或隐藏手电筒
     *
     * @param v
     */
    open fun bindFlashlightView(v: View?) {
        flashlightView = v
        ambientLightManager?.isLightSensorEnabled = v != null

    }

    /**
     * 设置光线足够暗的阈值（单位：lux），需要通过{@link #bindFlashlightView(View)}绑定手电筒才有效
     *
     * @param lightLux
     */
    open fun setDarkLightLux(lightLux: Float) {
        ambientLightManager?.setDarkLightLux(lightLux)
    }

    /**
     * 设置光线足够明亮的阈值（单位：lux），需要通过{@link #bindFlashlightView(View)}绑定手电筒才有效
     *
     * @param lightLux
     */
    open fun setBrightLightLux(lightLux: Float) {
        ambientLightManager?.setBrightLightLux(lightLux)
    }

    private fun bindCamera() {
        analyzer?.let {
            cameraController.setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(this),
                it
            )
        }

        cameraController.bindToLifecycle(this)
        previewView.controller = cameraController
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }


    private fun initAnalyzer() {
        analyzer = MlKitAnalyzer(
            listOf(detector),
            CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
            ContextCompat.getMainExecutor(this)
        ) { result: MlKitAnalyzer.Result? ->
            if (detector is BarcodeScanner) {
                val barcodeResults = result?.getValue(detector as BarcodeScanner)
                if ((barcodeResults == null) ||
                    (barcodeResults.size == 0) ||
                    (barcodeResults.first() == null)
                ) {
                    return@MlKitAnalyzer
                }
                beepManager?.playBeepSoundAndVibrate()
                setScanResultCallback(result)
            } else {
                setScanResultCallback(result)
            }
        }
        (analyzer as MlKitAnalyzer).setTargetResolution(targetResolution())
    }


    /**
     * 返回true时会自动初始化[.setContentView]，返回为false是需自己去初始化[.setContentView]
     *
     * @return 默认返回true
     */
    open fun isContentView(): Boolean = true

    /**
     * 布局ID；通过覆写此方法可以自定义布局
     *
     * @return
     */
    open fun layoutId(): Int = R.layout.ml_camera_scan

    /**
     * 预览界面[.previewView]的ID
     *
     * @return
     */
    open fun previewViewId(): Int = R.id.previewView

    /**
     * 自定义分辨率
     * @return Size?
     */
    open fun targetResolution(): Size? = null

    /**
     * 配置操作
     */
    abstract fun initConfig()

    abstract fun initDetector(): Detector<T>

    abstract fun setScanResultCallback(result: MlKitAnalyzer.Result?)

    override fun onDestroy() {
        super.onDestroy()
        detector.close()
        cameraController.unbind()
    }


    companion object {
        const val CODE_RESULT = "code_result"
        private const val TAG = "CameraX-MLKit"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA
            ).toTypedArray()


        /**
         * Defines the maximum duration in milliseconds between a touch pad
         * touch and release for a given touch to be considered a tap (click) as
         * opposed to a hover movement gesture.
         */
        private const val HOVER_TAP_TIMEOUT = 150

        /**
         * Defines the maximum distance in pixels that a touch pad touch can move
         * before being released for it to be considered a tap (click) as opposed
         * to a hover movement gesture.
         */
        private const val HOVER_TAP_SLOP = 20

        /**
         * 每次缩放改变的步长
         */
        private const val ZOOM_STEP_SIZE = 0.1f
    }


}
