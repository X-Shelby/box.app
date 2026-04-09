package com.box.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.components.SettingsToggleRow
import com.box.app.ui.miuix.HyperDialog
import com.box.app.utils.SystemBarMode
import com.box.app.utils.SystemBarSettings

@Composable
fun SystemBarSelectionDialog(
    currentSettings: SystemBarSettings,
    onDismiss: () -> Unit,
    onConfirm: (SystemBarSettings) -> Unit
) {
    var statusBarMode by remember { mutableStateOf(currentSettings.statusBar) }
    var navigationBarMode by remember { mutableStateOf(currentSettings.navigationBar) }

    HyperDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_system_bars_dialog_title),
        summary = stringResource(R.string.settings_system_bars_opaque_subtitle),
        confirmText = stringResource(R.string.action_apply),
        onConfirm = { onConfirm(SystemBarSettings(statusBarMode, navigationBarMode)) },
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = onDismiss,
        content = {
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
        }
    )
}
