package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val bg = Color.parseColor("#E1F5FE")
    private val accent = Color.parseColor("#81D4FA")
    private val dark = Color.parseColor("#01579B")

    private lateinit var spinner: Spinner
    private lateinit var nameEdit: EditText
    private lateinit var scriptEdit: EditText
    private lateinit var logView: TextView
    private var names: List<String> = emptyList()

    private val sample = """// Пример: листаем экран вверх и вниз
repeat(forever) {
  swipe(540, 1700, 540, 700, 300);
  sleep(800);
  swipe(540, 700, 540, 1700, 300);
  sleep(800);
}
"""

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setBackgroundColor(accent)
        b.setTextColor(dark)
        b.setOnClickListener { onClick() }
        return b
    }

    private fun row(vararg bs: Button): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (b in bs) {
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.setMargins(dp(3), dp(3), dp(3), dp(3))
            r.addView(b, lp)
        }
        return r
    }

    private fun label(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(dark)
        t.setPadding(0, dp(12), 0, dp(4))
        return t
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(bg)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(56), dp(16), dp(24))
        scroll.addView(root)

        val title = TextView(this)
        title.text = "Autoclicker"
        title.textSize = 22f
        title.setTextColor(dark)
        root.addView(title)

        val loopSwitch = Switch(this)
        loopSwitch.text = "Зациклить весь скрипт"
        loopSwitch.setTextColor(dark)
        loopSwitch.isChecked = Store.loop(this)
        loopSwitch.setOnCheckedChangeListener { _, v -> Store.setLoop(this, v) }
        root.addView(loopSwitch)

        root.addView(label("Сохранённые скрипты"))
        spinner = Spinner(this)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos in names.indices) {
                    val n = names[pos]
                    nameEdit.setText(n)
                    scriptEdit.setText(Store.load(this@MainActivity, n))
                    Store.setActive(this@MainActivity, n)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        root.addView(spinner)

        root.addView(label("Имя скрипта"))
        nameEdit = EditText(this)
        nameEdit.setSingleLine(true)
        nameEdit.setTextColor(dark)
        root.addView(nameEdit)

        root.addView(label("Скрипт"))
        scriptEdit = EditText(this)
        scriptEdit.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        scriptEdit.typeface = Typeface.MONOSPACE
        scriptEdit.gravity = Gravity.TOP or Gravity.START
        scriptEdit.minLines = 10
        scriptEdit.setTextColor(dark)
        scriptEdit.setBackgroundColor(Color.WHITE)
        scriptEdit.setPadding(dp(10), dp(10), dp(10), dp(10))
        scriptEdit.setText(sample)
        root.addView(scriptEdit)

        root.addView(row(
            btn("Сохранить") { save() },
            btn("Проверить") { check() }
        ))
        root.addView(row(
            btn("Новый") {
                nameEdit.setText("")
                scriptEdit.setText("")
            },
            btn("Удалить") { delete() }
        ))

        root.addView(label("Журнал и ошибки"))
        logView = TextView(this)
        logView.typeface = Typeface.MONOSPACE
        logView.textSize = 12f
        logView.setTextColor(dark)
        logView.setBackgroundColor(Color.WHITE)
        logView.setPadding(dp(10), dp(10), dp(10), dp(10))
        logView.minLines = 5
        root.addView(logView)
        root.addView(btn("Очистить журнал") {
            Store.clearLog(this)
            showLog()
        })

        root.addView(label("Служба"))
        root.addView(btn("Открыть специальные возможности") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(btn("Показать панель") {
            val s = ClickService.instance
            if (s != null) {
                s.showPanel()
            } else {
                Toast.makeText(this, "Сначала включите службу", Toast.LENGTH_LONG).show()
            }
        })

        setContentView(scroll)
        refresh(null)
    }

    override fun onResume() {
        super.onResume()
        showLog()
    }

    private fun showLog() {
        val s = Store.log(this)
        logView.text = if (s.isEmpty()) "(пусто)" else s
    }

    private fun refresh(select: String?) {
        names = Store.names(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        val i = names.indexOf(select ?: Store.active(this))
        if (i >= 0) spinner.setSelection(i)
    }

    private fun check(): Boolean {
        return try {
            val prog = Parser(Lexer(scriptEdit.text.toString()).lex()).parseProgram()
            Store.addLog(this, "Проверка: ошибок нет (команд верхнего уровня: ${prog.size})")
            true
        } catch (e: ScriptError) {
            Store.addLog(this, "Ошибка, строка ${e.line}: ${e.message}")
            false
        } finally {
            showLog()
        }
    }

    private fun save() {
        val n = nameEdit.text.toString().trim()
        if (n.isEmpty()) {
            Toast.makeText(this, "Введите имя скрипта", Toast.LENGTH_SHORT).show()
            return
        }
        Store.save(this, n, scriptEdit.text.toString())
        Store.setActive(this, n)
        refresh(n)
        check()
        Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
    }

    private fun delete() {
        val n = nameEdit.text.toString().trim()
        if (n.isEmpty()) return
        Store.delete(this, n)
        nameEdit.setText("")
        scriptEdit.setText("")
        refresh(null)
    }
}
