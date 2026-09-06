package com.cellular.rpc.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object AppUpdateManager {
    private const val TAG = "AppUpdateManager"
    
    // Configure your GitHub raw JSON release check URL here
    // Example format: {"versionCode": 2, "versionName": "1.1", "apkUrl": "https://raw.githubusercontent.com/username/repo/main/apk/app-debug.apk", "releaseNotes": "Bug fixes and improvements"}
    private const val VERSION_CHECK_URL = "https://raw.githubusercontent.com/earngameapps/cellular-rpc/main/apk/version.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val releaseNotes: String
    )

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(VERSION_CHECK_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyString = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyString)
                val remoteVersionCode = json.getInt("versionCode")
                val remoteVersionName = json.getString("versionName")
                val apkUrl = json.getString("apkUrl")
                val releaseNotes = json.optString("releaseNotes", "New update available.")

                val currentVersionCode = context.packageManager.getPackageInfo(context.packageName, 0).let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode
                }

                if (remoteVersionCode > currentVersionCode) {
                    return@withContext UpdateInfo(remoteVersionCode, remoteVersionName, apkUrl, releaseNotes)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for app update: ${e.message}")
        }
        null
    }

    suspend fun downloadAndInstallApk(context: Context, apkUrl: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(apkUrl).build()
            client.newCall(request).execute().use { response ->
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
