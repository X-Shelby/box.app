package com.box.app.ui.components.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import com.box.app.ui.theme.AppFonts
import top.yukonga.miuix.kmp.basic.Text

/**
 * 通用「翻牌 + 弹簧 + 脉冲」滚动数字组件。
 *
 * 适用场景：网速、订阅用量、系统指标、延迟 等动态变化的数字 / 含单位文本。
 * 不适用：IP 地址（应使用 marquee）、大段静态文本。
 *
 * 实现要点（来自 HomeLatencyCard 原私有实现的通用化）：
 * 1. **整体方向**：用前后两次完整文本中第一个数字串推断升/降，所有位数同向，
 *    解决 199→200 各位方向冲突造成的"乱跳"
 * 2. **弹簧物理**：滑动用 spring（中等刚度 + 阻尼 0.78）替代线性 tween
 * 3. **级联**：每位 28ms × index 的 fadeIn 延迟，呈"波浪"刷新
 * 4. **宽度形变**：animateContentSize 平滑过渡字符数变化（如 99→100）
 * 5. **到达脉冲**：值更新后整组 0.94→1.0 弹回（可关闭：[enablePulse] = false）
 * 6. **空闲免动**：相同字符不触发；非数字字符（小数点 / 单位 / 空格）走 fadeIn/Out
 *
 * 等宽字体（[fontFamily] 默认 [AppFonts.dataFamily]）保证字符宽度一致，
 * 避免逐位滚动时整体宽度抖动。
 */
@Composable
fun RollingNumberText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.SemiBold,
    fontFamily: FontFamily = AppFonts.dataFamily,
    enablePulse: Boolean = true
) {
    // 跟踪上一次完整文本，用于推断「整体升/降」
    var prevText by remember { mutableStateOf(text) }
    val direction: Int = remember(text, prevText) {
        val curr = parseLeadingNumber(text)
        val prev = parseLeadingNumber(prevText)
        when {
            curr == null || prev == null -> 0     // 无法判定 → 中性（按各位比较）
            curr > prev -> +1
            curr < prev -> -1
            else -> 0
        }
    }
    LaunchedEffect(text) { prevText = text }

    // 到达后的轻微脉冲（缩放）
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(text) {
        if (!enablePulse) return@LaunchedEffect
        pulse.snapTo(0.94f)
        pulse.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(
                if (enablePulse) Modifier.graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                } else Modifier
            )
            .animateContentSize(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = 0.85f
                )
            )
    ) {
        text.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    val prev = initialState
                    val curr = targetState
                    val bothDigits = prev.isDigit() && curr.isDigit()
                    if (bothDigits) {
                        val up = when {
                            direction > 0 -> true
                            direction < 0 -> false
                            else -> curr.digitToInt() >= prev.digitToInt()
                        }
                        val enterFrom: Int = if (up) +1 else -1
                        val exitTo: Int = if (up) -1 else +1
                        val slideSpec = spring<IntOffset>(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = 0.78f
                        )
                        // 级联：每位错开少量时间，多位数字呈"波浪"感
                        val stagger = (index * 28).coerceAtMost(140)
                        val fadeInSpec = tween<Float>(
                            durationMillis = 220,
                            delayMillis = stagger,
                            easing = FastOutSlowInEasing
                        )
                        val fadeOutSpec = tween<Float>(
                            durationMillis = 200,
                            easing = FastOutSlowInEasing
                        )
                        (slideInVertically(slideSpec) { it * enterFrom } + fadeIn(fadeInSpec))
                            .togetherWith(
                                slideOutVertically(slideSpec) { it * exitTo } + fadeOut(fadeOutSpec)
                            )
                    } else {
                        fadeIn(tween(180)).togetherWith(fadeOut(tween(180)))
                    }
                },
                label = "rolling_digit_$index"
            ) { ch ->
                Text(
                    text = ch.toString(),
                    style = style,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    color = color,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 解析文本中第一个数字串（含可选小数 / 负号），用于方向判断。
 * "12.5 MB/s" → 12.5；"123 ms" → 123.0；"-3.2°C" → -3.2；"--" → null
 */
private fun parseLeadingNumber(text: String): Double? =
    Regex("""-?[0-9]+(?:\.[0-9]+)?""").find(text)?.value?.toDoubleOrNull()
