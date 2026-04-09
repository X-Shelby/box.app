package com.box.app.data.backend

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal object PersistentRootShell {

    private const val COMMAND_TIMEOUT_MS = 12_000L

    suspend fun warmUp(minSessions: Int = 1) = withContext(Dispatchers.IO) {
        repeat(minSessions.coerceAtLeast(1)) {
            Shell.getShell()
        }
    }

    suspend fun execute(command: String): ShellExecutor.Result = withContext(Dispatchers.IO) {
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()

        try {
            val result = withTimeout(COMMAND_TIMEOUT_MS) {
                Shell.cmd(command)
                    .to(stdout, stderr)
                    .exec()
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
            ShellExecutor.Result("", "shell timeout", -1)
        } catch (e: Exception) {
            ShellExecutor.Result("", e.message ?: "shell error", -1)
        }
    }

    fun close() {
        runCatching {
            Shell.getCachedShell()?.waitAndClose()
        }
    }
}
