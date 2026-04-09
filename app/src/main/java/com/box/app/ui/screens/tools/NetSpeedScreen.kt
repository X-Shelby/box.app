package com.box.app.ui.screens.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.withFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.HomeMetricsApi
import com.box.app.data.repo.HomeRepository
import com.box.app.ui.components.contentPaddingWithNavBars
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class ConnFilter { Active, Closed }

@Composable
fun NetSpeedScreen(onBack: () -> Unit) {
    val metrics by HomeRepository.metricsState.collectAsState()
    val scope = rememberCoroutineScope()

    val isDark = isSystemInDarkTheme()
    // 下载绿色 / 上传蓝色 — 与首页语义一致
    val downColor = if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    val upColor = MiuixTheme.colorScheme.primary

    // 连接列表
    var allConnections by remember { mutableStateOf<List<BoxApi.ConnectionInfo>>(emptyList()) }
    var closedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filter by remember { mutableStateOf(ConnFilter.Active) }
    var refreshTick by remember { mutableIntStateOf(0) }

    // 定时刷新连接
    LaunchedEffect(Unit) {
        while (isActive) {
            val conns = BoxApi.getAllConnections()
            val currentIds = conns.map { it.id }.toSet()
            // 之前存在但现在不在的 → 已关闭
            val prevIds = allConnections.map { it.id }.toSet()
            closedIds = closedIds + (prevIds - currentIds)
            allConnections = conns
            refreshTick++
            delay(2000)
        }
    }

    val displayConnections = remember(allConnections, closedIds, filter) {
        when (filter) {
            ConnFilter.Active -> allConnections
            ConnFilter.Closed -> {
                // 从历史中找已关闭的（不在当前活跃列表中的）
                emptyList() // 已关闭的连接无法从 API 获取详情，仅显示 ID
            }
        }
    }

    val activeCount = allConnections.size
    val closedCount = closedIds.size

    val scrollBehavior = MiuixScrollBehavior()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        TopAppBar(
            title = stringResource(R.string.bottomsheet_net_speed_title),
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    backgroundColor = Color.Transparent,
                    cornerRadius = 16.dp
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPaddingWithNavBars(
                start = 16.dp, end = 16.dp, top = 8.dp, extraBottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 速度卡片
            item(key = "speed_stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SpeedStatCard(
                        icon = Icons.Filled.ArrowDownward,
                        label = stringResource(R.string.bottomsheet_net_speed_down),
                        value = metrics.netDown,
                        tint = downColor,
                        modifier = Modifier.weight(1f)
                    )
                    SpeedStatCard(
                        icon = Icons.Filled.ArrowUpward,
                        label = stringResource(R.string.bottomsheet_net_speed_up),
                        value = metrics.netUp,
                        tint = upColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 图表
            item(key = "chart") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(16.dp),
                    colors = CardDefaults.defaultColors()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChartLegendItem(downColor, stringResource(R.string.bottomsheet_net_speed_down), Icons.Filled.ArrowDownward)
                            ChartLegendItem(upColor, stringResource(R.string.bottomsheet_net_speed_up), Icons.Filled.ArrowUpward)
                        }
                        AnimatedSpeedChart(
                            downSeries = metrics.netDownHistory,
                            upSeries = metrics.netUpHistory,
                            downColor = downColor,
                            upColor = upColor,
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        )
                    }
                }
            }

            // 连接筛选标签
            if (metrics.useClashApiForNetSpeed) {
                item(key = "conn_filter") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            text = "Active ($activeCount)",
                            selected = filter == ConnFilter.Active,
                            onClick = { filter = ConnFilter.Active }
                        )
                        FilterChip(
                            text = "Closed ($closedCount)",
                            selected = filter == ConnFilter.Closed,
                            onClick = { filter = ConnFilter.Closed }
                        )
                    }
                }

                if (filter == ConnFilter.Active) {
                    if (allConnections.isEmpty()) {
                        item(key = "conn_empty") {
                            Text(
                                text = "No active connections",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    } else {
                        items(
                            items = allConnections,
                            key = { it.id }
                        ) { conn ->
                            ConnectionCard(
                                conn = conn,
                                downColor = downColor,
                                upColor = upColor,
                                onClose = {
                                    scope.launch {
                                        BoxApi.closeConnection(conn.id)
                                        closedIds = closedIds + conn.id
                                        allConnections = allConnections.filter { it.id != conn.id }
                                    }
                                }
                            )
                        }
                    }
                } else {
                    item(key = "closed_hint") {
                        Text(
                            text = if (closedCount > 0) {
                                "$closedCount connections closed this session"
                            } else {
                                "No closed connections"
                            },
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer
    val fg = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer

    Card(
        modifier = Modifier,
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = bg),
        onClick = onClick
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = fg
        )
    }
}

@Composable
private fun ConnectionCard(
    conn: BoxApi.ConnectionInfo,
    downColor: Color,
    upColor: Color,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
        ) {
            // 主机 + 关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conn.host.ifBlank { conn.destinationIP },
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp),
                    backgroundColor = Color.Transparent,
                    cornerRadius = 10.dp
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // 网络 + 规则
            Row(
                modifier = Modifier.padding(end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (conn.network.isNotBlank()) {
                    ConnInfoTag(conn.network.uppercase())
                }
                if (conn.type.isNotBlank()) {
                    ConnInfoTag(conn.type)
                }
                if (conn.rule.isNotBlank()) {
                    ConnInfoTag(conn.rule + if (conn.rulePayload.isNotBlank()) "(${conn.rulePayload})" else "")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 流量
            Row(
                modifier = Modifier.padding(end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = downColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = HomeMetricsApi.formatBytes(conn.download),
                        style = MiuixTheme.textStyles.footnote1,
                        color = downColor
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = upColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = HomeMetricsApi.formatBytes(conn.upload),
                        style = MiuixTheme.textStyles.footnote1,
                        color = upColor
                    )
                }
            }

            // 链路
            if (conn.chains.isNotEmpty()) {
                Text(
                    text = conn.chains.reversed().joinToString(" → "),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, end = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnInfoTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedRectangle(6.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSecondaryContainer,
            maxLines = 1
        )
    }
}

@Composable
private fun SpeedStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedRectangle(10.dp))
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
                }
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            Text(
                text = value,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChartLegendItem(
    color: Color,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedRectangle(6.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * 无缝滚动速度图表。
 *
 * 原理：用 withFrameMillis 持续驱动渲染（60+fps），基于「距上次数据推入的时间」
 * 计算水平偏移量。新数据到来时执行 array shift + reset timer，数学上偏移量
 * 在推入前后完全连续（shift前 t≈1 偏移=step，shift后 t=0 偏移=0，但数组
 * 整体左移了一格，视觉位置不变）。
 *
 * Y 轴缩放使用 Animatable 平滑过渡，避免 maxValue 突变导致全部跳变。
 */
@Composable
private fun AnimatedSpeedChart(
    downSeries: List<Float>,
    upSeries: List<Float>,
    downColor: Color,
    upColor: Color,
    modifier: Modifier = Modifier
) {
    val slots = 30
    val dataIntervalMs = 2000f

    // 原始值环形缓冲（不标准化，保留原始 bytes/s）
    val downBuf = remember { FloatArray(slots + 1) }
    val upBuf = remember { FloatArray(slots + 1) }
    var bufLen by remember { mutableIntStateOf(0) }
    var lastPushMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var initialized by remember { mutableStateOf(false) }

    // 动画 Y 轴最大值，避免缩放跳变
    val animMaxV = remember { Animatable(1f) }
    // 最新点高度动画：从旧值平滑过渡到新值
    val tipDown = remember { Animatable(0f) }
    val tipUp = remember { Animatable(0f) }

    // 帧时钟：持续驱动 Canvas 重绘
    var frameMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameMs = System.currentTimeMillis() }
        }
    }

    // 数据推入
    LaunchedEffect(downSeries.size, downSeries.lastOrNull(), upSeries.lastOrNull()) {
        if (!initialized) {
            val dPad = if (downSeries.size >= slots) downSeries.takeLast(slots)
            else List(slots - downSeries.size) { 0f } + downSeries
            val uPad = if (upSeries.size >= slots) upSeries.takeLast(slots)
            else List(slots - upSeries.size) { 0f } + upSeries
            for (i in 0 until slots) {
                downBuf[i] = dPad[i]
                upBuf[i] = uPad[i]
            }
            bufLen = slots
            val maxV = (dPad + uPad).maxOrNull()?.coerceAtLeast(1f) ?: 1f
            animMaxV.snapTo(maxV)
            tipDown.snapTo(downBuf[slots - 1])
            tipUp.snapTo(upBuf[slots - 1])
            initialized = true
            lastPushMs = System.currentTimeMillis()
            return@LaunchedEffect
        }

        val newD = downSeries.lastOrNull() ?: 0f
        val newU = upSeries.lastOrNull() ?: 0f

        // 左移一格，倒数第二格填入当前 tip 动画值（确保连续）
        for (i in 0 until bufLen - 1) {
            downBuf[i] = downBuf[i + 1]
            upBuf[i] = upBuf[i + 1]
        }
        // 最后一格暂存当前动画值作为绘制回退
        downBuf[bufLen - 1] = tipDown.value
        upBuf[bufLen - 1] = tipUp.value

        lastPushMs = System.currentTimeMillis()

        // tip 从当前值动画到新值
        launch { tipDown.animateTo(newD, spring(dampingRatio = 0.82f, stiffness = 300f)) }
        launch { tipUp.animateTo(newU, spring(dampingRatio = 0.82f, stiffness = 300f)) }

        // 平滑更新 Y 轴缩放
        val rawMax = maxOf(
            downBuf.take(bufLen).maxOrNull() ?: 0f,
            upBuf.take(bufLen).maxOrNull() ?: 0f,
            newD, newU
        ).coerceAtLeast(1f)
        launch { animMaxV.animateTo(rawMax, androidx.compose.animation.core.tween(600)) }
    }

    val downGrad = listOf(downColor.copy(alpha = 0.22f), downColor.copy(alpha = 0f))
    val upGrad = listOf(upColor.copy(alpha = 0.22f), upColor.copy(alpha = 0f))
    val gridColor = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.12f)

    // 读取动画值以触发重组
    val maxV = animMaxV.value
    val currentMs = frameMs
    val tipDVal = tipDown.value
    val tipUVal = tipUp.value

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padTop = 4f
        val chartH = h - padTop
        val step = w / (slots - 1).toFloat()
        val n = bufLen

        // 基于时间的平移
        val elapsed = (currentMs - lastPushMs).coerceAtLeast(0)
        val t = (elapsed / dataIntervalMs).coerceIn(0f, 1f)
        val shiftPx = t * step

        // 网格
        for (row in 1..3) {
            val gy = padTop + chartH * (row / 4f)
            drawLine(gridColor, Offset(0f, gy), Offset(w, gy), 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
        }

        if (n < 2) return@Canvas

        // 最后一个点使用 tip 动画值，其余用 buffer 原始值
        fun pt(buf: FloatArray, tipVal: Float, i: Int): Offset {
            val x = i * step - shiftPx
            val raw = if (i == n - 1) tipVal else buf[i]
            val v = (raw / maxV).coerceIn(0f, 1f)
            return Offset(x, padTop + chartH * (1f - v))
        }

        fun buildPath(buf: FloatArray, tipVal: Float): Path {
            val p = Path()
            val first = pt(buf, tipVal, 0)
            p.moveTo(first.x, first.y)
            for (i in 1 until n) {
                val prev = pt(buf, tipVal, i - 1)
                val cur = pt(buf, tipVal, i)
                val cx = (prev.x + cur.x) / 2f
                p.cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
            }
            return p
        }

        fun buildFill(buf: FloatArray, tipVal: Float): Path {
            val fill = Path()
            fill.addPath(buildPath(buf, tipVal))
            val lastPt = pt(buf, tipVal, n - 1)
            fill.lineTo(lastPt.x, h)
            val firstPt = pt(buf, tipVal, 0)
            fill.lineTo(firstPt.x, h)
            fill.close()
            return fill
        }

        drawContext.canvas.save()
        drawContext.canvas.clipRect(0f, 0f, w, h)

        drawPath(buildFill(downBuf, tipDVal), Brush.verticalGradient(downGrad, 0f, h))
        drawPath(buildFill(upBuf, tipUVal), Brush.verticalGradient(upGrad, 0f, h))
        drawPath(buildPath(upBuf, tipUVal), upColor, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(buildPath(downBuf, tipDVal), downColor, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 最新数据点标记
        val dotD = pt(downBuf, tipDVal, n - 1)
        val dotU = pt(upBuf, tipUVal, n - 1)
        if (tipDVal / maxV > 0.005f && dotD.x in 0f..w) {
            drawCircle(downColor.copy(alpha = 0.25f), 8f, dotD)
            drawCircle(downColor, 5f, dotD)
            drawCircle(Color.White, 2f, dotD)
        }
        if (tipUVal / maxV > 0.005f && dotU.x in 0f..w) {
            drawCircle(upColor.copy(alpha = 0.25f), 8f, dotU)
            drawCircle(upColor, 5f, dotU)
            drawCircle(Color.White, 2f, dotU)
        }

        drawContext.canvas.restore()
    }
}
