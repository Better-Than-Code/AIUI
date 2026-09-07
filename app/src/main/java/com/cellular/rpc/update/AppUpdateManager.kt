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
        "https://raw.githubusercontent.com/earngameapps/cellular-rpc/main/apk/version.json"
    private const val GITHUB_RAW_RELEASES_URL =
        "https://raw.githubusercontent.com/earngameapps/cellular-rpc/main/apk/releases.json"
    private const val GITHUB_RELEASES_DIR_HTML_URL =
        "https://github.com/earngameapps/cellular-rpc/tree/main/apk/releases"
    private const val GITHUB_API_CONTENTS_URL =
        "https://api.github.com/repos/earngameapps/cellular-rpc/contents/apk/releases"

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
     * Checks for available releases, catching the most recent 5 available releases
     * from GitHub or bundled fallbacks, displaying them cleanly for user selection.
     */
    suspend fun fetchRecentReleases(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val (currentVersionName, currentVersionCode) = getCurrentVersion(context)
        val allDiscovered = mutableMapOf<Int, ReleaseItem>()

        // 1. Try bundled fallback assets first (immediate baseline)
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

        // 3. Try fetching single version.json
        try {
            val req = Request.Builder().url(GITHUB_RAW_VERSION_URL).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    parseSingleVersionJson(body)?.let {
                        allDiscovered[it.versionCode] = it
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Single version.json check offline/unreachable: ${e.message}")
        }

        // 4. Try GitHub API contents endpoint for apk/releases
        try {
            val req = Request.Builder()
                .url(GITHUB_API_CONTENTS_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    parseGitHubApiContents(body).forEach {
                        allDiscovered[it.versionCode] = it
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "GitHub API contents check offline/unreachable: ${e.message}")
        }

        // 5. Try GitHub HTML scraping of the releases directory
        try {
            val req = Request.Builder().url(GITHUB_RELEASES_DIR_HTML_URL).build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    parseGitHubHtmlDirectory(body).forEach {
                        allDiscovered[it.versionCode] = it
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "GitHub HTML directory check offline/unreachable: ${e.message}")
        }

        // Sort all discovered releases descending by versionCode
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
            latestUpdate != null -> "Found new update: ${latestUpdate.versionName} (${latestUpdate.fileName})"
            sortedReleases.isNotEmpty() -> "App is up to date (v$currentVersionCode). Latest 5 releases available below."
            else -> "App version: $currentVersionName (v$currentVersionCode)"
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

        // Also check version.json
        if (list.isEmpty()) {
            try {
                val jsonStr = context.assets.open("version.json").bufferedReader().use { it.readText() }
                parseSingleVersionJson(jsonStr)?.let { list.add(it) }
            } catch (e: Exception) {
                Log.d(TAG, "Failed reading bundled version.json: ${e.message}")
            }
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
                val apkUrl = obj.optString("apkUrl", "https://raw.githubusercontent.com/earngameapps/cellular-rpc/main/apk/releases/$fileName")
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

    private fun parseSingleVersionJson(jsonStr: String): ReleaseItem? {
        return try {
            val json = JSONObject(jsonStr)
            val vCode = json.getInt("versionCode")
            val vName = json.optString("versionName", "v$vCode")
            val apkUrl = json.getString("apkUrl")
            val fileName = apkUrl.substringAfterLast('/', "pallyai-v$vCode.apk")
            val notes = json.optString("releaseNotes", "New update available.")
            ReleaseItem(vCode, vName, fileName, apkUrl, notes)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGitHubApiContents(jsonStr: String): List<ReleaseItem> {
        val results = mutableListOf<ReleaseItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.optString("name", "")
                val downloadUrl = item.optString("download_url", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    val code = extractVersionCode(name)
                    if (code > 0) {
                        results.add(
                            ReleaseItem(
                                versionCode = code,
                                versionName = if (code == 12) "2.1" else "$code.0",
                                fileName = name,
                                apkUrl = downloadUrl.ifBlank {
                                    "https://raw.githubusercontent.com/earngameapps/cellular-rpc/main/apk/releases/$name"
                                },
                                releaseNotes = "GitHub release: $name"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error parsing GitHub API contents: ${e.message}")
        }
        return results
    }

    private fun parseGitHubHtmlDirectory(html: String): List<ReleaseItem> {
        val results = mutableListOf<ReleaseItem>()
        try {
            val regex = Regex("""href=["']([^"']*/apk/releases/([^"']+\.apk))["']""", RegexOption.IGNORE_CASE)
            regex.findAll(html).forEach { match ->
                val fullPath = match.groupValues[1]
                val fileName = match.groupValues[2]
                val code = extractVersionCode(fileName)
                if (code > 0) {
                    val rawUrl = if (fullPath.startsWith("http")) fullPath else "https://raw.githubusercontent.com/earngameapps/cellular-rpc/main/apk/releases/$fileName"
                    results.add(
                        ReleaseItem(
                            versionCode = code,
                            versionName = if (code == 12) "2.1" else "$code.0",
                            fileName = fileName,
                            apkUrl = rawUrl,
                            releaseNotes = "GitHub release: $fileName"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error scraping GitHub HTML: ${e.message}")
        }
        return results
    }

    private fun extractVersionCode(fileName: String): Int {
        val match = Regex("""(?:v|pallyai-v)(\d+)""", RegexOption.IGNORE_CASE).find(fileName)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    suspend fun downloadAndInstallApk(context: Context, apkUrl: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(apkUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                val contentLength = body.contentLength()

                val apkFile = File(context.getExternalFilesDir(null), "update.apk")
                if (apkFile.exists()) apkFile.delete()

                body.byteStream().use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesCopied = 0L
                        var bytes: Int
                        while (input.read(buffer).also { bytes = it } >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesCopied += bytes
                            if (contentLength > 0) {
                                onProgress(bytesCopied.toFloat() / contentLength.toFloat())
                            }
                        }
                        output.flush()
                    }
                }

                // Trigger APK installation intent
                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK: ${e.message}", e)
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
