package com.skypass.skytrack.data.repository

import android.util.Log
import com.skypass.skytrack.data.remote.GithubReleaseApi
import com.skypass.skytrack.data.remote.GithubReleaseDto
import com.skypass.skytrack.util.VersionComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain model representing an available app update.
 */
data class AppUpdateInfo(
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String?,
    val releaseUrl: String,
    val downloadUrl: String?,
    val publishedAt: String? = null
)

@Singleton
class AppUpdateRepository @Inject constructor(
    private val githubReleaseApi: GithubReleaseApi
) {

    private val tag = "AppUpdateRepository"

    /**
     * Checks whether a newer release is published on GitHub.
     * Returns [AppUpdateInfo] if a newer stable release is available, or null if up to date or on failure.
     * Never throws exceptions to guarantee the app is never interrupted or blocked.
     */
    suspend fun checkForUpdate(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val release = fetchLatestStableRelease() ?: return@withContext null

            val remoteTag = release.tagName ?: release.name ?: return@withContext null
            val isNewer = VersionComparator.isNewer(remoteTag, currentVersion)

            Log.d(tag, "Version check: installed='$currentVersion', remote='$remoteTag', isNewer=$isNewer")

            if (!isNewer) {
                return@withContext null
            }

            // Find APK asset URL if attached to the release
            val apkAsset = release.assets?.firstOrNull { asset ->
                asset.name?.endsWith(".apk", ignoreCase = true) == true
            }

            val releaseUrl = release.htmlUrl?.takeIf { it.isNotBlank() }
                ?: "https://github.com/expressvisadoc-glitch/Skytrack/releases"

            val downloadUrl = apkAsset?.browserDownloadUrl ?: releaseUrl

            val displayVersion = VersionComparator.cleanVersionName(remoteTag)
            val displayTitle = release.name?.takeIf { it.isNotBlank() } ?: "SkyTrack v$displayVersion"

            AppUpdateInfo(
                versionName = displayVersion,
                releaseTitle = displayTitle,
                releaseNotes = release.body?.takeIf { it.isNotBlank() },
                releaseUrl = releaseUrl,
                downloadUrl = downloadUrl,
                publishedAt = release.publishedAt
            )
        } catch (e: Exception) {
            // Gracefully catch all network errors, offline states, 404s, or parse errors
            Log.d(tag, "Update check failed or offline: ${e.message}")
            null
        }
    }

    private suspend fun fetchLatestStableRelease(): GithubReleaseDto? {
        // 1. Try /releases/latest first (standard GitHub endpoint for latest non-draft, non-prerelease)
        try {
            val latest = githubReleaseApi.getLatestRelease()
            if (!latest.draft && !latest.prerelease && !latest.tagName.isNullOrBlank()) {
                return latest
            }
        } catch (e: Exception) {
            Log.d(tag, "/releases/latest unavailable, attempting fallback to releases list: ${e.message}")
        }

        // 2. Fallback to /releases list and find highest stable release
        try {
            val releases = githubReleaseApi.getReleases()
            val stableReleases = releases.filter { !it.draft && !it.prerelease && !it.tagName.isNullOrBlank() }
            return stableReleases.maxWithOrNull { a, b ->
                val aVer = a.tagName ?: ""
                val bVer = b.tagName ?: ""
                if (VersionComparator.isNewer(aVer, bVer)) 1
                else if (VersionComparator.isNewer(bVer, aVer)) -1
                else 0
            } ?: stableReleases.firstOrNull()
        } catch (e: Exception) {
            Log.d(tag, "/releases list failed: ${e.message}")
        }

        return null
    }
}
