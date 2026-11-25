package com.bubble.screentools.capture

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.bubble.screentools.model.SelectionRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * In-memory screen capture manager.
 * - Primary on Android 11+ (API 30+): AccessibilityService.takeScreenshot() — ZERO "Start recording" dialog!
 * - Legacy fallback (Android 7–10 / API 24–29): MediaProjection in RAM.
 * - Never writes screenshots to gallery or persistent storage.
 * - Releases VirtualDisplay & ImageReader promptly when session ends.
 */
class ScreenCaptureManager(private val context: Context) {

    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var lastFrameHash: Long = 0L
    private val isCapturing = AtomicBoolean(false)

    val hasActiveGrant: Boolean
        get() = mediaProjection != null

    fun createConsentIntent(): Intent = projectionManager.createScreenCaptureIntent()

    fun attachGrant(resultCode: Int, data: Intent): Boolean {
        if (resultCode != Activity.RESULT_OK) return false
        releaseSession()
        val projection = runCatching {
            projectionManager.getMediaProjection(resultCode, data)
        }.getOrNull() ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    releaseSession()
                }
            }, Handler(Looper.getMainLooper()))
        }
        mediaProjection = projection
        return true
    }

    /**
     * Captures a full-screen snapshot directly via AccessibilityService.takeScreenshot() on Android 11+
     * with ZERO MediaProjection "Start recording" dialog and ZERO Foreground Service notification.
     * Caller owns the returned Bitmap and MUST call bitmap.recycle() when the overlay closes.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureFullScreenViaAccessibility(
        service: AccessibilityService
    ): FullScreenCaptureResult = suspendCancellableCoroutine { cont ->
        var resumed = false
        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        if (resumed) {
                            runCatching { screenshot.hardwareBuffer.close() }
                            return
                        }
                        resumed = true
                        val bitmap = runCatching {
                            val buffer = screenshot.hardwareBuffer
                            try {
                                val hwBitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                try {
                                    hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                                } finally {
                                    hwBitmap?.recycle()
                                }
                            } finally {
                                buffer.close()
                            }
                        }.getOrNull()

                        if (bitmap != null) {
                            cont.resume(FullScreenCaptureResult.Success(bitmap))
                        } else {
                            cont.resume(FullScreenCaptureResult.Failed("Gagal menyalin buffer tangkapan layar"))
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (resumed) return
                        resumed = true
                        when (errorCode) {
                            ERROR_SECURE_WINDOW ->
                                cont.resume(FullScreenCaptureResult.ProtectedContent)
                            AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
                                cont.resume(FullScreenCaptureResult.RateLimited)
                            else ->
                                cont.resume(
                                    FullScreenCaptureResult.Failed(
                                        "Gagal mengambil tangkapan layar (kode $errorCode)"
                                    )
                                )
                        }
                    }
                }
            )
        } catch (se: SecurityException) {
            if (!resumed) {
                resumed = true
                cont.resume(FullScreenCaptureResult.ProtectedContent)
            }
        } catch (t: Throwable) {
            if (!resumed) {
                resumed = true
                cont.resume(FullScreenCaptureResult.Failed(t.message ?: "Gagal mengambil layar"))
            }
        }
    }

    /**
     * Legacy fallback (Android 7–10): captures one fresh frame strictly into RAM via MediaProjection
     * and crops to the user's selected region with accurate scaleFactor.
     * Caller owns the returned Bitmap and MUST call bitmap.recycle() after OCR/Lens prep.
     */
    suspend fun captureCroppedRegion(
        region: SelectionRegion,
        maxDimensionPx: Int
    ): CaptureResult = withContext(Dispatchers.Default) {
        val projection = mediaProjection
            ?: return@withContext CaptureResult.PermissionRequired

        if (!isCapturing.compareAndSet(false, true)) {
            return@withContext CaptureResult.Busy
        }

        try {
            val (width, height, densityDpi) = getDisplayDimensions()

            ensureVirtualDisplay(projection, width, height, densityDpi)
            val fullBitmap = acquireLatestFrameBitmap(width, height)
                ?: return@withContext CaptureResult.Failed("Frame layar belum tersedia")

            try {
                val frameHash = computeQuickHash(fullBitmap)
                val unchanged = (frameHash == lastFrameHash)
                lastFrameHash = frameHash

                val cropped = BitmapCropHelper.cropAndMask(fullBitmap, region, maxDimensionPx)
                CaptureResult.Success(
                    cropped = cropped,
                    frameUnchanged = unchanged
                )
            } finally {
                if (!fullBitmap.isRecycled) {
                    fullBitmap.recycle()
                }
            }
        } catch (se: SecurityException) {
            CaptureResult.ProtectedContent
        } catch (t: Throwable) {
            CaptureResult.Failed(t.message ?: "Gagal membaca frame layar")
        } finally {
            isCapturing.set(false)
        }
    }

    private fun ensureVirtualDisplay(
        projection: MediaProjection,
        width: Int,
        height: Int,
        densityDpi: Int
    ) {
        val existingReader = imageReader
        if (existingReader != null && existingReader.width == width && existingReader.height == height && virtualDisplay != null) {
            return
        }
        releaseDisplayOnly()
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplay = projection.createVirtualDisplay(
            "BubbleScreenCapture",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            null
        )
    }

    private suspend fun acquireLatestFrameBitmap(width: Int, height: Int): Bitmap? =
        suspendCancellableCoroutine { cont ->
            val reader = imageReader
            if (reader == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val handler = Handler(Looper.getMainLooper())
            var resumed = false

            val timeoutRunnable = Runnable {
                if (!resumed) {
                    resumed = true
                    reader.setOnImageAvailableListener(null, null)
                    val fallback = reader.acquireLatestImage()?.use { imageToBitmap(it, width, height) }
                    cont.resume(fallback)
                }
            }
            handler.postDelayed(timeoutRunnable, 350L)

            reader.setOnImageAvailableListener({ r ->
                if (resumed) return@setOnImageAvailableListener
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                resumed = true
                handler.removeCallbacks(timeoutRunnable)
                r.setOnImageAvailableListener(null, null)
                val bmp = image.use { imageToBitmap(it, width, height) }
                cont.resume(bmp)
            }, handler)

            cont.invokeOnCancellation {
                handler.removeCallbacks(timeoutRunnable)
                reader.setOnImageAvailableListener(null, null)
            }
        }

    private fun imageToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val paddedBitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        paddedBitmap.copyPixelsFromBuffer(buffer)
        if (rowPadding == 0) {
            return paddedBitmap
        }
        val exactBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
        paddedBitmap.recycle()
        return exactBitmap
    }

    private fun computeQuickHash(bitmap: Bitmap): Long {
        var hash = 17L
        val stepX = (bitmap.width / 8).coerceAtLeast(1)
        val stepY = (bitmap.height / 8).coerceAtLeast(1)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                hash = hash * 31L + bitmap.getPixel(x, y)
                x += stepX
            }
            y += stepY
        }
        return hash
    }

    private fun getDisplayDimensions(): Triple<Int, Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            val densityDpi = context.resources.configuration.densityDpi
            Triple(bounds.width(), bounds.height(), densityDpi)
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        }
    }

    private fun releaseDisplayOnly() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    fun releaseSession() {
        lastFrameHash = 0L
        releaseDisplayOnly()
        mediaProjection?.stop()
        mediaProjection = null
    }

    sealed class FullScreenCaptureResult {
        data class Success(val bitmap: Bitmap) : FullScreenCaptureResult()
        data object ProtectedContent : FullScreenCaptureResult()
        data object RateLimited : FullScreenCaptureResult()
        data class Failed(val message: String) : FullScreenCaptureResult()
    }

    sealed class CaptureResult {
        data class Success(val cropped: CroppedBitmapResult, val frameUnchanged: Boolean) : CaptureResult()
        data object PermissionRequired : CaptureResult()
        data object ProtectedContent : CaptureResult()
        data object Busy : CaptureResult()
        data class Failed(val message: String) : CaptureResult()
    }

    companion object {
        // AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW (API 33+)
        private const val ERROR_SECURE_WINDOW = 6
    }
}
