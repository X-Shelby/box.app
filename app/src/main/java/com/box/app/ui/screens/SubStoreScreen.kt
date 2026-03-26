package com.box.app.ui.screens

import android.webkit.URLUtil
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.semantics.clearAndSetSemantics

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.box.app.R

import com.box.app.data.backend.BoxApi
import com.box.app.ui.components.bottomsheets.AppModalBottomSheet
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.appErrorColor
import com.box.app.ui.web.clearWebViewAppData
import com.box.app.ui.web.ThemedWebView
import com.box.app.utils.SystemBarMode
import com.box.app.utils.ThemeManager
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubStoreScreen(
    onNavigateBack: () -> Unit,
    backRequestKey: Int = 0,
    onCanGoBackChange: (Boolean) -> Unit = {}
) {
    val c = appColors()
    val accent = appAccentColor()
    val danger = appErrorColor()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val systemBarSettings by ThemeManager.systemBarSettings.collectAsState()

    val portNotFoundText = stringResource(R.string.substore_port_not_found)

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
        ) {
            content()
        }
    }

    var subStoreUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var subStoreLoading by rememberSaveable { mutableStateOf(false) }
    var subStoreError by rememberSaveable { mutableStateOf<String?>(null) }
    var subStoreReloadKey by rememberSaveable { mutableStateOf(0) }
    var subStoreWebSessionKey by rememberSaveable { mutableStateOf(0) }
    var subStoreFetchKey by rememberSaveable { mutableStateOf(0) }
    var subStoreWebError by rememberSaveable { mutableStateOf<String?>(null) }
    var pageTitle by rememberSaveable { mutableStateOf<String?>(null) }

    var refreshing by rememberSaveable { mutableStateOf(false) }

    val prefs = remember(context) {
        context.getSharedPreferences("substore_panels", android.content.Context.MODE_PRIVATE)
    }
    val panelListKey = "substore_panel_list_v1"
    val selectedPanelIdKey = "substore_selected_id_v1"
    val builtInPanelId = "local"

    data class PanelEntry(
        val id: String,
        val name: String,
        val url: String?,
        val isBuiltIn: Boolean
    )

    fun parseCustomPanels(raw: String?): List<PanelEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val name = obj.optString("name").trim()
                    val url = obj.optString("url").trim().ifBlank { null }
                    if (id.isNotBlank() && name.isNotBlank() && url != null) {
                        add(PanelEntry(id = id, name = name, url = url, isBuiltIn = false))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun persistCustomPanels(custom: List<PanelEntry>) {
        val arr = JSONArray()
        custom.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("url", p.url)
            arr.put(obj)
        }
        prefs.edit().putString(panelListKey, arr.toString()).apply()
    }

    val builtInPanel = remember {
        PanelEntry(id = builtInPanelId, name = context.getString(R.string.substore_panel_local_name), url = null, isBuiltIn = true)
    }

    val initialCustomPanels = remember(prefs) {
        parseCustomPanels(prefs.getString(panelListKey, null))
    }
    val initialSelectedPanelId = remember(prefs, selectedPanelIdKey, builtInPanelId) {
        prefs.getString(selectedPanelIdKey, null)?.takeIf { it.isNotBlank() } ?: builtInPanelId
    }
    val initialSubStoreUrl = remember(initialCustomPanels, initialSelectedPanelId, builtInPanelId) {
        if (initialSelectedPanelId == builtInPanelId) {
            null
        } else {
            initialCustomPanels.firstOrNull { it.id == initialSelectedPanelId }?.url
        }
    }

    var customPanels by remember { mutableStateOf(initialCustomPanels) }
    var selectedPanelId by rememberSaveable { mutableStateOf(initialSelectedPanelId) }
    var showPanelSheet by rememberSaveable { mutableStateOf(false) }

    var newPanelName by rememberSaveable { mutableStateOf("") }
    var newPanelUrl by rememberSaveable { mutableStateOf("") }
    var addError by rememberSaveable { mutableStateOf<String?>(null) }

    val panels = remember(customPanels) { listOf(builtInPanel) + customPanels }
    val selectedPanel = remember(panels, selectedPanelId) {
        panels.firstOrNull { it.id == selectedPanelId } ?: builtInPanel
    }

    LaunchedEffect(Unit) {
        // Restore initial URL eagerly for custom panels so WebView can load immediately.
        if (subStoreUrl.isNullOrBlank() && initialSelectedPanelId != builtInPanelId) {
            subStoreUrl = initialSubStoreUrl
            subStoreReloadKey += 1
        }
    }

    fun applySelectedPanel(panel: PanelEntry) {
        val panelChanged = selectedPanelId != panel.id
        selectedPanelId = panel.id
        prefs.edit().putString(selectedPanelIdKey, panel.id).apply()

        subStoreWebError = null
        pageTitle = null
        subStoreError = null
        onCanGoBackChange(false)
        if (panelChanged) {
            subStoreWebSessionKey += 1
        }

        if (panel.id == builtInPanelId) {
            subStoreUrl = null
            subStoreFetchKey += 1
        } else {
            subStoreUrl = panel.url
            subStoreReloadKey += 1
        }
    }

    fun isValidHttpUrl(url: String): Boolean {
        val u = url.trim()
        if (!URLUtil.isValidUrl(u)) return false
        return u.startsWith("http://") || u.startsWith("https://")
    }

    fun addCustomPanel(name: String, url: String) {
        val n = name.trim()
        val u = url.trim()
        if (n.isBlank()) {
            addError = context.getString(R.string.substore_panel_error_name_required)
            return
        }
        if (!isValidHttpUrl(u)) {
            addError = context.getString(R.string.substore_panel_error_url_invalid)
            return
        }

        val id = java.util.UUID.randomUUID().toString()
        val next = customPanels + PanelEntry(id = id, name = n, url = u, isBuiltIn = false)
        customPanels = next
        persistCustomPanels(next)

        newPanelName = ""
        newPanelUrl = ""
        addError = null
        applySelectedPanel(next.last())
        showPanelSheet = false
    }

    var clearingWebData by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            containerColor = c.card,
            tonalElevation = 0.dp,
            title = {
                Text(
                    text = stringResource(R.string.web_action_clear_cache),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = c.textPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.web_action_clear_cache_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (clearingWebData) return@TextButton
                        showClearCacheDialog = false
                        clearingWebData = true
                        scope.launch {
                            val ok = clearWebViewAppData(context)
                            Toast
                                .makeText(
                                    context,
                                    context.getString(
                                        if (ok) R.string.web_action_cache_cleared else R.string.web_action_cache_clear_failed
                                    ),
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                            if (ok) {
                                subStoreWebError = null
                                subStoreReloadKey += 1
                            }
                            clearingWebData = false
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.web_action_clear_cache_confirm), color = danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel), color = c.textPrimary)
                }
            }
        )
    }

    val topInset = if (systemBarSettings.statusBar == SystemBarMode.OPAQUE) {
        0.dp
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    val topBarHeight = 52.dp
    val topPadding = topInset + topBarHeight

    // SubStore does NOT depend on Box service state; always try to load.
    LaunchedEffect(subStoreFetchKey) {
        if (selectedPanelId != builtInPanelId) return@LaunchedEffect
        subStoreLoading = true
        subStoreError = null

        if (subStoreUrl.isNullOrBlank()) {
            subStoreUrl = null
            subStoreWebError = null
            pageTitle = null
        }
        val url = if (selectedPanel.id == builtInPanelId) {
            var resolved: String? = null
            repeat(3) { attempt ->
                resolved = runCatching { BoxApi.getSubStoreUrl() }.getOrNull()?.takeIf { it.isNotBlank() }
                if (!resolved.isNullOrBlank()) return@repeat
                if (attempt != 2) delay(250)
            }
            resolved
        } else {
            selectedPanel.url
        }
        if (url.isNullOrBlank()) {
            subStoreError = portNotFoundText
        } else {
            subStoreUrl = url
        }
        subStoreLoading = false
    }

    LaunchedEffect(Unit) {
        // Built-in local panel: resolve on first open.
        if (initialSelectedPanelId == builtInPanelId) {
            subStoreFetchKey += 1
        }
    }

    val hasWebContent = !subStoreLoading && subStoreError.isNullOrBlank() && !subStoreUrl.isNullOrBlank()
    LaunchedEffect(hasWebContent) {
        if (!hasWebContent) {
            onCanGoBackChange(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.pageBg)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Background content (WebView / states)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding)
        ) {
            when {
                subStoreLoading -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = c.textPrimary,
                            strokeWidth = 4.dp
                        )
                        Text(
                            text = stringResource(R.string.substore_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.textSecondary
                        )
                    }
                }

                !subStoreError.isNullOrBlank() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = subStoreError!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.textSecondary
                        )
                        TextButton(
                            onClick = { subStoreFetchKey += 1 }
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }

                subStoreUrl.isNullOrBlank() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.substore_unavailable),
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.textSecondary
                        )
                        TextButton(
                            onClick = { subStoreFetchKey += 1 }
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }

                else -> {
                    ThemedWebView(
                        url = subStoreUrl!!,
                        isDark = isDark,
                        reloadKey = subStoreReloadKey,
                        sessionKey = subStoreWebSessionKey,
                        backRequestKey = backRequestKey,
                        hideUntilCommitVisible = false,
                        resetHistoryOnUrlChange = true,
                        onTitleChange = { pageTitle = it },
                        onPageFinished = { refreshing = false },
                        onWebError = { subStoreWebError = it },
                        onCanGoBackChange = onCanGoBackChange,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Show web error overlay if present
            if (!subStoreWebError.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(
                            color = c.cardAlt,
                            shape = RoundedRectangle(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = subStoreWebError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            }
        }

        // Top bar (same behavior as Panel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.pageBg)
                .height(topInset + topBarHeight)
                .padding(top = topInset, start = 0.dp, end = 0.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .zIndex(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NoRippleIconButton(
                onClick = onNavigateBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.action_back),
                    tint = c.textPrimary
                )
            }

            Text(
                text = pageTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.substore_title),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            NoRippleIconButton(
                onClick = {
                    subStoreWebError = null
                    refreshing = true

                    if (subStoreUrl.isNullOrBlank() || !subStoreError.isNullOrBlank()) {
                        subStoreFetchKey += 1
                        refreshing = false
                    } else {
                        subStoreReloadKey += 1
                    }
                },
                enabled = !subStoreLoading && !refreshing
            ) {
                if (subStoreLoading || refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = c.textSecondary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_refresh),
                        tint = c.textPrimary
                    )
                }
            }

            NoRippleIconButton(
                onClick = {
                    addError = null
                    showPanelSheet = true
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Widgets,
                    contentDescription = null,
                    tint = c.textPrimary
                )
            }

            NoRippleIconButton(
                onClick = {
                    if (clearingWebData) return@NoRippleIconButton
                    showClearCacheDialog = true
                },
                enabled = !subStoreLoading && !refreshing && !clearingWebData
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.web_action_clear_cache),
                    tint = c.textPrimary
                )
            }
        }

        if (showPanelSheet) {
            AppModalBottomSheet(
                onDismissRequest = { showPanelSheet = false }
            ) {
                @Composable
                fun SheetHandle() {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clearAndSetSemantics { }
                                .size(width = 28.dp, height = 3.dp)
                                .clip(Capsule())
                                .background(c.divider.copy(alpha = 0.42f))
                        )
                    }
                }

                @Composable
                fun DividerLine() {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(c.divider)
                    )
                }

                val sheetScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(sheetScrollState)
                        .padding(horizontal = 16.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SheetHandle()

                    Text(
                        text = stringResource(R.string.substore_panel_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.cardAlt, shape = RoundedRectangle(16.dp))
                    ) {
                        panels.forEachIndexed { index, p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        applySelectedPanel(p)
                                        showPanelSheet = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(c.card, RoundedRectangle(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (p.isBuiltIn) Icons.Filled.Home else Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = c.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = p.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = c.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val displayUrl = if (p.isBuiltIn) {
                                        subStoreUrl
                                    } else {
                                        p.url
                                    }
                                    if (!displayUrl.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = displayUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = c.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (p.id == selectedPanel.id) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Spacer(modifier = Modifier.width(24.dp))
                                }

                                if (!p.isBuiltIn) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                val next = customPanels.filterNot { it.id == p.id }
                                                customPanels = next
                                                persistCustomPanels(next)

                                                if (selectedPanelId == p.id) {
                                                    applySelectedPanel(builtInPanel)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (index != panels.lastIndex) {
                                DividerLine()
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.cardAlt, shape = RoundedRectangle(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.substore_panel_sheet_add_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = c.textPrimary
                        )

                        if (!addError.isNullOrBlank()) {
                            Text(
                                text = addError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Text(
                            text = stringResource(R.string.substore_panel_sheet_field_name),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = c.card,
                                    shape = RoundedRectangle(12.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = newPanelName,
                                onValueChange = {
                                    newPanelName = it
                                    addError = null
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = c.textPrimary,
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (newPanelName.isBlank()) {
                                Text(
                                    text = stringResource(R.string.substore_panel_sheet_field_name),
                                    color = c.textSecondary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.substore_panel_sheet_field_url),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = c.card,
                                    shape = RoundedRectangle(12.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = newPanelUrl,
                                onValueChange = {
                                    newPanelUrl = it
                                    addError = null
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = c.textPrimary,
                                    fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (newPanelUrl.isBlank()) {
                                Text(
                                    text = stringResource(R.string.substore_panel_sheet_field_url),
                                    color = c.textSecondary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { addCustomPanel(newPanelName, newPanelUrl) }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_add),
                                    color = accent,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                }
            }
        }
    }
}
