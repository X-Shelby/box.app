package com.box.app.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.ShellExecutor
import com.box.app.ui.components.ErrorToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.miuix.HyperTextField
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar

import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ─── 配置行模型 ────────────────────────────────────────────────────────────

/** 配置文件格式 */
private enum class ConfFormat {
    /** smartdns.conf: `key value` */
    SPACE,
    /** setting.conf: `KEY=VALUE` 或 `KEY="VALUE"` */
    SHELL
}

private sealed class ConfLine {
    data class Comment(val raw: String) : ConfLine()
    data class KV(val key: String, var value: String, val format: ConfFormat = ConfFormat.SPACE) : ConfLine()
    data class Blank(val raw: String = "") : ConfLine()
}

/** 自动检测格式并解析 */
private fun parseConf(text: String): Pair<ConfFormat, List<ConfLine>> {
    // 通过第一个非注释非空行判断格式
    val format = text.lines()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
        ?.let { if (it.contains('=')) ConfFormat.SHELL else ConfFormat.SPACE }
        ?: ConfFormat.SPACE

    val lines = text.lines().map { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.isBlank() -> ConfLine.Blank(line)
            trimmed.startsWith("#") -> ConfLine.Comment(line)
            format == ConfFormat.SHELL -> {
                // KEY=VALUE 或 KEY="VALUE"
                val eqIdx = trimmed.indexOf('=')
                if (eqIdx < 0) ConfLine.KV(trimmed, "", format)
                else {
                    val key = trimmed.substring(0, eqIdx)
                    val raw = trimmed.substring(eqIdx + 1).trim()
                    val value = raw.removeSurrounding("\"")
                    ConfLine.KV(key, value, format)
                }
            }
            else -> {
                // key value
                val spaceIdx = trimmed.indexOf(' ')
                if (spaceIdx < 0) ConfLine.KV(trimmed, "", format)
                else {
                    val key = trimmed.substring(0, spaceIdx)
                    val rest = trimmed.substring(spaceIdx + 1).trim()
                    ConfLine.KV(key, rest, format)
                }
            }
        }
    }
    return format to lines
}

private fun serializeConf(lines: List<ConfLine>): String = lines.joinToString("\n") { line ->
    when (line) {
        is ConfLine.Comment -> line.raw
        is ConfLine.Blank -> line.raw
        is ConfLine.KV -> when (line.format) {
            ConfFormat.SHELL -> "${line.key}=\"${line.value}\""
            ConfFormat.SPACE -> "${line.key} ${line.value}"
        }
    }
}

// ─── 辅助：从解析行列表取/设值 ──────────────────────────────────────────────

private fun List<ConfLine>.getValue(key: String): String? =
    filterIsInstance<ConfLine.KV>().firstOrNull { it.key == key }?.value

private fun List<ConfLine>.getBool(key: String): Boolean =
    getValue(key)?.lowercase() in listOf("yes", "true", "1")

private fun List<ConfLine>.getFormat(): ConfFormat =
    filterIsInstance<ConfLine.KV>().firstOrNull()?.format ?: ConfFormat.SPACE

private fun MutableList<ConfLine>.setValue(key: String, value: String) {
    val idx = indexOfFirst { it is ConfLine.KV && (it as ConfLine.KV).key == key }
    if (idx >= 0) (this[idx] as ConfLine.KV).value = value
}

/** 根据文件格式写入布尔值：SHELL → true/false，SPACE → yes/no */
private fun MutableList<ConfLine>.setBool(key: String, enabled: Boolean) {
    val fmt = getFormat()
    val v = when (fmt) {
        ConfFormat.SHELL -> if (enabled) "true" else "false"
        ConfFormat.SPACE -> if (enabled) "yes" else "no"
    }
    setValue(key, v)
}

// ─── 页面 ──────────────────────────────────────────────────────────────────

@Composable
fun SmartDnsConfigScreen(
    filePath: String,
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var originalText by remember { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // 解析后的配置行（可视化模式操作此列表）
    var confFormat by remember { mutableStateOf(ConfFormat.SPACE) }
    var confLines by remember { mutableStateOf<MutableList<ConfLine>>(mutableListOf()) }
    // 代码模式的文本
    var codeText by remember { mutableStateOf("") }

    val fileName = filePath.substringAfterLast('/')
    val hasChanges = remember(confLines, codeText, originalText, selectedTab) {
        val current = if (selectedTab == 0) serializeConf(confLines) else codeText
        current != originalText
    }

    ErrorToast(message = toastMessage, onConsumed = { toastMessage = null })

    // ── 加载（直接用 ShellExecutor，因为 SmartDNS 路径不在 ConfigRepository 的 /data/adb/box 根下）──
    LaunchedEffect(filePath) {
        loading = true
        withContext(Dispatchers.IO) {
            val res = ShellExecutor.execute("cat '$filePath' 2>/dev/null")
            if (res.exitCode != 0 || res.stdout.isBlank()) {
                toastMessage = "Failed to read $fileName"
            } else {
                val text = res.stdout
                originalText = text
                val (fmt, lines) = parseConf(text)
                confFormat = fmt
                confLines = lines.toMutableList()
                codeText = text
            }
        }
        loading = false
    }

    // ── 可视化辅助：revision 驱动重组 ──
    var revision by remember { mutableIntStateOf(0) }

    // ── 模式切换同步 ──
    fun syncVisualToCode() { codeText = serializeConf(confLines) }
    fun syncCodeToVisual() {
        val (fmt, lines) = parseConf(codeText)
        confFormat = fmt
        confLines = lines.toMutableList()
        revision++
    }

    // ── 保存 ──
    fun save() {
        if (saving) return
        saving = true
        val content = if (selectedTab == 0) serializeConf(confLines) else codeText
        scope.launch {
            val escaped = content.replace("'", "'\\''")
            val res = withContext(Dispatchers.IO) {
                ShellExecutor.execute("cat > '$filePath' << 'SDNS_EOF'\n$escaped\nSDNS_EOF")
            }
            if (res.exitCode != 0) {
                toastMessage = "Save failed"
            } else {
                originalText = content
                if (selectedTab == 0) codeText = content else { val (f, l) = parseConf(content); confFormat = f; confLines = l.toMutableList() }
                toastMessage = "Saved"
            }
            saving = false
        }
    }

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

    // ── 可视化辅助：读取和修改触发重组 ──
    fun readVal(key: String): String = confLines.getValue(key).let { _ -> revision; confLines.getValue(key) ?: "" }
    fun readBool(key: String): Boolean = confLines.getBool(key).let { _ -> revision; confLines.getBool(key) }
    fun writeVal(key: String, value: String) { confLines.setValue(key, value); revision++ }
    fun writeBool(key: String, enabled: Boolean) { confLines.setBool(key, enabled); revision++ }

    val scheme = MiuixTheme.colorScheme

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = fileName,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = scheme.onSurface)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { save() },
                        enabled = hasChanges && !saving
                    ) {
                        Icon(
                            Icons.Filled.Save, null,
                            tint = if (hasChanges && !saving) scheme.primary else scheme.onSurfaceSecondary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Tab 切换
            TabRowWithContour(
                tabs = listOf(
                    stringResource(R.string.sdns_config_tab_visual),
                    stringResource(R.string.sdns_config_tab_code)
                ),
                selectedTabIndex = selectedTab,
                onTabSelected = { newTab ->
                    // 切换前同步数据
                    if (newTab == 1 && selectedTab == 0) syncVisualToCode()
                    else if (newTab == 0 && selectedTab == 1) syncCodeToVisual()
                    selectedTab = newTab
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    InfiniteProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (selectedTab == 0) {
                // ═══ 可视化模式（根据文件格式渲染不同表单） ═══
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = contentPaddingWithNavBars(start = 12.dp, end = 12.dp, extraBottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (confFormat == ConfFormat.SHELL) {
                        // ════ setting.conf 可视化 ════
                        item { SmallTitle(text = stringResource(R.string.sdns_setting_section_startup)) }
                        item {
                            Card(Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Column(Modifier.fillMaxWidth()) {
                                SwitchPreference(checked = readBool("AUTO_RUN"), onCheckedChange = { writeBool("AUTO_RUN", it) }, title = "AUTO_RUN", summary = stringResource(R.string.smartdns_auto_run_subtitle))
                                SwitchPreference(checked = readBool("OOM_PROTECT"), onCheckedChange = { writeBool("OOM_PROTECT", it) }, title = "OOM_PROTECT", summary = stringResource(R.string.smartdns_oom_protect_subtitle))
                                ConfTextField("CPU_AFFINITY", readVal("CPU_AFFINITY")) { writeVal("CPU_AFFINITY", it) }
                            }}
                        }
                        item { SmallTitle(text = stringResource(R.string.sdns_setting_section_rules)) }
                        item {
                            Card(Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Column(Modifier.fillMaxWidth()) {
                                SwitchPreference(checked = readBool("RULE_AUTO_UPDATE"), onCheckedChange = { writeBool("RULE_AUTO_UPDATE", it) }, title = "RULE_AUTO_UPDATE", summary = stringResource(R.string.smartdns_rule_auto_update_subtitle, readVal("RULE_UPDATE_TIME").ifBlank { "12:00" }))
                                ConfTextField("RULE_UPDATE_TIME", readVal("RULE_UPDATE_TIME")) { writeVal("RULE_UPDATE_TIME", it) }
                                ConfTextField("RULE1_NAME", readVal("RULE1_NAME")) { writeVal("RULE1_NAME", it) }
                                ConfTextField("RULE1_URL", readVal("RULE1_URL")) { writeVal("RULE1_URL", it) }
                                ConfTextField("RULE2_NAME", readVal("RULE2_NAME")) { writeVal("RULE2_NAME", it) }
                                ConfTextField("RULE2_URL", readVal("RULE2_URL")) { writeVal("RULE2_URL", it) }
                            }}
                        }
                    } else {
                        // ════ smartdns.conf 可视化 ════
                        item { SmallTitle(text = stringResource(R.string.sdns_section_bind)) }
                        item { Card(Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                            ConfTextField("bind", readVal("bind")) { writeVal("bind", it) }
                        }}

                        item { SmallTitle(text = stringResource(R.string.sdns_section_log)) }
                        item {
                            val logLevels = listOf("off", "fatal", "error", "warn", "notice", "info", "debug")
                            val logLevelIdx = logLevels.indexOf(readVal("log-level").lowercase()).coerceAtLeast(0)
                            Card(Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Column(Modifier.fillMaxWidth()) {
                                WindowDropdownPreference(items = logLevels, selectedIndex = logLevelIdx, title = "log-level", onSelectedIndexChange = { writeVal("log-level", logLevels[it]) })
                                ConfTextField("log-file", readVal("log-file")) { writeVal("log-file", it) }
                                ConfTextField("log-size", readVal("log-size")) { writeVal("log-size", it) }
                                ConfTextField("log-num", readVal("log-num"), keyboardType = KeyboardType.Number) { writeVal("log-num", it) }
                            }}
                        }

                        item { SmallTitle(text = stringResource(R.string.sdns_section_cache)) }
                        item { Card(Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Column(Modifier.fillMaxWidth()) {
                            ConfTextField("cache-size", readVal("cache-size"), keyboardType = KeyboardType.Number) { writeVal("cache-size", it) }
                            SwitchPreference(checked = readBool("cache-persist"), onCheckedChange = { writeBool("cache-persist", it) }, title = "cache-persist")
                            ConfTextField("cache-file", readVal("cache-file")) { writeVal("cache-file", it) }
                            ConfTextField("cache-checkpoint-time", readVal("cache-checkpoint-time"), keyboardType = KeyboardType.Number) { writeVal("cache-checkpoint-time", it) }
                        }}}

                        item { SmallTitle(text = stringResource(R.string.sdns_section_prefetch)) }
                        item { Card(Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Column(Modifier.fillMaxWidth()) {
                            SwitchPreference(checked = readBool("prefetch-domain"), onCheckedChange = { writeBool("prefetch-domain", it) }, title = "prefetch-domain")
                            SwitchPreference(checked = readBool("serve-expired"), onCheckedChange = { writeBool("serve-expired", it) }, title = "serve-expired")
                            ConfTextField("serve-expired-reply-ttl", readVal("serve-expired-reply-ttl"), keyboardType = KeyboardType.Number) { writeVal("serve-expired-reply-ttl", it) }
                            ConfTextField("serve-expired-ttl", readVal("serve-expired-ttl"), keyboardType = KeyboardType.Number) { writeVal("serve-expired-ttl", it) }
                            ConfTextField("serve-expired-prefetch-time", readVal("serve-expired-prefetch-time"), keyboardType = KeyboardType.Number) { writeVal("serve-expired-prefetch-time", it) }
                        }}}

                        item { SmallTitle(text = stringResource(R.string.sdns_section_speed)) }
                        item {
                            val modes = listOf("first-ping", "fastest-ip", "fastest-response")
                            val modeIdx = modes.indexOf(readVal("response-mode")).coerceAtLeast(0)
                            Card(Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Column(Modifier.fillMaxWidth()) {
                                ConfTextField("speed-check-mode", readVal("speed-check-mode")) { writeVal("speed-check-mode", it) }
                                WindowDropdownPreference(items = modes, selectedIndex = modeIdx, title = "response-mode", onSelectedIndexChange = { writeVal("response-mode", modes[it]) })
                            }}
                        }

                        item { SmallTitle(text = "IPv6") }
                        item { Card(Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Column(Modifier.fillMaxWidth()) {
                            SwitchPreference(checked = readBool("force-AAAA-SOA"), onCheckedChange = { writeBool("force-AAAA-SOA", it) }, title = "force-AAAA-SOA", summary = stringResource(R.string.sdns_force_aaaa_soa_desc))
                            SwitchPreference(checked = readBool("dualstack-ip-selection"), onCheckedChange = { writeBool("dualstack-ip-selection", it) }, title = "dualstack-ip-selection")
                            SwitchPreference(checked = readBool("dualstack-ip-allow-force-AAAA"), onCheckedChange = { writeBool("dualstack-ip-allow-force-AAAA", it) }, title = "dualstack-ip-allow-force-AAAA")
                            ConfTextField("dualstack-ip-selection-threshold", readVal("dualstack-ip-selection-threshold"), keyboardType = KeyboardType.Number) { writeVal("dualstack-ip-selection-threshold", it) }
                        }}}

                        item { SmallTitle(text = "TTL") }
                        item { Card(Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Column(Modifier.fillMaxWidth()) {
                            ConfTextField("rr-ttl-min", readVal("rr-ttl-min"), keyboardType = KeyboardType.Number) { writeVal("rr-ttl-min", it) }
                            ConfTextField("rr-ttl-max", readVal("rr-ttl-max"), keyboardType = KeyboardType.Number) { writeVal("rr-ttl-max", it) }
                            ConfTextField("rr-ttl-reply-max", readVal("rr-ttl-reply-max"), keyboardType = KeyboardType.Number) { writeVal("rr-ttl-reply-max", it) }
                        }}}

                        item { SmallTitle(text = stringResource(R.string.sdns_section_servers)) }
                        item {
                            val servers = confLines.filterIsInstance<ConfLine.KV>().filter { it.key.startsWith("server") }
                            Card(Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (servers.isEmpty()) Text(stringResource(R.string.sdns_no_servers), style = MiuixTheme.textStyles.body2, color = scheme.onSurfaceSecondary)
                                else servers.forEach { sv -> Text("${sv.key} ${sv.value}", style = MiuixTheme.textStyles.footnote2, fontFamily = FontFamily.Monospace, color = scheme.onSurface, maxLines = 2) }
                                Text(stringResource(R.string.sdns_servers_edit_hint), style = MiuixTheme.textStyles.footnote2, color = scheme.onSurfaceSecondary, modifier = Modifier.padding(top = 4.dp))
                            }}
                        }

                        item { SmallTitle(text = "WebUI") }
                        item { Card(Modifier.fillMaxWidth(), cornerRadius = 18.dp) { Column(Modifier.fillMaxWidth()) {
                            ConfTextField("smartdns-ui.ip", readVal("smartdns-ui.ip")) { writeVal("smartdns-ui.ip", it) }
                            ConfTextField("smartdns-ui.user", readVal("smartdns-ui.user")) { writeVal("smartdns-ui.user", it) }
                            ConfTextField("smartdns-ui.password", readVal("smartdns-ui.password")) { writeVal("smartdns-ui.password", it) }
                        }}}
                    }

                    item { Spacer(Modifier.height(8.dp)) }
                }
            } else {
                // ═══ 代码模式 ═══
                HyperTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    label = fileName,
                    textStyle = MiuixTheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
                    singleLine = false
                )
            }
        }
    }
}

// ─── 配置文本输入行 ────────────────────────────────────────────────────────

@Composable
private fun ConfTextField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    HyperTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}
