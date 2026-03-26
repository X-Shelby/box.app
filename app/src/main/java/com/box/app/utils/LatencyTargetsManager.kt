package com.box.app.utils

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LatencyTarget(
    val name: String,
    val url: String
)

object LatencyTargetsManager {
    private const val PREFS_NAME = "latency_settings"

    private const val KEY_NAME_1 = "latency_name_1"
    private const val KEY_URL_1 = "latency_url_1"
    private const val KEY_NAME_2 = "latency_name_2"
    private const val KEY_URL_2 = "latency_url_2"
    private const val KEY_NAME_3 = "latency_name_3"
    private const val KEY_URL_3 = "latency_url_3"

    private val defaults = listOf(
        LatencyTarget(name = "Baidu", url = "https://baidu.com"),
        LatencyTarget(name = "Cloudflare", url = "https://cloudflare.com"),
        LatencyTarget(name = "Google", url = "https://google.com")
    )

    private val _targets = MutableStateFlow(defaults)
    val targets: StateFlow<List<LatencyTarget>> = _targets.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val t1 = LatencyTarget(
            name = prefs.getString(KEY_NAME_1, defaults[0].name).orEmpty().ifBlank { defaults[0].name },
            url = normalizeUrl(prefs.getString(KEY_URL_1, defaults[0].url).orEmpty(), defaults[0].url)
        )
        val t2 = LatencyTarget(
            name = prefs.getString(KEY_NAME_2, defaults[1].name).orEmpty().ifBlank { defaults[1].name },
            url = normalizeUrl(prefs.getString(KEY_URL_2, defaults[1].url).orEmpty(), defaults[1].url)
        )
        val t3 = LatencyTarget(
            name = prefs.getString(KEY_NAME_3, defaults[2].name).orEmpty().ifBlank { defaults[2].name },
            url = normalizeUrl(prefs.getString(KEY_URL_3, defaults[2].url).orEmpty(), defaults[2].url)
        )
        _targets.value = listOf(t1, t2, t3)
    }

    fun setTargets(context: Context, t1: LatencyTarget, t2: LatencyTarget, t3: LatencyTarget) {
        val next = listOf(
            LatencyTarget(name = t1.name.trim().ifBlank { defaults[0].name }, url = normalizeUrl(t1.url, defaults[0].url)),
            LatencyTarget(name = t2.name.trim().ifBlank { defaults[1].name }, url = normalizeUrl(t2.url, defaults[1].url)),
            LatencyTarget(name = t3.name.trim().ifBlank { defaults[2].name }, url = normalizeUrl(t3.url, defaults[2].url))
        )

        _targets.value = next

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_NAME_1, next[0].name)
            .putString(KEY_URL_1, next[0].url)
            .putString(KEY_NAME_2, next[1].name)
            .putString(KEY_URL_2, next[1].url)
            .putString(KEY_NAME_3, next[2].name)
            .putString(KEY_URL_3, next[2].url)
            .apply()
    }

    fun resetToDefaults(context: Context) {
        setTargets(context, defaults[0], defaults[1], defaults[2])
    }

    private fun normalizeUrl(raw: String, fallback: String): String {
        val t = raw.trim()
        if (t.isBlank()) return fallback
        if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)) return t
        return "https://$t"
    }
}
