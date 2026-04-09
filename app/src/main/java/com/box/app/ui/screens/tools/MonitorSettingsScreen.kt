package com.box.app.ui.screens.tools

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.miuix.HyperTextField
import com.box.app.ui.theme.appColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MonitorSettingsScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    // ── 状态 ──
    var loading by remember { mutableStateOf(true) }

    // 网络看门狗
    var enableNetworkWatchdog by remember { mutableStateOf(false) }
    var networkCheckInterval by remember { mutableStateOf("60") }
    var networkWatchdogTimeout by remember { mutableStateOf("5") }
    var networkTestHost by remember { mutableStateOf("https://cp.cloudflare.com") }

    // CPU 看门狗
    var enableCpuWatchdog by remember { mutableStateOf(false) }
    var cpuWatchdogThreshold by remember { mutableStateOf("90") }
    var cpuWatchdogDuration by remember { mutableStateOf("120") }

    // 定时重启
    var enableScheduledRestart by remember { mutableStateOf(false) }
    var scheduledRestartCron by remember { mutableStateOf("0 4 * * *") }

    // 日志与清理
    var monitorRunsLogLevel by remember { mutableStateOf("warning") }
    var clearRestartLog by remember { mutableStateOf(false) }
    var cleanGfwRules by remember { mutableStateOf(false) }

    // 下载
    var githubToken by remember { mutableStateOf("") }

    // ── 解析工具 ──
    fun parseSetting(settings: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(settings)?.groupValues?.getOrNull(1)
    }

    // ── 加载 ──
    LaunchedEffect(Unit) {
        loading = true
        val settings = runCatching { BoxApi.getSettings() }.getOrNull().orEmpty()
        if (settings.isNotBlank()) {
            enableNetworkWatchdog = parseSetting(settings, "enable_network_watchdog")?.toBooleanStrictOrNull() ?: false
            networkCheckInterval = parseSetting(settings, "network_check_interval") ?: "60"
            networkWatchdogTimeout = parseSetting(settings, "network_watchdog_timeout") ?: "5"
            networkTestHost = parseSetting(settings, "network_test_host") ?: "https://cp.cloudflare.com"
            enableCpuWatchdog = parseSetting(settings, "enable_cpu_watchdog")?.toBooleanStrictOrNull() ?: false
            cpuWatchdogThreshold = parseSetting(settings, "cpu_watchdog_threshold") ?: "90"
            cpuWatchdogDuration = parseSetting(settings, "cpu_watchdog_duration") ?: "120"
            enableScheduledRestart = parseSetting(settings, "enable_scheduled_restart")?.toBooleanStrictOrNull() ?: false
            scheduledRestartCron = parseSetting(settings, "scheduled_restart_cron") ?: "0 4 * * *"
            monitorRunsLogLevel = parseSetting(settings, "monitor_runs_log_level") ?: "warning"
            clearRestartLog = parseSetting(settings, "clear_restart_log")?.toBooleanStrictOrNull() ?: false
            cleanGfwRules = parseSetting(settings, "clean_gfw_rules")?.toBooleanStrictOrNull() ?: false
            githubToken = parseSetting(settings, "githubtoken") ?: ""
        }
        loading = false
    }

    // ── 文本字段 debounce 写入 ──
    @Composable
    fun DebouncedWrite(value: String, key: String, shouldWrite: Boolean = true) {
        LaunchedEffect(value) {
            if (loading || !shouldWrite) return@LaunchedEffect
            delay(500)
            runCatching { BoxApi.updateSetting(key, value) }
        }
    }

    DebouncedWrite(networkCheckInterval, "network_check_interval", enableNetworkWatchdog)
    DebouncedWrite(networkWatchdogTimeout, "network_watchdog_timeout", enableNetworkWatchdog)
    DebouncedWrite(networkTestHost, "network_test_host", enableNetworkWatchdog)
    DebouncedWrite(cpuWatchdogThreshold, "cpu_watchdog_threshold", enableCpuWatchdog)
    DebouncedWrite(cpuWatchdogDuration, "cpu_watchdog_duration", enableCpuWatchdog)
    DebouncedWrite(scheduledRestartCron, "scheduled_restart_cron", enableScheduledRestart)
    DebouncedWrite(githubToken, "githubtoken")

    // ── 滚动隐藏导航栏 ──
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

    // ── 布尔设置写入辅助 ──
    fun writeBool(key: String, value: Boolean) {
        scope.launch { runCatching { BoxApi.updateBooleanSetting(key, value) } }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.monitor_title),
                subtitle = stringResource(R.string.monitor_subtitle),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface
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
                start = 12.dp, end = 12.dp,
                top = innerPadding.calculateTopPadding(),
                extraBottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ═══ 网络看门狗 ═══
            item(key = "section_network") {
                SmallTitle(text = stringResource(R.string.monitor_section_network_watchdog))
            }
            item(key = "card_network") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
                    cornerRadius = 18.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            checked = enableNetworkWatchdog,
                            onCheckedChange = {
                                enableNetworkWatchdog = it
                                writeBool("enable_network_watchdog", it)
                            },
                            title = stringResource(R.string.monitor_network_watchdog_enable),
                            summary = stringResource(R.string.monitor_network_watchdog_enable_subtitle)
                        )
                        MonitorDivider()
                        MonitorTextFieldRow(
                            icon = Icons.Filled.Timer,
                            title = stringResource(R.string.monitor_network_check_interval),
                            subtitle = stringResource(R.string.monitor_network_check_interval_subtitle),
                            value = networkCheckInterval,
                            label = "60",
                            onValueChange = { networkCheckInterval = it.filter { c -> c.isDigit() } },
                            enabled = enableNetworkWatchdog,
                            keyboardType = KeyboardType.Number
                        )
                        MonitorDivider()
                        MonitorTextFieldRow(
                            icon = Icons.Filled.Replay,
                            title = stringResource(R.string.monitor_network_watchdog_timeout),
                            subtitle = stringResource(R.string.monitor_network_watchdog_timeout_subtitle),
                            value = networkWatchdogTimeout,
                            label = "5",
                            onValueChange = { networkWatchdogTimeout = it.filter { c -> c.isDigit() } },
                            enabled = enableNetworkWatchdog,
                            keyboardType = KeyboardType.Number
                        )
                        MonitorDivider()
                        MonitorTextFieldRow(
                            icon = Icons.Filled.Link,
                            title = stringResource(R.string.monitor_network_test_host),
                            subtitle = stringResource(R.string.monitor_network_test_host_subtitle),
                            value = networkTestHost,
                            label = "URL",
                            onValueChange = { networkTestHost = it },
                            enabled = enableNetworkWatchdog
                        )
                    }
                }
            }

            // ═══ CPU 看门狗 ═══
            item(key = "section_cpu") {
                SmallTitle(text = stringResource(R.string.monitor_section_cpu_watchdog))
            }
            item(key = "card_cpu") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            checked = enableCpuWatchdog,
                            onCheckedChange = {
                                enableCpuWatchdog = it
                                writeBool("enable_cpu_watchdog", it)
                            },
                            title = stringResource(R.string.monitor_cpu_watchdog_enable),
                            summary = stringResource(R.string.monitor_cpu_watchdog_enable_subtitle)
                        )
                        MonitorDivider()
                        MonitorTextFieldRow(
                            icon = Icons.Filled.Speed,
                            title = stringResource(R.string.monitor_cpu_watchdog_threshold),
                            subtitle = stringResource(R.string.monitor_cpu_watchdog_threshold_subtitle),
                            value = cpuWatchdogThreshold,
                            label = "90",
                            onValueChange = { cpuWatchdogThreshold = it.filter { c -> c.isDigit() } },
                            enabled = enableCpuWatchdog,
                            keyboardType = KeyboardType.Number
                        )
                        MonitorDivider()
                        MonitorTextFieldRow(
                            icon = Icons.Filled.Timer,
                            title = stringResource(R.string.monitor_cpu_watchdog_duration),
                            subtitle = stringResource(R.string.monitor_cpu_watchdog_duration_subtitle),
                            value = cpuWatchdogDuration,
                            label = "120",
                            onValueChange = { cpuWatchdogDuration = it.filter { c -> c.isDigit() } },
                            enabled = enableCpuWatchdog,
                            keyboardType = KeyboardType.Number
                        )
                    }
                }
            }

            // ═══ 定时重启 ═══
            item(key = "section_scheduled") {
                SmallTitle(text = stringResource(R.string.monitor_section_scheduled_restart))
            }
            item(key = "card_scheduled") {
                // Cron 预设与自定义
                val cronPresets = listOf(
                    "0 3 * * *" to stringResource(R.string.monitor_cron_daily_3am),
                    "0 4 * * *" to stringResource(R.string.monitor_cron_daily_4am),
                    "0 */6 * * *" to stringResource(R.string.monitor_cron_every_6h),
                    "0 */12 * * *" to stringResource(R.string.monitor_cron_every_12h),
                    "0 3 * * 1" to stringResource(R.string.monitor_cron_weekly_mon),
                )
                val cronDesc = cronPresets.firstOrNull { it.first == scheduledRestartCron }?.second

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SwitchPreference(
                            checked = enableScheduledRestart,
                            onCheckedChange = {
                                enableScheduledRestart = it
                                writeBool("enable_scheduled_restart", it)
                            },
                            title = stringResource(R.string.monitor_scheduled_restart_enable),
                            summary = stringResource(R.string.monitor_scheduled_restart_enable_subtitle)
                        )

                        if (enableScheduledRestart) {
                            MonitorDivider()

                            // 快捷预设选择
                            val presetEntries = cronPresets.map { (cron, label) ->
                                "$label ($cron)"
                            } + stringResource(R.string.monitor_cron_custom)
                            val presetIndex = cronPresets.indexOfFirst { it.first == scheduledRestartCron }
                                .let { if (it < 0) presetEntries.lastIndex else it }

                            WindowDropdownPreference(
                                items = presetEntries,
                                selectedIndex = presetIndex,
                                title = stringResource(R.string.monitor_scheduled_restart_cron),
                                summary = cronDesc ?: scheduledRestartCron,
                                onSelectedIndexChange = { index ->
                                    if (index < cronPresets.size) {
                                        scheduledRestartCron = cronPresets[index].first
                                        scope.launch { runCatching { BoxApi.updateSetting("scheduled_restart_cron", cronPresets[index].first) } }
                                    }
                                    // 选自定义则不做修改，用户在下方输入框手动编辑
                                }
                            )

                            // 自定义 Cron 输入
                            MonitorDivider()
                            MonitorTextFieldRow(
                                icon = Icons.Filled.Schedule,
                                title = stringResource(R.string.monitor_cron_custom),
                                subtitle = stringResource(R.string.monitor_scheduled_restart_cron_subtitle),
                                value = scheduledRestartCron,
                                label = "Cron",
                                onValueChange = { scheduledRestartCron = it },
                                enabled = enableScheduledRestart
                            )
                        }
                    }
                }
            }

            // ═══ 日志与清理 ═══
            item(key = "section_logging") {
                SmallTitle(text = stringResource(R.string.monitor_section_logging))
            }
            item(key = "card_logging") {
                val logLevelEntries = listOf(
                    stringResource(R.string.monitor_log_level_all),
                    stringResource(R.string.monitor_log_level_warning),
                    stringResource(R.string.monitor_log_level_error),
                    stringResource(R.string.monitor_log_level_none)
                )
                val logLevelValues = listOf("all", "warning", "error", "none")
                val logLevelIndex = logLevelValues.indexOf(monitorRunsLogLevel).coerceAtLeast(0)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        WindowDropdownPreference(
                            items = logLevelEntries,
                            selectedIndex = logLevelIndex,
                            title = stringResource(R.string.monitor_log_level),
                            onSelectedIndexChange = { index ->
                                val value = logLevelValues[index]
                                monitorRunsLogLevel = value
                                scope.launch { runCatching { BoxApi.updateSetting("monitor_runs_log_level", value) } }
                            }
                        )
                        MonitorDivider()
                        SwitchPreference(
                            checked = clearRestartLog,
                            onCheckedChange = {
                                clearRestartLog = it
                                writeBool("clear_restart_log", it)
                            },
                            title = stringResource(R.string.monitor_clear_restart_log),
                            summary = stringResource(R.string.monitor_clear_restart_log_subtitle)
                        )
                        MonitorDivider()
                        SwitchPreference(
                            checked = cleanGfwRules,
                            onCheckedChange = {
                                cleanGfwRules = it
                                writeBool("clean_gfw_rules", it)
                            },
                            title = stringResource(R.string.monitor_clean_gfw_rules),
                            summary = stringResource(R.string.monitor_clean_gfw_rules_subtitle)
                        )
                    }
                }
            }

            // ═══ 下载设置 ═══
            item(key = "section_download") {
                SmallTitle(text = stringResource(R.string.monitor_section_download))
            }
            item(key = "card_download") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MonitorTextFieldRow(
                            icon = Icons.Filled.Key,
                            title = stringResource(R.string.monitor_github_token),
                            subtitle = stringResource(R.string.monitor_github_token_subtitle),
                            value = githubToken,
                            label = "ghp_...",
                            onValueChange = { githubToken = it }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

// ─── 分隔线 ─────────────────────────────────────────────────────────────────

@Composable
private fun MonitorDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.08f))
    )
}

// ─── 文本输入行（miuix TextField） ──────────────────────────────────────────

@Composable
private fun MonitorTextFieldRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    value: String,
    label: String = "",
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val alpha = if (enabled) 1f else 0.38f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = alpha),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.padding(start = 10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        HyperTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = label.ifBlank { title },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}
