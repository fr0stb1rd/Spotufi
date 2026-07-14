package io.github.sekademi.spotufi.data.update

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.sekademi.spotufi.BuildConfig
import io.github.sekademi.spotufi.data.preferences.getUpdateRepoUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object UpdateChecker {

    private const val TAG = "UpdateChecker"
    private const val KEY_SKIP = "skip_fingerprint"

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val fingerprint: String,
        val releaseBody: String,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val result = runCatching { fetchLatestRelease(context) }
            .onFailure { Log.d(TAG, "update check failed: ${it.message}") }
            .getOrNull() ?: return@withContext null

        if (!isNewer(result.version, BuildConfig.VERSION_NAME)) return@withContext null

        val skipPrefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        if (skipPrefs.getString(KEY_SKIP, null) == result.fingerprint) return@withContext null

        result
    }

    fun skipRelease(context: Context, info: UpdateInfo) {
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIP, info.fingerprint).apply()
    }

    private fun fetchLatestRelease(context: Context): UpdateInfo? {
        val repoUrl = getUpdateRepoUrl(context).trimEnd('/')
        val repoPath = repoUrl.removePrefix("https://github.com/").removePrefix("http://github.com/")
        val rawUrl = "https://raw.githubusercontent.com/$repoPath/main/latest.txt"

        val request = Request.Builder().url(rawUrl).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body.string().trim()
        val version = body.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: return null

        val tag = "v$version"
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: ""
        val apkUrl = listOf(abi, "universal").firstNotNullOfOrNull { a ->
            "https://github.com/$repoPath/releases/download/$tag/app-$a-release.apk"
                .takeIf { urlExists(it) }
        } ?: "$repoUrl/releases/tag/$tag"

        return UpdateInfo(
            version = version,
            downloadUrl = apkUrl,
            fingerprint = "v$version",
            releaseBody = "",
        )
    }

    private fun urlExists(url: String): Boolean {
        return try {
            val req = Request.Builder().url(url).head().build()
            client.newCall(req).execute().isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    private fun isNewer(remote: String, installed: String): Boolean =
        remote != installed
}
