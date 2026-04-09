package com.box.app.ui.screens.Settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.components.LiquidGlassButton
import com.box.app.ui.components.LocalLiquidBackdrop
import com.box.app.ui.components.ToolsRowIcon
import com.box.app.ui.components.ToolsSectionCard
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.theme.appColors
import com.mikepenz.aboutlibraries.Libs
import com.box.app.utils.ThemeManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
    onNavVisibilityChange: (Boolean) -> Unit,
    enableBackHandler: Boolean = true
) {
    val c = appColors()
    val context = LocalContext.current

    val pagePadding = 16.dp
    val liquidBackdrop = rememberLayerBackdrop()

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    var topBarHeightPx by rememberSaveable { mutableStateOf(0) }
    val density = LocalDensity.current

    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val defaultTopBarHeight = 8.dp + 40.dp
    var lastNonZeroTopBarHeightPx by rememberSaveable {
        mutableStateOf(with(density) { (defaultTopBarHeight + statusBarTopInset).roundToPx() })
    }

    val effectiveTopBarHeightPx = if (topBarHeightPx > 0) topBarHeightPx else lastNonZeroTopBarHeightPx
    val measuredTopBarHeight = with(density) { effectiveTopBarHeightPx.toDp() }
    val topInset = measuredTopBarHeight + 16.dp

    androidx.compose.runtime.LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        androidx.compose.runtime.snapshotFlow {
            listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { now ->
                if (now > last) {
                    onNavVisibilityChange(false)
                } else if (now < last) {
                    onNavVisibilityChange(true)
                }
                last = now
            }
    }

    if (enableBackHandler) {
        BackHandler {
            onBack()
        }
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
                    interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }

    val loadResult by produceState<Pair<Libs?, String>>(initialValue = null to "") {
        value = try {
            val candidateNames = listOf(
                "aboutlibraries",
                "aboutlibraries_debug",
                "aboutlibraries_release"
            )
            var usedName: String? = null
            val resId = candidateNames
                .asSequence()
                .map { name ->
                    val id = context.resources.getIdentifier(name, "raw", context.packageName)
                    if (id != 0 && usedName == null) usedName = name
                    id
                }
                .firstOrNull { it != 0 }
                ?: 0
            if (resId == 0) {
                null to "raw not found. tried=${candidateNames.joinToString()}"
            } else {
                val json = context.resources.openRawResource(resId)
                    .bufferedReader()
                    .use { it.readText() }
                val libs = Libs.Builder().withJson(json).build()
                libs to "raw=${usedName ?: "?"} id=$resId jsonLen=${json.length}"
            }
        } catch (e: Exception) {
            null to "load failed: ${e::class.java.simpleName}"
        }
    }

    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            
            .imePadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(liquidBackdrop),
            contentPadding = contentPaddingWithNavBars(
                start = pagePadding,
                end = pagePadding,
                top = topInset
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val libs = loadResult.first
            val items = libs?.libraries?.sortedBy { it.name.lowercase() }.orEmpty()

            item {
                ToolsSectionCard(
                    title = stringResource(R.string.settings_open_source_licenses),
                    subtitle = stringResource(R.string.settings_open_source_licenses_subtitle),
                    content = {
                        if (libs == null) {
                            Text(
                                text = loadResult.second,
                                style = MiuixTheme.textStyles.footnote1,
                                color = c.textSecondary
                            )
                        }
                    }
                )
            }

            items(items) { lib ->
                ToolsSectionCard(
                    title = lib.name,
                    subtitle = lib.uniqueId,
                    content = {
                        val url = lib.website
                            ?: lib.licenses.firstOrNull()?.url

                        ToolsRowIcon(
                            icon = Icons.Filled.Description,
                            title = lib.licenses.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                                ?: (url ?: lib.uniqueId),
                            subtitle = url ?: stringResource(R.string.settings_open_source_licenses_subtitle),
                            showDivider = false,
                            onClick = {
                                val u = url
                                if (!u.isNullOrBlank()) {
                                    uriHandler.openUri(u)
                                }
                            }
                        )
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
            LicensesFloatingTopBar(
                onBack = onBack,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned {
                        val h = it.size.height
                        if (h > 0) {
                            topBarHeightPx = h
                            lastNonZeroTopBarHeightPx = h
                        }
                    }
            )
        }
    }
}

@Composable
private fun LicensesFloatingTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val backdrop = requireNotNull(LocalLiquidBackdrop.current)
    val isDark = ThemeManager.shouldUseDarkTheme()

    val selectedTint = if (isDark) Color(0xFF2B2F37) else Color(0xFFE3E6EA)
    val tint = selectedTint.copy(alpha = 0.25f)

    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, top = 8.dp, end = 20.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassButton(
                onClick = onBack,
                backdrop = backdrop,
                surfaceColor = tint
            ) {
                Text(
                    text = stringResource(R.string.tools_update_back_compact),
                    color = c.textPrimary,
                    style = MiuixTheme.textStyles.button,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
