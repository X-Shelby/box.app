package com.box.app.ui.effect

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

fun supportsAndroidRenderBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun Modifier.androidRenderBlur(
    radius: Float,
    enabled: Boolean = true
): Modifier {
    if (!enabled || radius <= 0f || !supportsAndroidRenderBlur()) return this

    val clampedRadius = radius.coerceAtLeast(0.01f)
    return graphicsLayer {
        renderEffect = RenderEffect
            .createBlurEffect(clampedRadius, clampedRadius, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
        clip = true
    }
}
