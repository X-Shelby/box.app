package com.box.app.ui.screens.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import com.kyant.shapes.Capsule
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.box.app.R
import com.box.app.data.repo.ConfigRepository
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.LiquidGlassButton
import com.box.app.ui.components.LiquidGlassIconButton
import com.box.app.ui.components.LiquidGlassTextFieldPill
import com.box.app.ui.components.LocalLiquidBackdrop
import com.box.app.ui.components.systemNavBarPadding
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.AppColors
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appErrorColor
import com.box.app.utils.ThemeManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.launch

@Composable
fun ConfigEditorScreen(
    filePath: String,
    onBack: () -> Unit
) {
    val c = appColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val accent = appAccentColor()
    val danger = appErrorColor()
    val accentArgb = accent.toArgb()

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    ErrorToast(
        message = error,
        onConsumed = { error = null }
    )

    var original by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val wordWrapPrefs = remember {
        context.getSharedPreferences("config_editor_prefs", android.content.Context.MODE_PRIVATE)
    }
    val initialWordWrap = remember { wordWrapPrefs.getBoolean("word_wrap", true) }
    var wordWrap by remember { mutableStateOf(initialWordWrap) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedMatchIndex by remember { mutableStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }

    var lastNonBlankQuery by remember { mutableStateOf<String?>(null) }

    var showConfirmBack by remember { mutableStateOf(false) }

    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }

    var applyTextToken by remember { mutableStateOf(0) }

    val liquidBackdrop = rememberLayerBackdrop()

    val topControlsInset =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
            56.dp + if (searchExpanded) 52.dp else 0.dp

    LaunchedEffect(wordWrap) {
        wordWrapPrefs.edit().putBoolean("word_wrap", wordWrap).apply()
    }

    fun hasChanges(): Boolean = text != original

    fun handleBack() {
        if (hasChanges()) {
            showConfirmBack = true
        } else {
            onBack()
        }
    }

    val matches by remember(text, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim()
            if (q.isEmpty()) return@derivedStateOf emptyList<IntRange>()
            val hay = text
            val needle = q
            val out = ArrayList<IntRange>()
            var start = 0
            while (start <= hay.length) {
                val idx = hay.indexOf(needle, startIndex = start, ignoreCase = true)
                if (idx < 0) break
                out.add(idx until (idx + needle.length))
                start = idx + needle.length
            }
            out
        }
    }

    fun indexToLineCol(source: String, index: Int): Pair<Int, Int> {
        var line = 0
        var col = 0
        val end = index.coerceIn(0, source.length)
        for (i in 0 until end) {
            val ch = source[i]
            if (ch == '\n') {
                line += 1
                col = 0
            } else {
                col += 1
            }
        }
        return line to col
    }

    fun jumpToMatch(targetIndex: Int) {
        val ed = editorRef ?: return
        if (matches.isEmpty()) return
        val safeIndex = targetIndex.coerceIn(0, matches.lastIndex)
        val range = matches[safeIndex]
        val startIndex = range.first
        val endExclusive = (range.last + 1).coerceIn(0, text.length)
        val (startLine, startCol) = indexToLineCol(text, startIndex)
        val (endLine, endCol) = indexToLineCol(text, endExclusive)
        runCatching { ed.setSelectionRegion(startLine, startCol, endLine, endCol, SelectionChangeEvent.CAUSE_SEARCH) }
    }

    fun scrollToMatch(targetIndex: Int) {
        val ed = editorRef ?: return
        if (matches.isEmpty()) return
        val safeIndex = targetIndex.coerceIn(0, matches.lastIndex)
        val range = matches[safeIndex]
        val startIndex = range.first
        val (startLine, startCol) = indexToLineCol(text, startIndex)

        // Try to scroll without affecting cursor/selection.
        // Prefer CodeEditor.ensurePositionVisible(line, column) when available.
        val ensured = runCatching {
            val m = ed.javaClass.methods.firstOrNull {
                it.name == "ensurePositionVisible" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    it.parameterTypes[1] == Int::class.javaPrimitiveType
            }
            if (m != null) {
                m.isAccessible = true
                m.invoke(ed, startLine, startCol)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        // If we can't scroll without affecting cursor/selection, do nothing.
        // Search highlight is still handled by editor.searcher.search(...)
    }

    BackHandler {
        handleBack()
    }

    LaunchedEffect(isDark) {
        editorRef?.let { ed ->
            applyTextMate(ed, filePath, isDark, c, accentArgb)
        }
    }

    LaunchedEffect(filePath) {
        loading = true
        error = null
        ConfigRepository.warmUpShell()
        val res = ConfigRepository.readFile(filePath)
        if (res.error != null) {
            error = res.error
            loading = false
        } else {
            val content = res.data.orEmpty()
            original = content
            text = content
            applyTextToken += 1
            loading = false
        }
    }

    if (showConfirmBack) {
        AlertDialog(
            onDismissRequest = { showConfirmBack = false },
            containerColor = c.card,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = { Text(text = stringResource(R.string.config_editor_discard_changes_title)) },
            text = { Text(text = stringResource(R.string.config_editor_discard_changes_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmBack = false
                        onBack()
                    }
                ) {
                    Text(text = stringResource(R.string.config_editor_discard), color = danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmBack = false }) {
                    Text(text = stringResource(R.string.action_cancel), color = c.textPrimary)
                }
            }
        )
    }

    LaunchedEffect(matches.size) {
        if (matches.isEmpty()) {
            selectedMatchIndex = 0
        } else {
            selectedMatchIndex = selectedMatchIndex.coerceIn(0, matches.lastIndex)
        }
    }

    LaunchedEffect(searchQuery, matches.size, editorRef) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            lastNonBlankQuery = null
            return@LaunchedEffect
        }
        if (matches.isEmpty()) return@LaunchedEffect

        if (lastNonBlankQuery != q) {
            lastNonBlankQuery = q
            selectedMatchIndex = 0
            scrollToMatch(0)
        }
    }

    fun applySearch() {
        val ed = editorRef ?: return
        val q = searchQuery
        if (q.isBlank()) {
            runCatching { ed.searcher.stopSearch() }
        } else {
            val options = EditorSearcher.SearchOptions(true, true)
            runCatching { ed.searcher.search(q, options) }
        }
    }

    fun clearSearch() {
        searchQuery = ""
        selectedMatchIndex = 0
        lastNonBlankQuery = null
        runCatching { editorRef?.searcher?.stopSearch() }
    }

    LaunchedEffect(editorRef, searchQuery, text) {
        applySearch()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topControlsInset)
                .systemNavBarPadding()
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    var lastAppliedToken by remember { mutableStateOf(-1) }
                    AndroidView(
                        factory = { ctx ->
                            CodeEditor(ctx).also { editor ->
                                editorRef = editor
                                editor.setWordwrap(wordWrap)
                                editor.setText(text)
                                lastAppliedToken = applyTextToken
                                applyTextMate(editor, filePath, isDark, c, accentArgb)
                                editor.subscribeAlways(io.github.rosemoe.sora.event.ContentChangeEvent::class.java) {
                                    val cur = editor.text.toString()
                                    if (cur != text) {
                                        text = cur
                                    }
                                }
                            }
                        },
                        update = { editor ->
                            editorRef = editor
                            editor.setWordwrap(wordWrap)
                            if (!loading && lastAppliedToken != applyTextToken) {
                                editor.setText(text)
                                lastAppliedToken = applyTextToken
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(liquidBackdrop)
                    )

                    if (loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                ,
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = accent)
                                Text(
                                    text = "Loading file...",
                                    style = MiuixTheme.textStyles.body2,
                                    color = c.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, top = 8.dp, end = 20.dp)
                .fillMaxWidth()
        ) {
            val selectedTint = if (isDark) Color(0xFF2B2F37) else Color(0xFFE3E6EA)
            val tint = selectedTint.copy(alpha = if (isDark) 0.22f else 0.30f)

            CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidGlassButton(
                            onClick = { handleBack() },
                            backdrop = liquidBackdrop,
                            surfaceColor = tint
                        ) {
                            Text(
                                text = stringResource(R.string.tools_config_back_compact),
                                color = c.textPrimary,
                                style = MiuixTheme.textStyles.button,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LiquidGlassIconButton(
                                onClick = {
                                    searchExpanded = !searchExpanded
                                    if (!searchExpanded) {
                                        clearSearch()
                                    }
                                },
                                backdrop = liquidBackdrop,
                                surfaceColor = tint
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.config_editor_search_hint),
                                    tint = if (searchExpanded) accent else c.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            LiquidGlassIconButton(
                                onClick = { wordWrap = !wordWrap },
                                backdrop = liquidBackdrop,
                                surfaceColor = tint
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.WrapText,
                                    contentDescription = "Toggle wrap",
                                    tint = if (wordWrap) accent else c.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            val saveEnabled = hasChanges() && !saving && !loading
                            LiquidGlassIconButton(
                                onClick = {
                                    if (saving || loading || !hasChanges()) return@LiquidGlassIconButton
                                    saving = true
                                    scope.launch {
                                        val saveRes = ConfigRepository.writeFile(filePath, text)
                                        if (saveRes.error != null) {
                                            error = saveRes.error
                                        } else {
                                            original = text
                                        }
                                        saving = false
                                    }
                                },
                                enabled = saveEnabled,
                                backdrop = liquidBackdrop,
                                surfaceColor = tint
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Save,
                                    contentDescription = "Save",
                                    tint = if (saveEnabled) c.textPrimary else c.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    LaunchedEffect(searchExpanded) {
                        if (searchExpanded) {
                            kotlinx.coroutines.delay(80)
                            runCatching { focusRequester.requestFocus() }
                            keyboardController?.show()
                        } else {
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = searchExpanded,
                        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 140)) +
                            androidx.compose.animation.expandVertically(animationSpec = androidx.compose.animation.core.tween(durationMillis = 180)),
                        exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 110)) +
                            androidx.compose.animation.shrinkVertically(animationSpec = androidx.compose.animation.core.tween(durationMillis = 160))
                    ) {
                        val counter = remember(searchQuery, matches, selectedMatchIndex) {
                            if (searchQuery.isBlank()) null
                            else if (matches.isEmpty()) "0/0"
                            else "${selectedMatchIndex + 1}/${matches.size}"
                        }

                        val selectionColors = remember(accent) {
                            TextSelectionColors(
                                handleColor = accent,
                                backgroundColor = accent.copy(alpha = 0.28f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                                LiquidGlassTextFieldPill(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    backdrop = liquidBackdrop,
                                    surfaceColor = tint
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 14.dp)
                                    ) {
                                        if (searchQuery.isBlank()) {
                                            Text(
                                                text = stringResource(R.string.config_editor_search_hint),
                                                style = MiuixTheme.textStyles.button,
                                                color = c.textSecondary,
                                                modifier = Modifier.align(Alignment.CenterStart)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BasicTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                singleLine = true,
                                                cursorBrush = SolidColor(accent),
                                                textStyle = TextStyle(
                                                    color = c.textPrimary,
                                                    fontSize = MiuixTheme.textStyles.button.fontSize,
                                                    fontWeight = MiuixTheme.textStyles.button.fontWeight
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .focusRequester(focusRequester)
                                            )

                                            androidx.compose.animation.AnimatedVisibility(
                                                visible = searchQuery.isNotBlank(),
                                                enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 90)),
                                                exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 90))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(Capsule())
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null,
                                                            onClick = {
                                                                clearSearch()
                                                                runCatching { focusRequester.requestFocus() }
                                                                keyboardController?.show()
                                                            }
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Close,
                                                        contentDescription = null,
                                                        tint = c.textSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            if (!counter.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = counter,
                                                    style = MiuixTheme.textStyles.footnote1,
                                                    color = c.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            LiquidGlassIconButton(
                                onClick = {
                                    if (searchQuery.isBlank()) return@LiquidGlassIconButton
                                    if (matches.isEmpty()) return@LiquidGlassIconButton
                                    selectedMatchIndex = if (selectedMatchIndex - 1 < 0) matches.lastIndex else selectedMatchIndex - 1
                                    jumpToMatch(selectedMatchIndex)
                                },
                                enabled = searchQuery.isNotBlank(),
                                backdrop = liquidBackdrop,
                                surfaceColor = tint
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Previous match",
                                    tint = if (searchQuery.isNotBlank()) c.textPrimary else c.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            LiquidGlassIconButton(
                                onClick = {
                                    if (searchQuery.isBlank()) return@LiquidGlassIconButton
                                    if (matches.isEmpty()) return@LiquidGlassIconButton
                                    selectedMatchIndex = (selectedMatchIndex + 1) % matches.size
                                    jumpToMatch(selectedMatchIndex)
                                },
                                enabled = searchQuery.isNotBlank(),
                                backdrop = liquidBackdrop,
                                surfaceColor = tint
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Next match",
                                    tint = if (searchQuery.isNotBlank()) c.textPrimary else c.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    selected: Boolean,
    selectedTint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val c = appColors()
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .background(c.cardAlt, Capsule())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        val tint = when {
            !enabled -> c.textSecondary
            selected && selectedTint != Color.Unspecified -> selectedTint
            else -> c.textPrimary
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    cursorColor: Color,
    trailingText: String?,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val selectionColors = remember(cursorColor) {
        TextSelectionColors(
            handleColor = cursorColor,
            backgroundColor = cursorColor.copy(alpha = 0.28f)
        )
    }
    Box(
        modifier = modifier
            .background(c.cardAlt, Capsule())
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = c.textSecondary,
                        style = MiuixTheme.textStyles.button
                    )
                }
                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = true,
                        cursorBrush = SolidColor(cursorColor),
                        textStyle = TextStyle(
                            color = c.textPrimary,
                            fontSize = MiuixTheme.textStyles.button.fontSize
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!trailingText.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = trailingText,
                    style = MiuixTheme.textStyles.footnote1,
                    color = c.textSecondary
                )
            }
        }
    }
}

private fun applyTextMate(
    editor: CodeEditor,
    filePath: String,
    isDark: Boolean,
    c: AppColors,
    primaryArgb: Int
) {
    val fileName = filePath.substringAfterLast('/')
    val ext = filePath.substringAfterLast('.', "").lowercase()

    val languageSyntax = when {
        ext == "sh" -> "shell.json"
        fileName.startsWith("box.") -> "shell.json"
        fileName.endsWith(".inotify") -> "shell.json"
        fileName == "settings.ini" -> "shell.json"
        fileName.startsWith("start.") -> "shell.json"
        fileName.startsWith("ctr.") -> "shell.json"
        fileName.startsWith("net.") -> "shell.json"
        ext in listOf("ini", "cfg", "conf") -> "ini.json"
        ext in listOf("yaml", "yml") -> "yaml.json"
        ext == "json" -> "json.json"
        else -> null
    }

    val scopeName = when (languageSyntax) {
        "yaml.json" -> "source.yaml"
        "json.json" -> "source.json"
        "ini.json" -> "source.ini"
        "shell.json" -> "source.shell"
        else -> null
    }

    ThemeRegistry.getInstance().setTheme(if (isDark) "darcula" else "light")
    editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
    applyEditorChromeColors(editor, c, primaryArgb)

    scopeName?.let {
        val lang = TextMateLanguage.create(it, true)
        editor.setEditorLanguage(lang)
    }

    editor.invalidate()
}

private fun applyEditorChromeColors(editor: CodeEditor, c: AppColors, primaryArgb: Int) {
    editor.colorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, c.pageBg.toArgb())
    editor.colorScheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, c.pageBg.toArgb())
    editor.colorScheme.setColor(EditorColorScheme.LINE_NUMBER, c.textSecondary.toArgb())
    editor.colorScheme.setColor(EditorColorScheme.LINE_DIVIDER, c.divider.toArgb())
    editor.colorScheme.setColor(EditorColorScheme.CURRENT_LINE, c.pageBg.copy(alpha = 0.35f).toArgb())
    editor.colorScheme.setColor(EditorColorScheme.SELECTION_INSERT, primaryArgb)
    editor.colorScheme.setColor(EditorColorScheme.SELECTION_HANDLE, primaryArgb)
}

@Composable
private fun SolidPillButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null
) {
    val c = appColors()
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .background(c.cardAlt, Capsule())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            leading?.invoke()
            Text(
                text = text,
                color = if (enabled) c.textPrimary else c.textSecondary,
                style = MiuixTheme.textStyles.button
            )
        }
    }
}
