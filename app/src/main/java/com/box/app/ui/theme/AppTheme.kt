package com.box.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.box.app.utils.ThemeManager

data class AppColors(
    val pageBg: Color,
    val card: Color,
    val cardAlt: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

@Composable
fun appColors(): AppColors {
    val isDark = ThemeManager.shouldUseDarkTheme()
    val trueBlack by ThemeManager.trueBlack.collectAsState()
    
    return if (isDark) {
        AppColors(
            pageBg = if (trueBlack) Color.Black else Color(0xFF0A0C0F),
            card = Color(0xFF1C1F26),
            cardAlt = Color(0xFF252930),
            divider = Color.White.copy(alpha = 0.09f),
            textPrimary = Color(0xFFF5F7FA),
            textSecondary = Color(0xFFB4BCC8)
        )
    } else {
        AppColors(
            pageBg = Color(0xFFF6F8FA),
            card = Color(0xFFFFFFFF),
            cardAlt = Color(0xFFF1F3F5),
            divider = Color.Black.copy(alpha = 0.07f),
            textPrimary = Color(0xFF0D1117),
            textSecondary = Color(0xFF57606A)
        )
    }
}

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val systemBarSettings by ThemeManager.systemBarSettings.collectAsState()
    
    val scheme = if (isDark) {
        darkColorScheme(
            primary = Color(0xFF58A6FF),
            onPrimary = Color(0xFF000000),
            primaryContainer = Color(0xFF1F6FEB),
            onPrimaryContainer = Color(0xFFFFFFFF),
            background = c.pageBg,
            surface = c.card,
            surfaceVariant = c.cardAlt,
            onBackground = c.textPrimary,
            onSurface = c.textPrimary,
            onSurfaceVariant = c.textSecondary,
            outline = c.divider
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0969DA),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF6CB6FF),
            onPrimaryContainer = Color(0xFF000000),
            background = c.pageBg,
            surface = c.card,
            surfaceVariant = c.cardAlt,
            onBackground = c.textPrimary,
            onSurface = c.textPrimary,
            onSurfaceVariant = c.textSecondary,
            outline = c.divider
        )
    }

    MaterialTheme(colorScheme = scheme) {
        val view = LocalView.current
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window

            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

            WindowCompat.setDecorFitsSystemWindows(window, false)
            
            // Apply system bar settings
            val statusBarColor = when (systemBarSettings.statusBar) {
                com.box.app.utils.SystemBarMode.TRANSPARENT -> android.graphics.Color.TRANSPARENT
                com.box.app.utils.SystemBarMode.OPAQUE -> c.pageBg.toArgb()
            }
            
            val navigationBarColor = when (systemBarSettings.navigationBar) {
                com.box.app.utils.SystemBarMode.TRANSPARENT -> android.graphics.Color.TRANSPARENT
                com.box.app.utils.SystemBarMode.OPAQUE -> c.pageBg.toArgb()
            }
            
            @Suppress("DEPRECATION")
            window.statusBarColor = statusBarColor
            @Suppress("DEPRECATION")
            window.navigationBarColor = navigationBarColor
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                window.isStatusBarContrastEnforced = false
                @Suppress("DEPRECATION")
                window.isNavigationBarContrastEnforced = false
            }

            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }

        val contentModifier = Modifier
            .then(
                if (systemBarSettings.statusBar == com.box.app.utils.SystemBarMode.OPAQUE) {
                    Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                    Modifier
                }
            )
            .then(
                if (systemBarSettings.navigationBar == com.box.app.utils.SystemBarMode.OPAQUE) {
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                } else {
                    Modifier
                }
            )

        Surface(modifier = Modifier.fillMaxSize(), color = c.pageBg) {
            Box(modifier = contentModifier.fillMaxSize()) {
                content()
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// Additional helper functions for compatibility
@Composable
fun appAccentColor(): Color {
    val isDark = ThemeManager.shouldUseDarkTheme()
    return if (isDark) Color(0xFF6CB6FF) else Color(0xFF0969DA)
}

@Composable
fun appErrorColor(): Color {
    val isDark = ThemeManager.shouldUseDarkTheme()
    return if (isDark) Color(0xFFFF6B6B) else Color(0xFFB42318)
}

@Composable
fun isDark() = ThemeManager.shouldUseDarkTheme()