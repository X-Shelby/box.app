package com.box.app.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.box.app.data.backend.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object AppUtils {

    private const val CACHE_TTL_MS: Long = 2 * 60 * 1000L
    @Volatile private var cachedAtMs: Long = 0L
    @Volatile private var cachedApps: List<InstalledApp> = emptyList()
    private val cacheMutex = Mutex()

    /** 并发 shell 命令信号量，避免过载 */
    private val shellSemaphore = Semaphore(8)

    data class InstalledApp(
        val name: String,
        val packageName: String,
        val userId: Int,
        val isSystemApp: Boolean,
        val apkPath: String?,
        val installTime: Long,
        val hasNetworkPermission: Boolean
    ) {
        val userScopedPackageName: String
            get() = "$userId:$packageName"
    }

    suspend fun getInstalledApps(context: Context, forceRefresh: Boolean = false): List<InstalledApp> {
        // 快路径：读缓存不阻塞扫描
        if (!forceRefresh) {
            cacheMutex.withLock {
                val now = System.currentTimeMillis()
                if (cachedApps.isNotEmpty() && (now - cachedAtMs) < CACHE_TTL_MS) {
                    return cachedApps
                }
            }
        }

        // 慢路径：并发扫描，不持有锁
        val result = coroutineScope {
            val currentUserDeferred = async(Dispatchers.IO) {
                scanCurrentUser(context.applicationContext.packageManager)
            }
            val otherUsersDeferred = async(Dispatchers.IO) {
                scanOtherUsers(context.applicationContext.packageManager)
            }

            val all = currentUserDeferred.await() + otherUsersDeferred.await()

            // CPU 密集排序放 Default 调度器
            withContext(Dispatchers.Default) {
                all.distinctBy { it.userScopedPackageName }
                    .sortedWith(
                        compareBy<InstalledApp> { it.userId }
                            .thenBy { it.isSystemApp }
                            .thenBy { it.name.lowercase() }
                    )
            }
        }

        // 写缓存
        cacheMutex.withLock {
            cachedApps = result
            cachedAtMs = System.currentTimeMillis()
        }

        return result
    }

    // ─── 当前用户扫描（单次批量 PM 查询） ───────────────────────────────

    /**
     * 用单次 getInstalledPackages(GET_PERMISSIONS) 批量获取所有信息，
     * 替代原来 N×3 次 IPC 调用（name + installTime + permissions）。
     */
    private fun scanCurrentUser(pm: PackageManager): List<InstalledApp> {
        val packages: List<PackageInfo> = runCatching {
            pm.getInstalledPackages(
                PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
            )
        }.getOrDefault(emptyList())

        val perUserRange = 100_000

        return packages.map { pi ->
            val ai = pi.applicationInfo ?: return@map null

            InstalledApp(
                name = runCatching { pm.getApplicationLabel(ai).toString() }
                    .getOrDefault(pi.packageName),
                packageName = pi.packageName,
                userId = ai.uid / perUserRange,
                isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                apkPath = ai.publicSourceDir,
                installTime = pi.firstInstallTime,
                hasNetworkPermission = pi.requestedPermissions?.any {
                    it == android.Manifest.permission.INTERNET ||
                        it == android.Manifest.permission.ACCESS_NETWORK_STATE ||
                        it == android.Manifest.permission.ACCESS_WIFI_STATE
                } ?: false
            )
        }.filterNotNull()
    }

    // ─── 多用户并发扫描 ─────────────────────────────────────────────────

    private suspend fun scanOtherUsers(pm: PackageManager): List<InstalledApp> = coroutineScope {
        val userIds = getOtherUserIds()
        if (userIds.isEmpty()) return@coroutineScope emptyList()

        // 所有用户并发获取包列表
        val perUserPackages = userIds.map { userId ->
            async(Dispatchers.IO) {
                userId to getPackagesForUser(userId)
            }
        }.awaitAll()

        // 所有包并发解析元数据（信号量限制并发度）
        perUserPackages.flatMap { (userId, packageNames) ->
            packageNames.chunked(50).flatMap { chunk ->
                chunk.map { pkgName ->
                    async(Dispatchers.IO) {
                        shellSemaphore.acquire()
                        try {
                            resolveAppInfo(pm, pkgName, userId)
                        } finally {
                            shellSemaphore.release()
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
    }

    /** 获取非主用户 ID 列表 */
    private suspend fun getOtherUserIds(): List<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val result = ShellExecutor.execute("pm list users")
            if (result.exitCode != 0) return@withContext emptyList()

            val regex = Regex("UserInfo\\{(\\d+):")
            result.stdout.lineSequence()
                .mapNotNull { regex.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                .filter { it != 0 }
                .toList()
        }.getOrDefault(emptyList())
    }

    /** 获取指定用户的包名列表 */
    private suspend fun getPackagesForUser(userId: Int): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val result = ShellExecutor.execute("pm list packages --user $userId")
            if (result.exitCode != 0) return@withContext emptyList()

            result.stdout.lineSequence()
                .filter { it.startsWith("package:") }
                .map { it.substring(8).trim() }
                .filter { it.isNotBlank() }
                .toList()
        }.getOrDefault(emptyList())
    }

    /** 解析单个包的元数据 */
    private fun resolveAppInfo(pm: PackageManager, packageName: String, userId: Int): InstalledApp? {
        return runCatching {
            val ai = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()

            val name = ai?.let {
                runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(packageName)
            } ?: packageName

            val pi = runCatching {
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }.getOrNull()

            InstalledApp(
                name = name,
                packageName = packageName,
                userId = userId,
                isSystemApp = ai?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false,
                apkPath = ai?.publicSourceDir,
                installTime = pi?.firstInstallTime ?: 0L,
                hasNetworkPermission = pi?.requestedPermissions?.any {
                    it == android.Manifest.permission.INTERNET ||
                        it == android.Manifest.permission.ACCESS_NETWORK_STATE ||
                        it == android.Manifest.permission.ACCESS_WIFI_STATE
                } ?: true
            )
        }.getOrNull()
    }

    // ─── 公共工具方法 ───────────────────────────────────────────────────

    suspend fun getAllUserIds(): List<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val result = ShellExecutor.execute("pm list users")
            if (result.exitCode != 0) return@withContext listOf(0)

            val regex = Regex("UserInfo\\{(\\d+):")
            val ids = result.stdout.lineSequence()
                .mapNotNull { regex.find(it)?.groupValues?.get(1)?.toIntOrNull() }
                .toList()
            ids.ifEmpty { listOf(0) }
        }.getOrDefault(listOf(0))
    }

    suspend fun getUserDisplayName(userId: Int): String {
        if (userId == 0) return "Main"

        return withContext(Dispatchers.IO) {
            runCatching {
                val result = ShellExecutor.execute("pm list users")
                if (result.exitCode != 0) return@withContext "User $userId"

                val match = Regex("UserInfo\\{$userId:([^:]+):").find(result.stdout)
                    ?: return@withContext "User $userId"

                val name = match.groupValues[1].trim()
                when {
                    name.contains("Work", ignoreCase = true) -> "Work"
                    name.contains("Clone", ignoreCase = true) -> "Clone"
                    name.contains("Dual", ignoreCase = true) -> "Dual"
                    name.contains("Second", ignoreCase = true) -> "Second"
                    else -> name.take(10)
                }
            }.getOrDefault("User $userId")
        }
    }
}
