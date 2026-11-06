package com.example.shortsautoscroll

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import kotlin.math.abs

class VisualProgressCaptureService : Service() {

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null

    private val handler = Handler(Looper.getMainLooper())
    private var endFrames = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        createNotificationChannel()

        val notification = Notification.Builder(this, "capture")
            .setContentTitle("Shorts Visual Smart Detect")
            .setContentText("Monitoring video progress")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                1001,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(1001, notification)
        }

        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0

        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>("data")
        }

        if (data == null || resultCode != Activity.RESULT_OK) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val manager = getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

            projection = manager.getMediaProjection(resultCode, data)

            projection?.registerCallback(
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopSelf()
                    }
                },
                handler
            )

            startCapture()

        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(
                NotificationManager::class.java
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    "capture",
                    "Screen Capture",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun startCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        reader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        reader?.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage()
                ?: return@setOnImageAvailableListener

            try {
                analyzeImage(image)
            } finally {
                image.close()
            }
        }, handler)

        display = projection?.createVirtualDisplay(
            "ShortsProgress",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            handler
        )
    }

    private fun analyzeImage(image: Image) {
        val plane = image.planes[0]
        val buffer = plane.buffer

        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val width = image.width
        val height = image.height

        val xStart = (width * 0.08f).toInt()
        val xEnd = (width * 0.92f).toInt()
        val scanWidth = xEnd - xStart

        val yStart = (height * 0.85f).toInt()
        val yEnd = (height * 0.97f).toInt()

        var bestFraction = 0f
        var detected = false

        for (y in yStart until yEnd step 2) {
            var current = 0
            var first = -1
            var last = -1

            for (x in xStart until xEnd) {
                val index = y * rowStride + x * pixelStride

                val r = buffer.get(index).toInt() and 255
                val g = buffer.get(index + 1).toInt() and 255
                val b = buffer.get(index + 2).toInt() and 255

                val white = r > 210 &&
                    g > 210 &&
                    b > 210 &&
                    abs(r - g) < 25 &&
                    abs(g - b) < 25

                if (white) {
                    if (first == -1) first = x
                    current++
                    last = x
                } else {
                    if (first >= 0) {
                        val startsAtLeft = first <= xStart + scanWidth * 0.04f

                        if (startsAtLeft && current > scanWidth * 0.10f) {
                            detected = true
                            bestFraction = maxOf(
                                bestFraction,
                                current.toFloat() / scanWidth
                            )
                        }
                    }

                    current = 0
                    first = -1
                    last = -1
                }
            }

            if (first >= 0) {
                val startsAtLeft = first <= xStart + scanWidth * 0.04f

                if (startsAtLeft && current > scanWidth * 0.10f) {
                    detected = true
                    bestFraction = maxOf(
                        bestFraction,
                        current.toFloat() / scanWidth
                    )
                }
            }
        }

        val prefs = getSharedPreferences("shorts_auto", MODE_PRIVATE)

        if (prefs.getString("mode", "smart") != "smart" ||
            !prefs.getBoolean("enabled", false)
        ) return

        if (detected && bestFraction >= 0.96f) {
            endFrames++

            if (endFrames >= 5) {
                prefs.edit().putBoolean("visual_end", true).apply()
                endFrames = 0
            }
        } else {
            endFrames = 0

            if (!detected || bestFraction < 0.70f) {
                prefs.edit().putBoolean("visual_end", false).apply()
            }
        }
    }

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        projection = null
        super.onDestroy()
    }
}