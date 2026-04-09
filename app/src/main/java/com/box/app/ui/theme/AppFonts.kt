package com.box.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.box.app.R

private val gmsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val jetBrainsMono = GoogleFont("JetBrains Mono")

/**
 * 数据展示专用字体族 — JetBrains Mono
 *
 * 等宽设计适合：IP 地址、流量数字、延迟毫秒、CPU/RAM 百分比。
 * 通过 GMS Downloadable Fonts 按需下载，无需打包字体文件。
 */
object AppFonts {
    val dataFamily: FontFamily = FontFamily(
        Font(googleFont = jetBrainsMono, fontProvider = gmsProvider, weight = FontWeight.Normal),
        Font(googleFont = jetBrainsMono, fontProvider = gmsProvider, weight = FontWeight.Medium),
        Font(googleFont = jetBrainsMono, fontProvider = gmsProvider, weight = FontWeight.SemiBold)
    )
}
