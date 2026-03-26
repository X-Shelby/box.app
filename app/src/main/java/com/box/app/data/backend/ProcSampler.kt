package com.box.app.data.backend

import java.io.File
import kotlin.math.max

internal object ProcSampler {

    data class NetSample(val rxBytes: Long, val txBytes: Long, val timeMs: Long)

    suspend fun readTotalNetBytes(): NetSample? {
        val content = try {
            File("/proc/net/dev").readText()
        } catch (_: Exception) {
            ""
        }
        if (content.isBlank()) return null

        var rx: Long = 0
        var tx: Long = 0
        content.lines().forEach { line ->
            if (line.contains(":")) {
                val trimmed = line.trim()
                if (trimmed.startsWith("lo:")) return@forEach
                val parts = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
                // iface: rxBytes ... txBytes
                if (parts.size >= 10) {
                    rx += parts[1].toLongOrNull() ?: 0L
                    tx += parts[9].toLongOrNull() ?: 0L
                }
            }
        }
        return NetSample(rxBytes = rx, txBytes = tx, timeMs = System.currentTimeMillis())
    }

    data class CpuTimes(val idle: Long, val total: Long)

    suspend fun readCpuTimes(): CpuTimes? {
        val line = try {
            File("/proc/stat").useLines { it.firstOrNull() ?: "" }
        } catch (_: Exception) {
            ""
        }
        if (line.isBlank()) return null

        val parts = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size < 5) return null
        // cpu user nice system idle iowait irq softirq steal guest guest_nice
        val nums = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (nums.size < 4) return null
        val idle = nums.getOrElse(3) { 0L } + nums.getOrElse(4) { 0L }
        val total = nums.sum()
        return CpuTimes(idle = idle, total = total)
    }

    suspend fun readMemUsedMb(): Long? {
        val content = try {
            File("/proc/meminfo").readText()
        } catch (_: Exception) {
            ""
        }
        if (content.isBlank()) return null

        fun findKb(key: String): Long? {
            val line = content.lines().firstOrNull { it.startsWith(key) } ?: return null
            return line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull()
        }

        val memTotal = findKb("MemTotal:") ?: return null
        val memAvailable = findKb("MemAvailable:") ?: return null
        val usedKb = max(0L, memTotal - memAvailable)
        return usedKb / 1024
    }

    fun formatSpeed(bytesPerSec: Double): String {
        val b = max(0.0, bytesPerSec)
        val kb = b / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format("%.1f MB/s", mb)
            kb >= 1.0 -> String.format("%.1f KB/s", kb)
            else -> String.format("%.0f B/s", b)
        }
    }
}
