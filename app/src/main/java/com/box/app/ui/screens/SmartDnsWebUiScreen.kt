package com.box.app.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.box.app.data.backend.ShellExecutor
import com.box.app.ui.screens.tools.buildSmartDnsAutoLoginJs
import com.box.app.ui.theme.appColors
import com.box.app.ui.web.ThemedWebView
import com.box.app.utils.SystemBarMode
import com.box.app.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SDNS_CONF = "/data/adb/smartdns/smartdns/smartdns.conf"

/**
 * SmartDNS WebUI 独立二级页面，与 PanelScreen / SubStoreScreen 同级。
 * 参考 PanelScreen 的 TopBar 浮层 + WebView 全屏布局。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SmartDnsWebUiScreen(
    onNavigateBack: () -> Unit,
    backRequestKey: Int = 0,
    onCanGoBackChange: (Boolean) -> Unit = {}
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val scheme = MiuixTheme.colorScheme
    val systemBarSettings by ThemeManager.systemBarSettings.collectAsState()

    // 状态栏 inset + TopBar 高度（与 PanelScreen 一致）
    val topInset = if (systemBarSettings.statusBar == SystemBarMode.OPAQUE) {
        0.dp
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    val topBarHeight = 52.dp

    // ── 状态 ──
    var webUiUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var webUiUser by rememberSaveable { mutableStateOf("root") }
    var webUiPasswd by rememberSaveable { mutableStateOf("root") }
    var loading by rememberSaveable { mutableStateOf(true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var reloadKey by rememberSaveable { mutableStateOf(0) }
    var webSessionKey by rememberSaveable { mutableStateOf(0) }
    var refreshing by rememberSaveable { mutableStateOf(false) }
    var pageTitle by rememberSaveable { mutableStateOf<String?>(null) }

    fun parseConfValue(text: String, key: String): String? {
        val regex = Regex("""^\s*${Regex.escape(key)}\s+(.+)$""", setOf(RegexOption.MULTILINE))
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    // ── 加载配置 ──
    LaunchedEffect(Unit) {
        loading = true
        error = null
        withContext(Dispatchers.IO) {
            val confRes = ShellExecutor.execute("cat $SDNS_CONF 2>/dev/null")
            val ct = confRes.stdout
            if (ct.isBlank()) {
                error = "Cannot read SmartDNS config"
                loading = false
                return@withContext
            }
            parseConfValue(ct, "smartdns-ui.ip")?.let { webUiUrl = it }
            parseConfValue(ct, "smartdns-ui.user")?.let { webUiUser = it }
            parseConfValue(ct, "smartdns-ui.password")?.let { webUiPasswd = it }
            if (webUiUrl.isNullOrBlank()) {
                error = "WebUI not configured in smartdns.conf"
            }
        }
        loading = false
    }

    @Composable
    fun NoRippleIconButton(
        enabled: Boolean = true,
        onClick: () -> Unit,
        content: @Composable () -> Unit
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) { content() }
    }

    // ── 主布局（与 PanelScreen 一致：TopBar 浮层 + WebView 全屏） ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // WebView 内容区（顶部避让 TopBar）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset + topBarHeight)
        ) {
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfiniteProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
                error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error!!, style = MiuixTheme.textStyles.body1, color = scheme.onSurfaceSecondary)
                    }
                }
                webUiUrl != null -> {
                    ThemedWebView(
                        url = webUiUrl!!,
                        isDark = isDark,
                        reloadKey = reloadKey,
                        sessionKey = webSessionKey,
                        backRequestKey = backRequestKey,
                        hideUntilCommitVisible = false,
                        resetHistoryOnUrlChange = true,
                        injectJsOnPageFinished = buildSmartDnsAutoLoginJs(webUiUser, webUiPasswd),
                        onTitleChange = { pageTitle = it },
                        onPageFinished = { refreshing = false },
                        onCanGoBackChange = onCanGoBackChange,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // TopBar 浮层（状态栏 inset + 固定高度，与 PanelScreen 一致）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(topInset + topBarHeight)
                .padding(top = topInset)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .zIndex(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回
            NoRippleIconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = c.textPrimary
                )
            }

            // 标题
            Text(
                text = pageTitle?.takeIf { it.isNotBlank() } ?: "SmartDNS WebUI",
                style = MiuixTheme.textStyles.title2,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // 刷新
            NoRippleIconButton(
                enabled = !loading && !refreshing,
                onClick = {
                    refreshing = true
                    reloadKey += 1
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = if (refreshing) c.textSecondary else c.textPrimary
                )
            }
        }
    }
}
