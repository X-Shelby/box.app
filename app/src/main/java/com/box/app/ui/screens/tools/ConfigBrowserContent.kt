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

// 管理模式的操作枚举
internal enum class ManageAction { Refresh, NewFile, NewFolder, Search }

@Composable
internal fun ConfigBrowserContent(
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

    // ─── 创建文件/文件夹对话框 ───
    HyperDialog(
        show = showCreate,
        onDismissRequest = { showCreate = false },
        title = if (createIsFolder) stringResource(R.string.tools_config_create_folder) else stringResource(R.string.tools_config_create_file),
        confirmText = stringResource(R.string.tools_config_menu_create),
        onConfirm = {
            val name = createName.trim()
            if (name.isBlank()) return@HyperDialog
            showCreate = false
            createName = ""
            scope.launch {
                loading = true
                val res = ConfigRepository.createItem(currentPath, name, createIsFolder)
                loading = false
                if (res.error != null) error = res.error
                load()
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
                label = stringResource(R.string.tools_config_name),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = if (createIsFolder) stringResource(R.string.tools_config_create_folder_helper) else stringResource(R.string.tools_config_create_file_helper),
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
                    load()
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
                deleteTarget = null
                scope.launch {
                    loading = true
                    val res = ConfigRepository.deleteItem(target)
                    loading = false
                    if (res.error != null) error = res.error
                    load()
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

    // ─── 内容区域 ───
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallTitle(
            text = stringResource(R.string.tools_config_files_folders_title),
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
                        loading -> stringResource(R.string.tools_config_loading)
                        items.isEmpty() && query.isNotBlank() -> stringResource(R.string.tools_config_no_results_found)
                        items.isEmpty() -> stringResource(R.string.tools_config_empty_directory)
                        else -> stringResource(R.string.tools_config_items_count, items.size)
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 2.dp)
                )

                val canGoUp = query.isBlank() && currentPath.isNotBlank()

                if (loading) {
                    ConfigLoadingState(
                        message = stringResource(R.string.tools_config_loading_files)
                    )
                } else if (items.isEmpty()) {
                    if (canGoUp) {
                        val parentPath = currentPath.substringBeforeLast('/', "")
                        ConfigParentFolderRow(
                            subtitle = stringResource(R.string.tools_config_parent_directory),
                            onClick = { onCurrentPathChange(parentPath) }
                        )
                        ConfigDivider()
                    }

                    ConfigEmptyState(
                        message = if (query.isNotBlank()) stringResource(R.string.tools_config_no_files_match)
                        else stringResource(R.string.tools_config_directory_empty)
                    )
                } else {
                    if (canGoUp) {
                        val parentPath = currentPath.substringBeforeLast('/', "")
                        ConfigParentFolderRow(
                            subtitle = stringResource(R.string.tools_config_parent_directory),
                            onClick = {
                                openRowKey = null
                                onCurrentPathChange(parentPath)
                            }
                        )
                        ConfigDivider()
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
                                    ConfigFolderRow(
                                        name = item.name,
                                        subtitle = stringResource(R.string.tools_config_folder),
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
                                    ConfigFileItemRow(
                                        fileName = item.name,
                                        subtitle = item.description.ifBlank { stringResource(R.string.tools_config_file) },
                                        onClick = {
                                            openRowKey = null
                                            onOpenEditor(item.path)
                                        }
                                    )
                                }
                            )
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
