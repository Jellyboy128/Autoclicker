package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ClickService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ClickService? = null
    }

    private var windowManager: WindowManager? = null
    private var panel: View? = null
    private var worker: Thread? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        showPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        stopScript()
        hidePanel()
        instance = null
        return super.onUnbind(intent)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makeButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.minWidth = 0
        b.minHeight = 0
        b.minimumWidth = 0
        b.minimumHeight = 0
        b.setPadding(dp(12), dp(6), dp(12), dp(6))
        b.setBackgroundColor(Color.parseColor("#81D4FA"))
        b.setTextColor(Color.parseColor("#01579B"))
        b.setOnClickListener { onClick() }
        return b
    }

    fun showPanel() {
        if (panel != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#B3E5FC"))
        bg.cornerRadius = dp(16).toFloat()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.HORIZONTAL
        root.background = bg
        root.setPadding(dp(6), dp(6), dp(6), dp(6))
        root.gravity = Gravity.CENTER_VERTICAL

        val handle = TextView(this)
        handle.text = "☰"
        handle.textSize = 22f
        handle.setTextColor(Color.parseColor("#01579B"))
        handle.setPadding(dp(10), 0, dp(10), 0)
        root.addView(handle)

        val btnParams = {
            val p = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            p.marginStart = dp(4)
            p
        }
        root.addView(makeButton("Старт") { startScript() }, btnParams())
        root.addView(makeButton("Стоп") { stopScript() }, btnParams())
        root.addView(makeButton("✕") {
            stopScript()
            hidePanel()
        }, btnParams())

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = dp(16)
        lp.y = dp(200)

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        handle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    touchX = e.rawX
                    touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - touchX).toInt()
                    lp.y = startY + (e.rawY - touchY).toInt()
                    wm.updateViewLayout(root, lp)
                }
            }
            true
        }

        wm.addView(root, lp)
        panel = root
    }

    fun hidePanel() {
        val p = panel ?: return
        try {
            windowManager?.removeView(p)
        } catch (e: Exception) {
        }
        panel = null
    }

    // ---- скрипт (пока демо: листаем экран вверх-вниз) ----

    fun startScript() {
        if (worker?.isAlive == true) return
        val t = Thread {
            try {
                val w = resources.displayMetrics.widthPixels.toFloat()
                val h = resources.displayMetrics.heightPixels.toFloat()
                while (!Thread.currentThread().isInterrupted) {
                    swipe(w / 2, h * 0.7f, w / 2, h * 0.3f, 400)
                    pause(800)
                    swipe(w / 2, h * 0.3f, w / 2, h * 0.7f, 400)
                    pause(800)
                }
            } catch (e: InterruptedException) {
            }
        }
        worker = t
        t.start()
    }

    fun stopScript() {
        worker?.interrupt()
        worker = null
    }

    // ---- базовые действия ----

    private fun pause(ms: Long) {
        Thread.sleep(ms)
    }

    private fun tap(x: Float, y: Float) {
        val path = Path()
        path.moveTo(x, y)
        gesture(path, 50)
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        gesture(path, durationMs)
    }

    private fun gesture(path: Path, durationMs: Long) {
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        val g = GestureDescription.Builder().addStroke(stroke).build()
        val latch = CountDownLatch(1)
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                latch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                latch.countDown()
            }
        }, null)
        if (ok) latch.await(durationMs + 1000, TimeUnit.MILLISECONDS)
    }
}
