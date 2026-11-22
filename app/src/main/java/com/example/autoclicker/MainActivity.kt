package com.example.autoclicker

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "Autoclicker: сборка работает!"
        tv.textSize = 22f
        tv.setPadding(48, 200, 48, 48)
        setContentView(tv)
    }
}
