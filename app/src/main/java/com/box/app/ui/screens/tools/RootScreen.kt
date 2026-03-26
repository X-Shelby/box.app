package com.box.app.ui.screens.tools

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.box.app.data.backend.BoxApi
import com.box.app.R
import com.box.app.ui.components.ErrorToast
import com.box.app.data.repo.HomeRepository
import com.box.app.ui.components.ToolsRowIcon
import com.box.app.ui.components.ToolsSectionCard
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.components.bottomsheets.AppModalBottomSheet
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appAccentColor
import com.box.app.BuildConfig
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsRootScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    listState: LazyListState,
    onOpenConfigManage: () -> Unit,
    onOpenConfigSelect: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenNetworkControl: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenUpdateSubscription: () -> Unit,
    onOpenUpdateCnip: () -> Unit
) {
    val c = appColors()
    val accent = appAccentColor()
    val pagePadding = 16.dp

    val context = LocalContext.current
    val updatePrefs = remember { context.getSharedPreferences("tools_update", Context.MODE_PRIVATE) }
    val persistedCore = remember { updatePrefs.getString("selected_update_core", "mihomo") ?: "mihomo" }

    val scope = rememberCoroutineScope()
    var toastMessage by remember { mutableStateOf<String?>(null) }

    ErrorToast(
        message = toastMessage,
        onConsumed = { toastMessage = null }
    )

    fun mapKernelNameForUpdate(name: String): String {
        return if (name == "v2ray") "v2fly" else name
    }

    val updateTargetCoreLabel = stringResource(R.string.tools_update_target_core)
    val updateTargetSubscriptionLabel = stringResource(R.string.tools_update_target_subscription)
    val updateTargetWebuiLabel = stringResource(R.string.tools_update_target_webui)

    var showUpdateCoreSheet by remember { mutableStateOf(false) }
    var selectedUpdateCore by rememberSaveable { mutableStateOf(persistedCore) }

    LaunchedEffect(selectedUpdateCore) {
        updatePrefs.edit().putString("selected_update_core", selectedUpdateCore).apply()
    }

    if (showUpdateCoreSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val coreOptions = listOf(
            Triple("mihomo", stringResource(R.string.tools_update_core_mihomo_subtitle), true),
            Triple("mihomo_smart", stringResource(R.string.tools_update_core_mihomo_smart_subtitle), true),
            Triple("sing-box", stringResource(R.string.tools_update_core_sing_box_subtitle), true),
            Triple("xray", stringResource(R.string.tools_update_core_xray_subtitle), true),
            Triple("v2ray", stringResource(R.string.tools_update_core_v2ray_subtitle), true),
            Triple("hysteria", stringResource(R.string.tools_update_core_hysteria_subtitle), true)
        )

        AppModalBottomSheet(
            onDismissRequest = { showUpdateCoreSheet = false },
            sheetState = sheetState
        ) {
            val sheetScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 28.dp, height = 3.dp)
                            .background(c.divider.copy(alpha = 0.42f), shape = Capsule())
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .verticalScroll(sheetScrollState)
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.tools_update_sheet_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.tools_update_sheet_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    coreOptions.forEachIndexed { idx, (name, subtitle, _) ->
                        val isLast = idx == coreOptions.lastIndex
                        UpdateCoreOptionRow(
                            title = name,
                            subtitle = subtitle,
                            selected = selectedUpdateCore == name,
                            showDivider = !isLast,
                            onRowClick = { selectedUpdateCore = name },
                            onUpdate = {
                                val kernel = mapKernelNameForUpdate(name)
                                scope.launch {
                                    toastMessage = context.getString(R.string.tools_update_started, updateTargetCoreLabel)
                                    val success = BoxApi.updateKernel(kernel)
                                    toastMessage = if (success) {
                                        context.getString(R.string.tools_update_completed, kernel)
                                    } else {
                                        context.getString(R.string.tools_update_failed)
                                    }
                                }
                            }
                        )
                    }
                }
                
                // Add navigation bar padding at the bottom
                Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }

    LaunchedEffect(Unit) {
        HomeRepository.startPolling()
    }

    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { now ->
                if (now > last) {
                    onNavVisibilityChange(false)
                } else if (now < last) {
                    onNavVisibilityChange(true)
                }
                last = now
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(c.pageBg),
        contentPadding = contentPaddingWithNavBars(
            start = pagePadding,
            end = pagePadding,
            top = 0.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Text(
                    text = stringResource(R.string.tools_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.tools_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        item {
            ToolsSectionCard(
                title = stringResource(R.string.tools_section_config_title),
                subtitle = stringResource(R.string.tools_section_config_subtitle)
            ) {
                ToolsRowIcon(
                    icon = Icons.Filled.Description,
                    title = stringResource(R.string.tools_row_manage),
                    subtitle = stringResource(R.string.tools_row_browse_search_create),
                    onClick = onOpenConfigManage
                )
                ToolsRowIcon(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.tools_row_select),
                    subtitle = stringResource(R.string.tools_row_choose_active_config),
                    showDivider = false,
                    onClick = onOpenConfigSelect
                )
            }
        }

        item {
            ToolsSectionCard(
                title = stringResource(R.string.tools_section_apps_title),
                subtitle = stringResource(R.string.tools_section_apps_subtitle)
            ) {
                ToolsRowIcon(
                    icon = Icons.Filled.Apps,
                    title = stringResource(R.string.tools_row_manage),
                    subtitle = stringResource(R.string.tools_row_manage_app_rules),
                    showDivider = false,
                    onClick = onOpenApps
                )
            }
        }

        item {
            ToolsSectionCard(
                title = stringResource(R.string.tools_section_network_control_title),
                subtitle = stringResource(R.string.tools_section_network_control_subtitle)
            ) {
                ToolsRowIcon(
                    icon = Icons.Filled.Router,
                    title = stringResource(R.string.tools_row_open),
                    subtitle = stringResource(R.string.tools_row_network_control_subtitle),
                    showDivider = false,
                    onClick = onOpenNetworkControl
                )
            }
        }

        item {
            ToolsSectionCard(
                title = stringResource(R.string.tools_section_logs_title),
                subtitle = stringResource(R.string.tools_section_logs_subtitle)
            ) {
                ToolsRowIcon(
                    icon = Icons.AutoMirrored.Filled.Article,
                    title = stringResource(R.string.tools_row_view),
                    subtitle = stringResource(R.string.tools_row_open_unified_logs),
                    showDivider = false,
                    onClick = onOpenLogs
                )
            }
        }

        item {
            ToolsSectionCard(
                title = stringResource(R.string.tools_section_update_title),
                subtitle = stringResource(R.string.tools_section_update_subtitle)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedRectangle(14.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(accent.copy(alpha = 0.22f), shape = RoundedRectangle(10.dp)),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                text = stringResource(R.string.tools_update_tip_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = accent
                            )
                            Text(
                                text = stringResource(R.string.tools_update_tip_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                UpdateTargetRow(
                    leftIcon = Icons.Filled.Storage,
                    title = stringResource(R.string.tools_update_target_core),
                    subtitle = stringResource(R.string.tools_update_subtitle_core),
                    onRowClick = {
                        if (BuildConfig.FLAVOR != "bfr") {
                            showUpdateCoreSheet = true
                        }
                    },
                    onUpdate = {
                        val kernel = mapKernelNameForUpdate(selectedUpdateCore)
                        scope.launch {
                            toastMessage = context.getString(R.string.tools_update_started, updateTargetCoreLabel)
                            val success = BoxApi.updateKernel(kernel)
                            toastMessage = if (success) {
                                context.getString(R.string.tools_update_completed, kernel)
                            } else {
                                context.getString(R.string.tools_update_failed)
                            }
                        }
                    }
                )
                UpdateTargetRow(
                    leftIcon = Icons.Filled.Link,
                    title = stringResource(R.string.tools_update_target_subscription),
                    subtitle = stringResource(R.string.tools_update_subtitle_subscription),
                    onRowClick = onOpenUpdateSubscription,
                    onUpdate = {
                        scope.launch {
                            toastMessage = context.getString(R.string.tools_update_started, updateTargetSubscriptionLabel)
                            val success = BoxApi.updateSubs()
                            toastMessage = if (success) {
                                context.getString(R.string.tools_update_completed_subscription)
                            } else {
                                context.getString(R.string.tools_update_failed)
                            }
                        }
                    }
                )
                UpdateTargetRow(
                    leftIcon = Icons.Filled.Language,
                    title = stringResource(R.string.tools_update_target_webui),
                    subtitle = stringResource(R.string.tools_update_subtitle_webui),
                    showDivider = BuildConfig.FLAVOR != "bfr",
                    onRowClick = {},
                    onUpdate = {
                        scope.launch {
                            toastMessage = context.getString(R.string.tools_update_started, updateTargetWebuiLabel)
                            val success = BoxApi.updateWebUI()
                            toastMessage = if (success) {
                                context.getString(R.string.tools_update_completed_webui)
                            } else {
                                context.getString(R.string.tools_update_failed)
                            }
                        }
                    }
                )

                if (BuildConfig.FLAVOR != "bfr") {
                    val updateTargetCnipLabel = stringResource(R.string.tools_update_target_cnip)
                    UpdateTargetRow(
                        leftIcon = Icons.Filled.Public,
                        title = stringResource(R.string.tools_update_target_cnip),
                        subtitle = stringResource(R.string.tools_update_subtitle_cnip),
                        showDivider = false,
                        onRowClick = onOpenUpdateCnip,
                        onUpdate = {
                            scope.launch {
                                toastMessage = context.getString(R.string.tools_update_started, updateTargetCnipLabel)
                                val success = BoxApi.updateCnipList()
                                toastMessage = if (success) {
                                    context.getString(R.string.tools_update_completed_cnip)
                                } else {
                                    context.getString(R.string.tools_update_failed)
                                }
                            }
                        }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun UpdateTargetRow(
    leftIcon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    showDivider: Boolean = true,
    onRowClick: () -> Unit,
    onUpdate: () -> Unit
) {
    val c = appColors()
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onRowClick
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(c.cardAlt, shape = RoundedRectangle(10.dp)),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(
                imageVector = leftIcon,
                contentDescription = stringResource(R.string.action_update),
                tint = c.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
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
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Filled.SystemUpdate,
            contentDescription = stringResource(R.string.action_update),
            tint = c.textPrimary,
            modifier = Modifier
                .size(18.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onUpdate() }
        )
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

@Composable
private fun UpdateCoreOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    showDivider: Boolean,
    onRowClick: () -> Unit,
    onUpdate: () -> Unit
) {
    val c = appColors()
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onRowClick
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(c.cardAlt, shape = RoundedRectangle(10.dp)),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.Storage,
                contentDescription = null,
                tint = c.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
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
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Filled.SystemUpdate,
            contentDescription = stringResource(R.string.action_update),
            tint = c.textPrimary,
            modifier = Modifier
                .size(18.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onUpdate() }
        )
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
