package com.mattbrady.checklist.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

/**
 * Checks GitHub Releases for a newer build than the one currently installed.
 * build-apk.yml names each release "v<build number> (<commit>)", where the
 * build number is the same versionCode baked into that build's APK, so we
 * can compare it directly against BuildConfig.VERSION_CODE.
 */
object UpdateChecker {

    data class UpdateInfo(
        val versionCode: Int,
        val versionLabel: String,
        val downloadUrl: String,
    )

    private const val RELEASES_URL =
        "https://api.github.com/repos/MattBEng/Phone-Note_Checklist/releases"

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns update info if a newer build is available, otherwise null (including on any error). */
    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("User-Agent", "ChecklistApp")
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val releases = json.decodeFromString<List<GithubRelease>>(body)
                val latest = releases.firstOrNull() ?: return@withContext null

                val label = latest.name ?: latest.tagName
                val match = Regex("""v(\d+)""").find(label) ?: return@withContext null
                val versionCode = match.groupValues[1].toIntOrNull() ?: return@withContext null

                if (versionCode <= currentVersionCode) return@withContext null

                val apkAsset = latest.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext null

                // Just "v9", not the full release name (which also has the long
                // commit hash tacked on) - keeps the update banner short enough
                // to fit on screen next to the Update button.
                UpdateInfo(versionCode, match.value, apkAsset.browserDownloadUrl)
            }
        } catch (e: Exception) {
            null
        }
    }
}
