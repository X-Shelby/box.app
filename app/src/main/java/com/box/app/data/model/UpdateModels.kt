package com.box.app.data.model

data class ReleaseInfo(
    val tag: String,
    val name: String,
    val url: String,
    val isPrerelease: Boolean,
    val body: String = "",
    val publishedAt: String = "",
    val downloadUrl: String = "",
    val commitSha: String = ""
)

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val stableRelease: ReleaseInfo?,
    val prereleaseRelease: ReleaseInfo?,
    val currentVersion: String,
    val recommendedRelease: ReleaseInfo?
)