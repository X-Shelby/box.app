package com.box.app.ui.components.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class LatencySeverity {
    Testing,
    Unknown,
    Fast,
    Medium,
    Slow,
    Error
}

/**
 * 延迟卡片 — 仿参考设计
 *
 * ┌──────────────  状态 ● ⚙ ↻ ─┐  ← 操作行（actions 靠右，无段标题）
 * └─────────────────────────────┘
 * ┌─────────────────────────────┐  ← Card
 * │ Baidu ●  Cloudflare ●  Google ● │
 * │ -- ms     -- ms         -- ms   │
 * └─────────────────────────────┘
 */
@Composable
fun HomeLatencyCard(
    label1: String,
    baidu: String,
    label2: String,
    cloudflare: String,
    label3: String,
    google: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    onOpenTargets: () -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val badgeRes = latencyBadgeTexts(loading, listOf(baidu, cloudflare, google))
    val statusAccent = when (badgeRes) {
        R.string.home_latency_badge_ok -> homeSuccessColors().accent
        R.string.home_latency_badge_part -> homeWarningColors().accent
        R.string.home_latency_badge_down -> homeDangerColors().accent
        else -> MiuixTheme.colorScheme.onSurfaceSecondary
    }
    val animatedStatusColor by animateColorAsState(
        targetValue = statusAccent,
        animationSpec = tween(durationMillis = 360),
        label = "home_latency_status_color"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // ── 操作行（无标题，actions 整体靠右）──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // 状态文字 + 状态点
            Text(
                text = stringResource(badgeRes),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(Capsule())
                    .background(animatedStatusColor)
            )
            // 配置延迟目标入口（迁自设置 → 延迟目标）
            IconButton(
                onClick = onOpenTargets,
                modifier = Modifier.size(28.dp),
                backgroundColor = Color.Transparent,
                cornerRadius = 8.dp
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.settings_latency_targets_title),
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
            // 刷新按钮
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(28.dp),
                backgroundColor = Color.Transparent,
                cornerRadius = 8.dp,
                enabled = !loading
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // ── 数据卡片 ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = if (compact) 14.dp else 18.dp,
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = if (compact) 12.dp else 14.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LatencyChip(
                    label = label1,
                    value = baidu,
                    loading = loading,
                    modifier = Modifier.weight(1f)
                )
                LatencyChip(
                    label = label2,
                    value = cloudflare,
                    loading = loading,
                    modifier = Modifier.weight(1f)
                )
                LatencyChip(
                    label = label3,
                    value = google,
                    loading = loading,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 单个延迟条目：
 * ┌─────────────┐
 * │ Label ●     │  ← 名称 + 状态点
 * │ -- ms       │  ← 数值（使用等宽数据字体）
 * └─────────────┘
 */
/**
 * 延迟单元（B1：呼吸点 + mini sparkline）：
 * ┌─────────────┐
 * │  ◉ Label    │  ← 状态点（呼吸/闪烁）+ 名称（居中）
 * │   -- ms     │  ← 数值（居中，逐字滚动）
 * │ ⌒‿⌒‿⌒̇      │  ← Mini sparkline（最近 [MAX_SPARK_POINTS] 次延迟历史）
 * └─────────────┘
 */
@Composable
private fun LatencyChip(
    label: String,
    value: String,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    val severity = latencySeverity(value, loading)
    val dotColor = latencyDotColor(severity)
    val valueColor = latencyValueColor(severity)
    val animatedDotColor by animateColorAsState(
        targetValue = dotColor,
        animationSpec = tween(durationMillis = 360),
        label = "home_latency_dot_$label"
    )
    val animatedValueColor by animateColorAsState(
        targetValue = valueColor,
        animationSpec = tween(durationMillis = 360),
        label = "home_latency_value_$label"
    )

    // ── 呼吸脉冲（B1）：Fast/Medium 慢呼吸；Testing 快闪；其它静止 ──
    val pulseScale by rememberLatencyPulseScale(severity, label)

    // ── Sparkline 历史（B1）：每次解析成功的毫秒数 push 入采样窗口 ──
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(value, loading) {
        val ms = parseLatencyMillis(value, loading) ?: return@LaunchedEffect
        history.add(ms)
        while (history.size > MAX_SPARK_POINTS) history.removeAt(0)
    }

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 状态点 + 名称
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        // 呼吸时 alpha 同步起伏，强化"脉搏"感（仅缩放变化时生效）
                        alpha = if (pulseScale != 1f) 0.55f + 0.45f * pulseScale else 1f
                    }
                    .clip(Capsule())
                    .background(animatedDotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 数值：逐字符滚动动画（通用 RollingNumberText 组件）
        RollingNumberText(
            text = normalizeLatency(value),
            style = MiuixTheme.textStyles.body1,
            color = animatedValueColor
        )
        // Mini sparkline（≥ 2 采样点才绘制；不足时占位防止抖动）
        if (history.size >= 2) {
            LatencySparkline(
                values = history.toList(),
                color = animatedDotColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .padding(horizontal = 2.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

// ── B1 helpers ─────────────────────────────────────────────────────────────

private const val MAX_SPARK_POINTS = 12

/** 从延迟字符串提取毫秒数；loading / "..." / "-" 返回 null */
private fun parseLatencyMillis(value: String, loading: Boolean): Float? {
    if (loading) return null
    val trimmed = value.trim()
    if (trimmed.isBlank() || trimmed == "-" || trimmed == "—" || trimmed == "...") return null
    return Regex("""([0-9]+(?:\.[0-9]+)?)""")
        .find(trimmed)?.groupValues?.getOrNull(1)?.toFloatOrNull()
}

/** 状态点呼吸缩放：Fast/Medium 1500ms；Testing 800ms；其它返回静止常量 1f */
@Composable
private fun rememberLatencyPulseScale(
    severity: LatencySeverity,
    label: String
): State<Float> {
    val durationMs = when (severity) {
        LatencySeverity.Fast, LatencySeverity.Medium -> 1500
        LatencySeverity.Testing -> 800
        else -> 0
    }
    if (durationMs == 0) {
        return remember { mutableStateOf(1f) }
    }
    val transition = rememberInfiniteTransition(label = "latency_pulse_${label}_transition")
    return transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "latency_pulse_$label"
    )
}

/** Mini sparkline — 用最大-最小归一化，留 7.5% 上下边距，端点亮色 */
@Composable
private fun LatencySparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val maxVal = (values.maxOrNull() ?: return@Canvas).coerceAtLeast(1f)
        val minVal = (values.minOrNull() ?: return@Canvas).coerceAtMost(maxVal)
        val range = (maxVal - minVal).coerceAtLeast(1f)
        val stepX = w / (values.size - 1).coerceAtLeast(1)
        val topPad = h * 0.075f
        val bottomPad = h * 0.075f
        val drawH = h - topPad - bottomPad
        val pts = values.mapIndexed { i, v ->
            val x = i * stepX
            val norm = (v - minVal) / range
            // 延迟越低越好 → 越靠下方（视觉上"贴底" = 网络通畅）
            val y = topPad + (1f - norm) * drawH
            Offset(x, y)
        }
        val strokeWidthPx = 1.5.dp.toPx()
        val path = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
        // 折线（半透明）
        drawPath(
            path = path,
            color = color.copy(alpha = 0.55f),
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        // 末位端点（强调"当前值"）
        drawCircle(
            color = color,
            radius = 2.dp.toPx(),
            center = pts.last()
        )
    }
}

/**
 * 延迟数值动画 — 已抽取为通用 [RollingNumberText]（见同包 RollingNumberText.kt）。
 * 此处保留 LatencyChip 的"严重度配色 + 状态点脉冲"逻辑，数字渲染走通用实现。
 *
 * 通用组件仍提供「整体方向 / 弹簧 / 级联 / 宽度形变 / 到达脉冲 / 空闲免动」六大特性，
 * 适用于所有 dataFamily 数字（网速、订阅、系统指标、延迟）。
 */
@Composable
private fun latencyDotColor(severity: LatencySeverity): Color {
    val scheme = MiuixTheme.colorScheme
    return when (severity) {
        LatencySeverity.Fast -> homeSuccessColors().accent
        LatencySeverity.Medium -> homeInfoColors().accent
        LatencySeverity.Slow -> homeWarningColors().accent
        LatencySeverity.Error -> homeDangerColors().accent
        LatencySeverity.Testing -> scheme.primary
        LatencySeverity.Unknown -> scheme.onSurfaceSecondary.copy(alpha = 0.4f)
    }
}

@Composable
private fun latencyValueColor(severity: LatencySeverity): Color {
    val scheme = MiuixTheme.colorScheme
    return when (severity) {
        LatencySeverity.Unknown -> scheme.onSurfaceSecondary
        LatencySeverity.Testing -> scheme.onSurfaceSecondary
        else -> scheme.onSurface
    }
}

private fun latencySeverity(value: String, loading: Boolean = false): LatencySeverity {
    val trimmed = value.trim()
    if (loading || trimmed == "...") return LatencySeverity.Testing
    if (trimmed.isBlank() || trimmed == "-" || trimmed == "—") return LatencySeverity.Unknown
    if (trimmed.equals("timeout", ignoreCase = true) ||
        trimmed.equals("down", ignoreCase = true) ||
        trimmed.equals("DNS", ignoreCase = true) ||
        trimmed.equals("N/A", ignoreCase = true) ||
        trimmed.startsWith("HTTP ", ignoreCase = true)
    ) return LatencySeverity.Error

    val latencyMs = Regex("""([0-9]+(?:\.[0-9]+)?)""")
        .find(trimmed)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        ?: return LatencySeverity.Unknown

    return when {
        latencyMs <= 80f -> LatencySeverity.Fast
        latencyMs <= 180f -> LatencySeverity.Medium
        latencyMs <= 400f -> LatencySeverity.Slow
        else -> LatencySeverity.Error
    }
}

private fun normalizeLatency(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank() || trimmed == "-" || trimmed == "—") return "-- ms"
    if (trimmed == "...") return trimmed
    if (trimmed.endsWith("ms", ignoreCase = true)) return trimmed
    return when {
        trimmed.all { it.isDigit() } -> "${trimmed} ms"
        else -> trimmed
    }
}

private fun latencyBadgeTexts(loading: Boolean, values: List<String>): Int {
    if (loading || values.any { it.trim() == "..." }) return R.string.home_latency_badge_test
    val severities = values.map { latencySeverity(it) }
    if (severities.all { it == LatencySeverity.Error || it == LatencySeverity.Unknown }) {
        return R.string.home_latency_badge_down
    }
    if (severities.any { it == LatencySeverity.Error || it == LatencySeverity.Unknown }) {
        return R.string.home_latency_badge_part
    }
    return R.string.home_latency_badge_ok
}
