package com.box.app.ui.components.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.box.app.ui.theme.AppFonts
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.box.app.R
import java.util.Locale
import androidx.compose.foundation.layout.PaddingValues
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeTwoColumnGrid(models: List<HomeCardModel>, modifier: Modifier = Modifier) {
    val rows = remember(models) { models.chunked(2) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    model = row[0],
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                if (row.size > 1) {
                    MetricCard(
                        model = row[1],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                } else {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCard(model: HomeCardModel, modifier: Modifier = Modifier) {
    val clickModifier = if (model.onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            model.onClick.invoke()
        }
    } else {
        Modifier
    }

    Card(
        modifier = modifier.then(clickModifier),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = metricCardContainerColor(model.kind))
    ) {
        val palette = metricPalette(model)
        if (model.kind == HomeMetricKind.System) {
            SystemMetricCardContent(model = model)
        } else if (model.kind == HomeMetricKind.Subscription) {
            SubscriptionMetricCardContent(model = model)
        } else if (model.kind == HomeMetricKind.Speed) {
            SpeedMetricCardContent(model = model)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.title,
                            style = MiuixTheme.textStyles.button,
                            color = palette.titleColor,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBadge(
                            kind = model.kind,
                            accent = model.accent,
                            overrideText = model.badgeText,
                            containerColor = palette.badgeContainer,
                            textColor = palette.badgeText
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    val animatedValueColor by animateColorAsState(
                        targetValue = metricValueColor(model),
                        animationSpec = tween(durationMillis = 360),
                        label = "metric_value_${model.kind}"
                    )
                    Text(
                        text = model.value,
                        style = if (model.kind == HomeMetricKind.Ip) {
                            MiuixTheme.textStyles.title4
                        } else {
                            MiuixTheme.textStyles.title2
                        },
                        fontFamily = AppFonts.dataFamily,
                        fontWeight = FontWeight.Medium,
                        color = if (model.kind == HomeMetricKind.Ip) palette.valueColor else animatedValueColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (model.subtitle.isNotBlank()) {
                        Text(
                            text = model.subtitle,
                            style = MiuixTheme.textStyles.body2,
                            color = palette.supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (model.progress != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = model.progress.coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedRectangle(6.dp)),
                            colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = model.accent,
                                backgroundColor = palette.progressTrack
                            )
                        )
                    }
                }

                if (model.onCornerAction != null && model.cornerActionIcon != null) {
                    Icon(
                            imageVector = model.cornerActionIcon,
                            contentDescription = null,
                            tint = palette.supportingColor,
                            modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 2.dp, bottom = 2.dp)
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                model.onCornerAction.invoke()
                            }
                    )
                }
            }
        }
    }
}

private fun extractSpeedValue(raw: String): String {
    val text = raw.trim()
    if (text.isBlank()) return "-"
    val idx = text.indexOf(' ')
    return if (idx in 1 until text.lastIndex) text.substring(idx + 1).trim().ifBlank { text } else text
}

private fun formatSpeedValue(raw: String): String {
    val text = raw.trim()
    if (text.isBlank() || text == "-" || text == "—") return "-"

    val pattern = Regex("""^([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?)(?:i)?B/s$""", RegexOption.IGNORE_CASE)
    val m = pattern.find(text) ?: return text
    val value = m.groupValues[1].toFloatOrNull() ?: return text
    val unit = m.groupValues[2].uppercase(Locale.US)
    val number = String.format(Locale.US, "%.1f", value)
    return if (unit.isBlank()) "${number}B/s" else "${number}${unit}B/s"
}

@Composable
private fun SpeedMetricCardContent(model: HomeCardModel) {
    val upColor = MiuixTheme.colorScheme.primary
    val downColor = homeSuccessColors().accent
    val palette = metricPalette(model)
    val upValue = remember(model.subtitle) { formatSpeedValue(extractSpeedValue(model.subtitle)) }
    val downValue = remember(model.value) { formatSpeedValue(model.value) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
    ) {
        // 背景实时图表（全卡片覆盖，渐变淡出）
        if (model.sparkDown != null && model.sparkUp != null) {
            SpeedSparkline(
                downSeries = model.sparkDown,
                upSeries = model.sparkUp,
                downColor = downColor,
                upColor = upColor,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 前景内容
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.title,
                    style = MiuixTheme.textStyles.button,
                    color = palette.titleColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MetricBadge(
                    kind = model.kind,
                    accent = model.accent,
                    overrideText = model.badgeText,
                    containerColor = palette.badgeContainer,
                    textColor = palette.badgeText
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 上传行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = null,
                    tint = upColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = upValue,
                    style = MiuixTheme.textStyles.body2,
                    fontFamily = AppFonts.dataFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = upColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 下载行
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = null,
                    tint = downColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = downValue,
                    style = MiuixTheme.textStyles.body2,
                    fontFamily = AppFonts.dataFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = downColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SubscriptionMetricCardContent(model: HomeCardModel) {
    val animatedAccentColor by animateColorAsState(
        targetValue = model.accent,
        animationSpec = tween(durationMillis = 360),
        label = "subscription_accent"
    )
    val palette = metricPalette(model)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(12.dp)
    ) {
        // 标题行：钉在顶部
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = model.title,
                style = MiuixTheme.textStyles.button,
                color = palette.titleColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            MetricBadge(
                kind = model.kind,
                accent = model.accent,
                overrideText = model.badgeText,
                containerColor = palette.badgeContainer,
                textColor = palette.badgeText
            )
        }

        // 数据区域：钉在底部，与系统卡底部对齐
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 已用：标签左 + 数值右
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_subscription_used_label),
                    style = MiuixTheme.textStyles.footnote2,
                    color = palette.supportingColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = model.value,
                    style = MiuixTheme.textStyles.footnote1,
                    fontFamily = AppFonts.dataFamily,
                    fontWeight = FontWeight.Medium,
                    color = palette.valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 总量：同上行模式
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_subscription_total_label),
                    style = MiuixTheme.textStyles.footnote2,
                    color = palette.supportingColor,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = model.subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    fontFamily = AppFonts.dataFamily,
                    fontWeight = FontWeight.Medium,
                    color = palette.valueColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (model.progress != null) {
                LinearProgressIndicator(
                    progress = model.progress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedRectangle(6.dp)),
                    colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = animatedAccentColor,
                        backgroundColor = palette.progressTrack
                    )
                )
            }
        }
    }
}

private fun parseCpuPercent(text: String): Float? {
    val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*%""").find(text) ?: return null
    return match.groupValues.getOrNull(1)?.toFloatOrNull()
}

private fun parseRamMb(text: String): Float? {
    val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*MB""", RegexOption.IGNORE_CASE).find(text) ?: return null
    return match.groupValues.getOrNull(1)?.toFloatOrNull()
}

private fun ramDisplayCeilingMb(ramMb: Float?): Int {
    val v = (ramMb ?: 0f).coerceAtLeast(0f)
    return when {
        v <= 64f -> 128
        v <= 128f -> 256
        v <= 256f -> 512
        v <= 512f -> 1024
        v <= 1024f -> 2048
        else -> 4096
    }
}

@Composable
private fun SystemMetricCardContent(model: HomeCardModel) {
    val isActive = model.isActive
    val palette = metricPalette(model)

    val cpuPercent = remember(model.value) { parseCpuPercent(model.value) }
    val ramMb = remember(model.subtitle) { parseRamMb(model.subtitle) }
    val ramCeiling = remember(ramMb) { ramDisplayCeilingMb(ramMb) }

    val cpuProgressRaw = ((cpuPercent ?: 0f) / 100f).coerceIn(0f, 1f)
    val ramProgressRaw = (((ramMb ?: 0f) / ramCeiling.toFloat())).coerceIn(0f, 1f)

    val cpuProgress by animateFloatAsState(
        targetValue = cpuProgressRaw,
        animationSpec = tween(durationMillis = 280),
        label = "system_cpu_progress"
    )
    val ramProgress by animateFloatAsState(
        targetValue = ramProgressRaw,
        animationSpec = tween(durationMillis = 280),
        label = "system_ram_progress"
    )

    val cpuColor = when {
        (cpuPercent ?: 0f) >= 100f -> homeDangerColors().accent
        (cpuPercent ?: 0f) >= 85f -> homeWarningColors().accent
        else -> homeSuccessColors().accent
    }
    val ramColor = homeInfoColors().accent
    val animatedCpuColor by animateColorAsState(
        targetValue = cpuColor,
        animationSpec = tween(durationMillis = 360),
        label = "system_cpu_color"
    )
    val animatedRamColor by animateColorAsState(
        targetValue = ramColor,
        animationSpec = tween(durationMillis = 360),
        label = "system_ram_color"
    )
    val track = palette.progressTrack

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .padding(12.dp)
    ) {
        // 标题行：钉在顶部
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = model.title,
                style = MiuixTheme.textStyles.button,
                color = palette.titleColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            MetricBadge(
                kind = model.kind,
                accent = model.accent,
                overrideText = model.badgeText,
                containerColor = palette.badgeContainer,
                textColor = palette.badgeText
            )
        }

        // 数据区域：钉在底部
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isActive) {
                SystemMetricBar(
                    label = "CPU",
                    valueText = cpuPercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--",
                    progress = cpuProgress,
                    color = animatedCpuColor,
                    trackColor = track
                )
                SystemMetricBar(
                    label = "RAM",
                    valueText = ramMb?.let { String.format(Locale.US, "%.1fMB", it) } ?: "--",
                    rightHint = "/${ramCeiling}MB",
                    progress = ramProgress,
                    color = animatedRamColor,
                    trackColor = track
                )
            } else {
                SystemMetricDisabledHint()
            }
        }
    }
}

@Composable
private fun SystemMetricDisabledHint() {
    val palette = metricPaletteForKind(
        kind = HomeMetricKind.System,
        accent = MiuixTheme.colorScheme.secondary,
        isActive = false
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(10.dp))
            .background(palette.badgeContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.home_system_disabled_hint),
            style = MiuixTheme.textStyles.body2,
            color = palette.supportingColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SystemMetricBar(
    label: String,
    valueText: String,
    progress: Float,
    color: Color,
    trackColor: Color,
    rightHint: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = valueText,
                style = MiuixTheme.textStyles.footnote1,
                fontFamily = AppFonts.dataFamily,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (!rightHint.isNullOrBlank()) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = rightHint,
                    style = MiuixTheme.textStyles.footnote2,
                    fontFamily = AppFonts.dataFamily,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1
                )
            }
        }

        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedRectangle(6.dp)),
            colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = color,
                backgroundColor = trackColor
            )
        )
    }
}

@Composable
fun MetricBadge(
    kind: HomeMetricKind,
    accent: Color,
    modifier: Modifier = Modifier,
    overrideText: String? = null,
    containerColor: Color? = null,
    textColor: Color? = null
) {
    val text = overrideText ?: when (kind) {
        HomeMetricKind.Service -> stringResource(R.string.home_badge_service)
        HomeMetricKind.Ip -> stringResource(R.string.home_badge_ip)
        HomeMetricKind.Speed -> stringResource(R.string.home_badge_net)
        HomeMetricKind.Latency -> stringResource(R.string.home_badge_ms)
        HomeMetricKind.Subscription -> stringResource(R.string.home_badge_sub)
        HomeMetricKind.System -> stringResource(R.string.home_badge_sys)
    }

    val palette = metricBadgePalette(kind = kind, accent = accent)

    Card(
        modifier = modifier
            .clip(Capsule()),
        cornerRadius = 999.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
        colors = CardDefaults.defaultColors(color = containerColor ?: palette.first)
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.SemiBold,
            color = textColor ?: palette.second
        )
    }
}

/**
 * 实时网速图表：直接绘制最新数据，仅 Y 轴比例平滑过渡。
 *
 * 数据每秒 takeLast(60) 自然左移一位，相邻帧差异极小，
 * 无需逐点插值动画，避免 snapTo/animateTo 重播问题。
 */
@Composable
fun SpeedSparkline(
    downSeries: List<Float>,
    upSeries: List<Float>,
    downColor: Color,
    upColor: Color,
    modifier: Modifier = Modifier
) {
    // 仅 Y 轴比例需要平滑过渡（防止峰值出现/消失时整体跳变）
    val rawMax = ((downSeries + upSeries).maxOrNull() ?: 0f).coerceAtLeast(1f)
    val animMaxV by animateFloatAsState(
        targetValue = rawMax,
        animationSpec = tween(durationMillis = 800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "sparkline_max"
    )

    Canvas(modifier = modifier.graphicsLayer { clip = true }) {
        val w = size.width
        val h = size.height
        val topMargin = h * 0.12f
        val chartH = h - topMargin

        fun buildSmoothPath(series: List<Float>): Path {
            val p = Path()
            if (series.isEmpty()) return p
            val n = series.size
            fun point(i: Int): Offset {
                val x = if (n == 1) w / 2f else (i.toFloat() / (n - 1).toFloat()) * w
                val v = (series[i] / animMaxV).coerceIn(0f, 1f)
                val y = topMargin + chartH * (1f - v)
                return Offset(x, y)
            }
            val p0 = point(0)
            p.moveTo(p0.x, p0.y)
            if (n == 1) return p
            for (i in 1 until n) {
                val prev = point(i - 1)
                val cur = point(i)
                val cpX = (prev.x + cur.x) / 2f
                p.cubicTo(cpX, prev.y, cpX, cur.y, cur.x, cur.y)
            }
            return p
        }

        fun buildFillPath(series: List<Float>): Path {
            val stroke = buildSmoothPath(series)
            if (series.isEmpty()) return stroke
            val n = series.size
            val lastX = if (n == 1) w / 2f else w
            val fill = Path()
            fill.addPath(stroke)
            fill.lineTo(lastX, h)
            fill.lineTo(0f, h)
            fill.close()
            return fill
        }

        // 下载：渐变填充 + 描边
        drawPath(
            path = buildFillPath(downSeries),
            brush = Brush.verticalGradient(
                colors = listOf(downColor.copy(alpha = 0.25f), Color.Transparent),
                startY = topMargin, endY = h
            ),
            style = Fill
        )
        drawPath(
            path = buildSmoothPath(downSeries),
            color = downColor.copy(alpha = 0.8f),
            style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 上传：渐变填充 + 描边
        drawPath(
            path = buildFillPath(upSeries),
            brush = Brush.verticalGradient(
                colors = listOf(upColor.copy(alpha = 0.15f), Color.Transparent),
                startY = topMargin, endY = h
            ),
            style = Fill
        )
        drawPath(
            path = buildSmoothPath(upSeries),
            color = upColor.copy(alpha = 0.6f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun metricCardContainerColor(kind: HomeMetricKind): Color = when (kind) {
    HomeMetricKind.Ip -> metricPaletteForKind(kind).containerColor
    HomeMetricKind.Speed -> metricPaletteForKind(kind).containerColor
    HomeMetricKind.Subscription -> metricPaletteForKind(kind).containerColor
    HomeMetricKind.System -> metricPaletteForKind(kind).containerColor
    HomeMetricKind.Service -> metricPaletteForKind(kind).containerColor
    HomeMetricKind.Latency -> metricPaletteForKind(kind).containerColor
}

@Composable
private fun metricValueColor(model: HomeCardModel): Color = metricPalette(model).valueColor

private data class MetricPalette(
    val containerColor: Color,
    val titleColor: Color,
    val valueColor: Color,
    val supportingColor: Color,
    val badgeContainer: Color,
    val badgeText: Color,
    val progressTrack: Color
)

@Composable
private fun metricPalette(model: HomeCardModel): MetricPalette = metricPaletteForKind(
    kind = model.kind,
    accent = model.accent,
    isActive = model.isActive
)

@Composable
private fun metricPaletteForKind(
    kind: HomeMetricKind,
    accent: Color = MiuixTheme.colorScheme.primary,
    isActive: Boolean = true
): MetricPalette {
    val scheme = MiuixTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return MetricPalette(
        containerColor = scheme.surfaceContainer,
        titleColor = scheme.onSurface,
        valueColor = when (kind) {
            HomeMetricKind.Service, HomeMetricKind.Latency -> accent
            HomeMetricKind.Speed -> scheme.onSurface
            HomeMetricKind.System -> if (isActive) scheme.onSurface else scheme.onSurfaceSecondary
            else -> scheme.onSurface
        },
        supportingColor = scheme.onSurfaceSecondary,
        badgeContainer = when (kind) {
            // NET: 主色蓝，保持不变
            HomeMetricKind.Speed -> scheme.primary
            // SUB/延迟: 紫色调 — 传达订阅/数据的高级感
            HomeMetricKind.Subscription, HomeMetricKind.Latency ->
                if (dark) Color(0xFF311B92) else Color(0xFFD1C4E9)
            // WAN/IP: 青色调 — 传达地理/连接位置
            HomeMetricKind.Ip ->
                if (dark) Color(0xFF004D40) else Color(0xFFB2DFDB)
            // SYS: 蓝灰调 — 传达技术/系统感
            HomeMetricKind.System -> if (isActive) {
                if (dark) Color(0xFF37474F) else Color(0xFFCFD8DC)
            } else {
                if (dark) Color(0xFF2D3B41) else Color(0xFFCFD8DC)
            }
            HomeMetricKind.Service ->
                if (dark) Color(0xFF37474F) else Color(0xFFCFD8DC)
        },
        badgeText = when (kind) {
            HomeMetricKind.Speed -> scheme.onPrimary
            HomeMetricKind.Subscription, HomeMetricKind.Latency ->
                if (dark) Color(0xFFB39DDB) else Color(0xFF4527A0)
            HomeMetricKind.Ip ->
                if (dark) Color(0xFF80CBC4) else Color(0xFF00695C)
            HomeMetricKind.System -> if (isActive) {
                if (dark) Color(0xFFB0BEC5) else Color(0xFF263238)
            } else {
                if (dark) Color(0xFF607D8B) else Color(0xFF607D8B)
            }
            HomeMetricKind.Service ->
                if (dark) Color(0xFFB0BEC5) else Color(0xFF263238)
        },
        progressTrack = scheme.surfaceContainerHighest
    )
}

@Composable
private fun metricBadgePalette(kind: HomeMetricKind, accent: Color): Pair<Color, Color> {
    val palette = metricPaletteForKind(kind = kind, accent = accent)
    return palette.badgeContainer to palette.badgeText
}
