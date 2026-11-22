package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(48, 160, 48, 48)

        val info = TextView(this)
        info.text = "1. Нажмите кнопку ниже и включите службу «Autoclicker» в специальных возможностях.\n" +
            "2. На экране появится плавающая панель.\n" +
            "3. Откройте длинную страницу (например, в Chrome) и нажмите «Старт»: экран будет листаться вверх-вниз."
        info.textSize = 16f
        root.addView(info)

        val b1 = Button(this)
        b1.text = "Открыть специальные возможности"
        b1.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        root.addView(b1)

        val b2 = Button(this)
        b2.text = "Показать панель"
        b2.setOnClickListener {
            val s = ClickService.instance
            if (s != null) {
                s.showPanel()
            } else {
                Toast.makeText(this, "Сначала включите службу", Toast.LENGTH_LONG).show()
            }
        }
        root.addView(b2)

        setContentView(root)
    }
}
