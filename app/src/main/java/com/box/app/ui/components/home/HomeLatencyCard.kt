package com.box.app.ui.components.home

import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.theme.AppFonts
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

private data class LatencyTone(
    val headlineColor: Color
)

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
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val badge = latencyBadgeTexts(loading, listOf(baidu, cloudflare, google))
    val successColors = homeSuccessColors()
    val warningColors = homeWarningColors()
    val dangerColors = homeDangerColors()
    val neutralColors = homeNeutralColors()
    val badgeContainer = when (badge) {
        R.string.home_latency_badge_ok -> successColors.container
        R.string.home_latency_badge_part -> warningColors.container
        R.string.home_latency_badge_down -> dangerColors.container
        else -> neutralColors.container
    }
    val badgeTextColor = when (badge) {
        R.string.home_latency_badge_down -> dangerColors.onContainer
        R.string.home_latency_badge_ok -> successColors.onContainer
        R.string.home_latency_badge_part -> warningColors.onContainer
        else -> neutralColors.onContainer
    }
    val animatedBadgeBg by animateColorAsState(
        targetValue = badgeContainer,
        animationSpec = tween(durationMillis = 360),
        label = "home_latency_badge_bg"
    )
    val animatedBadgeText by animateColorAsState(
        targetValue = badgeTextColor,
        animationSpec = tween(durationMillis = 360),
        label = "home_latency_badge_text"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = if (compact) 14.dp else 16.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
    ) {
        if (compact) {
            // 紧凑：单行 标题 | 三列值 | badge | 刷新
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.home_latency_title),
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LatencyChip(label = label1, value = baidu, modifier = Modifier.weight(1f))
                    LatencyChip(label = label2, value = cloudflare, modifier = Modifier.weight(1f))
                    LatencyChip(label = label3, value = google, modifier = Modifier.weight(1f))
                }
                Box(
                    modifier = Modifier
                        .clip(Capsule())
                        .background(animatedBadgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(badge),
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Medium,
                        color = animatedBadgeText
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(24.dp),
                    backgroundColor = Color.Transparent,
                    cornerRadius = 8.dp,
                    enabled = !loading
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        } else {
            // 宽松：标题行 + 三列数值行，更舒适的布局
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.home_latency_title),
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(Capsule())
                            .background(animatedBadgeBg)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(badge),
                            style = MiuixTheme.textStyles.footnote2,
                            fontWeight = FontWeight.Medium,
                            color = animatedBadgeText
                        )
                    }
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
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LatencyChip(label = label1, value = baidu, modifier = Modifier.weight(1f))
                    LatencyChip(label = label2, value = cloudflare, modifier = Modifier.weight(1f))
                    LatencyChip(label = label3, value = google, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LatencyChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val tone = latencyTone(value)
    val animatedValueColor by animateColorAsState(
        targetValue = tone.headlineColor,
        animationSpec = tween(durationMillis = 360),
        label = "home_latency_value_$label"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = normalizeLatency(value),
            style = MiuixTheme.textStyles.footnote1,
            fontFamily = AppFonts.dataFamily,
            fontWeight = FontWeight.SemiBold,
            color = animatedValueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun latencyTone(value: String): LatencyTone {
    val scheme = MiuixTheme.colorScheme
    val gray = scheme.onSurfaceSecondary
    val success = homeSuccessColors()
    val info = homeInfoColors()
    val warning = homeWarningColors()
    val danger = homeDangerColors()

    return when (latencySeverity(value)) {
        LatencySeverity.Fast -> LatencyTone(headlineColor = success.accent)
        LatencySeverity.Medium -> LatencyTone(headlineColor = info.accent)
        LatencySeverity.Slow -> LatencyTone(headlineColor = warning.accent)
        LatencySeverity.Error -> LatencyTone(headlineColor = danger.accent)
        LatencySeverity.Testing -> LatencyTone(headlineColor = scheme.onSurface)
        LatencySeverity.Unknown -> LatencyTone(headlineColor = gray)
    }
}

private fun latencySeverity(value: String): LatencySeverity {
    val trimmed = value.trim()
    if (trimmed == "...") return LatencySeverity.Testing
    if (
        trimmed.isBlank() ||
        trimmed == "-" ||
        trimmed == "—"
    ) {
        return LatencySeverity.Unknown
    }
    if (trimmed.equals("timeout", ignoreCase = true) || trimmed.equals("down", ignoreCase = true)) {
        return LatencySeverity.Error
    }

    val latencyMs = Regex("""([0-9]+(?:\.[0-9]+)?)""")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.toFloatOrNull()
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
    if (trimmed.isBlank()) return "-"
    if (trimmed == "...") return trimmed
    if (trimmed.endsWith("ms", ignoreCase = true)) return trimmed
    return when {
        trimmed.all { it.isDigit() } -> "${trimmed}ms"
        else -> trimmed
    }
}

private fun latencyBadgeTexts(loading: Boolean, values: List<String>): Int {
    if (loading || values.any { it.trim() == "..." }) return R.string.home_latency_badge_test

    val severities = values.map { latencySeverity(it) }

    // 全部无响应/错误
    if (severities.all { it == LatencySeverity.Error || it == LatencySeverity.Unknown }) {
        return R.string.home_latency_badge_down
    }
    // 存在不可达（错误/超时/无响应）
    if (severities.any { it == LatencySeverity.Error || it == LatencySeverity.Unknown }) {
        return R.string.home_latency_badge_part
    }
    return R.string.home_latency_badge_ok
}
