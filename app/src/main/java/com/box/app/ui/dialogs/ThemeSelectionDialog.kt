package com.box.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.miuix.HyperDialog
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.appColors
import com.box.app.utils.ThemeManager
import com.box.app.utils.ThemeMode
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ThemeSelectionDialog(
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    HyperDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.theme_dialog_title),
        confirmText = stringResource(R.string.action_cancel),
        onConfirm = onDismiss,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).forEach { mode ->
                    ThemeOptionItem(
                        mode = mode,
                        isSelected = mode == currentMode,
                        onClick = { onSelect(mode) }
                    )
                }
            }
        }
    )
}

@Composable
private fun ThemeOptionItem(
    mode: ThemeMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    
    val bgColor = if (isSelected) {
        appAccentColor().copy(alpha = if (isDark) 0.20f else 0.12f)
    } else {
        c.cardAlt
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(14.dp))
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (mode) {
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_follow_system)
                },
                style = MiuixTheme.textStyles.subtitle,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = c.textPrimary
            )
            Text(
                text = when (mode) {
                    ThemeMode.LIGHT -> stringResource(R.string.theme_light_desc)
                    ThemeMode.DARK -> stringResource(R.string.theme_dark_desc)
                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system_desc)
                },
                style = MiuixTheme.textStyles.body2,
                color = c.textSecondary
            )
        }
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = appAccentColor(),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
