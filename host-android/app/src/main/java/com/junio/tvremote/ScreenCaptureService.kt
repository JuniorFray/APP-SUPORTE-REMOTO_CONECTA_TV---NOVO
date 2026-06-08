package com.junio.tvremote

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "projectionCallback.onStop")
            stopCapture()
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        createNotificationChannel()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Remote Host")
            .setContentText("Captura de tela ativa")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startCaptureThread()
        readDisplayMetrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode =
            intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")

        Log.d(TAG, "onStartCommand resultCode=$resultCode dataNull=${data == null}")

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "Dados de permissão inválidos, encerrando serviço")
            stopSelf()
            return START_NOT_STICKY
        }

        ScreenCaptureStore.resultCode = resultCode
        ScreenCaptureStore.data = data

        if (mediaProjection == null) {
            startProjection(resultCode, data)
        } else {
            Log.d(TAG, "MediaProjection já inicializada")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCaptureThread() {
        captureThread = HandlerThread("ScreenCaptureThread").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)
        Log.d(TAG, "captureThread iniciada")
    }

    private fun readDisplayMetrics() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()

        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        screenDensity = metrics.densityDpi
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        Log.d(TAG, "displayMetrics width=$screenWidth height=$screenHeight density=$screenDensity")
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        try {
            Log.d(TAG, "startProjection begin")
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = manager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                Log.e(TAG, "getMediaProjection retornou null")
                stopSelf()
                return
            }

            mediaProjection?.registerCallback(projectionCallback, captureHandler)
            Log.d(TAG, "MediaProjection criada")

            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                2
            )
            Log.d(TAG, "ImageReader criado")

            imageReader?.setOnImageAvailableListener({ reader ->
                processLatestImage(reader)
            }, captureHandler)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "TVRemoteScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                captureHandler
            )

            Log.d(TAG, "VirtualDisplay criada=${virtualDisplay != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar projeção", e)
            stopSelf()
        }
    }

    private fun processLatestImage(reader: ImageReader) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()

            if (image == null) {
                return
            }

            val width = image.width
            val height = image.height
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()

            ScreenCaptureStore.updateBitmap(croppedBitmap)
            Log.d(TAG, "Bitmap atualizada width=$width height=$height")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar imagem", e)
        } finally {
            image?.close()
        }
    }

    private fun stopCapture() {
        Log.d(TAG, "stopCapture")
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1
    }
}