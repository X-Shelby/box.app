package com.box.app.data.model

data class EnvironmentState(
    val checked: Boolean = false,
    val hasRoot: Boolean = false,
    val hasModule: Boolean = false,
    val hasScripts: Boolean = false,
    val message: String = ""
) {
    val isReady: Boolean get() = hasRoot && hasModule && hasScripts
}
