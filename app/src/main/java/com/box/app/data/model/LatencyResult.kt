package com.box.app.data.model

sealed class LatencyResult {
    data object Loading : LatencyResult()
    data object NotAvailable : LatencyResult()
    data class Success(val latencyMs: Long) : LatencyResult()
}
