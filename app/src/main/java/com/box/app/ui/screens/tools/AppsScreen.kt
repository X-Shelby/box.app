package com.box.app.ui.screens.tools

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.box.app.R
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.screens.tools.apps.AppsManageAction
import com.box.app.ui.screens.tools.apps.AppsOverflowMenu
import com.box.app.ui.screens.tools.apps.AppsProxyMode
import com.box.app.ui.screens.tools.apps.AppsSortOrder
import com.box.app.ui.screens.tools.apps.SmartSelectApplyMode
import com.box.app.ui.screens.tools.apps.SmartSelectConfirmDialog
import com.box.app.ui.screens.tools.apps.SmartSelectProgressDialog
import com.box.app.ui.screens.tools.apps.SortFilterBottomSheet
import com.box.app.ui.screens.tools.apps.filterAndSortApps
import com.box.app.ui.screens.tools.apps.rememberAppsScreenState
import com.box.app.utils.AppIcon
import com.box.app.utils.AppUtils
import com.box.app.utils.Permissions
import com.box.app.utils.rememberPermissionHelper
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ─── 预分配静态 Shape ────────────────────────────────────────────────────────

private val shapeTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
private val shapeBottom = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
private val shapeFull = RoundedCornerShape(16.dp)
private val shapeNone = RoundedCornerShape(0.dp)
private val iconClipShape = SmoothRoundedCornerShape(10.dp)

@Composable
@OptIn(FlowPreview::class)
fun ToolsAppsScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scrollBehavior = MiuixScrollBehavior()

    val filterPrefs = remember {
        context.getSharedPreferences("tools_apps_sort_filter", android.content.Context.MODE_PRIVATE)
    }

    val state = rememberAppsScreenState()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var needsRestart by rememberSaveable { mutableStateOf(false) }
    var hintDismissed by remember { mutableStateOf(false) }

    fun requestSave(afterSaved: (() -> Unit)? = null) {
        val dirty = state.proxyMode != state.savedProxyMode ||
            state.selectedPackages != state.savedSelectedPackages
        if (!dirty || state.loading || state.saving) {
            afterSaved?.invoke()
            return
        }
        scope.launch {
            val err = state.saveConfig(context)
            if (err != null) {
                state.saveError = err
            } else {
                needsRestart = true
                hintDismissed = false
                afterSaved?.invoke()
            }
        }
    }

    // ─── 派生数据（缓存，仅依赖变化时重算） ──────────────────────────────

    val filtered by remember(
        state.apps, state.debouncedQuery,
        state.appliedSortOrder, state.appliedAppType,
        state.appliedNetworkFilter, state.appliedUserFilter,
        state.userDisplayNames
    ) {
        derivedStateOf {
            filterAndSortApps(
                apps = state.apps,
                query = state.debouncedQuery,
                sortOrder = state.appliedSortOrder,
                appType = state.appliedAppType,
                networkFilter = state.appliedNetworkFilter,
                userFilter = state.appliedUserFilter,
                userDisplayNames = state.userDisplayNames
            )
        }
    }

    // 单次遍历构建平坦列表（已选在前 + 分隔标记 + 未选在后）
    val flatItems: List<ListItem> = remember(filtered, state.selectedPackages, state.userDisplayNames) {
        buildFlatList(filtered, state.selectedPackages, state.userDisplayNames)
    }

    val selectedCount = remember(flatItems) {
        flatItems.count { it is ListItem.App && it.selected }
    }

    val proxyModeLabel = when (state.proxyMode) {
        AppsProxyMode.BLACKLIST -> stringResource(R.string.tools_apps_proxy_blacklist)
        AppsProxyMode.WHITELIST -> stringResource(R.string.tools_apps_proxy_whitelist)
        AppsProxyMode.CORE -> stringResource(R.string.tools_apps_proxy_core)
    }

    // ─── 副作用 ────────────────────────────────────────────────────────

    // 进入页面时请求应用列表权限（国产 ROM 需要）
    val permHelper = rememberPermissionHelper()
    LaunchedEffect(Unit) {
        if (!permHelper.isGranted(Permissions.GET_INSTALLED_APPS)) {
            permHelper.requestSuspend(Permissions.GET_INSTALLED_APPS)
        }
        state.loadSavedFilters(filterPrefs)
        state.loadAppsAndBackendConfig(context, forceRefresh = false)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { state.query }
            .debounce(120)
            .collect { state.debouncedQuery = it }
    }

    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { now ->
                if (now > last) onNavVisibilityChange(false)
                else if (now < last) onNavVisibilityChange(true)
                last = now
            }
    }

    LaunchedEffect(state.showSortFilter) {
        if (state.showSortFilter) state.syncDraftsFromApplied()
    }

    LaunchedEffect(state.appliedSortOrder, state.appliedAppType, state.appliedNetworkFilter, state.appliedUserFilter, state.filtersLoaded) {
        if (state.filtersLoaded) state.saveFilters(filterPrefs)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { state.menuAction }
            .collect { action ->
                if (action == null) return@collect
                state.menuAction = null
                when (action) {
                    AppsManageAction.Refresh -> requestSave {
                        scope.launch { state.loadAppsAndBackendConfig(context, forceRefresh = true) }
                    }
                    else -> state.handleMenuAction(action, filtered)
                }
            }
    }

    LaunchedEffect(state.smartSelecting) {
        if (state.smartSelecting) state.runSmartSelect(context, filtered)
    }

    BackHandler(enabled = true) {
        requestSave(afterSaved = onBack)
    }

    fun openSystemAppDetails(packageName: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ─── 主布局 ────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.tools_apps_title),
                color = MiuixTheme.colorScheme.surface,
                largeTitle = stringResource(R.string.tools_apps_title),
                subtitle = if (needsRestart && !hintDismissed) {
                    stringResource(R.string.tools_apps_hint_restart)
                } else {
                    "$proxyModeLabel · $selectedCount / ${filtered.size}"
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = { requestSave(afterSaved = onBack) }) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                    }
                },
                actions = {
                    val dirty = state.proxyMode != state.savedProxyMode ||
                        state.selectedPackages != state.savedSelectedPackages
                    val isSaving = state.saving
                    IconButton(onClick = { requestSave() }) {
                        Icon(
                            imageVector = if (isSaving) Icons.Filled.Sync else Icons.Filled.Save,
                            contentDescription = null,
                            tint = when {
                                isSaving -> MiuixTheme.colorScheme.primary
                                dirty -> MiuixTheme.colorScheme.primary
                                else -> MiuixTheme.colorScheme.onSurfaceSecondary
                            }
                        )
                    }
                    Box {
                        IconButton(onClick = { state.overflowMenuExpanded = true }) {
                            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
                        }
                        AppsOverflowMenu(
                            show = state.overflowMenuExpanded,
                            onDismissRequest = { state.overflowMenuExpanded = false },
                            onMenuAction = { state.menuAction = it }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        SmartSelectConfirmDialog(
            show = state.showSmartSelectConfirm,
            onDismiss = { state.showSmartSelectConfirm = false },
            onReplace = {
                state.smartSelectApplyMode = SmartSelectApplyMode.Replace
                state.showSmartSelectConfirm = false
                state.smartSelecting = true
            },
            onMerge = {
                state.smartSelectApplyMode = SmartSelectApplyMode.Merge
                state.showSmartSelectConfirm = false
                state.smartSelecting = true
            }
        )

        SmartSelectProgressDialog(show = state.smartSelecting)

        SortFilterBottomSheet(
            show = state.showSortFilter,
            onDismiss = { state.showSortFilter = false },
            draftSortOrder = state.draftSortOrder,
            draftAppType = state.draftAppType,
            draftNetworkFilter = state.draftNetworkFilter,
            draftUserFilter = state.draftUserFilter,
            onSortOrderChange = { state.draftSortOrder = it },
            onAppTypeChange = { state.draftAppType = it },
            onNetworkFilterChange = { state.draftNetworkFilter = it },
            onUserFilterChange = { state.draftUserFilter = it },
            onApply = { state.applyDrafts(filterPrefs) }
        )

        ErrorToast(message = state.saveError, onConsumed = { state.saveError = null })

        // 跳转索引（在 Composable 上下文中 remember）
        // +3 是 LazyColumn 前面有 search/proxy_mode/sort 三个固定 item 的偏移
        // hint item 在搜索栏前面但仅在显示时存在，不影响偏移（hint 可见时内容不会跳转）
        val headerSelIndex = remember(flatItems) {
            val idx = flatItems.indexOfFirst { it.key == "header_sel" }
            if (idx >= 0) idx + 3 else -1
        }
        val headerUnselIndex = remember(flatItems) {
            val idx = flatItems.indexOfFirst { it.key == "header_unsel" }
            if (idx >= 0) idx + 3 else -1
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = contentPaddingWithNavBars(top = innerPadding.calculateTopPadding()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
            // 搜索栏
            item(key = "search", contentType = "search") {
                SearchBar(
                    inputField = {
                        InputField(
                            query = state.query,
                            onQueryChange = { state.query = it },
                            onSearch = { searchExpanded = false },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it }
                        )
                    },
                    expanded = searchExpanded,
                    onExpandedChange = { searchExpanded = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                ) {}
            }

            // 代理模式
            item(key = "proxy_mode", contentType = "tab") {
                val tabs = if (state.isBfr) {
                    listOf(stringResource(R.string.tools_apps_proxy_blacklist), stringResource(R.string.tools_apps_proxy_whitelist))
                } else {
                    listOf(stringResource(R.string.tools_apps_proxy_blacklist), stringResource(R.string.tools_apps_proxy_whitelist), stringResource(R.string.tools_apps_proxy_core))
                }
                TabRowWithContour(
                    tabs = tabs,
                    selectedTabIndex = when (state.proxyMode) {
                        AppsProxyMode.BLACKLIST -> 0; AppsProxyMode.WHITELIST -> 1; AppsProxyMode.CORE -> if (state.isBfr) 0 else 2
                    },
                    onTabSelected = { state.proxyMode = when (it) { 0 -> AppsProxyMode.BLACKLIST; 1 -> AppsProxyMode.WHITELIST; else -> AppsProxyMode.CORE } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // 排序
            item(key = "sort", contentType = "sort") {
                SmallTitle(
                    text = stringResource(when (state.appliedSortOrder) {
                        AppsSortOrder.NAME_ASC -> R.string.tools_apps_sort_name_asc
                        AppsSortOrder.NAME_DESC -> R.string.tools_apps_sort_name_desc
                        AppsSortOrder.INSTALL_TIME_ASC -> R.string.tools_apps_sort_install_asc
                        AppsSortOrder.INSTALL_TIME_DESC -> R.string.tools_apps_sort_install_desc
                    }),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { state.showSortFilter = true }
                    )
                )
            }

            // 加载中
            if (state.loading) {
                item(key = "loading", contentType = "status") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            InfiniteProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.tools_apps_smart_selecting_analyzing), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        }
                    }
                }
            } else if (flatItems.isEmpty()) {
                item(key = "empty", contentType = "status") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (state.debouncedQuery.isNotBlank()) stringResource(R.string.tools_apps_no_results) else stringResource(R.string.tools_apps_no_apps),
                            style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            } else {
                // 统一平坦列表：header + app items，一个 items() 块处理全部
                items(
                    count = flatItems.size,
                    key = { flatItems[it].key },
                    contentType = { flatItems[it].contentType }
                ) { index ->
                    when (val item = flatItems[index]) {
                        is ListItem.Header -> {
                            val title = if (item.isSelectedGroup) {
                                stringResource(R.string.tools_apps_selected) + " · ${item.count}"
                            } else {
                                stringResource(R.string.tools_apps_others, item.count)
                            }
                            // 跳转目标：已选 header → 跳到未选；未选 header → 跳到已选
                            val jumpTarget = if (item.isSelectedGroup) headerUnselIndex else headerSelIndex
                            val jumpLabel = if (item.isSelectedGroup) {
                                stringResource(R.string.tools_apps_others, 0).substringBefore("(").substringBefore("（").trim()
                            } else {
                                stringResource(R.string.tools_apps_selected)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (jumpTarget >= 0) {
                                    Text(
                                        text = "$jumpLabel ↓",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                        modifier = Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                scope.launch { listState.animateScrollToItem(jumpTarget) }
                                            }
                                        )
                                    )
                                }
                            }
                        }
                        is ListItem.App -> {
                            val cardShape = cardShape(item.positionInGroup, item.groupSize)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .clip(cardShape)
                                    .background(MiuixTheme.colorScheme.surfaceContainer)
                            ) {
                                AppRow(
                                    packageName = item.packageName,
                                    appName = item.appName,
                                    subtitle = if (item.userId != 0) {
                                        "${item.packageName} · ${item.userLabel}"
                                    } else {
                                        item.packageName
                                    },
                                    isSelected = item.selected,
                                    onToggle = { state.togglePackage(item.scopedName) },
                                    onLongClick = { openSystemAppDetails(item.packageName) }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }

            // 快速滚动条
            FastScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        } // Box
    }
}

// ─── 平坦列表模型 ───────────────────────────────────────────────────────────

private sealed class ListItem {
    abstract val key: String
    abstract val contentType: String

    data class Header(
        val isSelectedGroup: Boolean,
        val count: Int,
        override val key: String
    ) : ListItem() {
        override val contentType = "header"
    }

    data class App(
        val packageName: String,
        val appName: String,
        val scopedName: String,
        val selected: Boolean,
        val positionInGroup: Int,
        val groupSize: Int,
        val userId: Int,
        val userLabel: String
    ) : ListItem() {
        override val key = (if (selected) "s:" else "u:") + scopedName
        override val contentType = "app"
    }
}

/** 单次遍历构建平坦列表：已选组 + 未选组，含 header 和位置信息 */
private fun buildFlatList(
    filtered: List<AppUtils.InstalledApp>,
    selectedPackages: Set<String>,
    userDisplayNames: Map<Int, String>
): List<ListItem> {
    val selected = ArrayList<AppUtils.InstalledApp>(filtered.size / 4)
    val unselected = ArrayList<AppUtils.InstalledApp>(filtered.size)
    for (app in filtered) {
        if (app.userScopedPackageName in selectedPackages) selected.add(app)
        else unselected.add(app)
    }

    val result = ArrayList<ListItem>(selected.size + unselected.size + 2)

    fun addGroup(apps: List<AppUtils.InstalledApp>, isSelected: Boolean, headerKey: String) {
        if (apps.isEmpty()) return
        result.add(ListItem.Header(isSelectedGroup = isSelected, count = apps.size, key = headerKey))
        for (i in apps.indices) {
            val app = apps[i]
            result.add(ListItem.App(
                packageName = app.packageName,
                appName = app.name,
                scopedName = app.userScopedPackageName,
                selected = isSelected,
                positionInGroup = i,
                groupSize = apps.size,
                userId = app.userId,
                userLabel = userDisplayNames[app.userId] ?: "User ${app.userId}"
            ))
        }
    }

    addGroup(selected, true, "header_sel")
    addGroup(unselected, false, "header_unsel")
    return result
}

private fun cardShape(position: Int, total: Int) = when {
    total == 1 -> shapeFull
    position == 0 -> shapeTop
    position == total - 1 -> shapeBottom
    else -> shapeNone
}

// ─── 应用行 ─────────────────────────────────────────────────────────────────

@Composable
private fun AppRow(
    packageName: String,
    appName: String,
    subtitle: String,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onLongClick: () -> Unit
) {
    BasicComponent(
        title = appName,
        summary = subtitle,
        startAction = {
            AsyncImage(
                model = AppIcon(packageName),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(iconClipShape)
            )
        },
        endActions = {
            Switch(checked = isSelected, onCheckedChange = { onToggle() })
        },
        onClick = onToggle
    )
}

/**
 * 快速滚动条：右侧半透明滑轨 + 可拖拽滑块。
 * 滚动时自动显示，空闲后渐隐。拖拽滑块可快速跳转位置。
 */
@Composable
private fun FastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val totalItems by remember { derivedStateOf { listState.layoutInfo.totalItemsCount } }
    if (totalItems <= 0) return

    var isDragging by remember { mutableStateOf(false) }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    val barAlpha by animateFloatAsState(
        targetValue = if (isScrolling || isDragging) 1f else 0f,
        animationSpec = if (isScrolling || isDragging) tween(80) else tween(400, delayMillis = 800),
        label = "scrollbar_alpha"
    )
    if (barAlpha <= 0.01f) return

    val scope = rememberCoroutineScope()
    val visibleFraction by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.size.toFloat() / info.totalItemsCount.coerceAtLeast(1))
                .coerceIn(0.08f, 1f)
        }
    }

    // 拖拽时用可变状态跟手，非拖拽时跟随列表
    var dragFraction by remember { mutableStateOf(0f) }
    val listFraction by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 1) 0f
            else (listState.firstVisibleItemIndex.toFloat() / (total - 1).toFloat()).coerceIn(0f, 1f)
        }
    }
    val displayFraction = if (isDragging) dragFraction else listFraction

    val thumbColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary
        .copy(alpha = if (isDragging) 0.7f else 0.4f)

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .alpha(barAlpha)
            .pointerInput(totalItems) {
                detectVerticalDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragFraction = listFraction
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                ) { _, dragAmount ->
                    val trackH = size.height.toFloat()
                    dragFraction = (dragFraction + dragAmount / trackH).coerceIn(0f, 1f)
                    val targetIndex = (dragFraction * (totalItems - 1)).toInt().coerceIn(0, totalItems - 1)
                    scope.launch {
                        listState.scrollToItem(targetIndex)
                    }
                }
            }
    ) {
        val trackH = maxHeight
        val thumbH = (trackH * visibleFraction).coerceAtLeast(36.dp)
        val thumbOffsetY = (trackH - thumbH) * displayFraction

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = thumbOffsetY)
                .width(6.dp)
                .height(thumbH)
                .clip(RoundedCornerShape(3.dp))
                .background(thumbColor)
        )
    }
}
