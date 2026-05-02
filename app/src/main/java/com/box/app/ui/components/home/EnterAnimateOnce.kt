package com.box.app.ui.components.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay

/**
 * 一次性入场动画包装器（用于首页分阶段编排，D1）。
 *
 * 行为：
 *   - 首次组合时不可见 → 延迟 [delayMs] → 触发 fadeIn + slideUp
 *   - 状态用 [rememberSaveable] 保留，LazyColumn 内 item 离屏再回来不会重放动画
 *   - 滚动 / 数据更新触发的重组也不会重放（visible=true 持久）
 *
 * 入场曲线：
 *   - alpha: 0 → 1 with tween 280ms
 *   - translationY: itemHeight/6 → 0 with spring(MediumLow, damping 0.85)
 *
 * 推荐 delay 阶梯（单位 ms）：
 *   - header: 0
 *   - hero:   60
 *   - quick:  120
 *   - latency:180
 *   - grid:   240
 */
@Composable
fun EnterAnimateOnce(
    delayMs: Int,
    content: @Composable () -> Unit
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!visible) {
            if (delayMs > 0) delay(delayMs.toLong())
            visible = true
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 280)) +
                slideInVertically(
                    initialOffsetY = { it / 6 },
                    animationSpec = spring<IntOffset>(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = 0.85f
                    )
                )
    ) {
        content()
    }
}
