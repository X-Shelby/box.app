package com.box.app.ui.screens.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalClipboardManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.box.app.BuildConfig
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.miuix.HyperBottomSheet
import com.box.app.ui.miuix.HyperTextButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class WifiListMode { BLACKLIST, WHITELIST }
private enum class MacProxyMode { BLACKLIST, WHITELIST }
private enum class WifiPickType { SSID, BSSID }

private data class MacCandidate(val mac: String, val isSelected: Boolean = false)
private data class WifiCandidate(val ssid: String, val bssid: String, val isCurrent: Boolean = false)

@Composable
fun ToolsNetworkControlScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val isBfr = remember { BuildConfig.FLAVOR == "bfr" }

    val loadFailedText = stringResource(R.string.tools_network_control_load_failed)
    val saveFailedText = stringResource(R.string.tools_network_control_save_failed)
    val wifiPickerTitle = stringResource(R.string.tools_network_control_wifi_picker_title)
    val wifiPickerLoading = stringResource(R.string.tools_network_control_wifi_picker_loading)
    val wifiPickerEmpty = stringResource(R.string.tools_network_control_wifi_picker_empty)
    val wifiPickerError = stringResource(R.string.tools_network_control_wifi_picker_error)
    val wifiPickerPermissionRequired = stringResource(R.string.tools_network_control_wifi_picker_permission_required)
    val wifiPickerLocationRequired = stringResource(R.string.tools_network_control_wifi_picker_location_required)
    val hotspotMacPickerTitle = stringResource(R.string.tools_network_control_hotspot_mac_picker_title)
    val hotspotMacPickerLoading = stringResource(R.string.tools_network_control_hotspot_mac_picker_loading)
    val hotspotMacPickerEmpty = stringResource(R.string.tools_network_control_hotspot_mac_picker_empty)
    val hotspotMacPickerError = stringResource(R.string.tools_network_control_hotspot_mac_picker_error)

    // ─── 状态变量 ──────────────────────────────────────────────────────

    var enableNetworkControl by rememberSaveable { mutableStateOf(false) }
    var useOnWifiDisconnect by rememberSaveable { mutableStateOf(true) }
    var useOnWifi by rememberSaveable { mutableStateOf(true) }
    var enableSsidMatching by rememberSaveable { mutableStateOf(false) }
    var wifiListMode by rememberSaveable { mutableStateOf(WifiListMode.BLACKLIST) }
    var enableNetworkControlLog by rememberSaveable { mutableStateOf(false) }
    var macFilterEnable by rememberSaveable { mutableStateOf(false) }
    var macProxyMode by rememberSaveable { mutableStateOf(MacProxyMode.BLACKLIST) }
    var macs by rememberSaveable { mutableStateOf(listOf("")) }
    var ssids by rememberSaveable { mutableStateOf(listOf("")) }
    var bssids by rememberSaveable { mutableStateOf(listOf("")) }

    // 初始值（脏检测）
    var initialEnableNetworkControl by rememberSaveable { mutableStateOf(false) }
    var initialUseOnWifiDisconnect by rememberSaveable { mutableStateOf(true) }
    var initialUseOnWifi by rememberSaveable { mutableStateOf(true) }
    var initialEnableSsidMatching by rememberSaveable { mutableStateOf(false) }
    var initialWifiListMode by rememberSaveable { mutableStateOf(WifiListMode.BLACKLIST) }
    var initialEnableNetworkControlLog by rememberSaveable { mutableStateOf(false) }
    var initialSsids by rememberSaveable { mutableStateOf(listOf("")) }
    var initialBssids by rememberSaveable { mutableStateOf(listOf("")) }
    var initialMacFilterEnable by rememberSaveable { mutableStateOf(false) }
    var initialMacProxyMode by rememberSaveable { mutableStateOf(MacProxyMode.BLACKLIST) }
    var initialMacs by rememberSaveable { mutableStateOf(listOf("")) }

    var loading by rememberSaveable { mutableStateOf(true) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    var wifiPickerVisible by rememberSaveable { mutableStateOf(false) }
    var wifiPickerTargetType by rememberSaveable { mutableStateOf(WifiPickType.SSID) }
    var wifiPickerTargetIndex by rememberSaveable { mutableStateOf(0) }
    var wifiPickerCandidates by remember { mutableStateOf(emptyList<WifiCandidate>()) }
    var wifiPickerLoadingState by remember { mutableStateOf(false) }
    var wifiPickerErrorText by remember { mutableStateOf<String?>(null) }

    var hotspotMacPickerVisible by rememberSaveable { mutableStateOf(false) }
    var hotspotMacPickerTargetIndex by rememberSaveable { mutableStateOf(0) }
    var hotspotMacPickerCandidates by remember { mutableStateOf(emptyList<MacCandidate>()) }
    var hotspotMacPickerLoadingState by remember { mutableStateOf(false) }
    var hotspotMacPickerErrorText by remember { mutableStateOf<String?>(null) }

    var wifiPermissionRequestedOnce by rememberSaveable { mutableStateOf(false) }

    val isDirty by remember {
        derivedStateOf {
            val norm = ssids.map { it.trim() }.filter { it.isNotEmpty() }
            val initNorm = initialSsids.map { it.trim() }.filter { it.isNotEmpty() }
            val normB = if (isBfr) emptyList() else bssids.map { it.trim() }.filter { it.isNotEmpty() }
            val initNormB = if (isBfr) emptyList() else initialBssids.map { it.trim() }.filter { it.isNotEmpty() }
            val normM = if (isBfr) emptyList() else macs.map { it.trim() }.filter { it.isNotEmpty() }
            val initNormM = if (isBfr) emptyList() else initialMacs.map { it.trim() }.filter { it.isNotEmpty() }
            enableNetworkControl != initialEnableNetworkControl ||
                useOnWifiDisconnect != initialUseOnWifiDisconnect ||
                useOnWifi != initialUseOnWifi ||
                enableSsidMatching != initialEnableSsidMatching ||
                wifiListMode != initialWifiListMode ||
                enableNetworkControlLog != initialEnableNetworkControlLog ||
                (!isBfr && macFilterEnable != initialMacFilterEnable) ||
                (!isBfr && macProxyMode != initialMacProxyMode) ||
                norm != initNorm || normB != initNormB || normM != initNormM
        }
    }

    ErrorToast(message = error, onConsumed = { error = null })

    // ─── WiFi 工具函数 ─────────────────────────────────────────────

    fun parseSetting(settings: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(settings)?.groupValues?.getOrNull(1)
    }

    fun normalizeSsid(raw: String): String {
        val s = raw.trim().trim('"')
        return if (s.isBlank() || s == "<unknown ssid>" || s == WifiManager.UNKNOWN_SSID) "<hidden>" else s
    }

    fun requiredWifiPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasWifiPermissions(): Boolean = requiredWifiPermissions().all { p ->
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationEnabled(ctx: Context): Boolean {
        val lm = ctx.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm.isLocationEnabled
        else @Suppress("DEPRECATION") (lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    @SuppressLint("MissingPermission")
    fun currentWifiCandidate(ctx: Context): WifiCandidate? {
        if (!hasWifiPermissions() || !isLocationEnabled(ctx)) return null
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        @Suppress("DEPRECATION") val info = wm.connectionInfo
        if (info.networkId == -1) return null
        @Suppress("DEPRECATION") val ssid = normalizeSsid(info.ssid.orEmpty())
        val bssid = info.bssid?.trim()?.lowercase().orEmpty()
        if (bssid.isBlank() || bssid == "02:00:00:00:00:00") return null
        return WifiCandidate(ssid = ssid, bssid = bssid)
    }

    @SuppressLint("MissingPermission")
    suspend fun loadWifiCandidates(): List<WifiCandidate> {
        if (!hasWifiPermissions()) return emptyList()
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return emptyList()
        val results: List<ScanResult> = runCatching { wm.scanResults }.getOrDefault(emptyList())
        val scanned = results.mapNotNull { r ->
            val ssid = normalizeSsid(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) r.wifiSsid?.toString().orEmpty() else @Suppress("DEPRECATION") r.SSID)
            val bssid = r.BSSID?.trim()?.lowercase().orEmpty()
            if (bssid.isBlank() || bssid == "02:00:00:00:00:00") null
            else WifiCandidate(ssid = ssid, bssid = bssid)
        }
        val current = currentWifiCandidate(context)?.copy(isCurrent = true)
        val filtered = scanned.filterNot { c -> current != null && c.bssid == current.bssid }.distinctBy { it.bssid + "|" + it.ssid }.sortedBy { it.ssid }
        return listOfNotNull(current) + filtered
    }

    fun refreshHotspotMacPicker() {
        scope.launch {
            hotspotMacPickerLoadingState = true; hotspotMacPickerErrorText = null; hotspotMacPickerCandidates = emptyList()
            val list = runCatching { BoxApi.getHotspotClientMacs() }.getOrElse { hotspotMacPickerErrorText = it.message ?: hotspotMacPickerError; emptyList() }
            val selected = macs.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            hotspotMacPickerCandidates = list.asSequence().map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct().sorted().map { MacCandidate(mac = it, isSelected = selected.contains(it)) }.toList()
            if (hotspotMacPickerCandidates.isEmpty() && hotspotMacPickerErrorText.isNullOrBlank()) hotspotMacPickerErrorText = hotspotMacPickerEmpty
            hotspotMacPickerLoadingState = false
        }
    }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (!requiredWifiPermissions().all { result[it] == true }) { wifiPickerErrorText = wifiPickerPermissionRequired; return@rememberLauncherForActivityResult }
        scope.launch { wifiPickerLoadingState = true; wifiPickerErrorText = null; wifiPickerCandidates = runCatching { loadWifiCandidates() }.getOrElse { wifiPickerErrorText = it.message ?: wifiPickerError; emptyList() }; wifiPickerLoadingState = false }
    }

    fun refreshWifiPicker() {
        if (!hasWifiPermissions()) { wifiPermissionLauncher.launch(requiredWifiPermissions()); return }
        if (!isLocationEnabled(context)) { wifiPickerErrorText = wifiPickerLocationRequired; return }
        scope.launch {
            wifiPickerLoadingState = true; wifiPickerErrorText = null; wifiPickerCandidates = emptyList()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) @Suppress("DEPRECATION") runCatching { (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.startScan() }
            delay(800)
            wifiPickerCandidates = runCatching { loadWifiCandidates() }.getOrElse { wifiPickerErrorText = it.message ?: wifiPickerError; emptyList() }
            wifiPickerLoadingState = false
        }
    }

    // ─── 加载 & 保存 ──────────────────────────────────────────────────

    fun save() {
        scope.launch {
            saving = true; error = null
            val cleanS = ssids.map { it.trim() }.filter { it.isNotEmpty() }
            val cleanB = if (isBfr) emptyList() else bssids.map { it.trim() }.filter { it.isNotEmpty() }
            val cleanM = if (isBfr) emptyList() else macs.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val ok = listOf(
                BoxApi.updateBooleanSetting("enable_network_service_control", enableNetworkControl),
                BoxApi.updateBooleanSetting("use_module_on_wifi_disconnect", useOnWifiDisconnect),
                BoxApi.updateBooleanSetting("use_module_on_wifi", useOnWifi),
                BoxApi.updateBooleanSetting("use_ssid_matching", enableSsidMatching),
                BoxApi.updateArraySetting("wifi_ssids_list", cleanS),
                BoxApi.updateSetting("use_wifi_list_mode", if (wifiListMode == WifiListMode.WHITELIST) "whitelist" else "blacklist"),
                BoxApi.updateBooleanSetting("inotify_log_enabled", enableNetworkControlLog),
                if (isBfr) true else BoxApi.updateArraySetting("wifi_bssids_list", cleanB),
                if (isBfr) true else BoxApi.updateBooleanSetting("mac_filter", macFilterEnable),
                if (isBfr) true else BoxApi.updateSetting("mac_mode", if (macProxyMode == MacProxyMode.WHITELIST) "whitelist" else "blacklist"),
                if (isBfr) true else BoxApi.updateArraySetting("macs_list", cleanM)
            ).all { it }
            if (ok) {
                initialEnableNetworkControl = enableNetworkControl; initialUseOnWifiDisconnect = useOnWifiDisconnect
                initialUseOnWifi = useOnWifi; initialEnableSsidMatching = enableSsidMatching
                initialWifiListMode = wifiListMode; initialEnableNetworkControlLog = enableNetworkControlLog
                initialSsids = cleanS.ifEmpty { listOf("") }; initialBssids = if (isBfr) listOf("") else cleanB.ifEmpty { listOf("") }
                if (!isBfr) { initialMacFilterEnable = macFilterEnable; initialMacProxyMode = macProxyMode; initialMacs = cleanM.ifEmpty { listOf("") } }
            } else error = saveFailedText
            saving = false
        }
    }

    LaunchedEffect(Unit) {
        loading = true; error = null
        val settings = runCatching { BoxApi.getSettings() }.getOrNull().orEmpty()
        if (settings.isBlank()) { error = loadFailedText; loading = false; return@LaunchedEffect }
        enableNetworkControl = parseSetting(settings, "enable_network_service_control")?.toBooleanStrictOrNull() ?: false
        useOnWifiDisconnect = parseSetting(settings, "use_module_on_wifi_disconnect")?.toBooleanStrictOrNull() ?: true
        useOnWifi = parseSetting(settings, "use_module_on_wifi")?.toBooleanStrictOrNull() ?: true
        enableSsidMatching = parseSetting(settings, "use_ssid_matching")?.toBooleanStrictOrNull() ?: false
        enableNetworkControlLog = parseSetting(settings, "inotify_log_enabled")?.toBooleanStrictOrNull() ?: false
        wifiListMode = if (parseSetting(settings, "use_wifi_list_mode")?.trim()?.lowercase() == "whitelist") WifiListMode.WHITELIST else WifiListMode.BLACKLIST
        ssids = (parseSetting(settings, "wifi_ssids_list")?.let { BoxApi.parseBashArray(it) }.orEmpty()).ifEmpty { listOf("") }
        bssids = if (isBfr) listOf("") else (parseSetting(settings, "wifi_bssids_list")?.let { BoxApi.parseBashArray(it) }.orEmpty()).ifEmpty { listOf("") }
        macFilterEnable = if (isBfr) false else parseSetting(settings, "mac_filter")?.toBooleanStrictOrNull() ?: false
        macProxyMode = if (isBfr) MacProxyMode.BLACKLIST else if (parseSetting(settings, "mac_mode")?.trim()?.lowercase() == "whitelist") MacProxyMode.WHITELIST else MacProxyMode.BLACKLIST
        macs = if (isBfr) listOf("") else (parseSetting(settings, "macs_list")?.let { BoxApi.parseBashArray(it) }.orEmpty()).ifEmpty { listOf("") }
        initialEnableNetworkControl = enableNetworkControl; initialUseOnWifiDisconnect = useOnWifiDisconnect; initialUseOnWifi = useOnWifi
        initialEnableSsidMatching = enableSsidMatching; initialWifiListMode = wifiListMode; initialEnableNetworkControlLog = enableNetworkControlLog
        initialSsids = ssids; initialBssids = bssids; initialMacFilterEnable = macFilterEnable; initialMacProxyMode = macProxyMode; initialMacs = macs
        loading = false
    }

    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { now -> if (now > last) onNavVisibilityChange(false) else if (now < last) onNavVisibilityChange(true); last = now }
    }

    // ─── UI ────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.tools_network_control_section_general_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { save() }, enabled = isDirty && !saving && !loading) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = null,
                            tint = if (isDirty && !saving) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        // Picker sheets
        if (wifiPickerVisible) {
            LaunchedEffect(wifiPickerVisible) { refreshWifiPicker() }
            PickerSheet(
                title = wifiPickerTitle,
                loading = wifiPickerLoadingState,
                loadingText = wifiPickerLoading,
                errorText = wifiPickerErrorText,
                emptyText = wifiPickerEmpty,
                onDismiss = { wifiPickerVisible = false },
                onRefresh = { refreshWifiPicker() }
            ) {
                wifiPickerCandidates.forEach { item ->
                    BasicComponent(
                        title = item.ssid,
                        summary = item.bssid,
                        endActions = { if (item.isCurrent) Icon(Icons.Filled.CheckCircle, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            when (wifiPickerTargetType) {
                                WifiPickType.SSID -> { val n = ssids.toMutableList(); if (wifiPickerTargetIndex in n.indices) n[wifiPickerTargetIndex] = item.ssid; ssids = n }
                                WifiPickType.BSSID -> { val n = bssids.toMutableList(); if (wifiPickerTargetIndex in n.indices) n[wifiPickerTargetIndex] = item.bssid; bssids = n }
                            }
                            wifiPickerVisible = false
                        }
                    )
                }
            }
        }

        if (hotspotMacPickerVisible) {
            LaunchedEffect(hotspotMacPickerVisible) { refreshHotspotMacPicker() }
            PickerSheet(
                title = hotspotMacPickerTitle,
                loading = hotspotMacPickerLoadingState,
                loadingText = hotspotMacPickerLoading,
                errorText = hotspotMacPickerErrorText,
                emptyText = hotspotMacPickerEmpty,
                onDismiss = { hotspotMacPickerVisible = false },
                onRefresh = { refreshHotspotMacPicker() }
            ) {
                hotspotMacPickerCandidates.forEach { item ->
                    BasicComponent(
                        title = item.mac,
                        endActions = { if (item.isSelected) Icon(Icons.Filled.CheckCircle, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            val n = macs.toMutableList().ifEmpty { mutableListOf("") }
                            if (hotspotMacPickerTargetIndex in n.indices) n[hotspotMacPickerTargetIndex] = item.mac
                            macs = n; hotspotMacPickerVisible = false
                        }
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPaddingWithNavBars(top = innerPadding.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ─── 通用设置 ────────────────────────────────
            item {
                SmallTitle(text = stringResource(R.string.tools_network_control_section_general_title))
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    SwitchPreference(title = stringResource(R.string.tools_network_control_enable_title), summary = stringResource(R.string.tools_network_control_enable_subtitle), checked = enableNetworkControl, onCheckedChange = { enableNetworkControl = it })
                    SwitchPreference(title = stringResource(R.string.tools_network_control_use_on_wifi_disconnect_title), summary = stringResource(R.string.tools_network_control_use_on_wifi_disconnect_subtitle), checked = useOnWifiDisconnect, onCheckedChange = { useOnWifiDisconnect = it }, enabled = enableNetworkControl)
                    SwitchPreference(title = stringResource(R.string.tools_network_control_use_on_wifi_title), summary = stringResource(R.string.tools_network_control_use_on_wifi_subtitle), checked = useOnWifi, onCheckedChange = { useOnWifi = it }, enabled = enableNetworkControl)
                    SwitchPreference(title = stringResource(R.string.tools_network_control_enable_ssid_matching_title), summary = stringResource(R.string.tools_network_control_enable_ssid_matching_subtitle), checked = enableSsidMatching, onCheckedChange = { enableSsidMatching = it }, enabled = enableNetworkControl)
                    SwitchPreference(title = stringResource(R.string.tools_network_control_enable_log_title), summary = stringResource(R.string.tools_network_control_enable_log_subtitle), checked = enableNetworkControlLog, onCheckedChange = { enableNetworkControlLog = it }, enabled = enableNetworkControl)
                }
            }

            // ─── SSID 模式 ───────────────────────────────
            item {
                SmallTitle(text = stringResource(R.string.tools_network_control_ssid_mode_title))
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    BasicComponent(
                        title = stringResource(R.string.tools_network_control_ssid_mode_blacklist),
                        summary = stringResource(R.string.tools_network_control_ssid_mode_blacklist_subtitle),
                        startAction = { Icon(if (wifiListMode == WifiListMode.BLACKLIST) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked, null, tint = if (wifiListMode == WifiListMode.BLACKLIST) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(end = 16.dp)) },
                        onClick = { wifiListMode = WifiListMode.BLACKLIST }
                    )
                    BasicComponent(
                        title = stringResource(R.string.tools_network_control_ssid_mode_whitelist),
                        summary = stringResource(R.string.tools_network_control_ssid_mode_whitelist_subtitle),
                        startAction = { Icon(if (wifiListMode == WifiListMode.WHITELIST) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked, null, tint = if (wifiListMode == WifiListMode.WHITELIST) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(end = 16.dp)) },
                        onClick = { wifiListMode = WifiListMode.WHITELIST }
                    )
                }
            }

            // ─── SSID 列表 ───────────────────────────────
            item {
                SmallTitle(text = stringResource(R.string.tools_network_control_ssid_list_title))
                // 快捷操作栏
                QuickActions(
                    enabled = enableNetworkControl,
                    onFillCurrent = {
                        val ssid = currentWifiCandidate(context)?.ssid
                        if (ssid != null) ssids = (ssids.filter { it.isNotBlank() } + ssid).distinct().ifEmpty { listOf("") }
                    },
                    onPasteMultiline = { text ->
                        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                        if (lines.isNotEmpty()) ssids = (ssids.filter { it.isNotBlank() } + lines).distinct().ifEmpty { listOf("") }
                    },
                    onClearAll = { ssids = listOf("") }
                )
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    EditableStringList(rows = ssids, hint = stringResource(R.string.tools_network_control_hint_enter_ssid), enabled = enableNetworkControl, onRowsChange = { ssids = it }, onScanClick = { idx -> wifiPickerTargetType = WifiPickType.SSID; wifiPickerTargetIndex = idx; wifiPickerVisible = true })
                }
            }

            // ─── BSSID 列表 ──────────────────────────────
            if (!isBfr) {
                item {
                    SmallTitle(text = stringResource(R.string.tools_network_control_bssid_list_title))
                    QuickActions(
                        enabled = enableNetworkControl,
                        onFillCurrent = {
                            val bssid = currentWifiCandidate(context)?.bssid
                            if (bssid != null) bssids = (bssids.filter { it.isNotBlank() } + bssid).distinct().ifEmpty { listOf("") }
                        },
                        onPasteMultiline = { text ->
                            val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
                            if (lines.isNotEmpty()) bssids = (bssids.filter { it.isNotBlank() } + lines).distinct().ifEmpty { listOf("") }
                        },
                        onClearAll = { bssids = listOf("") }
                    )
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        EditableStringList(rows = bssids, hint = stringResource(R.string.tools_network_control_hint_enter_bssid), enabled = enableNetworkControl, onRowsChange = { bssids = it }, onScanClick = { idx -> wifiPickerTargetType = WifiPickType.BSSID; wifiPickerTargetIndex = idx; wifiPickerVisible = true })
                    }
                }
            }

            // ─── 热点 MAC 过滤 ───────────────────────────
            if (!isBfr) {
                item {
                    SmallTitle(text = stringResource(R.string.tools_network_control_hotspot_mac_title))
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        SwitchPreference(title = stringResource(R.string.tools_network_control_hotspot_mac_enable_title), summary = stringResource(R.string.tools_network_control_hotspot_mac_enable_subtitle), checked = macFilterEnable, onCheckedChange = { macFilterEnable = it })
                        BasicComponent(
                            title = stringResource(R.string.tools_network_control_hotspot_mac_mode_blacklist_title),
                            startAction = { Icon(if (macProxyMode == MacProxyMode.BLACKLIST) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked, null, tint = if (macProxyMode == MacProxyMode.BLACKLIST) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(end = 16.dp)) },
                            onClick = { macProxyMode = MacProxyMode.BLACKLIST }
                        )
                        BasicComponent(
                            title = stringResource(R.string.tools_network_control_hotspot_mac_mode_whitelist_title),
                            startAction = { Icon(if (macProxyMode == MacProxyMode.WHITELIST) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked, null, tint = if (macProxyMode == MacProxyMode.WHITELIST) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary, modifier = Modifier.padding(end = 16.dp)) },
                            onClick = { macProxyMode = MacProxyMode.WHITELIST }
                        )
                        EditableStringList(rows = macs, hint = stringResource(R.string.tools_network_control_hint_enter_mac), enabled = macFilterEnable, onRowsChange = { macs = it }, onScanClick = { idx -> hotspotMacPickerTargetIndex = idx; hotspotMacPickerVisible = true })
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

// ─── Picker Sheet ───────────────────────────────────────────────────────────

@Composable
private fun PickerSheet(
    title: String,
    loading: Boolean,
    loadingText: String,
    errorText: String?,
    emptyText: String,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    HyperBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = title,
        endAction = { HyperTextButton(text = stringResource(R.string.action_refresh), onClick = onRefresh, prominent = true) }
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
            when {
                loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        InfiniteProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(text = loadingText, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                }
                !errorText.isNullOrBlank() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = errorText, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                }
                else -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        content()
                    }
                }
            }
        }
    }
}

// ─── 快捷操作栏（紧凑 primary 色文字链接） ──────────────────────────────────

@Composable
private fun QuickActions(
    enabled: Boolean,
    onFillCurrent: () -> Unit,
    onPasteMultiline: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val color = if (enabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceSecondary
    val clearColor = if (enabled) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceSecondary

    @Composable
    fun ActionLink(text: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = tint,
            modifier = if (enabled) Modifier.clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ) else Modifier
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ActionLink(stringResource(R.string.tools_network_control_quick_fill_current), color, onFillCurrent)
        ActionLink(stringResource(R.string.tools_network_control_quick_paste), color) {
            val text = clipboardManager.getText()?.text.orEmpty()
            if (text.isNotBlank()) onPasteMultiline(text)
        }
        ActionLink(stringResource(R.string.tools_network_control_quick_clear), clearColor, onClearAll)
    }
}

// ─── EditableStringList（Miuix 风格） ────────────────────────────────────────

@Composable
private fun EditableStringList(
    rows: List<String>,
    hint: String,
    enabled: Boolean = true,
    onRowsChange: (List<String>) -> Unit,
    onScanClick: ((Int) -> Unit)? = null
) {
    val safeRows = rows.ifEmpty { listOf("") }
    Column(modifier = Modifier.alpha(if (enabled) 1f else 0.45f)) {
        safeRows.forEachIndexed { idx, row ->
            BasicComponent(
                title = row.ifBlank { hint },
                startAction = if (onScanClick != null) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                } else null,
                endActions = {
                    IconButton(onClick = {
                        val n = safeRows.toMutableList()
                        if (n.size <= 1) n[0] = "" else n.removeAt(idx)
                        onRowsChange(n)
                    }, enabled = enabled) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                },
                onClick = if (onScanClick != null) { { onScanClick(idx) } } else null
            )
        }

        // 添加按钮
        BasicComponent(
            title = stringResource(R.string.action_add),
            summary = stringResource(R.string.tools_network_control_append_item),
            startAction = {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 16.dp)
                )
            },
            onClick = if (enabled) { { onRowsChange(safeRows + "") } } else null
        )
    }
}
