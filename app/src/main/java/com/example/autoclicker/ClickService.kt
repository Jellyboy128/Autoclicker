package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class ClickService : AccessibilityService(), Env {

    companion object {
        @Volatile
        var instance: ClickService? = null
    }

    private var windowManager: WindowManager? = null
    private var panel: View? = null
    private var picker: View? = null
    private val picked = ArrayList<String>()
    private var worker: Thread? = null
    private var lastShotAt = 0L
    @Volatile
    private var shotIntervalMs = 350L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        showPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        stopScript()
        stopPicker()
        hidePanel()
        instance = null
        return super.onUnbind(intent)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun report(msg: String) {
        Store.addLog(this, msg)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- плавающая панель ----

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
        root.addView(makeButton("Пипетка") { startPicker() }, btnParams())
        root.addView(makeButton("✕") {
            stopScript()
            stopPicker()
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

    // ---- пипетка ----

    fun startPicker() {
        if (picker != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        synchronized(picked) { picked.clear() }

        val frame = FrameLayout(this)
        frame.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_UP) pickAt(e.rawX.toInt(), e.rawY.toInt())
            true
        }

        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#B3E5FC"))
        bg.cornerRadius = dp(16).toFloat()

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(12), dp(6), dp(6), dp(6))
        bar.background = bg
        bar.isClickable = true

        val hint = TextView(this)
        hint.text = "Тапайте по точкам   "
        hint.setTextColor(Color.parseColor("#01579B"))
        bar.addView(hint)
        bar.addView(makeButton("Готово") { stopPicker() })

        val barLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        )
        barLp.topMargin = dp(48)
        frame.addView(bar, barLp)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(frame, lp)
        picker = frame
    }

    fun stopPicker() {
        val p = picker ?: return
        try {
            windowManager?.removeView(p)
        } catch (e: Exception) {
        }
        picker = null
    }

    private fun pickAt(x: Int, y: Int) {
        Thread {
            val color = try {
                pixel(x, y)
            } catch (e: Exception) {
                "?"
            }
            val text: String
            val line: String
            synchronized(picked) {
                line = "point(p${picked.size + 1}, $x, $y); // $color"
                picked.add(line)
                text = picked.joinToString("\n")
            }
            Store.addLog(this, line)
            Handler(Looper.getMainLooper()).post {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("points", text))
                Toast.makeText(this, "$x, $y  $color (скопировано)", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    // ---- запуск скрипта ----

    fun startScript() {
        if (worker?.isAlive == true) return
        val name = Store.active(this)
        val text = Store.load(this, name)
        if (name.isEmpty() || text.isBlank()) {
            report("Нет активного скрипта: откройте приложение и сохраните скрипт")
            return
        }
        val prog = try {
            Parser(Lexer(text).lex()).parseProgram()
        } catch (e: ScriptError) {
            report("Ошибка, строка ${e.line}: ${e.message}")
            return
        }
        val loopAll = Store.loop(this)
        shotIntervalMs = 350L
        val t = Thread {
            try {
                val interp = Interpreter(this@ClickService)
                report("Старт: $name")
                do {
                    interp.runOnce(prog)
                } while (loopAll)
                report("Скрипт завершён")
            } catch (e: StopSignal) {
                report("Остановлено командой stop/break")
            } catch (e: InterruptedException) {
                report("Остановлено кнопкой")
            } catch (e: ScriptError) {
                report("Ошибка, строка ${e.line}: ${e.message}")
            } catch (e: Exception) {
                report("Сбой: ${e.message}")
            }
        }
        worker = t
        t.start()
    }

    fun stopScript() {
        worker?.interrupt()
        worker = null
    }

    // ---- действия для интерпретатора ----

    override fun click(x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        gesture(path, 50)
    }

    override fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long) {
        val path = Path()
        path.moveTo(x1.toFloat(), y1.toFloat())
        path.lineTo(x2.toFloat(), y2.toFloat())
        gesture(path, ms)
    }

    override fun sleep(ms: Long) {
        if (ms > 0) Thread.sleep(ms)
    }

    override fun log(msg: String) {
        Store.addLog(this, msg)
    }

    override fun setShotRate(perSec: Int) {
        shotIntervalMs = maxOf(350L, 1000L / perSec)
    }

    override fun pixel(x: Int, y: Int): String {
        val b = screenshot()
        if (x < 0 || y < 0 || x >= b.width || y >= b.height) {
            throw ScriptError(0, "pixel($x, $y): точка вне экрана ${b.width}x${b.height}")
        }
        return String.format("#%06X", b.getPixel(x, y) and 0xFFFFFF)
    }

        fun findOld(color: String, x1: Int, y1: Int, x2: Int, y2: Int, tol: Int): IntArray? {
        val target = Color.parseColor(color)
        val b = screenshot()
        val l = maxOf(0, minOf(x1, x2))
        val t = maxOf(0, minOf(y1, y2))
        val r = minOf(b.width - 1, maxOf(x1, x2))
        val bt = minOf(b.height - 1, maxOf(y1, y2))
        if (r < l || bt < t) return null
        val w = r - l + 1
        val h = bt - t + 1
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, l, t, w, h)
        val tr = Color.red(target)
        val tg = Color.green(target)
        val tb = Color.blue(target)
        for (i in px.indices) {
            val p = px[i]
            if (abs(Color.red(p) - tr) <= tol &&
                abs(Color.green(p) - tg) <= tol &&
                abs(Color.blue(p) - tb) <= tol
            ) {
                return intArrayOf(l + i % w, t + i / w)
            }
        }
        return null
    }

     override fun find(colors: List<String>, x1: Int, y1: Int, x2: Int, y2: Int, tol: Int): IntArray? {
        val targets = colors.map { Color.parseColor(it) }
        val b = screenshot()
        val l = maxOf(0, minOf(x1, x2))
        val t = maxOf(0, minOf(y1, y2))
        val r = minOf(b.width - 1, maxOf(x1, x2))
        val bt = minOf(b.height - 1, maxOf(y1, y2))
        if (r < l || bt < t) return null
        val w = r - l + 1
        val h = bt - t + 1
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, l, t, w, h)
        for (ci in targets.indices) {
            val tr = Color.red(targets[ci])
            val tg = Color.green(targets[ci])
            val tb = Color.blue(targets[ci])
            for (i in px.indices) {
                val p = px[i]
                if (abs(Color.red(p) - tr) <= tol &&
                    abs(Color.green(p) - tg) <= tol &&
                    abs(Color.blue(p) - tb) <= tol
                ) {
                    return intArrayOf(l + i % w, t + i / w, ci)
                }
            }
        }
        return null
    }
    
    private fun screenshot(): Bitmap {
        val wait = shotIntervalMs - (System.currentTimeMillis() - lastShotAt)
        if (wait > 0) Thread.sleep(wait)
        lastShotAt = System.currentTimeMillis()
        val latch = CountDownLatch(1)
        var result: Bitmap? = null
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val hb = screenshot.hardwareBuffer
                val hw = Bitmap.wrapHardwareBuffer(hb, screenshot.colorSpace)
                result = hw?.copy(Bitmap.Config.ARGB_8888, false)
                hb.close()
                latch.countDown()
            }

            override fun onFailure(errorCode: Int) {
                latch.countDown()
            }
        })
        latch.await(3, TimeUnit.SECONDS)
        return result
            ?: throw ScriptError(0, "Не удалось сделать скриншот (экран защищён или слишком частые запросы)")
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
