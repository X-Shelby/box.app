package com.box.app.utils

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.os.Build
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import java.io.File
import java.util.zip.ZipFile

object AppClassifier {

    private val skipPrefixList = listOf(
        "com.google",
        "com.android.chrome",
        "com.android.vending",
        "com.microsoft",
        "com.apple",
        "com.facebook",
        "com.instagram",
        "com.whatsapp",
        "com.twitter",
        "com.zhiliaoapp.musically",
        "com.spotify",
        "com.netflix",
        "com.amazon",
        "com.reddit",
        "com.snapchat",
        "com.linkedin",
        "com.discord",
        "com.telegram",
        "org.telegram",
        "com.samsung",
        "com.sec.android",
        "com.adobe",
        "com.dropbox",
        "com.paypal",
        "com.uber",
        "com.airbnb",
        "com.booking",
        "com.expedia"
    )

    private val chinaAppPrefixList = listOf(
        "com.tencent",
        "com.qq",
        "com.alibaba",
        "com.ali",
        "com.alipay",
        "com.taobao",
        "com.tmall",
        "com.aliyun",
        "com.alicloud",
        "com.amap",
        "com.baidu",
        "com.bytedance",
        "com.ss",
        "com.netease",
        "com.sina",
        "com.weibo",
        "com.meituan",
        "com.sankuai",
        "com.dianping",
        "com.xunmeng",
        "com.jingdong",
        "com.jd",
        "com.xingin",
        "com.kuaishou",
        "com.smile",
        "tv.danmaku.bili",
        "com.bilibili",
        "com.zhihu",
        "com.sdu.didi",
        "com.didichuxing",
        "me.ele",
        "ctrip.android",
        "com.ctrip",
        "com.xiaomi",
        "com.miui",
        "com.huawei",
        "com.hihonor",
        "com.vivo",
        "com.bbk",
        "com.oppo",
        "com.coloros",
        "com.heytap",
        "com.iqoo",
        "com.meizu",
        "com.flyme",
        "com.gionee",
        "cn.nubia",
        "com.oplus",
        "com.oneplus",
        "com.unionpay",
        "com.chinamworld",
        "cmb.pb",
        "com.icbc",
        "com.ccb",
        "com.abc",
        "com.boc",
        "com.cib",
        "com.secneo",
        "s.h.e.l.l",
        "com.stub",
        "com.kiwisec",
        "com.secshell",
        "com.wrapper",
        "cn.securitystack",
        "com.mogosec",
        "com.secoen",
        "com.qihoo",
        "com.umeng",
        "com.qq.e",
        "com.bugly",
        "cn.jpush",
        "com.igexin",
        "cn.wps",
        "com.youdao",
        "com.youku",
        "com.tudou",
        "com.iqiyi",
        "com.letv",
        "com.kugou",
        "com.kuwo",
        "com.duomi",
        "com.qidian",
        "com.iflytek",
        "com.tencent.tmgp",
        "com.mihoyo",
        "com.hypergryph",
        "com.cheetahmobile",
        "com.sohu",
        "com.ifeng",
        "com.douban",
        "com.anjuke",
        "com.lianjia"
    )

    private val chinaAppRegex by lazy {
        ("(" + chinaAppPrefixList.joinToString("|").replace(".", "\\.") + ").*").toRegex()
    }

    @Suppress("DEPRECATION")
    fun isChinaApp(context: Context, packageName: String, apkPathHint: String? = null): Boolean {
        skipPrefixList.forEach { prefix ->
            if (packageName == prefix || packageName.startsWith("$prefix.")) return false
        }

        if (packageName.matches(chinaAppRegex)) return true

        val pm = context.packageManager
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS
            } else {
                PackageManager.GET_UNINSTALLED_PACKAGES or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS
            }

            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }

            mutableListOf<ComponentInfo>().apply {
                packageInfo.services?.let { addAll(it) }
                packageInfo.activities?.let { addAll(it) }
                packageInfo.receivers?.let { addAll(it) }
                packageInfo.providers?.let { addAll(it) }
            }.forEach { ci ->
                if (ci.name.matches(chinaAppRegex)) return true
            }

            val apkPath = apkPathHint ?: packageInfo.applicationInfo?.publicSourceDir
            if (!apkPath.isNullOrBlank()) {
                return analyzeApkDex(apkPath)
            }
        } catch (_: Exception) {
            return false
        }

        return false
    }

    private fun analyzeApkDex(apkPath: String): Boolean {
        try {
            ZipFile(File(apkPath)).use { zipFile ->
                val entries = zipFile.entries().toList()

                // dexlib rule: if contains firebase-*, treat as international
                entries.forEach { e ->
                    if (e.name.startsWith("firebase-")) return false
                }

                entries.forEach { entry ->
                    if (!(entry.name.startsWith("classes") && entry.name.endsWith(".dex"))) return@forEach

                    if (entry.size > 15_000_000) return true

                    try {
                        zipFile.getInputStream(entry).buffered().use { input ->
                            val dexFile = DexBackedDexFile.fromInputStream(null, input)
                            for (clazz in dexFile.classes) {
                                val className = clazz.type
                                    .substring(1, clazz.type.length - 1)
                                    .replace("/", ".")
                                    .replace("$", ".")

                                if (className.matches(chinaAppRegex)) return true
                            }
                        }
                    } catch (_: Exception) {
                        // ignore this dex and continue
                    }
                }
            }
        } catch (_: Exception) {
            return false
        }

        return false
    }
}
