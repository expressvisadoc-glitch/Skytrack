package com.skypass.skytrack

import com.skypass.skytrack.data.remote.GithubReleaseApi
import com.skypass.skytrack.data.remote.GithubReleaseAssetDto
import com.skypass.skytrack.data.remote.GithubReleaseDto
import com.skypass.skytrack.data.repository.AppUpdateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class AppUpdateRepositoryTest {

    private class FakeGithubReleaseApi(
        var latestReleaseResult: (() -> GithubReleaseDto)? = null,
        var releasesListResult: (() -> List<GithubReleaseDto>)? = null
    ) : GithubReleaseApi {
        override suspend fun getLatestRelease(): GithubReleaseDto {
            return latestReleaseResult?.invoke() ?: throw IOException("Not configured")
        }

        override suspend fun getReleases(): List<GithubReleaseDto> {
            return releasesListResult?.invoke() ?: emptyList()
        }
    }

    @Test
    fun testNewerReleaseAvailable() = runBlocking {
        val fakeApi = FakeGithubReleaseApi(
            latestReleaseResult = {
                GithubReleaseDto(
                    id = 123L,
                    tagName = "v1.1.0",
                    name = "SkyTrack v1.1.0",
                    body = "### What's Changed\n- Offline sync added\n- Bug fixes",
                    htmlUrl = "https://github.com/expressvisadoc-glitch/Skytrack/releases/tag/v1.1.0",
                    draft = false,
                    prerelease = false,
                    publishedAt = "2026-10-15T10:00:00Z",
                    assets = listOf(
                        GithubReleaseAssetDto(
                            name = "SkyTrack-v1.1.0.apk",
                            browserDownloadUrl = "https://github.com/expressvisadoc-glitch/Skytrack/releases/download/v1.1.0/SkyTrack-v1.1.0.apk"
                        )
                    )
                )
            }
        )

        val repository = AppUpdateRepository(fakeApi)
        val updateInfo = repository.checkForUpdate("1.0.0")

        assertNotNull(updateInfo)
        assertEquals("1.1.0", updateInfo?.versionName)
        assertEquals("SkyTrack v1.1.0", updateInfo?.releaseTitle)
        assertEquals("https://github.com/expressvisadoc-glitch/Skytrack/releases/tag/v1.1.0", updateInfo?.releaseUrl)
        assertEquals("https://github.com/expressvisadoc-glitch/Skytrack/releases/download/v1.1.0/SkyTrack-v1.1.0.apk", updateInfo?.downloadUrl)
        assertEquals("### What's Changed\n- Offline sync added\n- Bug fixes", updateInfo?.releaseNotes)
    }

    @Test
    fun testSameOrOlderVersionReturnsNull() = runBlocking {
        val fakeApi = FakeGithubReleaseApi(
            latestReleaseResult = {
                GithubReleaseDto(
                    tagName = "v1.0.0",
                    name = "SkyTrack v1.0.0",
                    draft = false,
                    prerelease = false
                )
            }
        )

        val repository = AppUpdateRepository(fakeApi)
        val updateInfo = repository.checkForUpdate("1.0.0")

        assertNull(updateInfo)
    }

    @Test
    fun testDraftOrPrereleaseIgnored() = runBlocking {
        val fakeApi = FakeGithubReleaseApi(
            latestReleaseResult = {
                GithubReleaseDto(
                    tagName = "v2.0.0",
                    name = "SkyTrack v2.0.0-beta",
                    draft = false,
                    prerelease = true
                )
            },
            releasesListResult = {
                listOf(
                    GithubReleaseDto(tagName = "v2.0.0", draft = false, prerelease = true),
                    GithubReleaseDto(tagName = "v1.0.0", draft = false, prerelease = false)
                )
            }
        )

        val repository = AppUpdateRepository(fakeApi)
        val updateInfo = repository.checkForUpdate("1.0.0")

        assertNull(updateInfo)
    }

    @Test
    fun testNetworkOrOfflineErrorReturnsNullGracefully() = runBlocking {
        val fakeApi = FakeGithubReleaseApi(
            latestReleaseResult = { throw IOException("Unable to resolve host: api.github.com") },
            releasesListResult = { throw IOException("Network is unreachable") }
        )

        val repository = AppUpdateRepository(fakeApi)
        val updateInfo = repository.checkForUpdate("1.0.0")

        // Must not throw, returns null gracefully
        assertNull(updateInfo)
    }
}
