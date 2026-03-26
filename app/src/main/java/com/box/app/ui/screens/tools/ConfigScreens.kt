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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.box.app.R
import com.box.app.data.model.ConfigFsItem
import com.box.app.data.repo.ConfigRepository
import com.box.app.ui.components.LiquidGlassButton
import com.box.app.ui.components.LiquidGlassIconButton
import com.box.app.ui.components.LiquidGlassDropdownMenu
import com.box.app.ui.components.LocalLiquidBackdrop
import com.box.app.ui.components.LiquidGlassTextFieldPill
import com.box.app.ui.components.StatusBadge
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.ToolsFileRow
import com.box.app.ui.components.ToolsRowIcon
import com.box.app.ui.components.ToolsSectionCard
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.appErrorColor
import com.box.app.utils.ThemeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

enum class ConfigHubTab { Manage, Select }

@Composable
private fun ConfigSelectOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBoundsInRoot: IntRect?,
    onMenuAction: (SelectAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()

    LiquidGlassDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        anchorBoundsInWindow = anchorBoundsInRoot,
        modifier = modifier,
        offset = DpOffset(x = 0.dp, y = 8.dp),
        shape = RoundedRectangle(16.dp),
        containerColor = c.cardAlt
    ) {
        val items = remember {
            listOf(SelectAction.Refresh, SelectAction.Create, SelectAction.Download)
        }

        items.forEachIndexed { index, action ->
            val label = when (action) {
                SelectAction.Refresh -> stringResource(R.string.tools_config_menu_refresh)
                SelectAction.Create -> stringResource(R.string.tools_config_menu_create)
                SelectAction.Download -> stringResource(R.string.tools_config_menu_download)
            }

            com.box.app.ui.components.NoRippleDropdownMenuItem(
                text = {
                    Text(
                        text = label,
                        color = c.textPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                onClick = { onMenuAction(action) }
            )
        }
    }
}

@Composable
private fun ConfigManageOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBoundsInRoot: IntRect?,
    onAction: (ManageAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()

    LiquidGlassDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        anchorBoundsInWindow = anchorBoundsInRoot,
        modifier = modifier,
        offset = DpOffset(x = 0.dp, y = 8.dp),
        shape = RoundedRectangle(16.dp),
        containerColor = c.cardAlt
    ) {
        val items = remember {
            listOf(ManageAction.Refresh, ManageAction.NewFile, ManageAction.NewFolder)
        }

        items.forEachIndexed { index, action ->
            val label = when (action) {
                ManageAction.Refresh -> stringResource(R.string.tools_config_menu_refresh)
                ManageAction.NewFile -> stringResource(R.string.tools_config_menu_new_file)
                ManageAction.NewFolder -> stringResource(R.string.tools_config_menu_new_folder)
                else -> ""
            }

            if (label.isNotBlank()) {
                com.box.app.ui.components.NoRippleDropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            color = c.textPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = { onAction(action) }
                )
            }
        }
    }
}

@Composable
fun ToolsConfigScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    initialTab: ConfigHubTab,
    onEditorModeChange: (Boolean) -> Unit = {},
    enableBackHandler: Boolean = true
) {
    ConfigHubScreen(
        onNavVisibilityChange = onNavVisibilityChange,
        onBack = onBack,
        initialTab = initialTab,
        onEditorModeChange = onEditorModeChange,
        enableBackHandler = enableBackHandler
    )
}

@Composable
private fun SwipeRevealRow(
    rowKey: String,
    openRowKey: String?,
    onOpenRowKeyChange: (String?) -> Unit,
    closeSignal: Int,
    revealWidth: androidx.compose.ui.unit.Dp,
    onRefresh: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val c = appColors()
    val accent = appAccentColor()
    val danger = appErrorColor()
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val revealPx = remember(revealWidth, density) { with(density) { revealWidth.toPx() } }
    val offsetX = remember { Animatable(0f) }
    var contentHeightPx by remember { mutableStateOf(0) }

    LaunchedEffect(closeSignal) {
        if (offsetX.value != 0f) {
            offsetX.animateTo(0f, animationSpec = tween(durationMillis = 160))
            onOpenRowKeyChange(null)
        }
    }

    LaunchedEffect(openRowKey) {
        if (openRowKey != rowKey && offsetX.value != 0f) {
            offsetX.animateTo(0f, animationSpec = tween(durationMillis = 160))
        }
    }

    val actionsCount = remember(onRefresh, onEdit, onRename) {
        var n = 1
        if (onRefresh != null) n += 1
        if (onEdit != null) n += 1
        if (onRename != null) n += 1
        n
    }
    val actionWidth = remember(revealWidth, actionsCount) { revealWidth / actionsCount }

    val contentHeightDp = remember(contentHeightPx, density) {
        if (contentHeightPx <= 0) 0.dp else with(density) { contentHeightPx.toDp() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(14.dp))
    ) {
        if (contentHeightDp > 0.dp) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(revealWidth)
                    .height(contentHeightDp)
                    .clip(RoundedRectangle(14.dp)),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onRefresh != null) {
                    Box(
                        modifier = Modifier
                            .width(actionWidth)
                            .fillMaxSize()
                            .background(c.cardAlt)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    onRefresh()
                                    onOpenRowKeyChange(null)
                                    scope.launch { offsetX.animateTo(0f, animationSpec = tween(160)) }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onEdit != null) {
                    Box(
                        modifier = Modifier
                            .width(actionWidth)
                            .fillMaxSize()
                            .background(c.cardAlt)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    onEdit()
                                    onOpenRowKeyChange(null)
                                    scope.launch { offsetX.animateTo(0f, animationSpec = tween(160)) }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = stringResource(R.string.tools_config_edit),
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onRename != null) {
                    Box(
                        modifier = Modifier
                            .width(actionWidth)
                            .fillMaxSize()
                            .background(c.cardAlt)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    onRename()
                                    onOpenRowKeyChange(null)
                                    scope.launch { offsetX.animateTo(0f, animationSpec = tween(160)) }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.tools_config_rename),
                            tint = c.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(actionWidth)
                        .fillMaxSize()
                        .background(danger.copy(alpha = 0.18f))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                onDelete()
                                onOpenRowKeyChange(null)
                                scope.launch { offsetX.animateTo(0f, animationSpec = tween(160)) }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.tools_config_delete),
                        tint = danger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(c.card)
                .onSizeChanged { contentHeightPx = it.height }
                .pointerInput(revealPx, openRowKey) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        val startOffset = offsetX.value
                        val slop = awaitHorizontalTouchSlopOrCancellation(
                            pointerId = down.id,
                            onTouchSlopReached = { change, over ->
                                change.consume()
                                val next = (startOffset + over).coerceIn(-revealPx, 0f)
                                scope.launch { offsetX.snapTo(next) }
                            }
                        )

                        if (slop != null) {
                            horizontalDrag(
                                pointerId = slop.id
                            ) { change ->
                                val dx = change.positionChange().x
                                if (dx != 0f) {
                                    change.consume()
                                    val next = (offsetX.value + dx).coerceIn(-revealPx, 0f)
                                    scope.launch { offsetX.snapTo(next) }
                                }
                            }

                            val shouldOpen = offsetX.value < -revealPx * 0.5f
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = if (shouldOpen) -revealPx else 0f,
                                    animationSpec = tween(durationMillis = 180)
                                )
                            }

                            if (shouldOpen) {
                                onOpenRowKeyChange(rowKey)
                            } else if (openRowKey == rowKey) {
                                onOpenRowKeyChange(null)
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }
}

private enum class ManageAction { Refresh, NewFile, NewFolder, Search }

private enum class SelectAction { Refresh, Create, Download }

@Composable
fun ConfigHubScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    initialTab: ConfigHubTab,
    onEditorModeChange: (Boolean) -> Unit = {},
    enableBackHandler: Boolean = true
) {
    val stateHolder = rememberSaveableStateHolder()
    val pagePadding = 20.dp
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var editorPath by rememberSaveable { mutableStateOf<String?>(null) }

    var selectOverflowExpanded by rememberSaveable { mutableStateOf(false) }
    var selectOverflowAnchorBoundsInRoot by remember { mutableStateOf<IntRect?>(null) }
    var manageOverflowExpanded by rememberSaveable { mutableStateOf(false) }
    var manageOverflowAnchorBoundsInRoot by remember { mutableStateOf<IntRect?>(null) }

    var manageCurrentPath by rememberSaveable { mutableStateOf("") }

    var manageAction by rememberSaveable { mutableStateOf<ManageAction?>(null) }
    var manageSearchVisible by rememberSaveable { mutableStateOf(false) }
    var manageQuery by rememberSaveable { mutableStateOf("") }

    var selectAction by rememberSaveable { mutableStateOf<SelectAction?>(null) }
    var selectCoreName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectActive by rememberSaveable { mutableStateOf<String?>(null) }
    var selectSelectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var selectActivateRequest by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = editorPath != null) {
        editorPath = null
    }

    LaunchedEffect(editorPath) {
        val isInEditor = editorPath != null
        onEditorModeChange(isInEditor)
        if (isInEditor) {
            onNavVisibilityChange(false)
        } else {
            onNavVisibilityChange(true)
        }
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var swipeCloseSignal by rememberSaveable { mutableStateOf(0) }

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
                swipeCloseSignal += 1
                if (now > last) {
                    onNavVisibilityChange(false)
                } else if (now < last) {
                    onNavVisibilityChange(true)
                }
                last = now
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors().pageBg)
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
                if (tab == ConfigHubTab.Manage) {
                    stateHolder.SaveableStateProvider("config_manage") {
                        ConfigBrowserContent(
                            rootPath = "",
                            titlePath = "/data/adb/box",
                            onExit = onBack,
                            onOpenEditor = { editorPath = it },
                            action = manageAction,
                            onActionConsumed = { manageAction = null },
                            query = manageQuery,
                            onQueryChange = { manageQuery = it },
                            searchVisible = manageSearchVisible,
                            currentPath = manageCurrentPath,
                            onCurrentPathChange = { manageCurrentPath = it },
                            backEnabled = editorPath == null && enableBackHandler,
                            swipeCloseSignal = swipeCloseSignal
                        )
                    }
                } else {
                    stateHolder.SaveableStateProvider("config_select") {
                        ConfigSelectCoreFolderContent(
                            onExit = onBack,
                            onOpenEditor = { editorPath = it },
                            action = selectAction,
                            onActionConsumed = { selectAction = null },
                            onCoreInfo = { core, active ->
                                selectCoreName = core
                                selectActive = active
                                if (selectSelectedFile == null) {
                                    selectSelectedFile = active
                                }
                            },
                            onSelectFile = { selectSelectedFile = it },
                            activateRequest = selectActivateRequest,
                            onActivateConsumed = { selectActivateRequest = null },
                            backEnabled = editorPath == null && enableBackHandler,
                            swipeCloseSignal = swipeCloseSignal
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
            if (tab == ConfigHubTab.Manage) {
                FloatingManageTopBar(
                    onBack = onBack,
                    onAction = { manageAction = it },
                    searchEnabled = manageSearchVisible,
                    query = manageQuery,
                    onQueryChange = { manageQuery = it },
                    onSearch = { manageAction = ManageAction.Search },
                    onClear = {
                        manageQuery = ""
                    },
                    onToggleSearch = {
                        manageSearchVisible = !manageSearchVisible
                        if (!manageSearchVisible) {
                            manageQuery = ""
                        }
                    },
                    menuExpanded = manageOverflowExpanded,
                    onMenuExpandedChange = { manageOverflowExpanded = it },
                    onMenuAnchorBoundsInRootChange = { manageOverflowAnchorBoundsInRoot = it },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .onGloballyPositioned { coordinates ->
                            val h = coordinates.size.height
                            if (h > 0) {
                                topBarHeightPx = h
                                lastNonZeroTopBarHeightPx = h
                            }
                        }
                )
            } else {
                FloatingSelectTopBar(
                    onBack = onBack,
                    coreName = selectCoreName,
                    selectedFile = selectSelectedFile,
                    activeFile = selectActive,
                    onOpenEditor = { path -> editorPath = path },
                    onActivate = { fileName -> selectActivateRequest = fileName },
                    onMenuAction = { selectAction = it },
                    menuExpanded = selectOverflowExpanded,
                    onMenuExpandedChange = { selectOverflowExpanded = it },
                    onMenuAnchorBoundsInRootChange = { selectOverflowAnchorBoundsInRoot = it },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .onGloballyPositioned { coordinates ->
                            val h = coordinates.size.height
                            if (h > 0) {
                                topBarHeightPx = h
                                lastNonZeroTopBarHeightPx = h
                            }
                        }
                )
            }
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
            ConfigSelectOverflowMenu(
                expanded = selectOverflowExpanded,
                onDismissRequest = { selectOverflowExpanded = false },
                anchorBoundsInRoot = selectOverflowAnchorBoundsInRoot,
                onMenuAction = { action ->
                    selectOverflowExpanded = false
                    selectAction = action
                }
            )
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
            ConfigManageOverflowMenu(
                expanded = manageOverflowExpanded,
                onDismissRequest = { manageOverflowExpanded = false },
                anchorBoundsInRoot = manageOverflowAnchorBoundsInRoot,
                onAction = { action ->
                    manageOverflowExpanded = false
                    manageAction = action
                }
            )
        }

        if (editorPath != null) {
            ConfigEditorScreen(
                filePath = editorPath!!,
                onBack = { editorPath = null }
            )
        }
    }
}

@Composable
private fun FloatingSelectTopBar(
    onBack: () -> Unit,
    coreName: String?,
    selectedFile: String?,
    activeFile: String?,
    onOpenEditor: (String) -> Unit,
    onActivate: (String) -> Unit,
    onMenuAction: (SelectAction) -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onMenuAnchorBoundsInRootChange: (IntRect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val backdrop = requireNotNull(LocalLiquidBackdrop.current)
    val accent = appAccentColor()
    val isDark = ThemeManager.shouldUseDarkTheme()

    val selectedTint = if (isDark) Color(0xFF2B2F37) else Color(0xFFE3E6EA)
    val tint = selectedTint.copy(alpha = 0.25f)
    val fallback = selectedTint.copy(alpha = 0.80f)

    

    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, top = 8.dp, end = 20.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassButton(
            onClick = onBack,
            backdrop = backdrop,
            surfaceColor = tint
        ) {
            Text(
                text = stringResource(R.string.tools_config_back_compact),
                color = c.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.TopEnd)
                .onGloballyPositioned { coords ->
                    val r = coords.boundsInWindow()
                    onMenuAnchorBoundsInRootChange(
                        IntRect(
                            left = r.left.roundToInt(),
                            top = r.top.roundToInt(),
                            right = r.right.roundToInt(),
                            bottom = r.bottom.roundToInt()
                        )
                    )
                }
        ) {
            LiquidGlassIconButton(
                onClick = { onMenuExpandedChange(true) },
                backdrop = backdrop,
                surfaceColor = tint
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                    tint = c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingManageTopBar(
    onBack: () -> Unit,
    onAction: (ManageAction) -> Unit,
    searchEnabled: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onToggleSearch: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onMenuAnchorBoundsInRootChange: (IntRect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val backdrop = requireNotNull(LocalLiquidBackdrop.current)
    val accent = appAccentColor()
    val isDark = ThemeManager.shouldUseDarkTheme()

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchEnabled) {
        if (searchEnabled) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    val selectedTint = if (isDark) Color(0xFF2B2F37) else Color(0xFFE3E6EA)
    val tint = selectedTint.copy(alpha = if (isDark) 0.22f else 0.30f)
    val fallback = selectedTint.copy(alpha = if (isDark) 0.82f else 0.74f)

    var menu by remember { mutableStateOf(false) }

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
                    text = stringResource(R.string.tools_config_back_compact),
                    color = c.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            LiquidGlassIconButton(
                onClick = onToggleSearch,
                backdrop = backdrop,
                surfaceColor = tint
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.tools_config_search_hint),
                    tint = if (searchEnabled) accent else c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopEnd)
                    .onGloballyPositioned { coords ->
                        val r = coords.boundsInWindow()
                        onMenuAnchorBoundsInRootChange(
                            IntRect(
                                left = r.left.roundToInt(),
                                top = r.top.roundToInt(),
                                right = r.right.roundToInt(),
                                bottom = r.bottom.roundToInt()
                            )
                        )
                    }
            ) {
                LiquidGlassIconButton(
                    onClick = {
                        onAction(ManageAction.Refresh)
                    },
                    backdrop = backdrop,
                    surfaceColor = tint
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_more),
                        tint = c.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = searchEnabled,
            enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 140)) +
                androidx.compose.animation.expandVertically(animationSpec = androidx.compose.animation.core.tween(durationMillis = 180)),
            exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 110)) +
                androidx.compose.animation.shrinkVertically(animationSpec = androidx.compose.animation.core.tween(durationMillis = 160))
        ) {
            val selectionColors = remember(accent) {
                TextSelectionColors(
                    handleColor = accent,
                    backgroundColor = accent.copy(alpha = 0.28f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    LiquidGlassTextFieldPill(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        backdrop = backdrop,
                        surfaceColor = tint
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp)
                        ) {
                            if (query.isBlank()) {
                                Text(
                                    text = stringResource(R.string.tools_config_search_hint),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = c.textSecondary,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = query,
                                    onValueChange = onQueryChange,
                                    singleLine = true,
                                    cursorBrush = SolidColor(accent),
                                    textStyle = TextStyle(
                                        color = c.textPrimary,
                                        fontSize = MaterialTheme.typography.labelLarge.fontSize
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                )

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = query.isNotBlank(),
                                    enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 90)),
                                    exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 90))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(Capsule())
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    onClear()
                                                    runCatching { focusRequester.requestFocus() }
                                                    keyboardController?.show()
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = null,
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(Capsule())
                ) {
                    LiquidGlassButton(
                        onClick = onSearch,
                        enabled = query.isNotBlank(),
                        backdrop = backdrop,
                        surfaceColor = tint
                    ) {
                        Text(
                            text = stringResource(R.string.action_search),
                            color = if (query.isNotBlank()) c.textPrimary else c.textSecondary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun FloatingBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val interactionSource = remember { MutableInteractionSource() }
    val isDark = ThemeManager.shouldUseDarkTheme()

    val selectedTint = if (isDark) Color(0xFF2B2F37) else Color(0xFFE3E6EA)
    // Keep the same base hue as the bottom nav selected indicator,
    // but restore a more transparent + blurred glass look.
    val tint = selectedTint.copy(alpha = 0.25f)
    val fallback = selectedTint.copy(alpha = 0.80f)

    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, top = 8.dp)
            .clip(Capsule())
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(tint)
        )
        Box(
            modifier = Modifier
                .clip(Capsule())
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onBack
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.tools_config_back_compact),
                color = c.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ConfigBrowserContent(
    rootPath: String,
    titlePath: String,
    onExit: () -> Unit,
    onOpenEditor: (String) -> Unit,
    action: ManageAction?,
    onActionConsumed: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    searchVisible: Boolean,
    currentPath: String,
    onCurrentPathChange: (String) -> Unit,
    backEnabled: Boolean,
    swipeCloseSignal: Int
) {
    val c = appColors()
    val scope = rememberCoroutineScope()
    val accent = appAccentColor()
    val danger = appErrorColor()

    var items by remember { mutableStateOf<List<ConfigFsItem>>(emptyList()) }
    var browseItems by remember { mutableStateOf<List<ConfigFsItem>>(emptyList()) }
    var rootItems by remember { mutableStateOf<List<ConfigFsItem>>(emptyList()) }
    var suppressAutoLoad by remember { mutableStateOf(false) }
    var lastQuery by remember { mutableStateOf(query) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ErrorToast(
        message = error,
        onConsumed = { error = null }
    )

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var createName by rememberSaveable { mutableStateOf("") }
    var createIsFolder by rememberSaveable { mutableStateOf(false) }

    var renameTarget by remember { mutableStateOf<ConfigFsItem?>(null) }
    var renameName by rememberSaveable { mutableStateOf("") }

    var deleteTarget by remember { mutableStateOf<ConfigFsItem?>(null) }

    var openRowKey by rememberSaveable { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            ConfigRepository.warmUpShell()
            val res = if (query.isBlank()) {
                ConfigRepository.listPath(currentPath)
            } else {
                ConfigRepository.search(query)
            }
            loading = false
            if (res.error != null) {
                error = res.error
            } else {
                val data = res.data.orEmpty()
                items = data
                if (query.isBlank()) {
                    browseItems = data
                    if (currentPath == rootPath) {
                        rootItems = data
                    }
                }
            }
        }
    }

    LaunchedEffect(currentPath) {
        if (query.isBlank()) {
            if (suppressAutoLoad) {
                suppressAutoLoad = false
            } else {
                load()
            }
        }
    }

    LaunchedEffect(query) {
        val becameBlank = lastQuery.isNotBlank() && query.isBlank()
        if (becameBlank) {
            val cachedRoot = if (rootItems.isNotEmpty()) rootItems else browseItems

            if (currentPath != rootPath) {
                suppressAutoLoad = cachedRoot.isNotEmpty()
                onCurrentPathChange(rootPath)
            }

            if (cachedRoot.isNotEmpty()) {
                items = cachedRoot
                error = null
            } else if (currentPath == rootPath) {
                load()
            }
        }
        lastQuery = query
    }

    BackHandler(enabled = backEnabled) {
        if (query.isNotBlank()) {
            onQueryChange("")
        } else if (currentPath.isNotBlank()) {
            onCurrentPathChange(currentPath.substringBeforeLast('/', ""))
        } else {
            onExit()
        }
    }

    LaunchedEffect(action) {
        when (action) {
            ManageAction.Refresh -> {
                onActionConsumed()
                load()
            }

            ManageAction.Search -> {
                onActionConsumed()
                load()
            }

            ManageAction.NewFile -> {
                onActionConsumed()
                createIsFolder = false
                showCreate = true
            }

            ManageAction.NewFolder -> {
                onActionConsumed()
                createIsFolder = true
                showCreate = true
            }

            null -> Unit
        }
    }

    LaunchedEffect(searchVisible) {
        if (!searchVisible && query.isNotBlank()) {
            onQueryChange("")
            items = browseItems
            error = null
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = c.card,
            tonalElevation = 0.dp,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = {
                Text(
                    text = if (createIsFolder) stringResource(R.string.tools_config_create_folder) else stringResource(R.string.tools_config_create_file)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.tools_config_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (createIsFolder) stringResource(R.string.tools_config_create_folder_helper) else stringResource(R.string.tools_config_create_file_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = createName.trim()
                        if (name.isBlank()) return@TextButton
                        showCreate = false
                        createName = ""
                        scope.launch {
                            loading = true
                            val res = ConfigRepository.createItem(currentPath, name, createIsFolder)
                            loading = false
                            if (res.error != null) error = res.error
                            load()
                        }
                    }
                ) { Text(text = stringResource(R.string.tools_config_menu_create)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(text = stringResource(R.string.action_cancel), color = c.textPrimary) } }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = c.card,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = { Text(text = stringResource(R.string.tools_config_rename)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.tools_config_current_name, target.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = { renameName = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.tools_config_new_name)) },
                        placeholder = { Text(text = target.name) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = c.textPrimary,
                            unfocusedTextColor = c.textPrimary,
                            cursorColor = accent,
                            focusedBorderColor = accent,
                            unfocusedBorderColor = c.divider,
                            focusedLabelColor = c.textSecondary,
                            unfocusedLabelColor = c.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameName.trim()
                        if (newName.isBlank()) return@TextButton
                        renameTarget = null
                        renameName = ""
                        scope.launch {
                            loading = true
                            val res = ConfigRepository.renameItem(target, newName)
                            loading = false
                            if (res.error != null) error = res.error
                            load()
                        }
                    }
                ) { Text(text = stringResource(R.string.action_save), color = accent) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(text = stringResource(R.string.action_cancel), color = c.textPrimary) } }
        )
    }

    deleteTarget?.let { target ->
        val deleteTargetType = if (target is ConfigFsItem.Folder) "folder" else "file"
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = c.card,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = { Text(text = stringResource(R.string.tools_config_delete_title, deleteTargetType)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.tools_config_delete_confirm, target.name))
                    Text(
                        text = if (target is ConfigFsItem.Folder) stringResource(R.string.tools_config_delete_folder_warning) else stringResource(R.string.tools_config_delete_file_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = danger
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            loading = true
                            val res = ConfigRepository.deleteItem(target)
                            loading = false
                            if (res.error != null) error = res.error
                            load()
                        }
                    }
                ) { Text(text = stringResource(R.string.action_delete), color = danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(text = stringResource(R.string.action_cancel), color = c.textPrimary) } }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ToolsSectionCard(
            title = stringResource(R.string.tools_config_files_folders_title),
            subtitle = when {
                loading -> stringResource(R.string.tools_config_loading)
                items.isEmpty() && query.isNotBlank() -> stringResource(R.string.tools_config_no_results_found)
                items.isEmpty() -> stringResource(R.string.tools_config_empty_directory)
                else -> stringResource(R.string.tools_config_items_count, items.size)
            }
        ) {
            val canGoUp = query.isBlank() && currentPath.isNotBlank()
            if (loading) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = accent,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = stringResource(R.string.tools_config_loading_files),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                }
            } else if (items.isEmpty()) {
                if (canGoUp) {
                    val parentPath = currentPath.substringBeforeLast('/', "")
                    ToolsRowIcon(
                        icon = Icons.Filled.Folder,
                        title = "..",
                        subtitle = stringResource(R.string.tools_config_parent_directory),
                        showDivider = true,
                        onClick = { onCurrentPathChange(parentPath) }
                    )
                }

                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isNotBlank()) stringResource(R.string.tools_config_no_files_match) else stringResource(R.string.tools_config_directory_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                }
            } else {
                if (canGoUp) {
                    val parentPath = currentPath.substringBeforeLast('/', "")
                    ToolsRowIcon(
                        icon = Icons.Filled.Folder,
                        title = "..",
                        subtitle = stringResource(R.string.tools_config_parent_directory),
                        showDivider = true,
                        onClick = {
                            openRowKey = null
                            onCurrentPathChange(parentPath)
                        }
                    )
                }

                items.forEachIndexed { idx, item ->
                    val isLast = idx == items.lastIndex
                    when (item) {
                        is ConfigFsItem.Folder -> SwipeRevealRow(
                            rowKey = "manage:${item.path}",
                            openRowKey = openRowKey,
                            onOpenRowKeyChange = { openRowKey = it },
                            closeSignal = swipeCloseSignal,
                            revealWidth = 112.dp,
                            onRename = {
                                renameName = item.name
                                renameTarget = item
                            },
                            onDelete = { deleteTarget = item },
                            content = {
                                ToolsRowIcon(
                                    icon = Icons.Filled.Folder,
                                    title = item.name,
                                    subtitle = stringResource(R.string.tools_config_folder),
                                    showDivider = false,
                                    onClick = {
                                        openRowKey = null
                                        onCurrentPathChange(item.path)
                                    }
                                )
                            }
                        )

                        is ConfigFsItem.File -> SwipeRevealRow(
                            rowKey = "manage:${item.path}",
                            openRowKey = openRowKey,
                            onOpenRowKeyChange = { openRowKey = it },
                            closeSignal = swipeCloseSignal,
                            revealWidth = 112.dp,
                            onRename = {
                                renameName = item.name
                                renameTarget = item
                            },
                            onDelete = { deleteTarget = item },
                            content = {
                                ToolsFileRow(
                                    fileName = item.name,
                                    subtitle = item.description.ifBlank { stringResource(R.string.tools_config_file) },
                                    showDivider = false,
                                    onClick = {
                                        openRowKey = null
                                        onOpenEditor(item.path)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        )
                    }

                    if (!isLast) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 52.dp)
                                .height(1.dp)
                                .background(c.divider)
                        )
                    }
                }
            }
        }
    }

}

@Composable
private fun ConfigSelectCoreFolderContent(
    onExit: () -> Unit,
    onOpenEditor: (String) -> Unit,
    action: SelectAction?,
    onActionConsumed: () -> Unit,
    onCoreInfo: (coreName: String?, activeFile: String?) -> Unit,
    onSelectFile: (String?) -> Unit,
    activateRequest: String?,
    onActivateConsumed: () -> Unit,
    backEnabled: Boolean,
    swipeCloseSignal: Int
) {
    val c = appColors()
    val scope = rememberCoroutineScope()
    val accent = appAccentColor()
    val danger = appErrorColor()
    val context = LocalContext.current
    val pullPrefs = remember { context.getSharedPreferences("config_pull_cache", android.content.Context.MODE_PRIVATE) }

    fun getPullUrlForPath(path: String): String? = pullPrefs.getString(path, null)

    fun removePullCacheForPath(path: String) {
        val allKeys = pullPrefs.all.keys
        val editor = pullPrefs.edit()
        if (allKeys.contains(path)) {
            editor.remove(path)
        }
        val prefix = if (path.endsWith('/')) path else "$path/"
        allKeys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun renamePullCachePath(oldPath: String, newPath: String) {
        val url = pullPrefs.getString(oldPath, null) ?: return
        pullPrefs.edit().remove(oldPath).putString(newPath, url).apply()
    }

    var coreName by rememberSaveable { mutableStateOf<String?>(null) }
    var active by rememberSaveable { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ConfigFsItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var currentPath by rememberSaveable { mutableStateOf<String?>(null) }

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var createName by rememberSaveable { mutableStateOf("") }

    var renameTarget by remember { mutableStateOf<ConfigFsItem?>(null) }
    var renameName by rememberSaveable { mutableStateOf("") }

    var deleteTarget by remember { mutableStateOf<ConfigFsItem?>(null) }

    var openRowKey by rememberSaveable { mutableStateOf<String?>(null) }

    var showPull by rememberSaveable { mutableStateOf(false) }
    var pullUrl by rememberSaveable { mutableStateOf("") }
    var pullFileName by rememberSaveable { mutableStateOf("") }

    val coreNotFoundText = stringResource(R.string.tools_config_core_not_found)

    fun reload() {
        scope.launch {
            loading = true
            error = null
            ConfigRepository.warmUpShell()
            val core = coreName ?: ConfigRepository.getCurrentCoreName().also { coreName = it }
            if (core.isNullOrBlank()) {
                error = coreNotFoundText
                loading = false
                return@launch
            }
            if (currentPath.isNullOrBlank()) {
                currentPath = core
            }
            val act = active ?: ConfigRepository.getActiveConfigFileName(core).also { active = it }
            val res = ConfigRepository.listPath(currentPath ?: core)
            loading = false
            if (res.error != null) {
                error = res.error
            } else {
                val all = res.data.orEmpty()
                val filtered = all.filter {
                    when (it) {
                        is ConfigFsItem.Folder -> true
                        is ConfigFsItem.File -> {
                            val ext = it.name.substringAfterLast('.', "").lowercase()
                            ext in listOf("yaml", "yml", "json")
                        }
                    }
                }
                items = filtered
            }

            onCoreInfo(coreName, active)
        }
    }

    LaunchedEffect(Unit) { reload() }

    BackHandler(enabled = backEnabled) {
        val core = coreName
        val cur = currentPath
        if (!core.isNullOrBlank() && !cur.isNullOrBlank() && cur != core) {
            val parentPath = cur.substringBeforeLast('/', core)
            openRowKey = null
            currentPath = parentPath
            reload()
        } else {
            onExit()
        }
    }

    LaunchedEffect(action) {
        when (action) {
            SelectAction.Refresh -> {
                onActionConsumed()
                reload()
            }

            SelectAction.Create -> {
                onActionConsumed()
                showCreate = true
            }

            SelectAction.Download -> {
                onActionConsumed()
                showPull = true
            }

            null -> Unit
        }
    }

    LaunchedEffect(activateRequest) {
        val req = activateRequest ?: return@LaunchedEffect
        val core = coreName ?: return@LaunchedEffect
        onActivateConsumed()
        scope.launch {
            val res = ConfigRepository.setActiveConfigFile(core, req)
            if (res.error != null) error = res.error
            active = req
            onSelectFile(req)
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = c.card,
            tonalElevation = 0.dp,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = { Text(text = stringResource(R.string.tools_config_create_config_file)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.tools_config_file_name)) },
                        placeholder = { Text(text = stringResource(R.string.tools_config_placeholder_config_yaml)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.tools_config_supported_formats),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val core = coreName ?: return@TextButton
                        val name = createName.trim()
                        if (name.isBlank()) return@TextButton
                        showCreate = false
                        createName = ""
                        scope.launch {
                            loading = true
                            val res = ConfigRepository.createItem(core, name, false)
                            loading = false
                            if (res.error != null) error = res.error
                            reload()
                        }
                    }
                ) { Text(text = stringResource(R.string.tools_config_menu_create)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(text = stringResource(R.string.action_cancel), color = c.textPrimary) } }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = c.card,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = { Text(text = stringResource(R.string.tools_config_rename)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.tools_config_current_name, target.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                    OutlinedTextField(
                        value = renameName,
                        onValueChange = { renameName = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.tools_config_new_name)) },
                        placeholder = { Text(text = target.name) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = c.textPrimary,
                            unfocusedTextColor = c.textPrimary,
                            cursorColor = accent,
                            focusedBorderColor = accent,
                            unfocusedBorderColor = c.divider,
                            focusedLabelColor = c.textSecondary,
                            unfocusedLabelColor = c.textSecondary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameName.trim()
                        if (newName.isBlank()) return@TextButton
                        renameTarget = null
                        renameName = ""
                        scope.launch {
                            loading = true
                            val res = ConfigRepository.renameItem(target, newName)
                            loading = false
                            if (res.error != null) error = res.error
                            if (res.error == null) {
                                val parent = target.path.substringBeforeLast('/', "")
                                val newRel = if (parent.isBlank()) newName else "$parent/$newName"
                                renamePullCachePath(target.path, newRel)
                            }
                            reload()
                        }
                    }
                ) { Text(text = stringResource(R.string.action_save), color = accent) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(text = stringResource(R.string.action_cancel), color = c.textPrimary) } }
        )
    }

    deleteTarget?.let { target ->
        val deleteTargetType = if (target is ConfigFsItem.Folder) "folder" else "file"
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = c.card,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = { Text(text = stringResource(R.string.tools_config_delete_title, deleteTargetType)) },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.tools_config_delete_confirm, target.name))
                    Text(
                        text = if (target is ConfigFsItem.Folder) stringResource(R.string.tools_config_delete_folder_warning) else stringResource(R.string.tools_config_delete_file_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = danger
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openRowKey = null
                        deleteTarget = null
                        scope.launch {
                            loading = true
                            val res = ConfigRepository.deleteItem(target)
                            loading = false
                            if (res.error != null) error = res.error
                            if (res.error == null) {
                                removePullCacheForPath(target.path)
                            }
                            reload()
                        }
                    }
                ) { Text(text = stringResource(R.string.action_delete), color = danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(text = stringResource(R.string.action_cancel), color = c.textPrimary) } }
        )
    }

    if (showPull) {
        AlertDialog(
            onDismissRequest = { showPull = false },
            containerColor = c.card,
            tonalElevation = 0.dp,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = { Text(text = stringResource(R.string.tools_config_download_config_from_url)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = pullUrl,
                        onValueChange = { pullUrl = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.tools_config_url)) },
                        placeholder = { Text(text = stringResource(R.string.tools_config_url_example)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pullFileName,
                        onValueChange = { pullFileName = it },
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.tools_config_save_as)) },
                        placeholder = { Text(text = stringResource(R.string.tools_config_placeholder_config_yaml)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.tools_config_download_config_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val core = coreName ?: return@TextButton
                        val url = pullUrl.trim()
                        val name = pullFileName.trim()
                        if (url.isBlank() || name.isBlank()) return@TextButton
                        showPull = false
                        scope.launch {
                            loading = true
                            val res = ConfigRepository.pullConfigFileFromUrl(core, url, name)
                            loading = false
                            if (res.error != null) error = res.error
                            if (res.error == null) {
                                val rel = "$core/$name"
                                pullPrefs.edit().putString(rel, url).apply()
                            }
                            reload()
                        }
                    }
                ) { Text(text = stringResource(R.string.tools_config_menu_download)) }
            },
            dismissButton = { TextButton(onClick = { showPull = false }) { Text(text = stringResource(R.string.action_cancel), color = c.textPrimary) } }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ToolsSectionCard(
            title = stringResource(R.string.tools_config_configuration_files) + (coreName?.let { " ($it)" } ?: ""),
            subtitle = when {
                loading -> stringResource(R.string.tools_config_loading_files)
                items.isEmpty() -> stringResource(R.string.tools_config_no_config_files_found)
                else -> stringResource(
                    R.string.tools_config_files_active_summary,
                    items.size,
                    active ?: stringResource(R.string.tools_config_active_none)
                )
            }
        ) {
            if (loading) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = accent,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = stringResource(R.string.tools_config_loading_configuration_files),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                }
            } else if (items.isEmpty()) {
                val core = coreName
                val cur = currentPath
                val canGoUp = !core.isNullOrBlank() && !cur.isNullOrBlank() && cur != core
                if (canGoUp) {
                    val parentPath = cur.substringBeforeLast('/', core)
                    ToolsRowIcon(
                        icon = Icons.Filled.Folder,
                        title = "..",
                        subtitle = stringResource(R.string.tools_config_parent_directory),
                        showDivider = true,
                        onClick = {
                            openRowKey = null
                            currentPath = parentPath
                            reload()
                        }
                    )
                }

                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tools_config_no_configuration_files_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary
                        )
                        Text(
                            text = stringResource(R.string.tools_config_create_new_config_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                }
            } else {
                val core = coreName
                val cur = currentPath
                val canGoUp = !core.isNullOrBlank() && !cur.isNullOrBlank() && cur != core

                if (canGoUp) {
                    val parentPath = cur.substringBeforeLast('/', core)
                    ToolsRowIcon(
                        icon = Icons.Filled.Folder,
                        title = "..",
                        subtitle = stringResource(R.string.tools_config_parent_directory),
                        showDivider = true,
                        onClick = {
                            currentPath = parentPath
                            reload()
                        }
                    )
                }

                items.forEachIndexed { idx, item ->
                    val isLast = idx == items.lastIndex
                    when (item) {
                        is ConfigFsItem.Folder -> SwipeRevealRow(
                            rowKey = "select:${item.path}",
                            openRowKey = openRowKey,
                            onOpenRowKeyChange = { openRowKey = it },
                            closeSignal = swipeCloseSignal,
                            revealWidth = 120.dp,
                            onRename = {
                                renameName = item.name
                                renameTarget = item
                            },
                            onDelete = {
                                deleteTarget = item
                            },
                            content = {
                                ToolsRowIcon(
                                    icon = Icons.Filled.Folder,
                                    title = item.name,
                                    subtitle = stringResource(R.string.tools_config_folder),
                                    showDivider = false,
                                    onClick = {
                                        scope.launch {
                                            loading = true
                                            openRowKey = null
                                            currentPath = item.path
                                            val res = ConfigRepository.listPath(item.path)
                                            loading = false
                                            if (res.error != null) {
                                                error = res.error
                                            } else {
                                                items = res.data.orEmpty()
                                            }
                                        }
                                    }
                                )
                            }
                        )

                        is ConfigFsItem.File -> {
                            val isActive = active == item.name
                            SwipeRevealRow(
                                rowKey = "select:${item.path}",
                                openRowKey = openRowKey,
                                onOpenRowKeyChange = { openRowKey = it },
                                closeSignal = swipeCloseSignal,
                                revealWidth = 180.dp,
                                onRefresh = getPullUrlForPath(item.path)?.let { cachedUrl ->
                                    {
                                        val uaCore = item.path.substringBefore('/', "")
                                        val userAgent = when (uaCore) {
                                            "mihomo" -> "ClashMeta"
                                            else -> uaCore
                                        }
                                        scope.launch {
                                            loading = true
                                            val r = ConfigRepository.pullConfigFileFromUrlToRelativePath(
                                                url = cachedUrl,
                                                relativePath = item.path,
                                                userAgent = userAgent
                                            )
                                            loading = false
                                            if (r.error != null) error = r.error
                                            reload()
                                        }
                                    }
                                },
                                onEdit = { onOpenEditor(item.path) },
                                onRename = {
                                    renameName = item.name
                                    renameTarget = item
                                },
                                onDelete = { deleteTarget = item },
                                content = {
                                    ToolsFileRow(
                                        fileName = item.name,
                                        subtitle = if (isActive) stringResource(R.string.tools_config_active_configuration) else stringResource(R.string.tools_config_tap_to_select),
                                        showDivider = false,
                                        badge = if (isActive) {
                                            { StatusBadge(text = stringResource(R.string.tools_config_badge_active), isActive = true) }
                                        } else null,
                                        onClick = {
                                            val core = coreName
                                            if (core.isNullOrBlank()) return@ToolsFileRow

                                            openRowKey = null
                                            scope.launch {
                                                val res = ConfigRepository.setActiveConfigFile(core, item.name)
                                                if (res.error != null) {
                                                    error = res.error
                                                    return@launch
                                                }
                                                active = item.name
                                                onSelectFile(item.name)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            )
                        }
                    }

                    if (!isLast) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 52.dp)
                                .height(1.dp)
                                .background(c.divider)
                        )
                    }
                }
            }
        }

        
    }
}
