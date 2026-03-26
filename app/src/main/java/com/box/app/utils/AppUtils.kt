package com.box.app.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.box.app.data.backend.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AppUtils {

    private const val CACHE_TTL_MS: Long = 2 * 60 * 1000L
    @Volatile private var cachedAtMs: Long = 0L
    @Volatile private var cachedApps: List<InstalledApp> = emptyList()
    private val cacheMutex = Mutex()

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
        return cacheMutex.withLock {
            val now = System.currentTimeMillis()
            val cacheValid = cachedApps.isNotEmpty() && (now - cachedAtMs) < CACHE_TTL_MS
            if (!forceRefresh && cacheValid) {
                return@withLock cachedApps
            }

            val result = mutableListOf<InstalledApp>()
            val pm = context.applicationContext.packageManager

            // Get apps from current user (PackageManager approach)
            val currentUserApps = getCurrentUserApps(pm)
            result.addAll(currentUserApps)

            // Get apps from other users using shell commands
            val otherUserApps = getOtherUserApps(pm)
            result.addAll(otherUserApps)

            // Remove duplicates and sort
            val uniqueApps = result
                .distinctBy { it.userScopedPackageName }
                .sortedWith(compareBy<InstalledApp>({ it.userId }, { it.isSystemApp }, { it.name.lowercase() }))

            cachedApps = uniqueApps
            cachedAtMs = now
            uniqueApps
        }
    }

    private fun getCurrentUserApps(pm: PackageManager): List<InstalledApp> {
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        fun userIdFromUid(uid: Int): Int {
            val perUserRange = 100_000
            return uid / perUserRange
        }

        return apps
            .asSequence()
            .map { ai ->
                val name = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(ai.packageName)
                val userId = userIdFromUid(ai.uid)

                val installTime = runCatching {
                    pm.getPackageInfo(ai.packageName, 0).firstInstallTime
                }.getOrDefault(0L)

                val hasNetworkPermission = runCatching {
                    val permissions = pm.getPackageInfo(
                        ai.packageName,
                        PackageManager.GET_PERMISSIONS
                    ).requestedPermissions
                    permissions?.any {
                        it == android.Manifest.permission.INTERNET ||
                            it == android.Manifest.permission.ACCESS_NETWORK_STATE ||
                            it == android.Manifest.permission.ACCESS_WIFI_STATE
                    } ?: false
                }.getOrDefault(true)

                InstalledApp(
                    name = name,
                    packageName = ai.packageName,
                    userId = userId,
                    isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    apkPath = ai.publicSourceDir,
                    installTime = installTime,
                    hasNetworkPermission = hasNetworkPermission
                )
            }
            .toList()
    }

    private suspend fun getOtherUserApps(pm: PackageManager): List<InstalledApp> {
        val result = mutableListOf<InstalledApp>()

        try {
            // Get list of all users
            val usersResult = ShellExecutor.execute("pm list users")
            if (usersResult.exitCode != 0) return result

            val userIds = mutableSetOf<Int>()
            usersResult.stdout.lines().forEach { line ->
                // Parse lines like "UserInfo{0:Owner:c13} running" or "UserInfo{10:Work profile:1030}"
                val userIdMatch = Regex("UserInfo\\{(\\d+):").find(line)
                userIdMatch?.groupValues?.get(1)?.toIntOrNull()?.let { userId ->
                    if (userId != 0) { // Skip user 0 as we already got it via PackageManager
                        userIds.add(userId)
                    }
                }
            }

            // Get packages for each user
            userIds.forEach { userId ->
                val packagesResult = ShellExecutor.execute("pm list packages --user $userId")
                if (packagesResult.exitCode == 0) {
                    packagesResult.stdout.lines().forEach { line ->
                        if (line.startsWith("package:")) {
                            val packageName = line.substring(8).trim()
                            if (packageName.isNotBlank()) {
                                val app = createAppFromShell(pm, packageName, userId)
                                if (app != null) {
                                    result.add(app)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // If shell commands fail, silently continue with current user apps only
        }

        return result
    }

    private fun createAppFromShell(pm: PackageManager, packageName: String, userId: Int): InstalledApp? {
        return try {
            // Try to get app info from PackageManager first
            val ai = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                // If not found in current user, create minimal info
                null
            }

            val name = ai?.let { 
                runCatching { pm.getApplicationLabel(it).toString() }.getOrDefault(packageName)
            } ?: packageName

            val isSystemApp = ai?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false
            val apkPath = ai?.publicSourceDir

            val installTime = ai?.let {
                runCatching {
                    pm.getPackageInfo(packageName, 0).firstInstallTime
                }.getOrDefault(0L)
            } ?: 0L

            val hasNetworkPermission = ai?.let {
                runCatching {
                    val permissions = pm.getPackageInfo(
                        packageName,
                        PackageManager.GET_PERMISSIONS
                    ).requestedPermissions
                    permissions?.any {
                        it == android.Manifest.permission.INTERNET ||
                            it == android.Manifest.permission.ACCESS_NETWORK_STATE ||
                            it == android.Manifest.permission.ACCESS_WIFI_STATE
                    } ?: false
                }.getOrDefault(true)
            } ?: true

            InstalledApp(
                name = name,
                packageName = packageName,
                userId = userId,
                isSystemApp = isSystemApp,
                apkPath = apkPath,
                installTime = installTime,
                hasNetworkPermission = hasNetworkPermission
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get all available user IDs on the system
     */
    suspend fun getAllUserIds(): List<Int> {
        return try {
            val result = ShellExecutor.execute("pm list users")
            if (result.exitCode != 0) return listOf(0)

            val userIds = mutableListOf<Int>()
            result.stdout.lines().forEach { line ->
                val userIdMatch = Regex("UserInfo\\{(\\d+):").find(line)
                userIdMatch?.groupValues?.get(1)?.toIntOrNull()?.let { userId ->
                    userIds.add(userId)
                }
            }
            userIds.ifEmpty { listOf(0) }
        } catch (e: Exception) {
            listOf(0)
        }
    }

    /**
     * Get user display name for a given user ID
     */
    suspend fun getUserDisplayName(userId: Int): String {
        if (userId == 0) return "Main"
        
        return try {
            val result = ShellExecutor.execute("pm list users")
            if (result.exitCode != 0) return "User $userId"

            result.stdout.lines().forEach { line ->
                val match = Regex("UserInfo\\{$userId:([^:]+):").find(line)
                if (match != null) {
                    val name = match.groupValues[1].trim()
                    return when {
                        name.contains("Work", ignoreCase = true) -> "Work"
                        name.contains("Clone", ignoreCase = true) -> "Clone"
                        name.contains("Dual", ignoreCase = true) -> "Dual"
                        name.contains("Second", ignoreCase = true) -> "Second"
                        else -> name.take(10) // Limit length
                    }
                }
            }
            "User $userId"
        } catch (e: Exception) {
            "User $userId"
        }
    }
}
