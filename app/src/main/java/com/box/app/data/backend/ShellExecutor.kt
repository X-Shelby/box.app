package com.box.app.data.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ShellExecutor {
    data class Result(val stdout: String, val stderr: String, val exitCode: Int)

    suspend fun execute(command: String): Result = withContext(Dispatchers.IO) {
        PersistentRootShell.execute(command)
    }

    suspend fun warmUpRootShell(minSessions: Int = 1) = withContext(Dispatchers.IO) {
        PersistentRootShell.warmUp(minSessions)
    }
}
