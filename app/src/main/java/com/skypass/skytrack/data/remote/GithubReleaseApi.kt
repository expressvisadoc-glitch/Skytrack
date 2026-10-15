package com.skypass.skytrack.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Headers

/**
 * Data transfer object representing an asset attached to a GitHub Release.
 */
data class GithubReleaseAssetDto(
    @SerializedName("name")
    val name: String? = null,

    @SerializedName("browser_download_url")
    val browserDownloadUrl: String? = null,

    @SerializedName("content_type")
    val contentType: String? = null,

    @SerializedName("size")
    val size: Long? = null
)

/**
 * Data transfer object representing a GitHub Release.
 * Matches GitHub REST API response for /repos/{owner}/{repo}/releases.
 */
data class GithubReleaseDto(
    @SerializedName("id")
    val id: Long? = null,

    @SerializedName("tag_name")
    val tagName: String? = null,

    @SerializedName("name")
    val name: String? = null,

    @SerializedName("body")
    val body: String? = null,

    @SerializedName("html_url")
    val htmlUrl: String? = null,

    @SerializedName("draft")
    val draft: Boolean = false,

    @SerializedName("prerelease")
    val prerelease: Boolean = false,

    @SerializedName("published_at")
    val publishedAt: String? = null,

    @SerializedName("assets")
    val assets: List<GithubReleaseAssetDto>? = null
)

/**
 * Retrofit interface for accessing GitHub's public Releases API.
 */
interface GithubReleaseApi {

    /**
     * Fetches the latest published release (ignores drafts and prereleases automatically).
     */
    @GET("repos/expressvisadoc-glitch/Skytrack/releases/latest")
    @Headers(
        "User-Agent: SkyTrack-Android-App",
        "Accept: application/vnd.github+json"
    )
    suspend fun getLatestRelease(): GithubReleaseDto

    /**
     * Lists recent releases for the repository.
     */
    @GET("repos/expressvisadoc-glitch/Skytrack/releases")
    @Headers(
        "User-Agent: SkyTrack-Android-App",
        "Accept: application/vnd.github+json"
    )
    suspend fun getReleases(): List<GithubReleaseDto>
}
