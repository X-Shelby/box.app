package com.box.app.ui.screens.tools

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import com.box.app.R
import com.box.app.data.backend.ShellExecutor
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.contentPaddingWithNavBars
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.box.app.ui.miuix.HyperDialog
import com.box.app.ui.miuix.HyperFilterChip

import com.box.app.utils.MapleFontManager
import com.box.app.utils.ThemeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class LogLevel {
    Error,
    Warn,
    Info,
    Debug,
    Other
}

private data class LogModuleEntry(
    val index: Int,
    val rawLine: String,
    val level: LogLevel,
    val tag: String,
    val timestamp: String?
)

private data class LogLevelPalette(
    val badgeContainer: Color,
    val badgeContent: Color
)

@Composable
fun ToolsLogsScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scrollBehavior = MiuixScrollBehavior()
    val prefs = remember { context.getSharedPreferences("tools_logs", Context.MODE_PRIVATE) }

    val runDir = "/data/adb/box/run"
    val statusAvailableText = stringResource(R.string.tools_logs_status_available)
    val failedLoadText = stringResource(R.string.tools_logs_failed_load)
    val failedReadText = stringResource(R.string.tools_logs_failed_read)
    val failedRefreshText = stringResource(R.string.tools_logs_failed_refresh)
    val failedDeleteText = stringResource(R.string.tools_logs_failed_delete)
    val currentStatusText = stringResource(R.string.tools_logs_status_current)

    var autoRefresh by rememberSaveable { mutableStateOf(false) }
    var currentLogFile by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var prefsLoaded by remember { mutableStateOf(false) }
    var logFiles by rememberSaveable { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var logContent by rememberSaveable { mutableStateOf("") }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var levelFilter by rememberSaveable { mutableStateOf<LogLevel?>(null) }

    val mapleFontEnabled by ThemeManager.mapleFontLogs.collectAsState()
    val mapleFamily = MapleFontManager.getFontFamily()
    val logFontFamily = if (mapleFontEnabled && mapleFamily != null) {
        mapleFamily
    } else {
        FontFamily.Monospace
    }

    val nonBlankLineCount = remember(logContent) {
        logContent.lineSequence().count { it.isNotBlank() }
    }
    val moduleEntriesRaw = remember(logContent, currentLogFile) {
        parseModuleEntries(logContent, currentLogFile)
    }
    val moduleEntries = remember(moduleEntriesRaw, newestFirst, levelFilter) {
        var list = moduleEntriesRaw
        if (levelFilter != null) list = list.filter { it.level == levelFilter }
        if (newestFirst) list.asReversed() else list
    }
    // 详细日志视图的过滤内容（排序 + 级别过滤）
    val filteredLogContent = remember(logContent, levelFilter, newestFirst) {
        var lines = logContent.lines().filter { it.isNotBlank() }
        if (levelFilter != null) lines = lines.filter { detectLogLevel(it) == levelFilter }
        if (newestFirst) lines = lines.reversed()
        lines.joinToString("\n")
    }
    val dropdownItems = logFiles.map { (fileName, _) -> fileName }
    val selectedLogIndex = logFiles.indexOfFirst { it.first == currentLogFile }.let { index ->
        if (index >= 0) index else 0
    }

    ErrorToast(
        message = error,
        onConsumed = { error = null }
    )

    suspend fun queryLogFiles(): List<String> {
        val cmd = "for f in $runDir/*; do [ -f \"\$f\" ] || continue; b=\$(basename \"\$f\"); case \"\$b\" in *.log|*.LOG) echo \"\$b\";; esac; done"
        val result = ShellExecutor.execute(cmd)
        if (result.exitCode != 0 && result.stdout.isBlank()) {
            return emptyList()
        }
        return result.stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    suspend fun queryLogContent(fileName: String): String {
        if (fileName.isBlank()) return ""
        val safeName = fileName.replace("\"", "").replace("'", "")
        val path = "$runDir/$safeName"
        val cmd = "tail -n 600 '$path' 2>/dev/null || true"
        return ShellExecutor.execute(cmd).stdout
    }

    suspend fun deleteLogFile(fileName: String) {
        if (fileName.isBlank()) return
        val safeName = fileName.replace("\"", "").replace("'", "")
        val path = "$runDir/$safeName"
        ShellExecutor.execute("rm -f '$path' 2>/dev/null || true")
    }

    suspend fun refreshLogs(
        preferredFile: String? = currentLogFile,
        failureText: String,
        showLoading: Boolean
    ) {
        if (showLoading) loading = true
        error = null
        try {
            val names = queryLogFiles()
            logFiles = names.map { it to statusAvailableText }
            val chosen = when {
                !preferredFile.isNullOrBlank() && preferredFile in names -> preferredFile
                names.isNotEmpty() -> names.first()
                else -> ""
            }
            currentLogFile = chosen
            logContent = if (chosen.isBlank()) "" else queryLogContent(chosen)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: failureText
        } finally {
            if (showLoading) loading = false
        }
    }

    suspend fun reloadCurrentLog(
        failureText: String,
        showLoading: Boolean,
        surfaceErrors: Boolean
    ) {
        if (currentLogFile.isBlank()) {
            logContent = ""
            return
        }
        if (showLoading) loading = true
        if (surfaceErrors) error = null
        try {
            logContent = queryLogContent(currentLogFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (surfaceErrors) {
                error = e.message ?: failureText
            }
        } finally {
            if (showLoading) loading = false
        }
    }

    fun requestRefresh() {
        scope.launch {
            refreshLogs(
                preferredFile = currentLogFile,
                failureText = failedRefreshText,
                showLoading = true
            )
        }
    }

    fun requestSelectLog(fileName: String) {
        scope.launch {
            currentLogFile = fileName
            reloadCurrentLog(
                failureText = failedReadText,
                showLoading = true,
                surfaceErrors = true
            )
        }
    }

    fun requestDeleteCurrentLog() {
        if (currentLogFile.isBlank()) return
        scope.launch {
            loading = true
            error = null
            try {
                deleteLogFile(currentLogFile)
                val names = queryLogFiles()
                logFiles = names.map { it to statusAvailableText }
                val nextFile = names.firstOrNull().orEmpty()
                currentLogFile = nextFile
                logContent = if (nextFile.isBlank()) "" else queryLogContent(nextFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: failedDeleteText
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        autoRefresh = prefs.getBoolean("logs_auto_refresh", false)
        val lastFile = prefs.getString("logs_last_file", null)
        prefsLoaded = true
        refreshLogs(
            preferredFile = lastFile,
            failureText = failedLoadText,
            showLoading = true
        )
    }

    LaunchedEffect(autoRefresh, prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        prefs.edit().putBoolean("logs_auto_refresh", autoRefresh).apply()
    }

    LaunchedEffect(currentLogFile, prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        if (currentLogFile.isNotBlank()) {
            prefs.edit().putString("logs_last_file", currentLogFile).apply()
        }
    }

    LaunchedEffect(autoRefresh, currentLogFile) {
        if (!autoRefresh || currentLogFile.isBlank()) return@LaunchedEffect
        while (isActive) {
            delay(1500)
            reloadCurrentLog(
                failureText = failedReadText,
                showLoading = false,
                surfaceErrors = false
            )
        }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.tools_logs_title),
                largeTitle = stringResource(R.string.tools_logs_title),
                subtitle = currentLogFile.ifBlank { stringResource(R.string.tools_logs_no_files) },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = ::requestRefresh,
                        enabled = !loading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = if (loading) {
                                MiuixTheme.colorScheme.onSurfaceSecondary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = currentLogFile.isNotBlank() && !loading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = if (currentLogFile.isBlank() || loading) {
                                MiuixTheme.colorScheme.onSurfaceSecondary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = contentPaddingWithNavBars(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding(),
                extraBottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "logs_source_title") {
                SmallTitle(text = stringResource(R.string.tools_logs_section_source))
            }

            item(key = "logs_summary_card") {
                LogsSummaryCard(
                    runDir = runDir,
                    currentLogFile = currentLogFile,
                    autoRefresh = autoRefresh,
                    lineCount = nonBlankLineCount,
                    logFiles = logFiles,
                    dropdownItems = dropdownItems,
                    selectedLogIndex = selectedLogIndex,
                    loading = loading,
                    onLogSelected = { index ->
                        val next = logFiles.getOrNull(index)?.first ?: return@LogsSummaryCard
                        requestSelectLog(next)
                    },
                    onAutoRefreshChange = { enabled -> autoRefresh = enabled }
                )
            }

            item(key = "logs_view_title") {
                SmallTitle(text = stringResource(R.string.tools_logs_section_view))
            }

            item(key = "logs_view_tabs") {
                TabRowWithContour(
                    tabs = listOf(
                        stringResource(R.string.tools_logs_tab_module),
                        stringResource(R.string.tools_logs_tab_detail)
                    ),
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 排序 + 级别过滤控制栏（两个视图共享）
            item(key = "logs_controls") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    // 排序切换
                    Row(
                        modifier = Modifier
                            .clip(SmoothRoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant)
                            .clickable { newestFirst = !newestFirst }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapVert,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (newestFirst) "最新" else "最早",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }

                    // 级别过滤 chips（HyperOS3 风格，水平滚动）
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HyperFilterChip(
                            selected = levelFilter == null,
                            onClick = { levelFilter = null },
                            label = "全部"
                        )
                        listOf(LogLevel.Error, LogLevel.Warn, LogLevel.Info, LogLevel.Debug).forEach { level ->
                            HyperFilterChip(
                                selected = levelFilter == level,
                                onClick = { levelFilter = if (levelFilter == level) null else level },
                                label = level.displayName()
                            )
                        }
                    }
                }
            }

            if (loading) {
                item(key = "logs_loading") { LogsLoadingCard() }
            } else if (selectedTabIndex == 0) {
                if (moduleEntries.isEmpty()) {
                    item(key = "logs_module_empty") {
                        LogsEmptyCard(text = stringResource(R.string.tools_logs_no_content))
                    }
                } else {
                    items(
                        items = moduleEntries,
                        key = { entry -> entry.index }
                    ) { entry ->
                        ModuleLogCard(entry = entry, fontFamily = logFontFamily)
                    }
                }
            } else {
                item(key = "logs_detail") {
                    DetailedLogsCard(
                        logContent = filteredLogContent,
                        logFontFamily = logFontFamily
                    )
                }
            }
        }
    }

    HyperDialog(
        show = showDeleteConfirm,
        onDismissRequest = { showDeleteConfirm = false },
        title = stringResource(R.string.tools_logs_delete_dialog_title),
        summary = if (currentLogFile.isBlank()) {
            stringResource(R.string.tools_logs_no_files)
        } else {
            stringResource(R.string.tools_logs_delete_dialog_body, currentLogFile, runDir)
        },
        confirmText = stringResource(R.string.action_delete),
        onConfirm = {
            showDeleteConfirm = false
            requestDeleteCurrentLog()
        },
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = { showDeleteConfirm = false },
        icon = Icons.Filled.DeleteOutline
    )
}

@Composable
private fun LogsSummaryCard(
    runDir: String,
    currentLogFile: String,
    autoRefresh: Boolean,
    lineCount: Int,
    logFiles: List<Pair<String, String>>,
    dropdownItems: List<String>,
    selectedLogIndex: Int,
    loading: Boolean,
    onLogSelected: (Int) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit
) {
    val canToggleAutoRefresh = currentLogFile.isNotBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 日志文件选择
            if (dropdownItems.isNotEmpty()) {
                WindowDropdownPreference(
                    title = stringResource(R.string.tools_logs_log_file),
                    summary = stringResource(
                        R.string.tools_logs_current_prefix,
                        currentLogFile,
                        stringResource(R.string.tools_logs_recent_lines, lineCount)
                    ),
                    items = dropdownItems,
                    selectedIndex = selectedLogIndex,
                    enabled = !loading,
                    onSelectedIndexChange = onLogSelected
                )
            } else {
                BasicComponent(
                    title = stringResource(R.string.tools_logs_log_file),
                    summary = stringResource(R.string.tools_logs_no_files)
                )
            }

            LogsPreferenceDivider()

            // 自动刷新开关
            top.yukonga.miuix.kmp.preference.SwitchPreference(
                title = stringResource(R.string.tools_logs_auto_refresh),
                summary = if (autoRefresh) {
                    stringResource(R.string.tools_logs_auto_refresh_on)
                } else {
                    stringResource(R.string.tools_logs_manual_refresh)
                },
                checked = autoRefresh,
                onCheckedChange = { onAutoRefreshChange(it) },
                enabled = canToggleAutoRefresh
            )
        }
    }
}

@Composable
private fun ModuleLogCard(
    entry: LogModuleEntry,
    fontFamily: FontFamily = FontFamily.Monospace
) {
    val palette = rememberLogLevelPalette(entry.level)
    val scheme = MiuixTheme.colorScheme
    val isDark = ThemeManager.shouldUseDarkTheme()

    // 日志消息根据级别着色（Error 红色、Warn 橙色、其他默认色）
    val messageColor = when (entry.level) {
        LogLevel.Error -> if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
        LogLevel.Warn -> if (isDark) Color(0xFFFFC46B) else Color(0xFFEF6C00)
        else -> scheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = scheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── 头部行：[级别] 标签 ··· 时间戳 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 级别字母徽标（紧凑方块）
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(SmoothRoundedCornerShape(6.dp))
                        .background(palette.badgeContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.level.badgeLetter(),
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Bold,
                        color = palette.badgeContent
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 标签名
                Text(
                    text = entry.tag,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 时间戳 + 行号
                Text(
                    text = buildList {
                        entry.timestamp?.let(::add)
                        add("#${entry.index}")
                    }.joinToString("  "),
                    style = MiuixTheme.textStyles.footnote2,
                    color = scheme.onSurfaceSecondary,
                    maxLines = 1
                )
            }

            // ── 日志正文（去除已展示的时间戳/级别，等宽/Maple 字体，级别着色） ──
            val cleanMessage = remember(entry.rawLine) {
                cleanLogMessage(entry.rawLine)
            }
            Text(
                text = cleanMessage.ifBlank { entry.rawLine },
                style = MiuixTheme.textStyles.footnote1.copy(
                    fontFamily = fontFamily,
                    lineHeight = 18.sp
                ),
                color = messageColor,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailedLogsCard(
    logContent: String,
    logFontFamily: FontFamily
) {
    val highlightedLog = rememberHighlightedLog(logContent)

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.tools_logs_detail_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.background)
                    .padding(14.dp)
            ) {
                if (logContent.isBlank()) {
                    Text(
                        text = stringResource(R.string.tools_logs_no_content),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                } else {
                    SelectionContainer {
                        BasicText(
                            text = highlightedLog,
                            modifier = Modifier.fillMaxWidth(),
                            style = MiuixTheme.textStyles.footnote1.copy(
                                fontFamily = logFontFamily,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfiniteProgressIndicator(modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.tools_logs_loading),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun LogsEmptyCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun LogsStatusBadge(
    text: String,
    container: Color,
    content: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = content,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LogsPreferenceDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.08f))
    )
}

@Composable
private fun rememberHighlightedLog(logContent: String): androidx.compose.ui.text.AnnotatedString {
    val isDark = ThemeManager.shouldUseDarkTheme()
    val mapleFontActive by ThemeManager.mapleFontLogs.collectAsState()
    val mapleLoaded = MapleFontManager.getFontFamily() != null
    return remember(logContent, isDark, mapleFontActive, mapleLoaded) {
        val errorColor = if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
        val warnColor = if (isDark) Color(0xFFFFC46B) else Color(0xFFEF6C00)
        val infoColor = if (isDark) Color(0xFF7FD6A3) else Color(0xFF2E7D32)
        val debugColor = if (isDark) Color(0xFF8DB9FF) else Color(0xFF1565C0)
        val defaultColor = if (isDark) Color(0xFFC5CDD8) else Color(0xFF52606D)

        // Nerd Font 可用时使用图形图标，否则使用 ASCII 级别标记
        val hasNerdFont = ThemeManager.mapleFontLogs.value && MapleFontManager.getFontFamily() != null
        val iconError = if (hasNerdFont) "\uF057" else "E"  // nf-fa-times_circle
        val iconWarn  = if (hasNerdFont) "\uF071" else "W"  // nf-fa-exclamation_triangle
        val iconInfo  = if (hasNerdFont) "\uF05A" else "I"  // nf-fa-info_circle
        val iconDebug = if (hasNerdFont) "\uF188" else "D"  // nf-fa-bug
        val iconOther = if (hasNerdFont) "\uF15C" else "·"  // nf-fa-file_text

        buildAnnotatedString {
            val lines = logContent.lines()
            lines.forEachIndexed { index, line ->
                val level = detectLogLevel(line)
                val (icon, lineColor) = when (level) {
                    LogLevel.Error -> iconError to errorColor
                    LogLevel.Warn  -> iconWarn to warnColor
                    LogLevel.Info  -> iconInfo to infoColor
                    LogLevel.Debug -> iconDebug to debugColor
                    LogLevel.Other -> iconOther to defaultColor
                }

                withStyle(SpanStyle(color = lineColor)) {
                    append(icon)
                }
                append(" ")

                // 清洗后的日志消息
                val cleaned = cleanLogMessage(line)
                withStyle(SpanStyle(color = lineColor)) {
                    append(cleaned.ifBlank { line })
                }
                if (index != lines.lastIndex) append('\n')
            }
        }
    }
}

@Composable
private fun rememberLogLevelPalette(level: LogLevel): LogLevelPalette {
    val isDark = ThemeManager.shouldUseDarkTheme()
    return remember(level, isDark) {
        when (level) {
            LogLevel.Error -> if (isDark) {
                LogLevelPalette(
                    badgeContainer = Color(0xFF5A2424),
                    badgeContent = Color(0xFFFFDDD8)
                )
            } else {
                LogLevelPalette(
                    badgeContainer = Color(0xFFFFE4E1),
                    badgeContent = Color(0xFFAE2D2D)
                )
            }

            LogLevel.Warn -> if (isDark) {
                LogLevelPalette(
                    badgeContainer = Color(0xFF5C4120),
                    badgeContent = Color(0xFFFFE0B4)
                )
            } else {
                LogLevelPalette(
                    badgeContainer = Color(0xFFFFEED3),
                    badgeContent = Color(0xFFAA640D)
                )
            }

            LogLevel.Info -> if (isDark) {
                LogLevelPalette(
                    badgeContainer = Color(0xFF214B33),
                    badgeContent = Color(0xFFD7F8E3)
                )
            } else {
                LogLevelPalette(
                    badgeContainer = Color(0xFFE0F3E2),
                    badgeContent = Color(0xFF246A2A)
                )
            }

            LogLevel.Debug -> if (isDark) {
                LogLevelPalette(
                    badgeContainer = Color(0xFF223D61),
                    badgeContent = Color(0xFFDCE8FF)
                )
            } else {
                LogLevelPalette(
                    badgeContainer = Color(0xFFE2ECFF),
                    badgeContent = Color(0xFF1453A4)
                )
            }

            LogLevel.Other -> if (isDark) {
                LogLevelPalette(
                    badgeContainer = Color(0xFF313A45),
                    badgeContent = Color(0xFFE2E8F0)
                )
            } else {
                LogLevelPalette(
                    badgeContainer = Color(0xFFE8ECF2),
                    badgeContent = Color(0xFF4F5C6C)
                )
            }
        }
    }
}

private fun parseModuleEntries(
    logContent: String,
    fallbackTag: String
): List<LogModuleEntry> {
    return logContent.lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .mapIndexed { index, line ->
            LogModuleEntry(
                index = index + 1,
                rawLine = line,
                level = detectLogLevel(line),
                tag = inferLogTag(line, fallbackTag),
                timestamp = extractTimestamp(line)
            )
        }
        .toList()
}

private fun detectLogLevel(line: String): LogLevel {
    val lower = line.lowercase()
    return when {
        "error" in lower || "fatal" in lower || "exception" in lower -> LogLevel.Error
        "warn" in lower || "warning" in lower -> LogLevel.Warn
        "info" in lower -> LogLevel.Info
        "debug" in lower || "trace" in lower -> LogLevel.Debug
        else -> LogLevel.Other
    }
}

private fun inferLogTag(line: String, fallbackTag: String): String {
    val bracketTag = Regex("""\[([^\]]+)]""")
        .find(line)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.length in 2..36 }
    if (!bracketTag.isNullOrBlank()) return bracketTag

    val colonTag = Regex("""\b([A-Za-z][A-Za-z0-9_.-]{1,24})\b(?=:)""")
        .find(line)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!colonTag.isNullOrBlank()) return colonTag

    return fallbackTag.substringBeforeLast('.').ifBlank { fallbackTag.ifBlank { "Log" } }
}

private fun extractTimestamp(line: String): String? {
    // 匹配完整日期时间（含可选毫秒）或纯时间
    val regex = Regex("""\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:[.,]\d+)?|\d{2}:\d{2}:\d{2}(?:[.,]\d+)?""")
    val raw = regex.find(line)?.value ?: return null
    // 截断毫秒，保留到秒：2026-04-08 11:42:29
    return raw.replace(Regex("""[.,]\d+$"""), "")
}

/**
 * 清洗日志正文：去除卡片头部已展示的时间戳和级别前缀，保留纯消息内容。
 * 例: "2026-04-08 10:30:43 [Warning]: 正在清理 TUN 规则。" → "正在清理 TUN 规则。"
 */
private fun cleanLogMessage(rawLine: String): String {
    var s = rawLine
    // 0. 行首时区偏移：+0800 / -05:00 / +08:00（mihomo 等核心输出格式）
    s = s.replace(Regex("""^[+-]\d{2}:?\d{2}\s+"""), "")
    // 1. time="2026-04-08T10:30:43.123+08:00" 结构化时间戳
    s = s.replace(Regex("""^time="[^"]*"\s*"""), "")
    // 2. 行首日期时间：2026-04-08 11:05:40 / 2026-04-08T11:05:40.123456+08:00
    s = s.replace(Regex("""^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:[.,]\d+)?(?:[+-]\d{2}:?\d{2}|Z)?\s*"""), "")
    // 3. 级别标签（带括号）：[Warning]: / [Info] / [INF] 等
    s = s.replace(Regex("""^\[(?:info|warn(?:ing)?|error|fatal|debug|trace|inf|wrn|err|dbg|trc)]:?\s*""", RegexOption.IGNORE_CASE), "")
    // 4. 裸级别词（无括号）：ERROR / WARN / INFO 等（仅匹配全大写或首字母大写，后跟空格）
    s = s.replace(Regex("""^(?:INFO|WARN(?:ING)?|ERROR|FATAL|DEBUG|TRACE|INF|WRN|ERR|DBG|TRC|Info|Warn(?:ing)?|Error|Fatal|Debug|Trace)\s+"""), "")
    // 5. level=warn 结构化级别
    s = s.replace(Regex("""^level=\w+\s*""", RegexOption.IGNORE_CASE), "")
    // 6. msg="..." 包裹
    s = s.replace(Regex("""^msg="(.*)"$"""), "$1")
    return s.trimStart()
}

private fun LogLevel.displayName(): String = when (this) {
    LogLevel.Error -> "ERROR"
    LogLevel.Warn -> "WARN"
    LogLevel.Info -> "INFO"
    LogLevel.Debug -> "DEBUG"
    LogLevel.Other -> "LOG"
}

private fun LogLevel.badgeLetter(): String = when (this) {
    LogLevel.Error -> "E"
    LogLevel.Warn -> "W"
    LogLevel.Info -> "I"
    LogLevel.Debug -> "D"
    LogLevel.Other -> "L"
}

