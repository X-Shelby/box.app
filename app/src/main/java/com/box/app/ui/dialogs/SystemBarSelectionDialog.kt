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

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.components.SettingsToggleRow
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appAccentColor
import com.box.app.utils.SystemBarMode
import com.box.app.utils.SystemBarSettings
import com.box.app.utils.ThemeManager

@Composable
fun SystemBarSelectionDialog(
    currentSettings: SystemBarSettings,
    onDismiss: () -> Unit,
    onConfirm: (SystemBarSettings) -> Unit
) {
    val c = appColors()
    var statusBarMode by remember { mutableStateOf(currentSettings.statusBar) }
    var navigationBarMode by remember { mutableStateOf(currentSettings.navigationBar) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        tonalElevation = 0.dp,

        title = {
            Text(
                text = stringResource(R.string.settings_system_bars_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SettingsToggleRow(
                    icon = Icons.Filled.PhoneAndroid,
                    title = stringResource(R.string.settings_system_bars_opaque_status_title),
                    subtitle = stringResource(R.string.settings_system_bars_opaque_subtitle),
                    checked = statusBarMode == SystemBarMode.OPAQUE,
                    onCheckedChange = { enabled ->
                        statusBarMode = if (enabled) SystemBarMode.OPAQUE else SystemBarMode.TRANSPARENT
                    },
                    showDivider = true
                )

                SettingsToggleRow(
                    icon = Icons.Filled.PhoneAndroid,
                    title = stringResource(R.string.settings_system_bars_opaque_navigation_title),
                    subtitle = stringResource(R.string.settings_system_bars_opaque_subtitle),
                    checked = navigationBarMode == SystemBarMode.OPAQUE,
                    onCheckedChange = { enabled ->
                        navigationBarMode = if (enabled) SystemBarMode.OPAQUE else SystemBarMode.TRANSPARENT
                    },
                    showDivider = false
                )
            }
        },

        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(SystemBarSettings(statusBarMode, navigationBarMode))
                }
            ) {
                Text(text = stringResource(R.string.action_apply), color = appAccentColor())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), color = c.textPrimary)
            }
        }
    )
}