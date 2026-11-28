package com.example.autoclicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

private class Gutter(ctx: Context, private val ed: EditText) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setBackgroundColor(Color.parseColor("#ECEFF1"))
    }

    override fun onDraw(c: Canvas) {
        val lay = ed.layout ?: return
        paint.textSize = ed.textSize
        paint.typeface = Typeface.MONOSPACE
        paint.color = Color.parseColor("#78909C")
        paint.textAlign = Paint.Align.RIGHT
        val x = width - 8 * resources.displayMetrics.density
        for (i in 0 until lay.lineCount) {
            c.drawText((i + 1).toString(), x, ed.getLineBounds(i, null).toFloat(), paint)
        }
    }
}

class CodeEditor(ctx: Context) {
    val edit = EditText(ctx)
    val view: View

    private val cKeyword = Color.parseColor("#7B1FA2")
    private val cCommand = Color.parseColor("#0277BD")
    private val cNumber = Color.parseColor("#E65100")
    private val cString = Color.parseColor("#2E7D32")
    private val cComment = Color.parseColor("#90A4AE")
    private val cSpecial = Color.parseColor("#C2185B")

    private val keywords = setOf("if", "else", "repeat", "forever", "point")
    private val commands = setOf(
        "click", "tap", "sleep", "wait", "swipe", "log", "stop", "break", "fps", "pixel", "findcolor"
    )
    private val specials = setOf("found", "foundx", "foundy", "foundcolor", "true", "false")

    init {
        val d = ctx.resources.displayMetrics.density

        edit.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        edit.setHorizontallyScrolling(true)
        edit.typeface = Typeface.MONOSPACE
        edit.textSize = 14f
        edit.gravity = Gravity.TOP or Gravity.START
        edit.minLines = 10
        edit.setTextColor(Color.parseColor("#263238"))
        edit.background = null
        edit.setPadding((8 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())

        val gutter = Gutter(ctx, edit)

        val hsv = HorizontalScrollView(ctx)
        hsv.isFillViewport = true
        hsv.addView(
            edit,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.isBaselineAligned = false
        row.setBackgroundColor(Color.WHITE)
        row.addView(
            gutter,
            LinearLayout.LayoutParams((44 * d).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
        )
        row.addView(
            hsv,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        view = row

        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) {
                if (e != null) highlight(e)
                gutter.invalidate()
            }
        })
        edit.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> gutter.invalidate() }
    }

    private fun color(e: Editable, a: Int, b: Int, col: Int) {
        if (b > a) e.setSpan(ForegroundColorSpan(col), a, b, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun highlight(e: Editable) {
        for (sp in e.getSpans(0, e.length, ForegroundColorSpan::class.java)) e.removeSpan(sp)
        for (sp in e.getSpans(0, e.length, StyleSpan::class.java)) e.removeSpan(sp)
        val s = e.toString()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '/' && i + 1 < s.length && s[i + 1] == '/') {
                var j = i
                while (j < s.length && s[j] != '\n') j++
                color(e, i, j, cComment)
                i = j
            } else if (c == '"' || c == '\u201C' || c == '\u201D') {
                var j = i + 1
                while (j < s.length && s[j] != '"' && s[j] != '\u201C' && s[j] != '\u201D' && s[j] != '\n') j++
                val end = if (j < s.length && s[j] != '\n') j + 1 else j
                color(e, i, end, cString)
                i = end
            } else if (c.isDigit()) {
                var j = i
                while (j < s.length && s[j].isDigit()) j++
                color(e, i, j, cNumber)
                i = j
            } else if (c.isLetter() || c == '_') {
                var j = i
                while (j < s.length && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                val w = s.substring(i, j).lowercase()
                if (w in keywords) {
                    color(e, i, j, cKeyword)
                    e.setSpan(StyleSpan(Typeface.BOLD), i, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (w in commands) {
                    color(e, i, j, cCommand)
                } else if (w in specials) {
                    color(e, i, j, cSpecial)
                }
                i = j
            } else {
                i++
            }
        }
    }
}
