package com.hive.hiragana.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.hive.hiragana.BuildConfig
import com.hive.hiragana.domain.update.AppUpdater
import com.hive.hiragana.domain.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * [AppUpdater] backed by a **public GitHub Releases repo** (distribution outside
 * the Play Store; the source repo stays private, so releases live in a separate
 * public repo the app can read unauthenticated — see [REPO]). Reads
 * `/releases/latest`, compares the release tag to this build's
 * [BuildConfig.VERSION_NAME], and — if newer and carrying an `.apk` asset —
 * downloads it to the private cache and launches the system package installer via
 * a [FileProvider] `content://` URI. No third-party HTTP dependency: plain
 * [HttpURLConnection] on [Dispatchers.IO]. Parsing uses the platform `org.json`
 * (same as the asset loader). Failures are swallowed to `null`/log — a flaky
 * network must never crash or block the app.
 */
class GithubAppUpdater(private val context: Context) : AppUpdater {

    override suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val json = httpGetString(LATEST_RELEASE_URL)
            val release = JSONObject(json)
            val tag = release.optString("tag_name")
            if (tag.isBlank() || !isNewer(remote = tag, current = BuildConfig.VERSION_NAME)) {
                return@withContext null
            }
            val apkUrl = firstApkAssetUrl(release) ?: return@withContext null
            val notes = release.optString("body").trim()
            UpdateInfo(
                versionName = tag.trimStart('v', 'V').ifBlank { tag },
                notes = notes.ifBlank { "A new version is available." },
                apkUrl = apkUrl,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            null
        }
    }

    override suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            // Keep the cache lean: only ever one pending APK.
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "HiraKataQuest-${info.versionName}.apk")
            val conn = open(info.apkUrl)
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("HTTP $code downloading APK")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastPct = -1
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct)
                                }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            out
        }

    override fun canInstall(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    override fun requestInstallPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Could not open install-permission settings", it) }
    }

    override fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Could not launch installer", it) }
    }

    // --- helpers ---

    private fun firstApkAssetUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                asset.optString("browser_download_url").takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return null
    }

    private fun httpGetString(urlStr: String): String {
        val conn = open(urlStr).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(urlStr: String): HttpURLConnection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            // GitHub's API rejects requests without a User-Agent.
            setRequestProperty("User-Agent", "HiraKataQuest-Updater")
        }

    companion object {
        private const val TAG = "AppUpdater"
        // A dedicated PUBLIC repo holds only the release APKs so the app can read
        // them unauthenticated; the source repo stays private. Publish releases here.
        private const val REPO = "MolnarMario/HiraKataQuest-releases"
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/$REPO/releases/latest"

        /**
         * True when [remote] (a release tag like "v0.1.2") represents a higher
         * version than [current] (this build's versionName, e.g. "0.1.1").
         * Compares dot-separated numeric components; a leading "v" is ignored and
         * any non-numeric suffix on a component is dropped.
         */
        internal fun isNewer(remote: String, current: String): Boolean {
            fun parts(v: String): List<Int> =
                v.trimStart('v', 'V').split('.', '-', '+', '_')
                    .map { seg -> seg.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val r = parts(remote)
            val c = parts(current)
            for (i in 0 until maxOf(r.size, c.size)) {
                val a = r.getOrElse(i) { 0 }
                val b = c.getOrElse(i) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}
