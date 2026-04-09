package com.box.app.utils

import android.content.Context
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

object AppClassifier {

    // ─── 结果缓存 ───────────────────────────────────────────────────────

    private val cache = ConcurrentHashMap<String, Boolean>()

    fun clearCache() = cache.clear()

    // ─── 已知国际应用（直接跳过） ───────────────────────────────────────

    private val internationalPrefixes = listOf(
        "com.google", "com.android.chrome", "com.android.vending",
        "com.microsoft", "com.apple", "com.facebook", "com.meta",
        "com.instagram", "com.whatsapp", "com.twitter",
        "com.zhiliaoapp.musically", // TikTok 国际版
        "com.spotify", "com.netflix", "com.amazon", "com.reddit",
        "com.snapchat", "com.linkedin", "com.discord",
        "com.telegram", "org.telegram", "org.mozilla",
        "com.samsung", "com.sec.android", "com.adobe",
        "com.dropbox", "com.paypal", "com.uber", "com.lyft",
        "com.airbnb", "com.booking", "com.expedia",
        "org.signal", "com.wire", "com.skype",
        "com.github", "com.gitlab", "io.github",
        "com.openai", "com.anthropic"
    )

    // ─── 中国应用包名前缀 ──────────────────────────────────────────────

    private val chinaPrefixes = listOf(
        // 腾讯
        "com.tencent", "com.qq", "com.epicgames.fortnite",
        // 阿里
        "com.alibaba", "com.ali", "com.alipay", "com.taobao",
        "com.tmall", "com.aliyun", "com.alicloud", "com.amap",
        "com.cainiao", "com.dingtalk", "com.laiwang",
        // 百度
        "com.baidu",
        // 字节跳动
        "com.bytedance", "com.ss.android", "com.lark",
        // 网易
        "com.netease",
        // 新浪/微博
        "com.sina", "com.weibo",
        // 美团
        "com.meituan", "com.sankuai", "com.dianping",
        // 拼多多
        "com.xunmeng", "com.pinduoduo",
        // 京东
        "com.jingdong", "com.jd",
        // 小红书
        "com.xingin",
        // 快手
        "com.kuaishou", "com.smile",
        // B站
        "tv.danmaku.bili", "com.bilibili",
        // 知乎
        "com.zhihu",
        // 滴滴
        "com.sdu.didi", "com.didichuxing",
        // 饿了么
        "me.ele",
        // 携程
        "ctrip.android", "com.ctrip", "com.trip",
        // 手机厂商
        "com.xiaomi", "com.miui", "com.mi.",
        "com.huawei", "com.hihonor",
        "com.vivo", "com.bbk",
        "com.oppo", "com.coloros", "com.heytap",
        "com.iqoo", "com.meizu", "com.flyme",
        "com.gionee", "cn.nubia", "com.oplus", "com.oneplus",
        // 银行/金融
        "com.unionpay", "com.chinamworld", "cmb.pb",
        "com.icbc", "com.ccb", "com.abc", "com.boc", "com.cib",
        "com.pingan", "com.cmbc", "com.spdb",
        // 安全/加固
        "com.secneo", "s.h.e.l.l", "com.stub", "com.kiwisec",
        "com.secshell", "com.wrapper", "cn.securitystack",
        "com.mogosec", "com.secoen", "com.qihoo", "com.bangcle",
        // 推送/统计 SDK 宿主
        "com.umeng", "com.bugly", "cn.jpush", "com.igexin",
        // 办公/工具
        "cn.wps", "com.youdao",
        // 视频/音乐
        "com.youku", "com.tudou", "com.iqiyi", "com.letv",
        "com.kugou", "com.kuwo", "com.duomi",
        "com.huya", "com.douyu", "com.mgtv",
        "fm.qingting", "com.ximalaya",
        // 阅读/文学
        "com.qidian", "com.ireader", "com.zongheng",
        // AI/语音
        "com.iflytek",
        // 游戏
        "com.tencent.tmgp", "com.mihoyo", "com.hoyoverse",
        "com.hypergryph", "com.lilith", "com.papegames",
        // 新闻/社交
        "com.sohu", "com.ifeng", "com.douban",
        "com.cheetahmobile", "com.ucweb",
        // 房产/生活
        "com.anjuke", "com.lianjia", "com.beike",
        "com.58", "com.ganji"
    )

    // ─── DEX 内部中国 SDK 特征类 ────────────────────────────────────────

    private val chinaSdkSignatures = listOf(
        // 推送
        "cn/jpush/", "com/igexin/", "com/xiaomi/mipush/",
        "com/huawei/hms/push/", "com/vivo/push/", "com/heytap/msp/",
        "com/meizu/cloud/pushsdk/",
        // 统计/监控
        "com/umeng/", "com/tencent/bugly/", "com/sensorsdata/",
        "com/growingio/", "com/alibaba/sdk/android/",
        // 支付
        "com/alipay/sdk/", "com/tencent/mm/opensdk/",
        "com/unionpay/uppay/",
        // 地图/定位
        "com/amap/api/", "com/baidu/mapapi/", "com/tencent/map/",
        // 加固壳
        "com/secneo/", "com/secshell/", "com/qihoo/util/",
        "com/bangcle/", "com/tencent/StubShell/",
        "com/wrapper/proxyapplication/", "s/h/e/l/l/",
        // 社交登录
        "com/sina/weibo/sdk/", "com/tencent/open/",
        "com/alipay/share/"
    )

    // ─── 预编译正则 ─────────────────────────────────────────────────────

    private val chinaRegex by lazy {
        val escaped = chinaPrefixes.joinToString("|") { Regex.escape(it) }
        Regex("($escaped).*")
    }

    private val internationalRegex by lazy {
        val escaped = internationalPrefixes.joinToString("|") { Regex.escape(it) }
        Regex("($escaped).*")
    }

    // ─── DEX 并行分析信号量 ─────────────────────────────────────────────

    private val dexSemaphore = Semaphore(4)

    // ─── 公开 API ──────────────────────────────────────────────────────

    suspend fun isChinaApp(
        context: Context,
        packageName: String,
        apkPathHint: String? = null
    ): Boolean {
        cache[packageName]?.let { return it }

        val result = classify(context, packageName, apkPathHint)
        cache[packageName] = result
        return result
    }

    private suspend fun classify(
        context: Context,
        packageName: String,
        apkPathHint: String?
    ): Boolean {
        // 阶段 1：包名快速判定
        if (packageName.matches(internationalRegex)) return false
        if (packageName.matches(chinaRegex)) return true
        if (packageName.startsWith("cn.")) return true

        // 阶段 2：组件名扫描
        val pm = context.packageManager
        val componentResult = withContext(Dispatchers.IO) {
            runCatching {
                val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS

                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, flags)
                }

                val components = mutableListOf<ComponentInfo>()
                packageInfo.services?.let { components.addAll(it) }
                packageInfo.activities?.let { components.addAll(it) }
                packageInfo.receivers?.let { components.addAll(it) }
                packageInfo.providers?.let { components.addAll(it) }

                // 组件名匹配中国前缀
                val hasChina = components.any { it.name.matches(chinaRegex) || it.name.startsWith("cn.") }
                val apk = apkPathHint ?: packageInfo.applicationInfo?.publicSourceDir
                hasChina to apk
            }.getOrNull()
        } ?: return false

        if (componentResult.first) return true

        // 阶段 3：DEX 并行深度分析
        val apkPath = componentResult.second
        if (apkPath.isNullOrBlank()) return false

        return analyzeDexParallel(apkPath)
    }

    // ─── DEX 并行分析 ───────────────────────────────────────────────────

    private suspend fun analyzeDexParallel(apkPath: String): Boolean = coroutineScope {
        runCatching {
            val zipFile = ZipFile(File(apkPath))

            // 检查是否包含 firebase（国际应用标志）
            val hasFirebase = zipFile.entries().asSequence().any { it.name.startsWith("firebase-") }
            if (hasFirebase) {
                zipFile.close()
                return@coroutineScope false
            }

            val dexEntries = zipFile.entries().asSequence()
                .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                .toList()

            if (dexEntries.isEmpty()) {
                zipFile.close()
                return@coroutineScope false
            }

            // 每个 DEX 文件并行扫描
            val results = dexEntries.map { entry ->
                async(Dispatchers.IO) {
                    dexSemaphore.acquire()
                    try {
                        scanSingleDex(zipFile, entry.name)
                    } finally {
                        dexSemaphore.release()
                    }
                }
            }.awaitAll()

            zipFile.close()
            results.any { it }
        }.getOrDefault(false)
    }

    /**
     * 扫描单个 DEX：采样类名 + SDK 特征检测。
     * 不遍历全部类——只扫前 2000 个类，找到任一中国特征即返回。
     */
    private fun scanSingleDex(zipFile: ZipFile, entryName: String): Boolean {
        return runCatching {
            val entry = zipFile.getEntry(entryName) ?: return false

            zipFile.getInputStream(entry).buffered().use { input ->
                val dexFile = DexBackedDexFile.fromInputStream(null, input)
                var scanned = 0
                val maxScan = 2000

                for (clazz in dexFile.classes) {
                    if (scanned++ > maxScan) break

                    val type = clazz.type // Lcom/tencent/mm/R;
                    if (type.length < 3) continue

                    // SDK 特征匹配（直接用 DEX 内部路径格式，避免字符串转换）
                    for (sig in chinaSdkSignatures) {
                        if (type.indexOf(sig, 1) != -1) return true
                    }

                    // 包名前缀匹配（转换为 dotted 格式）
                    val className = type.substring(1, type.length - 1).replace('/', '.')
                    if (className.matches(chinaRegex) || className.startsWith("cn.")) return true
                }
            }
            false
        }.getOrDefault(false)
    }
}
