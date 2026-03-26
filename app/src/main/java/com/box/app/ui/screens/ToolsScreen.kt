package com.box.app.ui.screens

import androidx.compose.animation.core.tween
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.lazy.LazyListState
import com.box.app.BuildConfig
import com.box.app.ui.components.LocalFloatingNavBarSpaceDp
import androidx.compose.ui.unit.dp
import com.box.app.ui.screens.tools.ToolsRootScreen
import kotlinx.coroutines.launch
import com.box.app.ui.screens.tools.ToolsConfigScreen
import com.box.app.ui.screens.tools.ToolsAppsScreen
import com.box.app.ui.screens.tools.ToolsLogsScreen
import com.box.app.ui.screens.tools.ToolsNetworkControlScreen
import com.box.app.ui.screens.tools.ToolsUpdateSubscriptionScreen
import com.box.app.ui.screens.tools.ToolsUpdateCnipScreen

private enum class ToolsRoute {
    Root,
    ConfigManage,
    ConfigSelect,
    Apps,
    NetworkControl,
    Logs,
    UpdateSubscription,
    UpdateCnip
}

@Composable
fun ToolsScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onMainPagerUserScrollEnabledChange: (Boolean) -> Unit = {},
    onMainTabAtRootChange: (Boolean) -> Unit = {},
    isActive: Boolean = true,
    savedRouteName: String? = null,
    onRouteSaved: (String) -> Unit = {},
    resetToRootRequest: Int = 0,
    onResetToRootRequestConsumed: () -> Unit = {},
    openLogsRequest: Int = 0,
    onOpenLogsRequestConsumed: () -> Unit = {},
    openUpdateSubscriptionRequest: Int = 0,
    onOpenUpdateSubscriptionRequestConsumed: () -> Unit = {},
    onEditorModeChange: (Boolean) -> Unit = {},
    openLogsFromHome: Boolean = false,
    onExitLogsToHome: () -> Unit = {},
    openUpdateSubscriptionFromHome: Boolean = false,
    onExitUpdateSubscriptionToHome: () -> Unit = {}
) {
    var route by rememberSaveable {
        mutableStateOf(savedRouteName?.let { name ->
            runCatching { ToolsRoute.valueOf(name) }.getOrNull() ?: ToolsRoute.Root
        } ?: ToolsRoute.Root)
    }

    LaunchedEffect(resetToRootRequest) {
        if (resetToRootRequest <= 0) return@LaunchedEffect
        route = ToolsRoute.Root
        onResetToRootRequestConsumed()
    }

    LaunchedEffect(savedRouteName) {
        val restored = savedRouteName?.let { name ->
            runCatching { ToolsRoute.valueOf(name) }.getOrNull()
        }
        if (restored != null && restored != route) {
            route = restored
        }
    }

    LaunchedEffect(route) {
        onRouteSaved(route.name)
    }

    LaunchedEffect(isActive, route) {
        if (!isActive) {
            onMainPagerUserScrollEnabledChange(true)
            onMainTabAtRootChange(true)
            return@LaunchedEffect
        }

        val atRoot = route == ToolsRoute.Root
        onMainPagerUserScrollEnabledChange(atRoot)
        onMainTabAtRootChange(atRoot)
        if (!atRoot) {
            onNavVisibilityChange(false)
        }
    }

    val rootListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    LaunchedEffect(openLogsRequest) {
        if (openLogsRequest <= 0) return@LaunchedEffect
        route = ToolsRoute.Logs
        onOpenLogsRequestConsumed()
    }

    LaunchedEffect(openUpdateSubscriptionRequest) {
        if (openUpdateSubscriptionRequest <= 0) return@LaunchedEffect
        route = ToolsRoute.UpdateSubscription
        onOpenUpdateSubscriptionRequestConsumed()
    }

    fun exitToRoot() {
        if (route == ToolsRoute.Logs && openLogsFromHome) {
            onExitLogsToHome()
        } else if (route == ToolsRoute.UpdateSubscription && openUpdateSubscriptionFromHome) {
            onExitUpdateSubscriptionToHome()
        } else {
            route = ToolsRoute.Root
        }
    }

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val transition = remember { androidx.compose.animation.core.Animatable(0f) }
    var lastNonRootRoute by rememberSaveable { mutableStateOf<ToolsRoute?>(null) }

    LaunchedEffect(route) {
        if (route != ToolsRoute.Root) {
            lastNonRootRoute = route
            transition.animateTo(1f, animationSpec = tween(durationMillis = 280))
        } else {
            transition.animateTo(0f, animationSpec = tween(durationMillis = 260))
        }
    }

    if (isActive && route != ToolsRoute.Root) {
        PredictiveBackHandler {
                progress: kotlinx.coroutines.flow.Flow<androidx.activity.BackEventCompat> ->
            try {
                progress.collect { backEvent ->
                    transition.snapTo((1f - backEvent.progress).coerceIn(0f, 1f))
                }
                exitToRoot()
            } catch (e: kotlinx.coroutines.CancellationException) {
                scope.launch {
                    transition.animateTo(1f, animationSpec = tween(durationMillis = 220))
                }
                throw e
            }
        }
    }

    val activeRoute = when {
        route != ToolsRoute.Root -> route
        transition.value > 0f -> lastNonRootRoute
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size -> containerWidthPx = size.width.toFloat() }
    ) {
        val w = containerWidthPx
        val t = transition.value

        val mainX = if (w > 0f) (-w / 3f) * t else 0f
        val subX = if (w > 0f) w * (1f - t) else 0f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = mainX
                    alpha = 1f
                }
        ) {
            ToolsRootScreen(
                onNavVisibilityChange = onNavVisibilityChange,
                listState = rootListState,
                onOpenConfigManage = { route = ToolsRoute.ConfigManage },
                onOpenConfigSelect = { route = ToolsRoute.ConfigSelect },
                onOpenApps = { route = ToolsRoute.Apps },
                onOpenNetworkControl = { route = ToolsRoute.NetworkControl },
                onOpenLogs = { route = ToolsRoute.Logs },
                onOpenUpdateSubscription = { route = ToolsRoute.UpdateSubscription },
                onOpenUpdateCnip = {
                    if (BuildConfig.FLAVOR != "bfr") {
                        route = ToolsRoute.UpdateCnip
                    }
                }
            )
        }

        if (activeRoute != null && (t > 0f || route != ToolsRoute.Root)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = subX
                        alpha = t
                    }
            ) {
                CompositionLocalProvider(LocalFloatingNavBarSpaceDp provides 0.dp) {
                    when (activeRoute) {
                        ToolsRoute.ConfigManage -> ToolsConfigScreen(
                            onNavVisibilityChange = onNavVisibilityChange,
                            initialTab = com.box.app.ui.screens.tools.ConfigHubTab.Manage,
                            onBack = { exitToRoot() },
                            onEditorModeChange = onEditorModeChange,
                            enableBackHandler = false
                        )

                        ToolsRoute.ConfigSelect -> ToolsConfigScreen(
                            onNavVisibilityChange = onNavVisibilityChange,
                            initialTab = com.box.app.ui.screens.tools.ConfigHubTab.Select,
                            onBack = { exitToRoot() },
                            onEditorModeChange = onEditorModeChange,
                            enableBackHandler = false
                        )

                        ToolsRoute.Apps -> ToolsAppsScreen(
                            onNavVisibilityChange = onNavVisibilityChange,
                            onBack = { exitToRoot() }
                        )

                        ToolsRoute.NetworkControl -> ToolsNetworkControlScreen(
                            onNavVisibilityChange = onNavVisibilityChange,
                            onBack = { exitToRoot() }
                        )

                        ToolsRoute.Logs -> ToolsLogsScreen(
                            onNavVisibilityChange = onNavVisibilityChange,
                            onBack = { exitToRoot() }
                        )

                        ToolsRoute.UpdateSubscription -> ToolsUpdateSubscriptionScreen(
                            onNavVisibilityChange = onNavVisibilityChange,
                            onBack = { exitToRoot() }
                        )

                        ToolsRoute.UpdateCnip -> {
                            if (BuildConfig.FLAVOR == "bfr") {
                                ToolsRootScreen(
                                    onNavVisibilityChange = onNavVisibilityChange,
                                    listState = rootListState,
                                    onOpenConfigManage = { route = ToolsRoute.ConfigManage },
                                    onOpenConfigSelect = { route = ToolsRoute.ConfigSelect },
                                    onOpenApps = { route = ToolsRoute.Apps },
                                    onOpenNetworkControl = { route = ToolsRoute.NetworkControl },
                                    onOpenLogs = { route = ToolsRoute.Logs },
                                    onOpenUpdateSubscription = { route = ToolsRoute.UpdateSubscription },
                                    onOpenUpdateCnip = { }
                                )
                            } else {
                                ToolsUpdateCnipScreen(
                                    onNavVisibilityChange = onNavVisibilityChange,
                                    onBack = { exitToRoot() }
                                )
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }
    }
}