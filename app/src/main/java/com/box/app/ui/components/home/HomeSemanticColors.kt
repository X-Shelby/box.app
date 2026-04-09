package com.box.app.ui.components.home

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class HomeSemanticColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color
)

/** 运行正常 — 绿 */
@Composable
internal fun homeSuccessColors(): HomeSemanticColors {
    val dark = isSystemInDarkTheme()
    return HomeSemanticColors(
        accent = if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32),
        container = if (dark) Color(0xFF1C3B20) else Color(0xFFE8F5E9),
        onContainer = if (dark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
    )
}

/** 信息 — 蓝 (primary) */
@Composable
internal fun homeInfoColors(): HomeSemanticColors = HomeSemanticColors(
    accent = MiuixTheme.colorScheme.primary,
    container = MiuixTheme.colorScheme.primaryContainer,
    onContainer = MiuixTheme.colorScheme.onPrimaryContainer
)

/** 警告 — 琥珀 */
@Composable
internal fun homeWarningColors(): HomeSemanticColors {
    val dark = isSystemInDarkTheme()
    return HomeSemanticColors(
        accent = if (dark) Color(0xFFFFB74D) else Color(0xFFE65100),
        container = if (dark) Color(0xFF2D2416) else Color(0xFFFFF3E0),
        onContainer = if (dark) Color(0xFFFFCC80) else Color(0xFFBF360C)
    )
}

/** 危险 — 红 */
@Composable
internal fun homeDangerColors(): HomeSemanticColors {
    val dark = isSystemInDarkTheme()
    return HomeSemanticColors(
        accent = if (dark) Color(0xFFEF5350) else Color(0xFFD32F2F),
        container = if (dark) Color(0xFF2D1618) else Color(0xFFFFEBEE),
        onContainer = if (dark) Color(0xFFEF9A9A) else Color(0xFFC62828)
    )
}

/** 中性 — 灰 */
@Composable
internal fun homeNeutralColors(): HomeSemanticColors = HomeSemanticColors(
    accent = MiuixTheme.colorScheme.onSurfaceSecondary,
    container = MiuixTheme.colorScheme.secondaryContainer,
    onContainer = MiuixTheme.colorScheme.onSecondaryContainer
)
