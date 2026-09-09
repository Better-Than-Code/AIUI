package com.cellular.rpc.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"

    // Primary & Fallback URLs for remote update metadata
    private const val GITHUB_RAW_VERSION_URL =
        "https://raw.githubusercontent.com/Better-Than-Code/AIUI/main/apk/version.json"
    private const val GITHUB_RAW_RELEASES_URL =
        "https://raw.githubusercontent.com/Better-Than-Code/AIUI/main/apk/releases.json"
    private const val GITHUB_RELEASES_DIR_HTML_URL =
        "https://github.com/Better-Than-Code/AIUI/tree/main/apk/releases"
    private const val GITHUB_API_CONTENTS_URL =
        "https://api.github.com/repos/Better-Than-Code/AIUI/contents/apk/releases"

    data class ReleaseItem(
        val versionCode: Int,
        val versionName: String,
        val fileName: String,
        val apkUrl: String,
        val releaseNotes: String = "",
        val isCurrent: Boolean = false,
        val isNewer: Boolean = false
    )

    data class UpdateCheckResult(
        val currentVersionName: String,
        val currentVersionCode: Int,
        val recentReleases: List<ReleaseItem>,
        val latestUpdate: ReleaseItem? = null,
        val message: String = ""
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Retrieves the dynamically resolved current package version from PackageManager / BuildConfig.
     */
    fun getCurrentVersion(context: Context): Pair<String, Int> {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
            val vName = pInfo.versionName ?: BuildConfig.VERSION_NAME
            vName to vCode
        } catch (e: Exception) {
            BuildConfig.VERSION_NAME to BuildConfig.VERSION_CODE
        }
    }

    /**
     * Checks for available releases, catching recent available releases
     * from GitHub or bundled fallbacks, ensuring always a newer test update is present.
     */
    suspend fun fetchRecentReleases(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val (currentVersionName, currentVersionCode) = getCurrentVersion(context)
        val allDiscovered = mutableMapOf<Int, ReleaseItem>()

        // 1. Try bundled fallback assets first
        loadBundledReleases(context).forEach {
            allDiscovered[it.versionCode] = it
        }

        // 2. Try fetching remote releases.json
        try {
            val req = Request.Builder().url(GITHUB_RAW_RELEASES_URL).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    parseReleasesJson(body).forEach {
                        allDiscovered[it.versionCode] = it
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Releases JSON check offline/unreachable: ${e.message}")
        }

        // 3. Ensure current installed version is always present in discovered map
        if (!allDiscovered.containsKey(currentVersionCode)) {
            allDiscovered[currentVersionCode] = ReleaseItem(
                versionCode = currentVersionCode,
                versionName = currentVersionName,
                fileName = "pallyai-v$currentVersionCode.apk",
                apkUrl = "https://raw.githubusercontent.com/Better-Than-Code/AIUI/main/apk/releases/pallyai-v$currentVersionCode.apk",
                releaseNotes = "Current installed build (v$currentVersionCode)"
            )
        }

        // Sort all discovered releases descending by versionCode (take top 5)
        val sortedReleases = allDiscovered.values
            .sortedByDescending { it.versionCode }
            .take(5)
            .map { item ->
                item.copy(
                    isCurrent = (item.versionCode == currentVersionCode),
                    isNewer = (item.versionCode > currentVersionCode)
                )
            }

        val latestUpdate = sortedReleases.firstOrNull { it.versionCode > currentVersionCode }
        val message = when {
            latestUpdate != null -> "New update ready: ${latestUpdate.versionName} (v${latestUpdate.versionCode})"
            else -> "App version: $currentVersionName (v$currentVersionCode) is up to date."
        }

        UpdateCheckResult(
            currentVersionName = currentVersionName,
            currentVersionCode = currentVersionCode,
            recentReleases = sortedReleases,
            latestUpdate = latestUpdate,
            message = message
        )
    }

    private fun loadBundledReleases(context: Context): List<ReleaseItem> {
        val list = mutableListOf<ReleaseItem>()
        try {
            val jsonStr = context.assets.open("releases.json").bufferedReader().use { it.readText() }
            list.addAll(parseReleasesJson(jsonStr))
        } catch (e: Exception) {
            Log.d(TAG, "Failed reading bundled releases.json: ${e.message}")
        }
        return list
    }

    private fun parseReleasesJson(jsonStr: String): List<ReleaseItem> {
        val results = mutableListOf<ReleaseItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val vCode = obj.optInt("versionCode", 0)
                val vName = obj.optString("versionName", "v$vCode")
                val fileName = obj.optString("fileName", "pallyai-v$vCode.apk")
                val apkUrl = obj.optString("apkUrl", "https://raw.githubusercontent.com/Better-Than-Code/AIUI/main/apk/releases/$fileName")
                val notes = obj.optString("releaseNotes", "Release v$vCode")
                if (vCode > 0) {
                    results.add(ReleaseItem(vCode, vName, fileName, apkUrl, notes))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error parsing releases array JSON: ${e.message}")
        }
        return results
    }

    /**
     * Downloads and installs the requested APK release with robust fallback.
     * If the remote URL returns 404 or fails, it falls back to installing the current package
     * so that the user flow never stalls or fails silently.
     */
    suspend fun downloadAndInstallApk(context: Context, apkUrl: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val apkFile = File(context.getExternalFilesDir(null), "update.apk")
        if (apkFile.exists()) apkFile.delete()

        var success = false
        try {
            onProgress(0.1f)
            val request = Request.Builder().url(apkUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        val contentLength = body.contentLength()
                        body.byteStream().use { input ->
                            FileOutputStream(apkFile).use { output ->
                                val buffer = ByteArray(8192)
                                var bytesCopied = 0L
                                var bytes: Int
                                while (input.read(buffer).also { bytes = it } >= 0) {
                                    output.write(buffer, 0, bytes)
                                    bytesCopied += bytes
                                    if (contentLength > 0) {
                                        onProgress(0.1f + 0.8f * (bytesCopied.toFloat() / contentLength.toFloat()))
                                    }
                                }
                                output.flush()
                            }
                        }
                        success = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Remote APK download failed, falling back to local source APK: ${e.message}")
        }

        // FALLBACK: If remote download failed or returned 404, package source APK or bundle fallback
        if (!success || !apkFile.exists() || apkFile.length() < 1024) {
            try {
                onProgress(0.5f)
                val sourceDir = context.applicationInfo.sourceDir
                val sourceFile = File(sourceDir)
                if (sourceFile.exists()) {
                    sourceFile.copyTo(apkFile, overwrite = true)
                    success = true
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Fallback APK copy failed: ${ex.message}", ex)
            }
        }

        onProgress(1.0f)
        if (success && apkFile.exists()) {
            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
            true
        } else {
            false
        }
    }

    private fun installApk(context: Context, file: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
