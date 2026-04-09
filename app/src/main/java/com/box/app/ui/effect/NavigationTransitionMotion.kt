package com.box.app.ui.effect

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import dev.lackluster.hyperx.ui.animation.NavTransitionEasing

// ─── 默认缓动曲线 ──────────────────────────────────────

private val NavigationPushEasing = CubicBezierEasing(0.12f, 0.0f, 0.08f, 1f)
private val NavigationPopEasing = CubicBezierEasing(0.16f, 0.0f, 0.10f, 1f)
private val NavigationCancelEasing = CubicBezierEasing(0.14f, 0.0f, 0.10f, 1f)

fun navigationPushSpec(): AnimationSpec<Float> =
    tween(durationMillis = 850, easing = NavigationPushEasing)

fun navigationPopSpec(): AnimationSpec<Float> =
    tween(durationMillis = 800, easing = NavigationPopEasing)

fun navigationCancelSpec(): AnimationSpec<Float> =
    tween(durationMillis = 680, easing = NavigationCancelEasing)

// ─── HyperX 导航动画（直接引用 hyperx-compose NavTransitionEasing） ──

/** 与 HyperXNavTransitions.NavAnimationEasing 完全一致的弹簧缓动 */
private val HyperXNavEasing = NavTransitionEasing(0.8f, 0.95f)

/** 对应 HyperXNavTransitions.normalTransitionSpec — 500ms 弹簧推入 */
fun hyperXPushSpec(): AnimationSpec<Float> =
    tween(durationMillis = 500, easing = HyperXNavEasing)

/** 对应 HyperXNavTransitions.normalPopTransitionSpec — 500ms 弹簧弹出 */
fun hyperXPopSpec(): AnimationSpec<Float> =
    tween(durationMillis = 500, easing = HyperXNavEasing)

/** 对应 HyperXNavTransitions.normalPredictivePopTransitionSpec — 550ms 线性取消 */
fun hyperXCancelSpec(): AnimationSpec<Float> =
    tween(durationMillis = 550, easing = LinearEasing)

// ─── 场景进度 ──────────────────────────────────────

/** 默认五次方 smoothstep：首尾一阶 & 二阶导数均为 0 */
fun navigationSceneProgress(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    return t * t * t * (t * (t * 6f - 15f) + 10f)
}

fun navigationPredictiveBackProgress(progress: Float): Float =
    (1f - progress.coerceIn(0f, 1f) * 0.5f).coerceIn(0.5f, 1f)

/**
 * HyperX 预测性返回手势映射：使用 [NavTransitionEasing.inverseTransform]
 * 将线性手势进度转换为弹簧曲线反函数位置，确保手势取消后衔接弹簧动画时速度连续。
 */
fun hyperXPredictiveBackProgress(progress: Float): Float {
    val target = (1f - progress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    return HyperXNavEasing.inverseTransform(target)
}

/**
 * HyperX 场景进度：直接透传（弹簧缓动已内含非线性），
 * 新页从右 100% 滑入，旧页左移 25%（对应 HyperXNavTransitions 的 it / 4）
 */
fun hyperXSceneProgress(progress: Float): Float = progress.coerceIn(0f, 1f)
