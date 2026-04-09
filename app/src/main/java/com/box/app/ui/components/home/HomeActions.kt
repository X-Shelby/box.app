package com.box.app.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeQuickActions(
    showSubStore: Boolean,
    onOpenPanel: () -> Unit,
    onOpenSubStore: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSmartDns: (() -> Unit)? = null,
    compact: Boolean = false
) {
    data class QuickItem(val title: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit)
    val items = buildList {
        add(QuickItem(stringResource(R.string.home_quick_panel_title), stringResource(R.string.home_quick_panel_subtitle), Icons.Filled.Dashboard, onOpenPanel))
        if (showSubStore) add(QuickItem(stringResource(R.string.home_quick_subs_title), stringResource(R.string.home_quick_subs_subtitle), Icons.Filled.Subscriptions, onOpenSubStore))
        add(QuickItem(stringResource(R.string.home_quick_logs_title), stringResource(R.string.home_quick_logs_subtitle), Icons.AutoMirrored.Filled.Article, onOpenLogs))
        if (onOpenSmartDns != null) add(QuickItem("DNS", "SmartDNS", Icons.Filled.Dns, onOpenSmartDns))
    }

    if (compact) {
        // 紧凑模式：一行胶囊按钮，无副标题，省高度
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                Card(
                    modifier = Modifier.weight(1f),
                    cornerRadius = 12.dp,
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                    onClick = item.onClick
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.size(5.dp))
                        Text(
                            text = item.title,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        // 宽松模式：图标+标题+副标题卡片
        val columns = if (items.size <= 3) items.size else 2
        val rows = items.chunked(columns)

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { item ->
                        QuickActionCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            icon = item.icon,
                            onClick = item.onClick,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    repeat(columns - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedRectangle(7.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
