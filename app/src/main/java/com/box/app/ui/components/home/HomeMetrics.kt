package com.box.app.ui.components.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.box.app.R
import com.box.app.ui.theme.appColors
import com.box.app.utils.ThemeManager

@Composable
fun HomeTwoColumnGrid(models: List<HomeCardModel>) {
    val rows = remember(models) { models.chunked(2) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(model = row[0], modifier = Modifier.weight(1f))
                if (row.size > 1) {
                    MetricCard(model = row[1], modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun MetricCard(model: HomeCardModel, modifier: Modifier = Modifier) {
    val c = appColors()
    val container = c.card

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
        shape = RoundedRectangle(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = model.value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary,
                    maxLines = if (model.kind == HomeMetricKind.Ip) 2 else 1,
                    overflow = if (model.kind == HomeMetricKind.Ip) TextOverflow.Clip else TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (model.subtitle.isNotBlank()) {
                    Text(
                        text = model.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (model.progress != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { model.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(Capsule()),
                        color = model.accent,
                        trackColor = c.divider
                    )
                }
            }

            MetricBadge(
                kind = model.kind,
                accent = model.accent,
                overrideText = model.badgeText,
                modifier = Modifier.align(Alignment.TopEnd)
            )

            if (model.kind == HomeMetricKind.Speed && model.sparkDown != null && model.sparkUp != null) {
                val isDark = ThemeManager.shouldUseDarkTheme()
                val down = if (isDark) Color(0xFF79C6FF) else Color(0xFF1E6EA8)
                val up = if (isDark) Color(0xFF7DE3B5) else Color(0xFF12936A)
                SpeedSparkline(
                    downSeries = model.sparkDown,
                    upSeries = model.sparkUp,
                    downColor = down,
                    upColor = up,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                        .size(width = 56.dp, height = 26.dp)
                )
            }

            if (model.onCornerAction != null && model.cornerActionIcon != null) {
                Icon(
                    imageVector = model.cornerActionIcon,
                    contentDescription = null,
                    tint = c.textSecondary,
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

@Composable
fun MetricBadge(kind: HomeMetricKind, accent: Color, modifier: Modifier = Modifier, overrideText: String? = null) {
    val c = appColors()
    val text = overrideText ?: when (kind) {
        HomeMetricKind.Service -> stringResource(R.string.home_badge_service)
        HomeMetricKind.Ip -> stringResource(R.string.home_badge_ip)
        HomeMetricKind.Speed -> stringResource(R.string.home_badge_net)
        HomeMetricKind.Latency -> stringResource(R.string.home_badge_ms)
        HomeMetricKind.Subscription -> stringResource(R.string.home_badge_sub)
        HomeMetricKind.System -> stringResource(R.string.home_badge_sys)
    }

    val isDark = ThemeManager.shouldUseDarkTheme()
    val bg = accent.copy(alpha = if (isDark) 0.18f else 0.12f)

    Box(
        modifier = modifier
            .clip(Capsule())
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = c.textPrimary
        )
    }
}

@Composable
fun SpeedSparkline(
    downSeries: List<Float>,
    upSeries: List<Float>,
    downColor: Color,
    upColor: Color,
    strokeWidth: Float = 2f,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val track = c.divider.copy(alpha = 0.35f)
    val all = (downSeries + upSeries)
    val maxV = (all.maxOrNull() ?: 0f).coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawLine(track, start = Offset(0f, h - 1f), end = Offset(w, h - 1f), strokeWidth = 1f)

        fun buildSmoothPath(series: List<Float>): Path {
            val p = Path()
            if (series.isEmpty()) return p
            val n = series.size
            fun point(i: Int): Offset {
                val x = if (n == 1) 0f else (i.toFloat() / (n - 1).toFloat()) * w
                val v = (series[i] / maxV).coerceIn(0f, 1f)
                val y = h - (v * (h - 2f))
                return Offset(x, y)
            }

            val p0 = point(0)
            p.moveTo(p0.x, p0.y)
            if (n == 1) return p

            // Quadratic smoothing using midpoints.
            for (i in 1 until n) {
                val prev = point(i - 1)
                val cur = point(i)
                val mid = Offset((prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                if (i == 1) {
                    p.quadraticTo(prev.x, prev.y, mid.x, mid.y)
                } else {
                    p.quadraticTo(prev.x, prev.y, mid.x, mid.y)
                }
                if (i == n - 1) {
                    p.quadraticTo(cur.x, cur.y, cur.x, cur.y)
                }
            }
            return p
        }

        val downPath = buildSmoothPath(downSeries)
        val upPath = buildSmoothPath(upSeries)
        drawPath(
            path = downPath,
            color = downColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = upPath,
            color = upColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
