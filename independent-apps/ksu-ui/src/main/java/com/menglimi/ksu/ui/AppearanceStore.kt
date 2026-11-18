package com.menglimi.ksu.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val title: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
data class Appearance(val mode: ThemeMode = ThemeMode.SYSTEM, val dynamic: Boolean = true, val amoled: Boolean = false)

class AppearanceStore(context: Context) {
    private val prefs = context.getSharedPreferences("standalone_appearance", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(Appearance(
        mode = ThemeMode.entries.firstOrNull { it.name == prefs.getString("mode", "SYSTEM") } ?: ThemeMode.SYSTEM,
        dynamic = prefs.getBoolean("dynamic", true),
        amoled = prefs.getBoolean("amoled", false),
    ))
    val state = mutable.asStateFlow()

    fun update(value: Appearance) {
        prefs.edit().putString("mode", value.mode.name).putBoolean("dynamic", value.dynamic)
            .putBoolean("amoled", value.amoled).apply()
        mutable.value = value
    }
}
