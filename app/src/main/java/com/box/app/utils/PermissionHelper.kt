package com.box.app.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.base.IPermission
import com.hjq.permissions.permission.dangerous.GetInstalledAppsPermission
import com.hjq.permissions.permission.dangerous.NearbyWifiDevicesPermission
import com.hjq.permissions.permission.dangerous.PostNotificationsPermission
import com.hjq.permissions.permission.dangerous.StandardDangerousPermission
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * XXPermissions v28 Compose 扩展封装。
 *
 * v28 使用 IPermission 对象（非字符串），权限由具体类表示。
 */
object Permissions {
    val POST_NOTIFICATIONS: IPermission = PostNotificationsPermission()
    val ACCESS_FINE_LOCATION: IPermission = StandardDangerousPermission(
        android.Manifest.permission.ACCESS_FINE_LOCATION, android.os.Build.VERSION_CODES.M
    )
    val NEARBY_WIFI_DEVICES: IPermission = NearbyWifiDevicesPermission()
    val GET_INSTALLED_APPS: IPermission = GetInstalledAppsPermission()
}

@Stable
data class PermissionResult(
    val granted: List<IPermission>,
    val denied: List<IPermission>,
    val allGranted: Boolean
) {
    val doNotAskAgain: Boolean get() = denied.isNotEmpty() && !allGranted
}

@Stable
class PermissionHelper(private val context: Context) {

    private fun findActivity(): Activity? {
        var ctx: Context = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    // ─── 检查 ───────────────────────────────────────────────────────

    fun isGranted(permission: IPermission): Boolean =
        permission.isGrantedPermission(context)

    fun allGranted(vararg permissions: IPermission): Boolean =
        permissions.all { it.isGrantedPermission(context) }

    // ─── 请求（回调式） ─────────────────────────────────────────────

    fun request(
        vararg permissions: IPermission,
        onResult: (PermissionResult) -> Unit
    ) {
        val act = findActivity() ?: run {
            onResult(PermissionResult(emptyList(), permissions.toList(), false))
            return
        }
        XXPermissions.with(act)
            .permissions(permissions.toList())
            .request(object : OnPermissionCallback {
                override fun onResult(
                    granted: MutableList<IPermission>,
                    denied: MutableList<IPermission>
                ) {
                    onResult(PermissionResult(
                        granted = granted,
                        denied = denied,
                        allGranted = denied.isEmpty()
                    ))
                }
            })
    }

    // ─── 请求（挂起式） ─────────────────────────────────────────────

    suspend fun requestSuspend(vararg permissions: IPermission): PermissionResult =
        suspendCancellableCoroutine { cont ->
            val act = findActivity()
            if (act == null) {
                cont.resume(PermissionResult(emptyList(), permissions.toList(), false))
                return@suspendCancellableCoroutine
            }
            XXPermissions.with(act)
                .permissions(permissions.toList())
                .request(object : OnPermissionCallback {
                    override fun onResult(
                        granted: MutableList<IPermission>,
                        denied: MutableList<IPermission>
                    ) {
                        if (cont.isActive) {
                            cont.resume(PermissionResult(
                                granted = granted,
                                denied = denied,
                                allGranted = denied.isEmpty()
                            ))
                        }
                    }
                })
        }

    // ─── 工具 ───────────────────────────────────────────────────────

    fun openSettings() {
        val act = findActivity() ?: return
        XXPermissions.startPermissionActivity(act)
    }

    fun openSettings(permissions: List<IPermission>) {
        val act = findActivity() ?: return
        XXPermissions.startPermissionActivity(act, permissions)
    }
}

@Composable
fun rememberPermissionHelper(): PermissionHelper {
    val context = LocalContext.current
    return remember(context) { PermissionHelper(context) }
}
