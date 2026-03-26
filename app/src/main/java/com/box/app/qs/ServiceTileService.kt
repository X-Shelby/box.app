package com.box.app.qs

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.box.app.AppApplication
import com.box.app.data.backend.BoxApi
import com.box.app.data.model.ServiceStatus
import com.box.app.service.BoxForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ServiceTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()

        val tile = qsTile ?: return
        val wasActive = tile.state == Tile.STATE_ACTIVE

        tile.state = Tile.STATE_UNAVAILABLE
        tile.updateTile()

        scope.launch {
            if (wasActive) {
                runCatching {
                    BoxForegroundService.ensureRunning(AppApplication.appContext)
                    BoxForegroundService.updateStatus(AppApplication.appContext, ServiceStatus.Stopping)
                }
                BoxApi.stopService()
            } else {
                runCatching {
                    BoxForegroundService.ensureRunning(AppApplication.appContext)
                    BoxForegroundService.updateStatus(AppApplication.appContext, ServiceStatus.Starting)
                }
                BoxApi.startService()
            }
            refreshTileState()
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        // no-op
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshTileState() {
        val tile = qsTile ?: return
        scope.launch {
            val pidRes = runCatching { BoxApi.getPid() }.getOrNull()
            val pid = pidRes?.stdout?.trim().orEmpty()
            val isRunning = pid.isNotBlank() && pid.all { it.isDigit() }

            runCatching {
                if (isRunning) {
                    BoxForegroundService.ensureRunning(AppApplication.appContext)
                    BoxForegroundService.updateStatus(AppApplication.appContext, ServiceStatus.Running)
                } else {
                    BoxForegroundService.stop(AppApplication.appContext)
                }
            }

            tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }
}
