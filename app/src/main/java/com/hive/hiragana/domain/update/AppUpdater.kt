package com.hive.hiragana.domain.update

import java.io.File

/**
 * A newer release discovered on GitHub than the currently-installed build.
 * [apkUrl] points at the release's `.apk` asset; [notes] is the release body.
 */
data class UpdateInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
)

/**
 * The updater's UI-facing state, surfaced through the ViewModel and rendered as
 * the "Update available" dialog. A quiet check that finds nothing stays [Idle]
 * (no dialog); [UpToDate] is only used for an explicit user-triggered check.
 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data object Installing : UpdateState
    data object UpToDate : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Checks a remote source for a newer app version, downloads its APK, and hands it
 * to the system package installer. All distribution is outside the Play Store; the
 * user always confirms the actual install (Android requirement for sideloaded apps).
 */
interface AppUpdater {
    /** Latest release if it is newer than this build (and has an APK asset), else null. */
    suspend fun check(): UpdateInfo?

    /** Download [info]'s APK to a private cache file, reporting 0..100 progress. */
    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File

    /** True if the app may currently request package installs (Android 8+ per-source grant). */
    fun canInstall(): Boolean

    /** Open the system "install unknown apps" screen for this app. */
    fun requestInstallPermission()

    /** Launch the system installer for the downloaded [apk]. */
    fun install(apk: File)
}
