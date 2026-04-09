package com.box.app.utils

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Maple Mono NF CN 字体管理器
 *
 * 从 GitHub 镜像下载 MapleMono-NF-CN.zip（含 Nerd Font 图标），
 * 解压 Regular 字体，缓存至 app filesDir，并提供 Compose FontFamily。
 */
object MapleFontManager {

    private const val FONT_FILE_NAME = "MapleMono-NF-CN-Regular.ttf"
    private const val LEGACY_FONT_FILE_NAME = "MapleMono-CN-Regular.ttf"
    private const val FONT_DIR = "maple_font"
    private const val ORIGIN_URL =
        "https://github.com/subframe7536/maple-font/releases/download/v7.9/MapleMono-NF-CN.zip"

    // 镜像优先，失败回退官方直连
    private val DOWNLOAD_URLS = listOf(
        "https://mirror.ghproxy.com/$ORIGIN_URL",
        "https://ghp.ci/$ORIGIN_URL",
        ORIGIN_URL
    )

    sealed class FontState {
        data object NotLoaded : FontState()
        data object Downloading : FontState()
        data class Ready(val fontFamily: FontFamily) : FontState()
        data class Error(val message: String) : FontState()
    }

    private val _state = MutableStateFlow<FontState>(FontState.NotLoaded)
    val state: StateFlow<FontState> = _state.asStateFlow()

    private var cachedFamily: FontFamily? = null

    /** 获取已就绪的 FontFamily，未就绪返回 null */
    fun getFontFamily(): FontFamily? = cachedFamily

    /** 检查字体文件是否已缓存 */
    fun isCached(context: Context): Boolean {
        return getFontFile(context).exists()
    }

    /** 同步加载已缓存的字体（应在 init 阶段调用） */
    fun loadCachedFont(context: Context) {
        // 清理旧版非 NF 字体缓存
        val legacyFile = File(File(context.filesDir, FONT_DIR), LEGACY_FONT_FILE_NAME)
        if (legacyFile.exists()) legacyFile.delete()

        val file = getFontFile(context)
        if (file.exists()) {
            runCatching {
                val typeface = Typeface.createFromFile(file)
                val family = FontFamily(typeface)
                cachedFamily = family
                _state.value = FontState.Ready(family)
            }.onFailure {
                _state.value = FontState.NotLoaded
            }
        }
    }

    /** 异步下载并安装字体 */
    suspend fun downloadAndInstall(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (_state.value is FontState.Downloading) return@withContext false

        _state.value = FontState.Downloading

        val fontDir = File(context.filesDir, FONT_DIR).apply { mkdirs() }
        val fontFile = File(fontDir, FONT_FILE_NAME)

        // 依次尝试镜像，最终回退官方直连
        val success = DOWNLOAD_URLS.any { url -> downloadAndExtract(url, fontFile) }

        if (!success || !fontFile.exists()) {
            _state.value = FontState.Error("Download failed")
            return@withContext false
        }

        return@withContext runCatching {
            val typeface = Typeface.createFromFile(fontFile)
            val family = FontFamily(typeface)
            cachedFamily = family
            _state.value = FontState.Ready(family)
            true
        }.getOrElse {
            _state.value = FontState.Error(it.message ?: "Load failed")
            false
        }
    }

    /** 删除已缓存的字体 */
    fun clearCache(context: Context) {
        val fontDir = File(context.filesDir, FONT_DIR)
        fontDir.deleteRecursively()
        cachedFamily = null
        _state.value = FontState.NotLoaded
    }

    private fun getFontFile(context: Context): File {
        return File(File(context.filesDir, FONT_DIR), FONT_FILE_NAME)
    }

    /**
     * 下载 zip 并解压出目标 ttf 文件
     * @return 是否成功
     */
    private fun downloadAndExtract(url: String, targetFile: File): Boolean = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true

        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            return@runCatching false
        }

        conn.inputStream.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    // 匹配 Regular 字体文件（可能在子目录中）
                    val name = entry.name
                    if (!entry.isDirectory && name.endsWith(FONT_FILE_NAME)) {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { out ->
                            zip.copyTo(out)
                        }
                        zip.closeEntry()
                        return@runCatching true
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        conn.disconnect()
        false
    }.getOrDefault(false)
}
