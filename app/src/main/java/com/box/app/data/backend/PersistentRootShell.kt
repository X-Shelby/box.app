package com.box.app.data.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID

internal object PersistentRootShell {

    private class ShellSession(
        val process: Process,
        val stdin: DataOutputStream,
        val stdout: BufferedReader,
        val stderr: BufferedReader,
        val mutex: Mutex = Mutex(),
        var lastUsed: Long = System.currentTimeMillis(),
        var commandCount: Int = 0
    ) {
        fun isAlive(): Boolean = process.isAlive
        fun touch() {
            lastUsed = System.currentTimeMillis()
            commandCount++
        }
    }

    private val sessions = mutableListOf<ShellSession>()
    private val sessionsMutex = Mutex()

    private const val MAX_SESSIONS = 3
    private const val SESSION_MAX_COMMANDS = 120
    private const val SESSION_IDLE_TIMEOUT = 300_000L
    private const val COMMAND_TIMEOUT_MS = 12_000L

    suspend fun warmUp(minSessions: Int = 1) {
        val target = minSessions.coerceIn(1, MAX_SESSIONS)
        sessionsMutex.withLock {
            cleanupLocked()
            while (sessions.size < target) {
                val created = createSessionLocked()
                if (created == null) break
            }
        }
    }

    private fun closeSession(session: ShellSession) {
        try {
            session.stdin.write("exit\n".toByteArray(StandardCharsets.UTF_8))
            session.stdin.flush()
        } catch (_: Exception) {
        }
        try {
            session.stdin.close()
        } catch (_: Exception) {
        }
        try {
            session.stdout.close()
        } catch (_: Exception) {
        }
        try {
            session.stderr.close()
        } catch (_: Exception) {
        }
        try {
            session.process.destroy()
        } catch (_: Exception) {
        }
    }

    private fun cleanupLocked() {
        val now = System.currentTimeMillis()
        val it = sessions.iterator()
        while (it.hasNext()) {
            val s = it.next()
            val shouldRemove = !s.isAlive() || s.commandCount >= SESSION_MAX_COMMANDS || (now - s.lastUsed) > SESSION_IDLE_TIMEOUT
            if (shouldRemove) {
                it.remove()
                closeSession(s)
            }
        }
    }

    private fun createSessionLocked(): ShellSession? {
        return try {
            val p = Runtime.getRuntime().exec("su")
            val session = ShellSession(
                process = p,
                stdin = DataOutputStream(p.outputStream),
                stdout = BufferedReader(InputStreamReader(p.inputStream, StandardCharsets.UTF_8)),
                stderr = BufferedReader(InputStreamReader(p.errorStream, StandardCharsets.UTF_8))
            )
            sessions.add(session)
            session
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun acquireSession(): ShellSession? = sessionsMutex.withLock {
        cleanupLocked()

        val idle = sessions
            .filter { it.isAlive() && !it.mutex.isLocked && it.commandCount < SESSION_MAX_COMMANDS }
            .minByOrNull { it.commandCount }

        if (idle != null) return@withLock idle

        if (sessions.size < MAX_SESSIONS) {
            return@withLock createSessionLocked()
        }

        null
    }

    suspend fun execute(command: String): ShellExecutor.Result = withContext(Dispatchers.IO) {
        var attempts = 0
        val maxAttempts = 10

        while (attempts < maxAttempts) {
            val session = acquireSession()
            if (session == null) {
                delay(when (attempts) {
                    0, 1 -> 50L
                    2, 3 -> 100L
                    4, 5 -> 200L
                    else -> 500L
                })
                attempts++
                continue
            }

            return@withContext session.mutex.withLock {
                try {
                    session.touch()
                    withTimeout(COMMAND_TIMEOUT_MS) {
                        executeWithMarkers(session, command)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    sessionsMutex.withLock {
                        sessions.remove(session)
                        closeSession(session)
                    }
                    ShellExecutor.Result("", e.message ?: "shell error", -1)
                }
            }
        }

        ShellExecutor.Result("", "Failed to acquire shell session", -1)
    }

    private suspend fun executeWithMarkers(session: ShellSession, command: String): ShellExecutor.Result {
        val marker = "__BOX_MARKER_${UUID.randomUUID()}__"
        val exitMarker = "__BOX_EXIT_${UUID.randomUUID()}__"

        val wrapped = """
            echo '$marker'
            { $command; } 2>&1
            _cmd_exit=${'$'}?
            echo
            echo '$exitMarker'${'$'}_cmd_exit
            echo '$marker'
        """.trimIndent()

        session.stdin.write((wrapped + "\n").toByteArray(StandardCharsets.UTF_8))
        session.stdin.flush()

        val lines = mutableListOf<String>()
        var reading = false
        var foundStart = false
        var exitCode = -1

        while (true) {
            val line = session.stdout.readLine() ?: break
            when {
                line == marker && !foundStart -> {
                    foundStart = true
                    reading = true
                }

                line.startsWith(exitMarker) -> {
                    exitCode = line.removePrefix(exitMarker).toIntOrNull() ?: -1
                    reading = false
                }

                line == marker && foundStart -> break

                reading -> lines.add(line)
            }
        }

        return ShellExecutor.Result(stdout = lines.joinToString("\n"), stderr = "", exitCode = exitCode)
    }

    fun close() {
        val snapshot = runCatching {
            sessionsMutex.tryLock()
        }.getOrNull()

        if (snapshot == true) {
            try {
                val copy = sessions.toList()
                sessions.clear()
                for (s in copy) closeSession(s)
            } finally {
                sessionsMutex.unlock()
            }
        }
    }
}
