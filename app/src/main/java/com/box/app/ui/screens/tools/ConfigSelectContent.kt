package com.box.app.ui.screens.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.model.ConfigFsItem
import com.box.app.data.repo.ConfigRepository
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.miuix.HyperDialog
import com.box.app.ui.miuix.HyperTextField
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appErrorColor
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

// 选择模式的操作枚举
internal enum class SelectAction { Refresh, Create, Download }

@Composable
internal fun ConfigSelectCoreFolderContent(
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

    ErrorToast(
        message = error,
        onConsumed = { error = null }
    )

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

    // ─── 创建配置文件对话框 ───
    HyperDialog(
        show = showCreate,
        onDismissRequest = { showCreate = false },
        title = stringResource(R.string.tools_config_create_config_file),
        confirmText = stringResource(R.string.tools_config_menu_create),
        onConfirm = {
            val core = coreName ?: return@HyperDialog
            val name = createName.trim()
            if (name.isBlank()) return@HyperDialog
            showCreate = false
            createName = ""
            scope.launch {
                loading = true
                val res = ConfigRepository.createItem(core, name, false)
                loading = false
                if (res.error != null) error = res.error
                reload()
            }
        },
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = { showCreate = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HyperTextField(
                value = createName,
                onValueChange = { createName = it },
                singleLine = true,
                label = stringResource(R.string.tools_config_file_name),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.tools_config_supported_formats),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }

    // ─── 重命名对话框 ───
    renameTarget?.let { target ->
        HyperDialog(
            show = true,
            onDismissRequest = { renameTarget = null },
            title = stringResource(R.string.tools_config_rename),
            confirmText = stringResource(R.string.action_save),
            onConfirm = {
                val newName = renameName.trim()
                if (newName.isBlank()) return@HyperDialog
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
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { renameTarget = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.tools_config_current_name, target.name),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                HyperTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    singleLine = true,
                    label = stringResource(R.string.tools_config_new_name),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // ─── 删除确认对话框 ───
    deleteTarget?.let { target ->
        val deleteTargetType = if (target is ConfigFsItem.Folder) "folder" else "file"
        HyperDialog(
            show = true,
            onDismissRequest = { deleteTarget = null },
            title = stringResource(R.string.tools_config_delete_title, deleteTargetType),
            icon = Icons.Filled.Warning,
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
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
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { deleteTarget = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.tools_config_delete_confirm, target.name),
                    style = MiuixTheme.textStyles.body2
                )
                Text(
                    text = if (target is ConfigFsItem.Folder) stringResource(R.string.tools_config_delete_folder_warning) else stringResource(R.string.tools_config_delete_file_warning),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error
                )
            }
        }
    }

    // ─── URL 下载对话框 ───
    HyperDialog(
        show = showPull,
        onDismissRequest = { showPull = false },
        title = stringResource(R.string.tools_config_download_config_from_url),
        confirmText = stringResource(R.string.tools_config_menu_download),
        onConfirm = {
            val core = coreName ?: return@HyperDialog
            val url = pullUrl.trim()
            val name = pullFileName.trim()
            if (url.isBlank() || name.isBlank()) return@HyperDialog
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
        },
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = { showPull = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HyperTextField(
                value = pullUrl,
                onValueChange = { pullUrl = it },
                singleLine = true,
                label = stringResource(R.string.tools_config_url),
                modifier = Modifier.fillMaxWidth()
            )
            HyperTextField(
                value = pullFileName,
                onValueChange = { pullFileName = it },
                singleLine = true,
                label = stringResource(R.string.tools_config_save_as),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.tools_config_download_config_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }

    // ─── 内容区域 ───
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallTitle(
            text = stringResource(R.string.tools_config_configuration_files) + (coreName?.let { " ($it)" } ?: ""),
            modifier = Modifier.padding(top = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 18.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 状态信息栏
                Text(
                    text = when {
                        loading -> stringResource(R.string.tools_config_loading_files)
                        items.isEmpty() -> stringResource(R.string.tools_config_no_config_files_found)
                        else -> stringResource(
                            R.string.tools_config_files_active_summary,
                            items.size,
                            active ?: stringResource(R.string.tools_config_active_none)
                        )
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 2.dp)
                )

                if (loading) {
                    ConfigLoadingState(
                        message = stringResource(R.string.tools_config_loading_configuration_files)
                    )
                } else if (items.isEmpty()) {
                    val core = coreName
                    val cur = currentPath
                    val canGoUp = !core.isNullOrBlank() && !cur.isNullOrBlank() && cur != core
                    if (canGoUp) {
                        val parentPath = cur.substringBeforeLast('/', core)
                        ConfigParentFolderRow(
                            subtitle = stringResource(R.string.tools_config_parent_directory),
                            onClick = {
                                openRowKey = null
                                currentPath = parentPath
                                reload()
                            }
                        )
                        ConfigDivider()
                    }

                    ConfigEmptyState(
                        message = stringResource(R.string.tools_config_no_configuration_files_found),
                        hint = stringResource(R.string.tools_config_create_new_config_hint)
                    )
                } else {
                    val core = coreName
                    val cur = currentPath
                    val canGoUp = !core.isNullOrBlank() && !cur.isNullOrBlank() && cur != core

                    if (canGoUp) {
                        val parentPath = cur.substringBeforeLast('/', core)
                        ConfigParentFolderRow(
                            subtitle = stringResource(R.string.tools_config_parent_directory),
                            onClick = {
                                currentPath = parentPath
                                reload()
                            }
                        )
                        ConfigDivider()
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
                                    ConfigFolderRow(
                                        name = item.name,
                                        subtitle = stringResource(R.string.tools_config_folder),
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
                                        ConfigSelectableFileRow(
                                            fileName = item.name,
                                            subtitle = if (isActive) stringResource(R.string.tools_config_active_configuration) else stringResource(R.string.tools_config_tap_to_select),
                                            isActive = isActive,
                                            onClick = {
                                                val coreVal = coreName
                                                if (coreVal.isNullOrBlank()) return@ConfigSelectableFileRow

                                                openRowKey = null
                                                scope.launch {
                                                    val res = ConfigRepository.setActiveConfigFile(coreVal, item.name)
                                                    if (res.error != null) {
                                                        error = res.error
                                                        return@launch
                                                    }
                                                    active = item.name
                                                    onSelectFile(item.name)
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        }

                        if (!isLast) {
                            ConfigDivider()
                        }
                    }
                }
            }
        }
    }
}
