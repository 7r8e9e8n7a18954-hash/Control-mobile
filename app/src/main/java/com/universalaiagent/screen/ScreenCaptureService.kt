package com.universalaiagent.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
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
import androidx.core.app.NotificationCompat
import com.universalaiagent.MainActivity
import com.universalaiagent.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private val lock = Any()
    private var pendingCapture: CompletableDeferred<Bitmap?>? = null
    private var captureWidth = 0
    private var captureHeight = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWithType()
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultData == null) {
            ScreenCaptureController.reportError("Screen capture consent data was missing.")
            stopSelf()
            return START_NOT_STICKY
        }
        return runCatching {
            setupProjection(resultCode, resultData)
            ScreenCaptureController.attach(this)
            START_NOT_STICKY
        }.getOrElse { error ->
            ScreenCaptureController.reportError(
                "Screen capture could not start: ${error.message ?: error.javaClass.simpleName}",
            )
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    suspend fun captureFrame(): Bitmap = withContext(Dispatchers.IO) {
        val reader = imageReader
        if (reader == null) throw IllegalStateException("The screen capture display is not ready.")
        val deferred = CompletableDeferred<Bitmap?>()
        synchronized(lock) {
            if (pendingCapture != null) {
                throw IllegalStateException("A screen capture is already in progress.")
            }
            pendingCapture = deferred
        }
        val bitmap = withTimeoutOrNull(5000) { deferred.await() }
        synchronized(lock) {
            if (pendingCapture === deferred) pendingCapture = null
        }
        bitmap ?: throw IllegalStateException(
            "Could not capture the screen. Some apps block screenshots while open.",
        )
    }

    private fun setupProjection(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = manager.getMediaProjection(resultCode, resultData)
            ?: throw IllegalStateException("Android did not provide a MediaProjection session.")
        mediaProjection = projection
        projection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    ScreenCaptureController.reportError("Screen sharing was stopped.")
                    stopSelf()
                }
            },
            Handler(mainLooper),
        )

        val metrics = resources.displayMetrics
        val scale = minOf(1f, MAX_CAPTURE_SIDE.toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels))
        captureWidth = (metrics.widthPixels * scale).toInt().coerceAtLeast(1)
        captureHeight = (metrics.heightPixels * scale).toInt().coerceAtLeast(1)

        handlerThread = HandlerThread("screen-capture").also { it.start() }
        handler = Handler(handlerThread!!.looper)
        val reader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 3)
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            val target = synchronized(lock) { pendingCapture }
            if (target == null) {
                image.close()
                return@setOnImageAvailableListener
            }
            val bitmap = runCatching { imageToBitmap(image) }.getOrNull()
            image.close()
            synchronized(lock) {
                if (pendingCapture === target) pendingCapture = null
            }
            target.complete(bitmap)
        }, handler)

        virtualDisplay = projection.createVirtualDisplay(
            "UniversalAIAgentScreen",
            captureWidth,
            captureHeight,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler,
        )
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        if (cropped !== padded) padded.recycle()
        return cropped
    }

    private fun stopCapture() {
        ScreenCaptureController.detach()
        synchronized(lock) {
            pendingCapture?.complete(null)
            pendingCapture = null
        }
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundWithType() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Screen understanding",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shown while you share your screen with the assistant."
                },
            )
        }
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Screen sharing is active")
            .setContentText("Frames are captured only when you tap Analyze. Tap Stop to end sharing.")
            .setContentIntent(openIntent)
            .addAction(0, "Stop sharing", stopIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val ACTION_START = "com.universalaiagent.screen.START"
        const val ACTION_STOP = "com.universalaiagent.screen.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 4102
        private const val MAX_CAPTURE_SIDE = 1600
    }
}
