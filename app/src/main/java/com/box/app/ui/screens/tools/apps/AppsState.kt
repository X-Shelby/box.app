package com.box.app.ui.screens.tools.apps

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.box.app.BuildConfig
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.ShellExecutor
import com.box.app.data.repo.ConfigRepository
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import com.topjohnwu.superuser.io.SuFileOutputStream
import com.box.app.utils.AppClassifier
import com.box.app.utils.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// ─── 枚举定义 ───────────────────────────────────────────────────────────────

enum class AppsProxyMode { BLACKLIST, WHITELIST, CORE }

enum class AppsSortOrder { NAME_ASC, NAME_DESC, INSTALL_TIME_ASC, INSTALL_TIME_DESC }

enum class AppsAppType { ALL, USER, SYSTEM }

enum class AppsNetworkFilter { ALL, ONLY_NETWORK, EXCLUDE_NETWORK }

enum class AppsUserFilter { ALL, MAIN_ONLY, WORK_ONLY, OTHER_ONLY }

enum class SmartSelectApplyMode { Replace, Merge }

enum class AppsManageAction { Refresh, SmartSelect, SelectAll, Invert }

// ─── 状态持有者 ──────────────────────────────────────────────────────────────

@Stable
class AppsScreenState(
    // 代理模式
    proxyMode: AppsProxyMode = AppsProxyMode.BLACKLIST,
    selectedPackages: Set<String> = emptySet(),
    // 保存的初始值（用于脏检查）
    savedProxyMode: AppsProxyMode = AppsProxyMode.BLACKLIST,
    savedSelectedPackages: Set<String> = emptySet(),
    // 搜索
    searchVisible: Boolean = false,
    query: String = "",
    debouncedQuery: String = "",
    // 过滤
    appliedSortOrder: AppsSortOrder = AppsSortOrder.NAME_ASC,
    appliedAppType: AppsAppType = AppsAppType.ALL,
    appliedNetworkFilter: AppsNetworkFilter = AppsNetworkFilter.ALL,
    appliedUserFilter: AppsUserFilter = AppsUserFilter.ALL,
    // 过滤草稿（底部工作表中编辑）
    draftSortOrder: AppsSortOrder = AppsSortOrder.NAME_ASC,
    draftAppType: AppsAppType = AppsAppType.ALL,
    draftNetworkFilter: AppsNetworkFilter = AppsNetworkFilter.ALL,
    draftUserFilter: AppsUserFilter = AppsUserFilter.ALL,
    // UI 状态
    showSortFilter: Boolean = false,
    showSmartSelectConfirm: Boolean = false,
    smartSelecting: Boolean = false,
    smartSelectApplyMode: SmartSelectApplyMode = SmartSelectApplyMode.Replace,
    saving: Boolean = false,
    saveError: String? = null,
    loading: Boolean = true,
    filtersLoaded: Boolean = false,
    menuAction: AppsManageAction? = null,
    overflowMenuExpanded: Boolean = false
) {
    var proxyMode by mutableStateOf(proxyMode)
    var selectedPackages by mutableStateOf(selectedPackages)
    var savedProxyMode by mutableStateOf(savedProxyMode)
    var savedSelectedPackages by mutableStateOf(savedSelectedPackages)

    var searchVisible by mutableStateOf(searchVisible)
    var query by mutableStateOf(query)
    var debouncedQuery by mutableStateOf(debouncedQuery)

    var appliedSortOrder by mutableStateOf(appliedSortOrder)
    var appliedAppType by mutableStateOf(appliedAppType)
    var appliedNetworkFilter by mutableStateOf(appliedNetworkFilter)
    var appliedUserFilter by mutableStateOf(appliedUserFilter)

    var draftSortOrder by mutableStateOf(draftSortOrder)
    var draftAppType by mutableStateOf(draftAppType)
    var draftNetworkFilter by mutableStateOf(draftNetworkFilter)
    var draftUserFilter by mutableStateOf(draftUserFilter)

    var showSortFilter by mutableStateOf(showSortFilter)
    var showSmartSelectConfirm by mutableStateOf(showSmartSelectConfirm)
    var smartSelecting by mutableStateOf(smartSelecting)
    var smartSelectApplyMode by mutableStateOf(smartSelectApplyMode)
    var saving by mutableStateOf(saving)
    var saveError by mutableStateOf(saveError)
    var loading by mutableStateOf(loading)
    var filtersLoaded by mutableStateOf(filtersLoaded)
    var menuAction by mutableStateOf(menuAction)
    var overflowMenuExpanded by mutableStateOf(overflowMenuExpanded)

    // 应用列表数据
    var apps by mutableStateOf<List<AppUtils.InstalledApp>>(emptyList())
    var userDisplayNames by mutableStateOf<Map<Int, String>>(emptyMap())

    val isBfr = BuildConfig.FLAVOR == "bfr"
    private val saveMutex = Mutex()

    val isCoreMode: Boolean get() = proxyMode == AppsProxyMode.CORE

    val hasUnsavedChanges: Boolean
        get() = proxyMode != savedProxyMode || selectedPackages != savedSelectedPackages

    // ─── 过滤器持久化 ──────────────────────────────────────────────────

    fun loadSavedFilters(prefs: SharedPreferences) {
        val so = prefs.getInt("sort_order", -1)
        val at = prefs.getInt("app_type", -1)
        val nf = prefs.getInt("network_filter", -1)
        val uf = prefs.getInt("user_filter", -1)

        appliedSortOrder = AppsSortOrder.entries.getOrNull(so) ?: appliedSortOrder
        appliedAppType = AppsAppType.entries.getOrNull(at) ?: appliedAppType
        appliedNetworkFilter = AppsNetworkFilter.entries.getOrNull(nf) ?: appliedNetworkFilter
        appliedUserFilter = AppsUserFilter.entries.getOrNull(uf) ?: appliedUserFilter

        filtersLoaded = true
    }

    fun saveFilters(prefs: SharedPreferences) {
        prefs.edit()
            .putInt("sort_order", appliedSortOrder.ordinal)
            .putInt("app_type", appliedAppType.ordinal)
            .putInt("network_filter", appliedNetworkFilter.ordinal)
            .putInt("user_filter", appliedUserFilter.ordinal)
            .apply()
    }

    fun syncDraftsFromApplied() {
        draftSortOrder = appliedSortOrder
        draftAppType = appliedAppType
        draftNetworkFilter = appliedNetworkFilter
        draftUserFilter = appliedUserFilter
    }

    fun applyDrafts(prefs: SharedPreferences) {
        appliedSortOrder = draftSortOrder
        appliedAppType = draftAppType
        appliedNetworkFilter = draftNetworkFilter
        appliedUserFilter = draftUserFilter
        saveFilters(prefs)
        showSortFilter = false
    }

    // ─── 代理模式解析 ──────────────────────────────────────────────────

    fun proxyModeToConfigString(mode: AppsProxyMode = proxyMode): String = when (mode) {
        AppsProxyMode.BLACKLIST -> "blacklist"
        AppsProxyMode.WHITELIST -> "whitelist"
        AppsProxyMode.CORE -> if (isBfr) "blacklist" else "core"
    }

    fun parseProxyModeFromSettings(settingsIni: String): AppsProxyMode {
        val line = settingsIni
            .lineSequence()
            .firstOrNull { it.trim().startsWith("proxy_mode=") }
            ?.trim()
            .orEmpty()

        val raw = line
            .substringAfter("=", "")
            .trim()
            .trim('"')
            .lowercase()

        return when (raw) {
            "whitelist", "white" -> AppsProxyMode.WHITELIST
            "core" -> if (isBfr) AppsProxyMode.BLACKLIST else AppsProxyMode.CORE
            else -> AppsProxyMode.BLACKLIST
        }
    }

    // ─── 后端交互 ──────────────────────────────────────────────────────

    suspend fun loadAppsAndBackendConfig(context: Context, forceRefresh: Boolean) {
        loading = true

        withContext(Dispatchers.IO) {
            // 用 libsu IO 读取配置文件
            val settingsContent = runCatching {
                val f = SuFile("/data/adb/box/settings.ini")
                if (f.exists()) SuFileInputStream.open(f).bufferedReader().use { it.readText() } else ""
            }.getOrDefault("")
            if (settingsContent.isNotBlank()) {
                proxyMode = parseProxyModeFromSettings(settingsContent)
            }

            val packageListContent = runCatching {
                val f = SuFile("/data/adb/box/package.list.cfg")
                if (f.exists()) SuFileInputStream.open(f).bufferedReader().use { it.readText() } else ""
            }.getOrDefault("")
            val backendSelected = if (packageListContent.isNotBlank()) {
                packageListContent
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .toSet()
            } else {
                emptySet()
            }

            apps = AppUtils.getInstalledApps(context, forceRefresh = forceRefresh)

            val userIds = apps.map { it.userId }.distinct()
            val userNames = mutableMapOf<Int, String>()
            userIds.forEach { userId ->
                userNames[userId] = AppUtils.getUserDisplayName(userId)
            }
            userDisplayNames = userNames

            val availableKeys = apps.asSequence().map { it.userScopedPackageName }.toSet()
            selectedPackages = backendSelected.intersect(availableKeys)

            savedProxyMode = proxyMode
            savedSelectedPackages = selectedPackages
        }

        loading = false
    }

    /**
     * 保存配置到文件（仅写文件，不重启服务）。
     * 自动保存和手动保存均调用此方法。
     */
    suspend fun saveConfig(context: Context): String? = saveMutex.withLock {
        if (!hasUnsavedChanges || loading) return@withLock null

        val targetProxyMode = proxyMode
        val targetSelectedPackages = selectedPackages.toSet()
        val mode = proxyModeToConfigString(targetProxyMode)
        val content = buildString {
            targetSelectedPackages
                .asSequence()
                .sorted()
                .forEach { append(it).append('\n') }
        }

        saving = true
        try {
            // 用 libsu IO 读-改-写 settings.ini（替代不可靠的 sed）
            val settingsFile = SuFile("/data/adb/box/settings.ini")
            val settingsContent = runCatching {
                if (settingsFile.exists()) {
                    SuFileInputStream.open(settingsFile).bufferedReader().use { it.readText() }
                } else ""
            }.getOrDefault("")

            val key = "proxy_mode"
            val newLine = "$key=\"$mode\""
            val updatedSettings = if (settingsContent.lines().any { it.trimStart().startsWith("$key=") }) {
                settingsContent.lines().joinToString("\n") { line ->
                    if (line.trimStart().startsWith("$key=")) newLine else line
                }
            } else {
                if (settingsContent.isBlank()) newLine
                else settingsContent.trimEnd() + "\n" + newLine
            }

            runCatching {
                SuFileOutputStream.open(settingsFile).use { out ->
                    out.write(updatedSettings.toByteArray(Charsets.UTF_8))
                }
            }.onFailure {
                return@withLock it.message ?: context.getString(R.string.tools_apps_failed_update_proxy_mode)
            }

            // 写 package.list.cfg
            val listRes = ConfigRepository.writeFile("package.list.cfg", content)
            if (listRes.error != null) {
                return@withLock listRes.error
            }

            // 写文件成功，更新 saved 基准值
            savedProxyMode = targetProxyMode
            savedSelectedPackages = targetSelectedPackages
        } finally {
            saving = false
        }

        null
    }

    suspend fun runSmartSelect(context: Context, filtered: List<AppUtils.InstalledApp>) {
        val inScope = filtered.filter { app ->
            when (appliedAppType) {
                AppsAppType.ALL -> true
                AppsAppType.USER -> !app.isSystemApp
                AppsAppType.SYSTEM -> app.isSystemApp
            }
        }

        val appCtx = context.applicationContext
        val chinaPackages = coroutineScope {
            inScope.map { app ->
                async(Dispatchers.IO) {
                    val isChina = AppClassifier.isChinaApp(appCtx, app.packageName, apkPathHint = app.apkPath)
                    if (isChina) app.userScopedPackageName else null
                }
            }.awaitAll().filterNotNull().toSet()
        }
        val computed = when (proxyMode) {
            AppsProxyMode.BLACKLIST, AppsProxyMode.CORE -> chinaPackages
            AppsProxyMode.WHITELIST -> inScope.map { it.userScopedPackageName }.toSet() - chinaPackages
        }

        selectedPackages = when (smartSelectApplyMode) {
            SmartSelectApplyMode.Replace -> computed
            SmartSelectApplyMode.Merge -> selectedPackages + computed
        }
        smartSelecting = false
    }

    // ─── 列表操作 ──────────────────────────────────────────────────────

    fun handleMenuAction(action: AppsManageAction, filtered: List<AppUtils.InstalledApp>) {
        when (action) {
            AppsManageAction.SelectAll -> {
                val allSelected = filtered.isNotEmpty() && filtered.all { it.userScopedPackageName in selectedPackages }
                selectedPackages = if (allSelected) {
                    selectedPackages - filtered.map { it.userScopedPackageName }.toSet()
                } else {
                    selectedPackages + filtered.map { it.userScopedPackageName }.toSet()
                }
            }
            AppsManageAction.Invert -> {
                val set = selectedPackages.toMutableSet()
                filtered.forEach { app ->
                    val key = app.userScopedPackageName
                    if (set.contains(key)) set.remove(key) else set.add(key)
                }
                selectedPackages = set
            }
            AppsManageAction.SmartSelect -> {
                showSmartSelectConfirm = true
            }
            else -> Unit
        }
    }

    fun togglePackage(packageName: String) {
        selectedPackages = if (packageName in selectedPackages) {
            selectedPackages - packageName
        } else {
            selectedPackages + packageName
        }
    }
}

@Composable
fun rememberAppsScreenState(): AppsScreenState {
    return remember { AppsScreenState() }
}

// ─── 过滤/排序逻辑 ──────────────────────────────────────────────────────────

fun filterAndSortApps(
    apps: List<AppUtils.InstalledApp>,
    query: String,
    sortOrder: AppsSortOrder,
    appType: AppsAppType,
    networkFilter: AppsNetworkFilter,
    userFilter: AppsUserFilter,
    userDisplayNames: Map<Int, String>
): List<AppUtils.InstalledApp> {
    val q = query.trim().lowercase()

    val searched = if (q.isBlank()) {
        apps
    } else {
        apps.filter { app ->
            val searchable = (app.name + "\n" + app.userScopedPackageName).lowercase()
            searchable.contains(q)
        }
    }

    val typed = searched.asSequence().filter { app ->
        when (appType) {
            AppsAppType.ALL -> true
            AppsAppType.USER -> !app.isSystemApp
            AppsAppType.SYSTEM -> app.isSystemApp
        }
    }

    val networked = typed.filter { app ->
        when (networkFilter) {
            AppsNetworkFilter.ALL -> true
            AppsNetworkFilter.ONLY_NETWORK -> app.hasNetworkPermission
            AppsNetworkFilter.EXCLUDE_NETWORK -> !app.hasNetworkPermission
        }
    }

    val userFiltered = networked.filter { app ->
        when (userFilter) {
            AppsUserFilter.ALL -> true
            AppsUserFilter.MAIN_ONLY -> app.userId == 0
            AppsUserFilter.WORK_ONLY -> {
                val userName = (userDisplayNames[app.userId] ?: "").lowercase()
                userName.contains("work") || userName.contains("clone") || userName.contains("dual")
            }
            AppsUserFilter.OTHER_ONLY -> app.userId != 0
        }
    }

    val list = userFiltered.toList()
    return when (sortOrder) {
        AppsSortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
        AppsSortOrder.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
        AppsSortOrder.INSTALL_TIME_ASC -> list.sortedBy { it.installTime }
        AppsSortOrder.INSTALL_TIME_DESC -> list.sortedByDescending { it.installTime }
    }
}
