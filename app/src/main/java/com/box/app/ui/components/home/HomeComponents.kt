package com.box.app.ui.components.home

import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import coil3.compose.AsyncImage
import com.box.app.BuildConfig
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.ShellExecutor
import com.box.app.data.model.HomeServiceState
import com.box.app.data.model.ServiceStatus
import com.box.app.R
import com.box.app.ui.components.ErrorToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.box.app.ui.components.bottomsheets.SheetBlurEffect
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class HomeCardModel(
    val title: String,
    val value: String,
    val subtitle: String,
    val kind: HomeMetricKind,
    val accent: Color,
    val badgeText: String? = null,
    val progress: Float? = null,
    val isActive: Boolean = true,
    val onClick: (() -> Unit)? = null,
    val cornerActionIcon: ImageVector? = null,
    val onCornerAction: (() -> Unit)? = null,
    val sparkDown: List<Float>? = null,
    val sparkUp: List<Float>? = null
)

enum class HomeMetricKind {
    Service,
    Ip,
    Speed,
    Latency,
    Subscription,
    System
}

@Composable
fun HomeHeader(
    onEdit: (() -> Unit)? = null,
    scrollBehavior: ScrollBehavior? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.home_header_title),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (onEdit != null) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp),
                backgroundColor = Color.Transparent,
                cornerRadius = 12.dp
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.home_header_edit),
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun HomeHeroCard(
    serviceState: HomeServiceState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReload: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val env = serviceState.env

    val status = serviceState.status
    val isRunning = status is ServiceStatus.Running
    val isStopped = status is ServiceStatus.Stopped
    val isBusy = !(isRunning || isStopped)

    val statusEditable = !isRunning

    val showUnavailable = env.checked && !env.isReady
    val isEnvReady = env.isReady

    val statusText = when (status) {
        is ServiceStatus.Running -> stringResource(R.string.home_service_status_running)
        is ServiceStatus.Stopped -> if (showUnavailable) stringResource(R.string.home_service_status_unavailable) else stringResource(R.string.home_service_status_stopped)
        is ServiceStatus.Starting -> stringResource(R.string.home_service_status_starting)
        is ServiceStatus.Stopping -> stringResource(R.string.home_service_status_stopping)
        is ServiceStatus.Restarting -> stringResource(R.string.home_service_status_restarting)
        is ServiceStatus.Checking -> stringResource(R.string.home_service_status_checking)
    }

    val statusColors = when {
        isRunning -> homeSuccessColors()
        showUnavailable -> homeDangerColors()
        isBusy -> homeWarningColors()
        else -> homeNeutralColors()
    }
    val statusAccent = statusColors.accent

    var showCoreSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }
    var showIpv6Sheet by remember { mutableStateOf(false) }
    var switchCoreVersions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var coreText by remember { mutableStateOf(serviceState.coreDisplayName) }
    var modeText by remember { mutableStateOf(serviceState.networkMode) }
    var ipv6Text by remember { mutableStateOf(serviceState.ipv6Text) }
    var dnsMode by remember { mutableStateOf(serviceState.dnsMode) }

    LaunchedEffect(serviceState.coreDisplayName, serviceState.networkMode, serviceState.ipv6Text, serviceState.dnsMode, isRunning) {
        if (isRunning) {
            coreText = serviceState.coreDisplayName
            modeText = serviceState.networkMode
            ipv6Text = serviceState.ipv6Text
            dnsMode = serviceState.dnsMode
        }
    }

    fun parseSetting(settings: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(settings)?.groupValues?.getOrNull(1)
    }

    fun coreDisplayName(binName: String?, xclashOption: String?): String? {
        val bin = binName?.trim().orEmpty()
        if (bin.isBlank()) return null
        if (BuildConfig.FLAVOR == "bfr" && bin == "clash") {
            val opt = xclashOption?.trim().orEmpty()
            if (opt.isNotBlank()) return "clash-$opt"
        }
        return bin
    }

    // 打开切换核心 Sheet 时异步检测版本
    LaunchedEffect(showCoreSheet) {
        if (showCoreSheet) {
            switchCoreVersions = emptyMap()
            withContext(Dispatchers.IO) {
                val versions = mutableMapOf<String, String>()
                val bins = listOf("mihomo", "sing-box", "xray", "v2fly", "hysteria")
                for (bin in bins) {
                    runCatching {
                        // 同时执行 -v 和 version，合并输出由解析器提取版本号
                        val cmd = """for p in "/data/adb/box/bin/$bin" "/data/adb/box/$bin/$bin"; do [ -x "${'$'}p" ] || continue; v=${'$'}( { "${'$'}p" -v 2>/dev/null; "${'$'}p" version 2>/dev/null; } | head -n20 ); [ -n "${'$'}v" ] && echo "${'$'}v" && exit 0; done"""
                        val res = ShellExecutor.execute(cmd)
                        val ver = res.stdout.trim()
                        if (ver.isNotBlank()) {
                            // 三级版本解析（避免误匹配时间戳）
                            val clean =
                                Regex("""(?i)version[=:\s]+"?v?(\d+\.\d+(?:\.\d+)?(?:-[\w.]+)?)"?""").find(ver)?.let { "v${it.groupValues[1]}" }
                                    ?: Regex("""\bv(\d+\.\d+(?:\.\d+)?(?:-[\w.]+)?)""").find(ver)?.let { "v${it.groupValues[1]}" }
                                    ?: Regex("""(?i)(?:mihomo|sing-box|xray|v2ray|v2fly|hysteria)\s+(?:meta\s+)?v?(\d+\.\d+\.\d+(?:-[\w.]+)?)""").find(ver)?.let { "v${it.groupValues[1]}" }
                            if (clean != null) versions[bin] = clean
                        }
                    }
                }
                switchCoreVersions = versions
            }
        }
    }

    suspend fun reloadSettingsLocal() {
        val settings = BoxApi.getSettings()
        if (settings.isBlank()) return
        val bin = parseSetting(settings, "bin_name")
        val opt = parseSetting(settings, "xclash_option")
        coreText = coreDisplayName(bin, opt)?.takeIf { it.isNotBlank() } ?: coreText
        modeText = parseSetting(settings, "network_mode")?.takeIf { it.isNotBlank() } ?: modeText
        val ipv6Enabled = when (parseSetting(settings, "ipv6")?.toBooleanStrictOrNull()) {
            true -> true
            false -> false
            else -> null
        }
        if (ipv6Enabled != null) {
            ipv6Text = if (ipv6Enabled) "true" else "false"
        }
    }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            reloadSettingsLocal()
        }
    }

    suspend fun writeSetting(key: String, value: String) {
        val file = "/data/adb/box/settings.ini"
        val escaped = value.replace("\"", "\\\"")
        val cmd = "if [ -f '$file' ]; then " +
            "if grep -q '^${key}=' '$file'; then " +
            "sed -i 's/^${key}=.*/${key}=\\\"${escaped}\\\"/' '$file'; " +
            "else echo '${key}=\\\"${escaped}\\\"' >> '$file'; fi; " +
            "else echo '${key}=\\\"${escaped}\\\"' > '$file'; fi"
        ShellExecutor.execute(cmd)
    }

    suspend fun removeSetting(key: String) {
        val file = "/data/adb/box/settings.ini"
        val cmd = "if [ -f '$file' ]; then sed -i '/^${key}=/d' '$file'; fi"
        ShellExecutor.execute(cmd)
    }

    suspend fun writeBoolSetting(key: String, enabled: Boolean) {
        val file = "/data/adb/box/settings.ini"
        val v = if (enabled) "true" else "false"
        val cmd = "if [ -f '$file' ]; then " +
            "if grep -q '^${key}=' '$file'; then " +
            "sed -i 's/^${key}=.*/${key}=\\\"${v}\\\"/' '$file'; " +
            "else echo '${key}=\\\"${v}\\\"' >> '$file'; fi; " +
            "else echo '${key}=\\\"${v}\\\"' > '$file'; fi"
        ShellExecutor.execute(cmd)
    }

    if (showCoreSheet) {
        data class CoreChoice(
            val title: String,
            val binName: String,
            val xclashOption: String? = null
        )

        val coreOptions = if (BuildConfig.FLAVOR == "bfr") {
            listOf(
                CoreChoice(title = "clash-mihomo", binName = "clash", xclashOption = "mihomo"),
                CoreChoice(title = "clash-premium", binName = "clash", xclashOption = "premium"),
                CoreChoice(title = "sing-box", binName = "sing-box"),
                CoreChoice(title = "xray", binName = "xray"),
                CoreChoice(title = "v2fly", binName = "v2fly"),
                CoreChoice(title = "hysteria", binName = "hysteria")
            )
        } else {
            listOf(
                CoreChoice(title = "mihomo", binName = "mihomo"),
                CoreChoice(title = "sing-box", binName = "sing-box"),
                CoreChoice(title = "xray", binName = "xray"),
                CoreChoice(title = "v2fly", binName = "v2fly"),
                CoreChoice(title = "hysteria", binName = "hysteria")
            )
        }
        val sheetNavBarPadding = WindowInsets.navigationBars
            .asPaddingValues().calculateBottomPadding()

        val coreSheetBlur = com.box.app.utils.ThemeManager.shouldUseBlurEffects()
        if (coreSheetBlur) SheetBlurEffect()
        WindowBottomSheet(
            show = showCoreSheet,
            title = stringResource(R.string.home_sheet_core_title),
            onDismissRequest = { showCoreSheet = false },
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            dragHandleColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val coreIconUrl = { name: String ->
                    when (name) {
                        "mihomo", "clash", "clash-mihomo", "clash-premium" ->
                            "https://cdn.jsdelivr.net/gh/MetaCubeX/mihomo@Alpha/docs/logo.png"
                        "sing-box" ->
                            "https://cdn.jsdelivr.net/gh/SagerNet/sing-box-for-android@main/app/src/main/ic_launcher-playstore.png"
                        "xray" ->
                            "https://avatars.githubusercontent.com/u/71564206?s=128&v=4"
                        "v2fly", "v2ray" ->
                            "https://cdn.jsdelivr.net/gh/v2fly/v2fly-github-io@master/docs/.vuepress/public/readme-logo.png"
                        "hysteria" ->
                            "https://cdn.jsdelivr.net/gh/apernet/hysteria@master/media-kit/png/symbol%201@2x.png"
                        else -> null
                    }
                }

                HomeSheetSummary(text = stringResource(R.string.home_sheet_core_subtitle))

                // HyperOS3 风格：独立圆角卡片 + 选中态动画
                val switchScheme = MiuixTheme.colorScheme
                coreOptions.forEachIndexed { index, item ->
                    val isCurrent = item.title == coreText
                    val iconUrl = coreIconUrl(item.binName) ?: coreIconUrl(item.title)

                    val bgColor by animateColorAsState(
                        if (isCurrent) switchScheme.primary.copy(alpha = 0.12f)
                        else switchScheme.surfaceContainerHigh,
                        animationSpec = tween(280), label = "sw_bg_$index"
                    )
                    val borderColor by animateColorAsState(
                        if (isCurrent) switchScheme.primary.copy(alpha = 0.28f)
                        else Color.Transparent,
                        animationSpec = tween(280), label = "sw_bd_$index"
                    )
                    val titleColor by animateColorAsState(
                        if (isCurrent) switchScheme.primary else switchScheme.onSurface,
                        animationSpec = tween(280), label = "sw_tt_$index"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SmoothRoundedCornerShape(16.dp))
                            .border(1.dp, borderColor, SmoothRoundedCornerShape(16.dp))
                            .background(bgColor)
                            .clickable {
                                if (!isCurrent) {
                                    showCoreSheet = false
                                    coreText = item.title
                                    scope.launch {
                                        writeSetting("bin_name", item.binName)
                                        val opt = item.xclashOption
                                        if (!opt.isNullOrBlank()) {
                                            writeSetting("xclash_option", opt)
                                        }
                                        reloadSettingsLocal()
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (iconUrl != null) {
                                AsyncImage(
                                    model = iconUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MiuixTheme.textStyles.body1,
                                    fontWeight = FontWeight.Medium,
                                    color = titleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val ver = switchCoreVersions[item.binName]
                                if (ver != null) {
                                    Text(
                                        text = ver,
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = switchScheme.onSurfaceSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = switchScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(sheetNavBarPadding))
            }
        }
    }

    if (showModeSheet) {
        val sheetNavBarPadding = WindowInsets.navigationBars
            .asPaddingValues().calculateBottomPadding()
        val modeOptions = listOf("tun", "tproxy", "redirect", "mixed", "enhance")
        val modeSheetBlur = com.box.app.utils.ThemeManager.shouldUseBlurEffects()
        if (modeSheetBlur) SheetBlurEffect()
        WindowBottomSheet(
            show = showModeSheet,
            title = stringResource(R.string.home_sheet_network_mode_title),
            onDismissRequest = { showModeSheet = false },
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            dragHandleColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HomeSheetSummary(text = stringResource(R.string.home_sheet_network_mode_subtitle))
                modeOptions.forEach { item ->
                    val isCurrent = item == modeText
                    HomeSheetOptionItem(
                        title = item,
                        selected = isCurrent,
                        onClick = {
                            if (!isCurrent) {
                                showModeSheet = false
                                modeText = item
                                scope.launch {
                                    writeSetting("network_mode", item)
                                    reloadSettingsLocal()
                                }
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(sheetNavBarPadding))
            }
        }
    }

    if (showIpv6Sheet) {
        val sheetNavBarPadding = WindowInsets.navigationBars
            .asPaddingValues().calculateBottomPadding()
        val checked = ipv6Text.equals("true", ignoreCase = true)
        val ipv6SheetBlur = com.box.app.utils.ThemeManager.shouldUseBlurEffects()
        if (ipv6SheetBlur) SheetBlurEffect()
        WindowBottomSheet(
            show = showIpv6Sheet,
            title = stringResource(R.string.home_sheet_ipv6_title),
            onDismissRequest = { showIpv6Sheet = false },
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            dragHandleColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HomeSheetSummary(text = stringResource(R.string.home_sheet_ipv6_subtitle))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh)
                ) {
                    SwitchPreference(
                        checked = checked,
                        title = stringResource(R.string.home_ipv6_label),
                        summary = if (checked) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                        onCheckedChange = { next ->
                            ipv6Text = if (next) "true" else "false"
                            scope.launch {
                                writeBoolSetting("ipv6", next)
                                reloadSettingsLocal()
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(sheetNavBarPadding))
            }
        }
    }

    var toastMessage by remember { mutableStateOf<String?>(null) }
    ErrorToast(
        message = toastMessage,
        onConsumed = { toastMessage = null }
    )

    val canReloadConfig = remember(isRunning, isEnvReady, coreText) {
        isRunning && isEnvReady && (coreText.equals("mihomo", ignoreCase = true) || coreText.equals("sing-box", ignoreCase = true))
    }

    val reloadConfigSuccessText = stringResource(R.string.home_reload_config_success)
    val reloadConfigFailedText = stringResource(R.string.home_reload_config_failed)
    val statusSummary = when {
        isRunning -> serviceState.uptimeText
        showUnavailable -> when {
            !env.hasRoot -> stringResource(R.string.home_env_title_root_required)
            !env.hasModule -> stringResource(R.string.home_env_title_module_missing)
            !env.hasScripts -> stringResource(R.string.home_env_body_scripts_missing)
            else -> stringResource(R.string.home_environment_not_ready)
        }
        isBusy -> stringResource(R.string.home_please_wait)
        else -> stringResource(R.string.home_tap_start_to_enable)
    }
    val animatedStatusColor by animateColorAsState(
        targetValue = statusAccent,
        animationSpec = tween(durationMillis = 360),
        label = "home_hero_status"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 左右双栏：左侧状态 | 右侧配置 ──
            val innerHeight = 110.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── 左侧：核心状态面板 ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(innerHeight)
                        .clip(RoundedRectangle(16.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                        .then(
                            if (statusEditable) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showCoreSheet = true }
                            else Modifier
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 上部：图标 + 状态大字 + 核心名胶囊
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_box_foreground),
                            contentDescription = null,
                            tint = animatedStatusColor,
                            modifier = Modifier.size(52.dp)
                        )
                        Column {
                            Text(
                                text = statusText,
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.Bold,
                                color = animatedStatusColor
                            )
                            Text(
                                text = coreText.ifBlank { stringResource(R.string.home_placeholder_dash) },
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // 底部：运行摘要
                    if (showUnavailable) {
                        Text(
                            text = statusSummary,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        MarqueeText(
                            text = statusSummary,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }

                // ── 右侧：配置信息面板 ──
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(innerHeight)
                        .clip(RoundedRectangle(16.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    HeroInfoRow(
                        label = stringResource(R.string.home_mode_label),
                        value = modeText.ifBlank { stringResource(R.string.home_placeholder_dash) },
                        enabled = statusEditable,
                        onClick = { showModeSheet = true }
                    )
                    HeroInfoRow(
                        label = stringResource(R.string.home_ipv6_label),
                        value = when {
                            ipv6Text.equals("true", ignoreCase = true) -> stringResource(R.string.common_on)
                            ipv6Text.equals("false", ignoreCase = true) -> stringResource(R.string.common_off)
                            else -> stringResource(R.string.home_placeholder_dash)
                        },
                        enabled = statusEditable,
                        onClick = { showIpv6Sheet = true }
                    )
                    HeroInfoRow(
                        label = stringResource(R.string.home_dns_label),
                        value = dnsMode.ifBlank { stringResource(R.string.home_placeholder_dash) }
                    )
                }
            }

            // ── 操作按钮 ──
            val showRunningControls = isRunning || status is ServiceStatus.Stopping || status is ServiceStatus.Restarting
            androidx.compose.animation.AnimatedContent(
                targetState = showRunningControls,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) +
                        slideInVertically(
                            animationSpec = tween(220),
                            initialOffsetY = { it / 6 }
                        )).togetherWith(
                        fadeOut(animationSpec = tween(160))
                    ).using(SizeTransform(clip = false))
                },
                label = "hero_buttons"
            ) { running ->
                if (running) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeroActionButton(
                            text = stringResource(R.string.home_action_stop),
                            tone = if (isBusy) HeroButtonTone.Muted else HeroButtonTone.Danger,
                            enabled = isRunning && isEnvReady,
                            loading = status is ServiceStatus.Stopping,
                            onClick = onStop,
                            modifier = Modifier.weight(1f)
                        )
                        HeroActionButton(
                            text = stringResource(R.string.home_action_reload),
                            tone = if (isBusy) HeroButtonTone.Muted else HeroButtonTone.Warning,
                            enabled = isRunning && isEnvReady,
                            loading = status is ServiceStatus.Restarting,
                            onClick = onReload,
                            modifier = Modifier.weight(1f)
                        )
                        if (canReloadConfig) {
                            HeroActionButton(
                                text = stringResource(R.string.home_action_reload_config),
                                tone = if (isBusy) HeroButtonTone.Muted else HeroButtonTone.Primary,
                                enabled = isRunning && isEnvReady,
                                onClick = {
                                    scope.launch {
                                        val res = BoxApi.reloadConfig()
                                        toastMessage = if (res.exitCode == 0) {
                                            reloadConfigSuccessText
                                        } else {
                                            res.stderr.ifBlank { reloadConfigFailedText }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    HeroActionButton(
                        text = stringResource(R.string.home_action_start),
                        tone = HeroButtonTone.Primary,
                        enabled = isStopped && isEnvReady,
                        loading = status is ServiceStatus.Starting,
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 单行跑马灯文字 — 文字超长时自动滚动，两侧带透明渐隐边缘。
 * 文字不超出时静止显示，无边缘裁切。
 */
@Composable
private fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MiuixTheme.textStyles.footnote2,
    color: Color = MiuixTheme.colorScheme.onSurfaceSecondary,
    fadeEdgeWidth: Dp = 10.dp
) {
    val textMeasurer = rememberTextMeasurer()
    var containerWidth by remember { mutableIntStateOf(0) }
    val measuredWidth = remember(text, style) {
        textMeasurer.measure(text, style = style, maxLines = 1).size.width
    }
    val needsMarquee = containerWidth in 1..<measuredWidth

    Text(
        text = text,
        modifier = modifier
            .onSizeChanged { containerWidth = it.width }
            .then(
                if (needsMarquee) Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fw = fadeEdgeWidth.toPx()
                        if (fw > 0f && size.width > fw * 2f) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    fw / size.width to Color.Black,
                                    1f - fw / size.width to Color.Black,
                                    1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 3000,
                        initialDelayMillis = 2000,
                        velocity = 30.dp
                    )
                else Modifier
            ),
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = if (needsMarquee) TextOverflow.Clip else TextOverflow.Ellipsis
    )
}

@Composable
private fun HeroInfoRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled && onClick != null) {
                    Modifier
                        .clip(RoundedRectangle(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onClick() }
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private enum class HeroButtonTone { Primary, Danger, Warning, Muted }

@Composable
private fun HeroActionButton(
    text: String,
    tone: HeroButtonTone = HeroButtonTone.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    // 柔和按钮色：低饱和度背景 + 同色系深色前景，护眼不刺激
    val (bg, fg) = when (tone) {
        HeroButtonTone.Primary -> {
            if (dark) Color(0xFF1E3A5F) to Color(0xFFA8C8E8)
            else Color(0xFFD6E6F6) to Color(0xFF1A5276)
        }
        HeroButtonTone.Danger -> {
            if (dark) Color(0xFF4A2028) to Color(0xFFE8A0A0)
            else Color(0xFFF5D5D5) to Color(0xFF8B2020)
        }
        HeroButtonTone.Warning -> {
            if (dark) Color(0xFF3D3018) to Color(0xFFD8C090)
            else Color(0xFFF2E6D0) to Color(0xFF7A5A1A)
        }
        HeroButtonTone.Muted -> {
            MiuixTheme.colorScheme.secondaryContainer to MiuixTheme.colorScheme.onSecondaryContainer
        }
    }
    val animatedBg by animateColorAsState(
        targetValue = bg,
        animationSpec = tween(durationMillis = 320),
        label = "hero_btn_bg"
    )
    val animatedFg by animateColorAsState(
        targetValue = fg,
        animationSpec = tween(durationMillis = 320),
        label = "hero_btn_fg"
    )

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        cornerRadius = 14.dp,
        colors = ButtonDefaults.buttonColors(
            color = animatedBg,
            disabledColor = animatedBg.copy(alpha = 0.68f),
            contentColor = animatedFg,
            disabledContentColor = animatedFg.copy(alpha = 0.65f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (loading) {
                top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                text = text,
                style = MiuixTheme.textStyles.button,
                color = animatedFg
            )
        }
    }
}

@Composable
private fun HomeSheetSummary(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun HomeSheetOptionItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    iconUrl: String? = null
) {
    val containerColor = if (selected) {
        MiuixTheme.colorScheme.primaryContainer
    } else {
        MiuixTheme.colorScheme.surfaceContainerHigh
    }
    val titleColor = if (selected) {
        MiuixTheme.colorScheme.onPrimaryContainer
    } else {
        MiuixTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(color = containerColor),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconUrl != null) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = titleColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
