package com.example.autoclicker

import android.content.Context

object Store {
    private fun p(c: Context) = c.getSharedPreferences("autoclicker", Context.MODE_PRIVATE)

    fun names(c: Context): List<String> =
        (p(c).getStringSet("names", null) ?: emptySet<String>()).sorted()

    fun load(c: Context, name: String): String = p(c).getString("s_$name", "") ?: ""

    fun save(c: Context, name: String, text: String) {
        val set = HashSet(p(c).getStringSet("names", null) ?: emptySet<String>())
        set.add(name)
        p(c).edit().putStringSet("names", set).putString("s_$name", text).apply()
    }

    fun delete(c: Context, name: String) {
        val set = HashSet(p(c).getStringSet("names", null) ?: emptySet<String>())
        set.remove(name)
        p(c).edit().putStringSet("names", set).remove("s_$name").apply()
        if (active(c) == name) setActive(c, "")
    }

    fun active(c: Context): String = p(c).getString("active", "") ?: ""
    fun setActive(c: Context, name: String) {
        p(c).edit().putString("active", name).apply()
    }

    fun loop(c: Context): Boolean = p(c).getBoolean("loop", false)
    fun setLoop(c: Context, v: Boolean) {
        p(c).edit().putBoolean("loop", v).apply()
    }

    fun log(c: Context): String = p(c).getString("log", "") ?: ""
    fun addLog(c: Context, msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        var s = log(c) + "[" + t + "] " + msg + "\n"
        if (s.length > 4000) s = s.substring(s.length - 4000)
        p(c).edit().putString("log", s).apply()
    }
    fun clearLog(c: Context) {
        p(c).edit().remove("log").apply()
    }
}
