package com.box.app.ui.screens

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.box.app.BuildConfig
import com.box.app.R
import com.box.app.data.backend.EnvironmentChecker
import com.box.app.utils.Permissions
import com.box.app.utils.rememberPermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })

    val permHelper = rememberPermissionHelper()
    var tosAccepted by rememberSaveable { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // 生命周期感知：从设置页返回时刷新权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnResume by rememberUpdatedState { refreshKey += 1 }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latestOnResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Root 状态
    var hasRoot by remember { mutableStateOf(false) }
    var requestingRoot by remember { mutableStateOf(false) }
    var rootError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        hasRoot = runCatching { EnvironmentChecker.check(forceRefresh = true).hasRoot }.getOrDefault(false)
    }

    // 权限状态（XXPermissions 统一检查）
    val hasNotifications = remember(refreshKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permHelper.isGranted(Permissions.POST_NOTIFICATIONS)
        } else true
    }

    val hasWifi = remember(refreshKey) {
        val loc = permHelper.isGranted(Permissions.ACCESS_FINE_LOCATION)
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permHelper.isGranted(Permissions.NEARBY_WIFI_DEVICES)
        } else true
        loc && nearby
    }

    val hasInstalledApps = remember(refreshKey) {
        permHelper.isGranted(Permissions.GET_INSTALLED_APPS)
    }

    // Root 请求（带重试）
    fun requestRoot() {
        if (requestingRoot) return
        rootError = null
        scope.launch {
            requestingRoot = true
            repeat(2) { attempt ->
                val granted = runCatching { EnvironmentChecker.requestRootAccess() }.getOrDefault(false)
                if (granted) {
                    hasRoot = true
                    requestingRoot = false
                    return@launch
                }
                if (attempt == 0) delay(500)
            }
            hasRoot = runCatching { EnvironmentChecker.check(forceRefresh = true).hasRoot }.getOrDefault(false)
            if (!hasRoot) rootError = context.getString(R.string.onboarding_root_denied)
            requestingRoot = false
        }
    }

    // 通知权限请求
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permHelper.request(Permissions.POST_NOTIFICATIONS) { refreshKey += 1 }
        }
    }

    // 应用列表权限请求（国产 ROM）
    fun requestInstalledApps() {
        permHelper.request(Permissions.GET_INSTALLED_APPS) { result ->
            refreshKey += 1
            if (result.doNotAskAgain && result.denied.isNotEmpty()) {
                permHelper.openSettings(result.denied)
            }
        }
    }

    // Wi-Fi 权限请求
    fun requestWifiPerms() {
        val perms = buildList {
            add(Permissions.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Permissions.NEARBY_WIFI_DEVICES)
            }
        }
        permHelper.request(*perms.toTypedArray()) { result ->
            refreshKey += 1
            if (result.doNotAskAgain && result.denied.isNotEmpty()) {
                permHelper.openSettings(result.denied)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // 顶栏：返回按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (pagerState.currentPage > 0) {
                IconButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 分页内容
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> UsagePage()
                2 -> PermissionsPage(
                    hasRoot = hasRoot,
                    hasNotifications = hasNotifications,
                    hasWifi = hasWifi,
                    hasInstalledApps = hasInstalledApps,
                    requestingRoot = requestingRoot,
                    rootError = rootError,
                    onRequestRoot = ::requestRoot,
                    onRequestNotifications = ::requestNotifications,
                    onRequestWifi = ::requestWifiPerms,
                    onRequestInstalledApps = ::requestInstalledApps
                )
                3 -> AgreementPage(
                    tosAccepted = tosAccepted,
                    onTosChanged = { tosAccepted = it }
                )
            }
        }

        // 底部按钮（官方 Miuix Button，零自定义）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            val isLast = pagerState.currentPage == 3
            val canFinish = tosAccepted && hasRoot
            Button(
                onClick = {
                    if (isLast) onFinish()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                enabled = if (isLast) canFinish else true,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(
                    text = if (isLast) stringResource(R.string.onboarding_finish)
                    else stringResource(R.string.onboarding_continue),
                    style = MiuixTheme.textStyles.button
                )
            }
        }
    }
}

// ── 第1页：欢迎 ──────────────────────────────────────────────────────────

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(SmoothRoundedCornerShape(28.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(R.drawable.ic_box_foreground),
                contentDescription = null,
                modifier = Modifier.size(80.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
}

// ── 第2页：使用说明 ──────────────────────────────────────────────────────

@Composable
private fun UsagePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        PageIcon(Icons.Filled.Info)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_usage_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_usage_body),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_tips_body),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── 第3页：权限申请 ──────────────────────────────────────────────────────

@Composable
private fun PermissionsPage(
    hasRoot: Boolean,
    hasNotifications: Boolean,
    hasWifi: Boolean,
    hasInstalledApps: Boolean,
    requestingRoot: Boolean,
    rootError: String?,
    onRequestRoot: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestWifi: () -> Unit,
    onRequestInstalledApps: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        PageIcon(Icons.Filled.Security)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_permissions_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 必需：Root
        SmallTitle(text = stringResource(R.string.onboarding_required_permissions))
        Card(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = stringResource(R.string.onboarding_perm_root_title),
                summary = when {
                    hasRoot -> stringResource(R.string.onboarding_perm_granted)
                    requestingRoot -> stringResource(R.string.onboarding_perm_requesting)
                    rootError != null -> rootError
                    else -> stringResource(R.string.onboarding_perm_root_summary)
                },
                endActions = {
                    if (hasRoot) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                onClick = if (!hasRoot && !requestingRoot) onRequestRoot else null
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 可选权限
        SmallTitle(text = stringResource(R.string.onboarding_optional_permissions_hint))
        Card(modifier = Modifier.fillMaxWidth()) {
            BasicComponent(
                title = stringResource(R.string.onboarding_perm_notifications_title),
                summary = if (hasNotifications) stringResource(R.string.onboarding_perm_granted)
                else stringResource(R.string.onboarding_perm_notifications_summary),
                endActions = {
                    if (hasNotifications) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                onClick = if (!hasNotifications) onRequestNotifications else null
            )
            BasicComponent(
                title = stringResource(R.string.onboarding_perm_wifi_title),
                summary = if (hasWifi) stringResource(R.string.onboarding_perm_granted)
                else stringResource(R.string.onboarding_perm_wifi_summary),
                endActions = {
                    if (hasWifi) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                onClick = if (!hasWifi) onRequestWifi else null
            )
            BasicComponent(
                title = stringResource(R.string.onboarding_perm_apps_title),
                summary = if (hasInstalledApps) stringResource(R.string.onboarding_perm_granted)
                else stringResource(R.string.onboarding_perm_apps_summary),
                endActions = {
                    if (hasInstalledApps) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                onClick = if (!hasInstalledApps) onRequestInstalledApps else null
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── 第4页：协议与声明 ────────────────────────────────────────────────────

@Composable
private fun AgreementPage(
    tosAccepted: Boolean,
    onTosChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        PageIcon(Icons.Filled.Description)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_agreement_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 协议内容
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_disclaimer_body),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 同意勾选（Miuix CheckboxPreference）
        Card(modifier = Modifier.fillMaxWidth()) {
            CheckboxPreference(
                title = stringResource(R.string.onboarding_tos_accept),
                checked = tosAccepted,
                onCheckedChange = { onTosChanged(it) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── 页面大图标 ───────────────────────────────────────────────────────────

@Composable
private fun PageIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.primary,
        modifier = Modifier.size(72.dp)
    )
}
