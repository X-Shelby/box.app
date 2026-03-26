package com.box.app.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UiScaleManager {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_UI_SCALE_PERCENT = "ui_scale_percent"

    private val _uiScalePercent = MutableStateFlow(100)
    val uiScalePercent: StateFlow<Int> = _uiScalePercent.asStateFlow()

    private val _uiScale = MutableStateFlow(1.0f)
    val uiScale: StateFlow<Float> = _uiScale.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val p = prefs.getInt(KEY_UI_SCALE_PERCENT, 100).coerceIn(80, 120)
        _uiScalePercent.value = p
        _uiScale.value = p / 100f
    }

    fun setUiScalePercent(context: Context, percent: Int) {
        val p = percent.coerceIn(80, 120)
        if (_uiScalePercent.value == p) return

        _uiScalePercent.value = p
        _uiScale.value = p / 100f

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_UI_SCALE_PERCENT, p).apply()
    }
}
