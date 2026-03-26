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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.box.app.R
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.appColors
import com.box.app.utils.AppLanguage
import com.box.app.utils.LanguageManager
import com.kyant.shapes.RoundedRectangle

@Composable
fun LanguageSelectionDialog(
    currentLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit
) {
    val c = appColors()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        tonalElevation = 0.dp,
        title = {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(AppLanguage.SYSTEM, AppLanguage.ENGLISH, AppLanguage.CHINESE).forEach { lang ->
                    LanguageOptionItem(
                        lang = lang,
                        isSelected = lang == currentLanguage,
                        onClick = { onSelect(lang) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), color = c.textPrimary)
            }
        }
    )
}

@Composable
private fun LanguageOptionItem(
    lang: AppLanguage,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val c = appColors()
    val interactionSource = remember { MutableInteractionSource() }

    val bgColor = if (isSelected) {
        appAccentColor().copy(alpha = 0.16f)
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
                text = when (lang) {
                    AppLanguage.SYSTEM -> stringResource(R.string.settings_language_follow_system)
                    AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
                    AppLanguage.CHINESE -> stringResource(R.string.settings_language_chinese)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = c.textPrimary
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
