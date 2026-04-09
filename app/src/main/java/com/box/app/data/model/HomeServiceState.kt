package com.box.app.data.model

data class HomeServiceState(
    val env: EnvironmentState = EnvironmentState(),
    val status: ServiceStatus = ServiceStatus.Stopped,
    val pid: String = "-",
    val uptimeText: String = "-",
    val coreDisplayName: String = "-",
    val networkMode: String = "-",
    val ipv6Text: String = "-",
    val dnsMode: String = "-"
)
