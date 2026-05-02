package com.box.app.data.backend

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 日志页面专用 Root Shell — 独立于全局 cached shell
 *
 * ── 为什么必须独立 ──
 *   日志页的命令（list / tail / rm log files）都是只读、不依赖会话状态的简短命令；
 *   但它的调用频率高（切换文件 / 周期刷新 / 模块重启后立即重试）。如果与全局
 *   cached shell 共用：
 *     1. 写操作（updateSetting、startService、bin 切换）正在排队时，日志读取被
 *        串行阻塞 — UI"加载中"
 *     2. 模块重启瞬间，cached shell 被"日志读 + 服务命令"双重命令撞死，单点故障
 *   独立 shell 把日志读取从主链路完全解耦：
 *     - 任意一边卡死/被回收，互不影响
 *     - 日志 shell 不依赖会话（cwd/env），单独重建零代价
 *     - 写操作仍走 [PersistentRootShell] 保持唯一会话不丢
 *
 * ── 设计要点 ──
 *   - 用 [Shell.Builder.create] 显式新建 shell（不进 libsu 全局 cache，避免污染默认池）
 *   - 单实例 + Mutex 串行：保证同一时刻只有一条命令在跑（日志读不需要并发）
 *   - 入口探活 + 短超时（4s）+ 失败即重建：与 [PersistentRootShell] 同款套路，
 *     模块重启时 UI 不卡顿
 *   - [close] 仅关本对象 shell，不影响全局 cached shell
 */
internal object LogsRootShell {

    private const val COMMAND_TIMEOUT_MS = 4_000L
    private const val SHELL_INIT_TIMEOUT_S = 10L

    @Volatile
    private var shell: Shell? = null
    private val lock = Mutex()

    /** 获取/重建 shell：死了就 close + 新建，对外保证返回一个 alive 的 root shell。 */
    private suspend fun obtain(): Shell = lock.withLock {
        val current = shell
        if (current != null && current.isAlive) return@withLock current

        // 旧 shell 已死，先确保资源释放
        current?.runCatching { waitAndClose() }
        shell = null

        // 显式 builder 新建 — 不走 Shell.cmd 的全局 cache，与 PersistentRootShell 隔离。
        // 不设 FLAG_REDIRECT_STDERR：stderr 由 Job.to(stdout, stderr) 独立收集，更精细。
        val newShell = Shell.Builder.create()
            .setTimeout(SHELL_INIT_TIMEOUT_S)
            .build()
        shell = newShell
        newShell
    }

    /**
     * 在日志专用 shell 上执行命令。
     *
     * 任何 timeout / 异常都会立即 close 本对象 shell，下一次 [execute] 自动重建。
     */
    suspend fun execute(command: String): ShellExecutor.Result = withContext(Dispatchers.IO) {
        val s = try {
            obtain()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext ShellExecutor.Result("", e.message ?: "logs shell init failed", -1)
        }

        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()

        try {
            val result = withTimeout(COMMAND_TIMEOUT_MS) {
                s.newJob().add(command).to(stdout, stderr).exec()
            }
            ShellExecutor.Result(
                stdout = stdout.joinToString("\n"),
                stderr = stderr.joinToString("\n"),
                exitCode = result.code
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            close()
            ShellExecutor.Result("", "logs shell timeout", -1)
        } catch (e: Exception) {
            close()
            ShellExecutor.Result("", e.message ?: "logs shell error", -1)
        }
    }

    /** 关闭本对象持有的日志 shell；不会影响 [PersistentRootShell] 的全局 cached shell。 */
    fun close() {
        runCatching { shell?.waitAndClose() }
        shell = null
    }
}
