package com.NovelRegEx.app.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.NovelRegEx.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
  val version: String,
  val downloadUrl: String,
)

sealed class UpdateResult {
  data class Available(val info: UpdateInfo) : UpdateResult()
  data object Latest : UpdateResult()
  data class Error(val message: String? = null) : UpdateResult()
}

object UpdateChecker {
  private const val PREFS = "NovelRegEx_update_prefs"
  private const val RELEASE_API = "https://api.github.com/repos/marv435624q/NovelRegEx/releases/latest"
  private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L

  const val KEY_DOWNLOAD_ID = "download_id"
  const val KEY_PENDING_URL = "pending_download_url"
  const val KEY_APK_PATH = "apk_path"
  const val KEY_APK_VERSION = "apk_version"

  private const val KEY_CACHE_VERSION = "cache_version"
  private const val KEY_CACHE_URL = "cache_download_url"
  private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"

  fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  suspend fun fetchLatest(context: Context): UpdateResult =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      runCatching {
        val connection = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          connectTimeout = 10_000
          readTimeout = 10_000
          setRequestProperty("Accept", "application/vnd.github+json")
          setRequestProperty("User-Agent", "NovelRegEx/${BuildConfig.VERSION_NAME}")
          setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
          if (connection.responseCode !in 200..299) error("GitHub API HTTP ${connection.responseCode}")
          val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
          val version = root.optString("tag_name").removePrefix("v").trim()
          require(version.isNotEmpty()) { "Missing tag_name" }
          val assets = root.optJSONArray("assets") ?: error("Missing assets")
          var downloadUrl: String? = null
          for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")) {
              downloadUrl = url
              break
            }
          }
          val apkUrl = downloadUrl ?: error("No APK asset in latest release")
          if (compareVersions(version, BuildConfig.VERSION_NAME) > 0) {
            UpdateResult.Available(UpdateInfo(version, apkUrl))
          } else {
            UpdateResult.Latest
          }
        } finally {
          connection.disconnect()
        }
      }.getOrElse { UpdateResult.Error(it.message) }
    }

  fun saveCache(context: Context, result: UpdateResult) {
    val now = System.currentTimeMillis()
    prefs(context).edit {
      putLong(KEY_CACHE_TIMESTAMP, now)
      when (result) {
        is UpdateResult.Available -> {
          putString(KEY_CACHE_VERSION, result.info.version)
          putString(KEY_CACHE_URL, result.info.downloadUrl)
        }
        else -> {
          remove(KEY_CACHE_VERSION)
          remove(KEY_CACHE_URL)
        }
      }
    }
  }

  fun hasFreshCache(context: Context): Boolean {
    val timestamp = prefs(context).getLong(KEY_CACHE_TIMESTAMP, 0L)
    return timestamp > 0L && System.currentTimeMillis() - timestamp < CACHE_TTL_MS
  }

  fun getCachedInfo(context: Context): UpdateInfo? {
    val p = prefs(context)
    val version = p.getString(KEY_CACHE_VERSION, null)?.takeIf { it.isNotBlank() } ?: return null
    val url = p.getString(KEY_CACHE_URL, null)?.takeIf { it.startsWith("https://") } ?: return null
    return UpdateInfo(version, url)
  }

  suspend fun checkAndNotify(context: Context) {
    val result = fetchLatest(context)
    saveCache(context, result)
    if (result is UpdateResult.Available) UpdateNotifier.showUpdateAvailable(context, result.info)
  }

  fun enqueueDownload(context: Context, downloadUrl: String, version: String): Long {
    if (!downloadUrl.startsWith("https://")) return -1L
    cancelDownload(context)
    cleanupApk(context)

    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return -1L
    if (!dir.exists()) dir.mkdirs()
    val target = File(dir, "NovelRegEx-v$version.apk")
    if (target.exists()) target.delete()

    val request =
      DownloadManager.Request(Uri.parse(downloadUrl))
        .setTitle(context.getString(com.NovelRegEx.app.R.string.noti_download_title))
        .setDescription(context.getString(com.NovelRegEx.app.R.string.noti_download_desc, version))
        .setMimeType("application/vnd.android.package-archive")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        .setDestinationUri(Uri.fromFile(target))
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(true)

    return runCatching {
      val id = context.getSystemService(DownloadManager::class.java).enqueue(request)
      prefs(context).edit {
        putLong(KEY_DOWNLOAD_ID, id)
        putString(KEY_PENDING_URL, downloadUrl)
        putString(KEY_CACHE_VERSION, version)
        putString(KEY_CACHE_URL, downloadUrl)
        putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
      }
      id
    }.getOrDefault(-1L)
  }

  fun cancelDownload(context: Context) {
    val p = prefs(context)
    val id = p.getLong(KEY_DOWNLOAD_ID, -1L)
    if (id != -1L) runCatching { context.getSystemService(DownloadManager::class.java).remove(id) }
    p.edit { remove(KEY_DOWNLOAD_ID) }
  }

  fun isDownloadActive(context: Context): Boolean {
    val id = prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)
    if (id == -1L) return false
    return runCatching {
      context.getSystemService(DownloadManager::class.java)
        .query(DownloadManager.Query().setFilterById(id))
        ?.use { cursor ->
          if (!cursor.moveToFirst()) return@use false
          when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED -> true
            else -> false
          }
        } ?: false
    }.getOrDefault(false)
  }

  fun cleanupApk(context: Context) {
    val p = prefs(context)
    p.getString(KEY_APK_PATH, null)?.let { runCatching { File(it).delete() } }
    p.edit {
      remove(KEY_APK_PATH)
      remove(KEY_APK_VERSION)
    }
  }

  fun purgeInstalledApk(context: Context) {
    val p = prefs(context)
    val readyVersion = p.getString(KEY_APK_VERSION, null) ?: return
    if (compareVersions(readyVersion, BuildConfig.VERSION_NAME) <= 0) cleanupApk(context)
  }

  fun isApkReady(context: Context): Boolean {
    val path = prefs(context).getString(KEY_APK_PATH, null) ?: return false
    return File(path).isFile
  }

  fun processAppUpdate(context: Context) {
    purgeInstalledApk(context)
    if (!isDownloadActive(context)) prefs(context).edit { remove(KEY_DOWNLOAD_ID) }
  }

  fun createInstallIntent(context: Context, apkFile: File): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apkFile)
    return Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  private fun compareVersions(a: String, b: String): Int {
    val aa = a.removePrefix("v").split(Regex("[.-]")).map { it.toIntOrNull() ?: 0 }
    val bb = b.removePrefix("v").split(Regex("[.-]")).map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(aa.size, bb.size)) {
      val av = aa.getOrElse(i) { 0 }
      val bv = bb.getOrElse(i) { 0 }
      if (av != bv) return av.compareTo(bv)
    }
    return 0
  }
}
