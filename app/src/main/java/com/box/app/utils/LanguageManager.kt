package com.box.app.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage {
    SYSTEM,
    ENGLISH,
    CHINESE
}

object LanguageManager {
    private const val PREFS_NAME = "language_settings"
    private const val KEY_LANGUAGE = "language"

    private val _language = MutableStateFlow(AppLanguage.SYSTEM)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name)
        val lang = runCatching { AppLanguage.valueOf(saved ?: AppLanguage.SYSTEM.name) }.getOrNull()
            ?: AppLanguage.SYSTEM
        _language.value = lang
        applyAppCompatLocales(lang)
    }

    fun setLanguage(context: Context, lang: AppLanguage) {
        _language.value = lang
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, lang.name).apply()
        applyAppCompatLocales(lang)
    }

    private fun applyAppCompatLocales(lang: AppLanguage) {
        val locales = when (lang) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.ENGLISH -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.CHINESE -> LocaleListCompat.forLanguageTags("zh-CN")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
