package com.example.shortsautoscroll

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("shorts_auto", MODE_PRIVATE)
    }

    private val captureRequest = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 28)
        }

        fun addText(value: String, size: Float, bold: Boolean = false) {
            root.addView(TextView(this).apply {
                text = value
                textSize = size
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 8, 0, 12)
            })
        }

        fun addButton(label: String, action: () -> Unit) {
            root.addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        addText("Shorts Auto Scroll", 26f, true)

        addText("1. Choose mode", 18f, true)

        val mode = Spinner(this)
        mode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Smart Mode (Visual Detect)", "Timer Mode")
        )
        mode.setSelection(
            if (prefs.getString("mode", "smart") == "timer") 1 else 0
        )
        root.addView(mode)

        addText("2. Timer interval", 18f, true)

        val interval = Spinner(this)
        interval.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("30 seconds", "45 seconds", "60 seconds")
        )
        interval.setSelection(
            listOf(30, 45, 60).indexOf(
                prefs.getInt("interval", 45)
            ).coerceAtLeast(0)
        )
        root.addView(interval)

        addText("3. Swipe direction", 18f, true)

        val direction = Spinner(this)
        direction.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Next Short (swipe up)", "Previous Short (swipe down)")
        )
        direction.setSelection(
            if (prefs.getString("direction", "next") == "previous") 1 else 0
        )
        root.addView(direction)

        addButton("Save settings") {
            saveSettings(mode, interval, direction, false)
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        }

        addButton("Enable Accessibility Service") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        addButton("Start Auto Scroll") {
            saveSettings(mode, interval, direction, true)

            if (mode.selectedItemPosition == 0) {
                val manager = getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

                startActivityForResult(
                    manager.createScreenCaptureIntent(),
                    captureRequest
                )
            } else {
                Toast.makeText(
                    this,
                    "Timer Mode started",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        addButton("Stop Auto Scroll") {
            prefs.edit()
                .putBoolean("enabled", false)
                .putBoolean("visual_end", false)
                .apply()

            stopService(Intent(this, VisualProgressCaptureService::class.java))

            Toast.makeText(this, "Stopped", Toast.LENGTH_SHORT).show()
        }

        addText(
            "Smart Mode requires screen capture permission. " +
            "The app will only swipe when visual progress detection confirms the end.",
            14f
        )

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun saveSettings(
        mode: Spinner,
        interval: Spinner,
        direction: Spinner,
        enabled: Boolean
    ) {
        val selectedMode =
            if (mode.selectedItemPosition == 1) "timer" else "smart"

        val seconds = listOf(30, 45, 60)[interval.selectedItemPosition]

        val selectedDirection =
            if (direction.selectedItemPosition == 1) "previous" else "next"

        prefs.edit()
            .putString("mode", selectedMode)
            .putInt("interval", seconds)
            .putString("direction", selectedDirection)
            .putBoolean("enabled", enabled)
            .putBoolean("visual_end", false)
            .apply()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == captureRequest) {
            if (resultCode == RESULT_OK && data != null) {
                val service = Intent(this, VisualProgressCaptureService::class.java)
                    .putExtra("resultCode", resultCode)
                    .putExtra("data", data)

                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    startForegroundService(service)
                } else {
                    startService(service)
                }

                Toast.makeText(
                    this,
                    "Visual Smart Detect started",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                prefs.edit().putBoolean("enabled", false).apply()
                Toast.makeText(
                    this,
                    "Screen capture permission denied",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}