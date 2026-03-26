package com.box.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kyant.shapes.RoundedRectangle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.box.app.data.backend.BoxApi
import com.box.app.BuildConfig
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.LiquidGlassButton
import com.box.app.ui.components.LiquidGlassIconButton
import com.box.app.ui.components.LocalLiquidBackdrop
import com.box.app.ui.components.SettingsToggleRow
import com.box.app.ui.components.ToolsSectionCard
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.theme.appColors
import com.box.app.utils.ThemeManager
import com.box.app.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun SubscriptionScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val c = appColors()
    val context = LocalContext.current
    val pagePadding = 16.dp
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scope = rememberCoroutineScope()

    val liquidBackdrop = rememberLayerBackdrop()

    var topBarHeightPx by rememberSaveable { mutableStateOf(0) }
    var lastNonZeroTopBarHeightPx by rememberSaveable { mutableStateOf(0) }
    val density = LocalDensity.current
    val effectiveTopBarHeightPx = if (topBarHeightPx > 0) topBarHeightPx else lastNonZeroTopBarHeightPx
    val topInset = with(density) { effectiveTopBarHeightPx.toDp() } + 16.dp

    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { now ->
                if (now > last) onNavVisibilityChange(false) else if (now < last) onNavVisibilityChange(true)
                last = now
            }
    }

    var renew by rememberSaveable { mutableStateOf(false) }
    var updateSubscription by rememberSaveable { mutableStateOf(false) }
    var runCrontab by rememberSaveable { mutableStateOf(false) }

    var intervaUpdate by rememberSaveable { mutableStateOf("") }
    var singboxUrl by rememberSaveable { mutableStateOf("") }
    var mihomoProvidePath by rememberSaveable { mutableStateOf("") }

    var mihomoUrls by rememberSaveable {
        mutableStateOf(listOf(""))
    }
    var mihomoConfigs by rememberSaveable {
        mutableStateOf(listOf(""))
    }

    var initialRenew by rememberSaveable { mutableStateOf(false) }
    var initialUpdateSubscription by rememberSaveable { mutableStateOf(false) }
    var initialRunCrontab by rememberSaveable { mutableStateOf(false) }
    var initialIntervaUpdate by rememberSaveable { mutableStateOf("") }
    var initialSingboxUrl by rememberSaveable { mutableStateOf("") }
    var initialMihomoProvidePath by rememberSaveable { mutableStateOf("") }
    var initialMihomoUrls by rememberSaveable { mutableStateOf(listOf("")) }
    var initialMihomoConfigs by rememberSaveable { mutableStateOf(listOf("")) }

    val isDirty by remember {
        derivedStateOf {
            val urls = mihomoUrls.map { it.trim() }.filter { it.isNotEmpty() }
            val cfgs = mihomoConfigs.map { it.trim() }.filter { it.isNotEmpty() }
            val initialUrls = initialMihomoUrls.map { it.trim() }.filter { it.isNotEmpty() }
            val initialCfgs = initialMihomoConfigs.map { it.trim() }.filter { it.isNotEmpty() }

            renew != initialRenew ||
                updateSubscription != initialUpdateSubscription ||
                runCrontab != initialRunCrontab ||
                intervaUpdate.trim() != initialIntervaUpdate.trim() ||
                singboxUrl.trim() != initialSingboxUrl.trim() ||
                mihomoProvidePath.trim() != initialMihomoProvidePath.trim() ||
                urls != initialUrls ||
                cfgs != initialCfgs
        }
    }

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val subscriptionUrlKey = remember {
        if (BuildConfig.FLAVOR == "bfr") "subscription_url_clash" else "subscription_url_mihomo"
    }
    val provideConfigKey = remember {
        if (BuildConfig.FLAVOR == "bfr") "name_provide_clash_config" else "name_provide_mihomo_config"
    }
    val providePathKey = remember {
        if (BuildConfig.FLAVOR == "bfr") "clash_provide_path" else "mihomo_provide_path"
    }

    ErrorToast(
        message = error,
        onConsumed = { error = null }
    )

    LaunchedEffect(Unit) {
        loading = true
        error = null
        val settings = BoxApi.getSettings()
        if (settings.isBlank()) {
            error = context.getString(R.string.tools_subscription_load_failed)
            loading = false
            return@LaunchedEffect
        }

        val lines = settings.lineSequence().map { it.trim() }
        for (line in lines) {
            when {
                line.startsWith("renew=") -> {
                    renew = line.substringAfter("=").trim().replace("\"", "").lowercase() == "true"
                }
                line.startsWith("update_subscription=") -> {
                    updateSubscription = line.substringAfter("=").trim().replace("\"", "").lowercase() == "true"
                }
                line.startsWith("run_crontab=") -> {
                    runCrontab = line.substringAfter("=").trim().replace("\"", "").lowercase() == "true"
                }
                line.startsWith("interva_update=") -> {
                    intervaUpdate = line.substringAfter("=").trim().replace("\"", "").replace("'", "")
                }
                line.startsWith("${subscriptionUrlKey}=") -> {
                    val raw = line.substringAfter("=").trim()
                    val parsed = BoxApi.parseBashArray(raw)
                    mihomoUrls = if (parsed.isEmpty()) listOf("") else parsed
                }
                line.startsWith("${provideConfigKey}=") -> {
                    val raw = line.substringAfter("=").trim()
                    val parsed = BoxApi.parseBashArray(raw)
                    mihomoConfigs = if (parsed.isEmpty()) listOf("") else parsed
                }
                line.startsWith("${providePathKey}=") -> {
                    mihomoProvidePath = line.substringAfter("=").trim().replace("\"", "").replace("'", "")
                        .replace("\${box_dir}", "/data/adb/box")
                }
                line.startsWith("subscription_url_singbox=") -> {
                    singboxUrl = line.substringAfter("=").trim().replace("\"", "").replace("'", "")
                }
            }
        }

        initialRenew = renew
        initialUpdateSubscription = updateSubscription
        initialRunCrontab = runCrontab
        initialIntervaUpdate = intervaUpdate
        initialSingboxUrl = singboxUrl
        initialMihomoProvidePath = mihomoProvidePath
        initialMihomoUrls = mihomoUrls
        initialMihomoConfigs = mihomoConfigs

        error = null
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.pageBg)
            .imePadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(liquidBackdrop),
            contentPadding = contentPaddingWithNavBars(
                start = pagePadding,
                end = pagePadding,
                top = topInset
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ToolsSectionCard(
                    title = stringResource(R.string.tools_subscription_section_general_title),
                    subtitle = stringResource(R.string.tools_subscription_section_general_subtitle)
                ) {
                    SettingsToggleRow(
                        icon = Icons.Filled.Autorenew,
                        title = stringResource(R.string.tools_subscription_renew_title),
                        subtitle = stringResource(R.string.tools_subscription_renew_subtitle),
                        checked = renew,
                        onCheckedChange = { renew = it }
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.Subscriptions,
                        title = stringResource(R.string.tools_subscription_update_subscription_title),
                        subtitle = stringResource(R.string.tools_subscription_update_subscription_subtitle),
                        checked = updateSubscription,
                        onCheckedChange = { updateSubscription = it }
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.Schedule,
                        title = stringResource(R.string.tools_subscription_run_crontab_title),
                        subtitle = stringResource(R.string.tools_subscription_run_crontab_subtitle),
                        checked = runCrontab,
                        onCheckedChange = { runCrontab = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    LabeledTextField(
                        icon = Icons.Filled.Tune,
                        title = stringResource(R.string.tools_subscription_interval_title),
                        subtitle = stringResource(R.string.tools_subscription_key_interva_update_subtitle),
                        value = intervaUpdate,
                        placeholder = stringResource(R.string.tools_subscription_interval_placeholder),
                        onValueChange = { intervaUpdate = it }
                    )
                }
            }

            item {
                ToolsSectionCard(
                    title = stringResource(R.string.tools_subscription_mihomo_urls_title),
                    subtitle = stringResource(R.string.tools_subscription_key_subscription_url_mihomo_subtitle)
                ) {
                    EditableStringList(
                        icon = Icons.Filled.Link,
                        rows = mihomoUrls,
                        hint = stringResource(R.string.tools_subscription_hint_enter_url),
                        enabled = updateSubscription,
                        onRowsChange = { mihomoUrls = it }
                    )
                }
            }

            item {
                ToolsSectionCard(
                    title = stringResource(R.string.tools_subscription_mihomo_configs_title),
                    subtitle = stringResource(R.string.tools_subscription_key_name_provide_mihomo_config_subtitle)
                ) {
                    EditableStringList(
                        icon = Icons.Filled.Description,
                        rows = mihomoConfigs,
                        hint = stringResource(R.string.tools_subscription_hint_enter_filename),
                        enabled = updateSubscription,
                        onRowsChange = { mihomoConfigs = it }
                    )
                }
            }

            item {
                ToolsSectionCard(
                    title = stringResource(R.string.tools_subscription_paths_title),
                    subtitle = stringResource(R.string.tools_subscription_paths_subtitle)
                ) {
                    LabeledTextField(
                        icon = Icons.Filled.Folder,
                        title = stringResource(R.string.tools_subscription_mihomo_provide_path_title),
                        subtitle = stringResource(R.string.tools_subscription_key_mihomo_provide_path_subtitle),
                        value = mihomoProvidePath,
                        placeholder = stringResource(R.string.tools_subscription_path_placeholder),
                        enabled = updateSubscription,
                        onValueChange = { mihomoProvidePath = it }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LabeledTextField(
                        icon = Icons.Filled.Public,
                        title = stringResource(R.string.tools_subscription_sing_box_url_title),
                        subtitle = stringResource(R.string.tools_subscription_key_subscription_url_singbox_subtitle),
                        value = singboxUrl,
                        placeholder = stringResource(R.string.tools_subscription_url_placeholder),
                        enabled = updateSubscription,
                        onValueChange = { singboxUrl = it }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
            SubscriptionFloatingTopBar(
                saveEnabled = !loading && !saving && isDirty,
                onBack = onBack,
                onSave = {
                    scope.launch {
                        error = null
                        saving = true
                        val urls = mihomoUrls.map { it.trim() }.filter { it.isNotEmpty() }
                        val cfgs = mihomoConfigs.map { it.trim() }.filter { it.isNotEmpty() }

                        val r1 = BoxApi.updateBooleanSetting("renew", renew)
                        val r2 = BoxApi.updateBooleanSetting("update_subscription", updateSubscription)
                        val r3 = BoxApi.updateBooleanSetting("run_crontab", runCrontab)
                        val r4 = BoxApi.updateSetting("interva_update", intervaUpdate.trim())
                        val r5 = BoxApi.updateArraySetting(subscriptionUrlKey, urls)
                        val r6 = BoxApi.updateArraySetting(provideConfigKey, cfgs)
                        val r7 = BoxApi.updateSetting(providePathKey, mihomoProvidePath.trim())
                        val r8 = BoxApi.updateSetting("subscription_url_singbox", singboxUrl.trim())

                        val ok = listOf(r1, r2, r3, r4, r5, r6, r7, r8).all { it }
                        if (ok) {
                            initialRenew = renew
                            initialUpdateSubscription = updateSubscription
                            initialRunCrontab = runCrontab
                            initialIntervaUpdate = intervaUpdate
                            initialSingboxUrl = singboxUrl
                            initialMihomoProvidePath = mihomoProvidePath
                            initialMihomoUrls = mihomoUrls
                            initialMihomoConfigs = mihomoConfigs
                            error = null
                        } else {
                            error = context.getString(R.string.tools_subscription_save_failed)
                        }
                        saving = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned {
                        val h = it.size.height
                        if (h > 0) {
                            topBarHeightPx = h
                            lastNonZeroTopBarHeightPx = h
                        }
                    }
            )
        }
    }
}

@Composable
private fun SubscriptionFloatingTopBar(
    saveEnabled: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val backdrop = requireNotNull(LocalLiquidBackdrop.current)
    val isDark = ThemeManager.shouldUseDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    val selectedTint = if (isDark) Color(0xFF2B2F37) else Color(0xFFE3E6EA)
    val tint = selectedTint.copy(alpha = 0.25f)
    val fallback = selectedTint.copy(alpha = 0.80f)

    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, top = 8.dp, end = 20.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassButton(
                onClick = onBack,
                backdrop = backdrop,
                surfaceColor = tint
            ) {
                Text(
                    text = stringResource(R.string.tools_update_back_compact),
                    color = c.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            LiquidGlassIconButton(
                onClick = onSave,
                enabled = saveEnabled,
                backdrop = backdrop,
                surfaceColor = tint
            ) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = stringResource(R.string.action_save),
                    tint = if (saveEnabled) c.textPrimary else c.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LabeledTextField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    value: String,
    placeholder: String,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit
) {
    val c = appColors()
    val contentAlpha = if (enabled) 1f else 0.45f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(contentAlpha)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(c.cardAlt, shape = RoundedRectangle(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(contentAlpha)
            .padding(horizontal = 6.dp),
        placeholder = {
            Text(text = placeholder, style = MaterialTheme.typography.bodyMedium)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
        shape = RoundedRectangle(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.divider,
            unfocusedBorderColor = c.divider,
            focusedContainerColor = c.cardAlt,
            unfocusedContainerColor = c.cardAlt,
            cursorColor = c.textPrimary
        )
    )
}

@Composable
private fun EditableStringList(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    rows: List<String>,
    hint: String,
    enabled: Boolean = true,
    onRowsChange: (List<String>) -> Unit
) {
    val c = appColors()
    val contentAlpha = if (enabled) 1f else 0.45f

    val safeRows = if (rows.isEmpty()) listOf("") else rows

    safeRows.forEachIndexed { idx, row ->
        val isLast = idx == safeRows.lastIndex
        EditableStringRow(
            icon = icon,
            value = row,
            hint = hint,
            showDivider = !isLast,
            enabled = enabled,
            onValueChange = { v ->
                val next = safeRows.toMutableList()
                next[idx] = v
                onRowsChange(next)
            },
            onDelete = {
                val next = safeRows.toMutableList()
                if (next.size <= 1) {
                    next[0] = ""
                } else {
                    next.removeAt(idx)
                }
                onRowsChange(next)
            }
        )
    }

    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(contentAlpha)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                onRowsChange(safeRows + "")
            }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(c.cardAlt, shape = RoundedRectangle(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = c.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = stringResource(R.string.action_add),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            Text(
                text = stringResource(R.string.tools_subscription_append_item),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
        }
    }
}

@Composable
private fun EditableStringRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    hint: String,
    showDivider: Boolean,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val c = appColors()
    val contentAlpha = if (enabled) 1f else 0.45f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(contentAlpha)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(c.cardAlt, shape = RoundedRectangle(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            placeholder = {
                Text(text = hint, style = MaterialTheme.typography.bodyMedium)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
            shape = RoundedRectangle(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.divider,
                unfocusedBorderColor = c.divider,
                focusedContainerColor = c.cardAlt,
                unfocusedContainerColor = c.cardAlt,
                cursorColor = c.textPrimary
            )
        )

        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = if (enabled) c.textSecondary else c.textSecondary.copy(alpha = 0.6f)
            )
        }
    }

    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp)
                .height(1.dp)
                .background(c.divider)
        )
    }
}
