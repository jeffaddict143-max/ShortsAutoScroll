package com.example.shortsautoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper

class AutoScrollAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastSwipeAt = 0L

    private val timerTick = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("shorts_auto", MODE_PRIVATE)

            if (prefs.getBoolean("enabled", false)) {
                val mode = prefs.getString("mode", "smart")
                val now = System.currentTimeMillis()

                if (mode == "timer") {
                    val interval = prefs.getInt("interval", 45) * 1000L

                    if (now - lastSwipeAt >= interval) {
                        swipe()
                    }
                } else if (prefs.getBoolean("visual_end", false)) {
                    swipe()
                    prefs.edit().putBoolean("visual_end", false).apply()
                }
            }

            handler.postDelayed(this, 500L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(timerTick)
        handler.post(timerTick)
    }

    private fun swipe() {
        val prefs = getSharedPreferences("shorts_auto", MODE_PRIVATE)

        if (!prefs.getBoolean("enabled", false)) return

        val now = System.currentTimeMillis()
        if (now - lastSwipeAt < 5000L) return

        lastSwipeAt = now

        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.5f

        val direction = prefs.getString("direction", "next")

        val startY: Float
        val endY: Float

        if (direction == "previous") {
            startY = dm.heightPixels * 0.25f
            endY = dm.heightPixels * 0.78f
        } else {
            startY = dm.heightPixels * 0.78f
            endY = dm.heightPixels * 0.25f
        }

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(path, 80L, 280L)
            )
            .build()

        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {
        handler.removeCallbacks(timerTick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(timerTick)
        super.onDestroy()
    }
}