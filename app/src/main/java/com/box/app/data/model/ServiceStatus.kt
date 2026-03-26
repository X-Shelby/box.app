package com.box.app.data.model

sealed class ServiceStatus {
    data object Running : ServiceStatus()
    data object Stopped : ServiceStatus()
    data object Checking : ServiceStatus()
    data object Starting : ServiceStatus()
    data object Stopping : ServiceStatus()
    data object Restarting : ServiceStatus()
}
