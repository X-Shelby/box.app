package com.box.app.ui.screens.tools

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.component.IconSize
import dev.lackluster.hyperx.ui.component.PreferenceIconSlot
import dev.lackluster.hyperx.ui.component.ImageSource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ─── 文件类型颜色映射 ──────────────────────────────────────────────────────────

private object ConfigFileColors {
    val yaml = Color(0xFF43A047)
    val json = Color(0xFF1E88E5)
    val ini = Color(0xFF8E24AA)
    val shell = Color(0xFF546E7A)
    val box = Color(0xFFF57C00)
    val folder = Color(0xFFFFB300)
    val folderNav = Color(0xFF78909C)
    val fallback = Color(0xFF90A4AE)
}

private fun fileTypeColor(fileName: String): Color {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext in listOf("yaml", "yml") -> ConfigFileColors.yaml
        ext == "json" -> ConfigFileColors.json
        ext in listOf("ini", "cfg", "conf") -> ConfigFileColors.ini
        ext == "sh" -> ConfigFileColors.shell
        fileName.startsWith("box.") -> ConfigFileColors.box
        else -> ConfigFileColors.fallback
    }
}

private fun fileTypeIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        ext in listOf("yaml", "yml", "json", "ini", "cfg", "conf") -> Icons.Filled.Description
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

// ─── 彩色图标容器（用于 startAction） ───────────────────────────────────────

@Composable
private fun ColoredIconSlot(
    icon: ImageVector,
    tint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(end = 8.dp)
            .size(IconSize.Medium.dp)
            .clip(SmoothRoundedCornerShape(12.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ─── 文件夹行（使用 ArrowPreference — 带导航箭头） ───────────────────────────

@Composable
internal fun ConfigFolderRow(
    name: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ArrowPreference(
        title = name,
        summary = subtitle,
        startAction = {
            ColoredIconSlot(
                icon = Icons.Filled.Folder,
                tint = ConfigFileColors.folder,
                containerColor = ConfigFileColors.folder.copy(alpha = 0.12f)
            )
        },
        onClick = onClick,
        modifier = modifier
    )
}

// ─── 返回上级目录行（使用 ArrowPreference） ──────────────────────────────────

@Composable
internal fun ConfigParentFolderRow(
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ArrowPreference(
        title = "..",
        summary = subtitle,
        startAction = {
            ColoredIconSlot(
                icon = Icons.Filled.FolderOpen,
                tint = ConfigFileColors.folderNav,
                containerColor = ConfigFileColors.folderNav.copy(alpha = 0.10f)
            )
        },
        onClick = onClick,
        modifier = modifier
    )
}

// ─── 配置文件行 — 管理模式（使用 BasicComponent） ────────────────────────────

@Composable
internal fun ConfigFileItemRow(
    fileName: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = fileTypeColor(fileName)
    val icon = fileTypeIcon(fileName)

    BasicComponent(
        title = fileName,
        summary = subtitle,
        startAction = {
            ColoredIconSlot(
                icon = icon,
                tint = color,
                containerColor = color.copy(alpha = 0.10f)
            )
        },
        endActions = {
            // 文件扩展名标签
            val ext = fileName.substringAfterLast('.', "").uppercase()
            if (ext.isNotBlank() && ext.length <= 5) {
                Box(
                    modifier = Modifier
                        .clip(SmoothRoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.08f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = ext,
                        style = MiuixTheme.textStyles.footnote2,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        onClick = onClick,
        modifier = modifier
    )
}

// ─── 配置文件行 — 选择模式（使用 BasicComponent + 激活状态） ─────────────────

@Composable
internal fun ConfigSelectableFileRow(
    fileName: String,
    subtitle: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = fileTypeColor(fileName)
    val icon = fileTypeIcon(fileName)
    val primary = MiuixTheme.colorScheme.primary

    val activeBg by animateColorAsState(
        targetValue = if (isActive) primary.copy(alpha = 0.06f) else Color.Transparent,
        animationSpec = tween(280),
        label = "activeBg"
    )
    val activeBorder by animateColorAsState(
        targetValue = if (isActive) primary.copy(alpha = 0.20f) else Color.Transparent,
        animationSpec = tween(280),
        label = "activeBorder"
    )

    BasicComponent(
        title = fileName,
        summary = subtitle,
        startAction = {
            // 图标容器：活跃时使用主色
            Box {
                ColoredIconSlot(
                    icon = icon,
                    tint = if (isActive) primary else color,
                    containerColor = if (isActive) primary.copy(alpha = 0.12f) else color.copy(alpha = 0.10f)
                )
                // 活跃状态叠加 CheckCircle
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 8.dp)
                            .size(14.dp)
                            .clip(SmoothRoundedCornerShape(7.dp))
                            .background(MiuixTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        },
        endActions = {
            if (isActive) {
                // 活跃 badge
                Box(
                    modifier = Modifier
                        .clip(SmoothRoundedCornerShape(8.dp))
                        .background(primary)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Active",
                        style = MiuixTheme.textStyles.footnote2,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                // 非活跃：文件扩展名标签
                val ext = fileName.substringAfterLast('.', "").uppercase()
                if (ext.isNotBlank() && ext.length <= 5) {
                    Box(
                        modifier = Modifier
                            .clip(SmoothRoundedCornerShape(6.dp))
                            .background(color.copy(alpha = 0.08f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = ext,
                            style = MiuixTheme.textStyles.footnote2,
                            color = color,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        onClick = onClick,
        modifier = modifier
            .then(
                if (isActive) Modifier
                    .clip(SmoothRoundedCornerShape(14.dp))
                    .border(1.dp, activeBorder, SmoothRoundedCornerShape(14.dp))
                    .background(activeBg)
                else Modifier
            )
    )
}

// ─── 列表分割线 ──────────────────────────────────────────────────────────────

@Composable
internal fun ConfigDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 66.dp, end = 16.dp)
            .size(width = 0.dp, height = (0.5).dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.6f))
    )
}

// ─── 空状态占位（使用 MiuiX Text） ───────────────────────────────────────────

@Composable
internal fun ConfigEmptyState(
    message: String,
    hint: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            if (!hint.isNullOrBlank()) {
                Text(
                    text = hint,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ─── 加载状态（使用 MiuiX InfiniteProgressIndicator） ────────────────────────

@Composable
internal fun ConfigLoadingState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InfiniteProgressIndicator()
            Text(
                text = message,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}
