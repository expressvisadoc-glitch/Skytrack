package com.skypass.skytrack.data.repository

import com.skypass.skytrack.BuildConfig
import com.skypass.skytrack.data.local.ConnectivityObserver
import com.skypass.skytrack.data.remote.ApiErrorExtractor
import com.skypass.skytrack.data.remote.AttendanceRequest
import com.skypass.skytrack.data.remote.GeofenceConfigDto
import com.skypass.skytrack.data.remote.SkyTrackApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the office geofence settings for in-app guidance. The result is intentionally not
 * cached: the backend re-reads the latest settings for every check-in and check-out.
 */
@Singleton
class GeofenceRepository @Inject constructor(
    private val api: SkyTrackApi,
    private val connectivityObserver: ConnectivityObserver
) {
    fun isOnline(): Boolean = connectivityObserver.isConnected()

    suspend fun fetchConfig(token: String): Result<GeofenceConfigDto> = runCatching {
        val response = try {
            api.geofenceConfig(
                BuildConfig.SKYTRACK_API_URL,
                "Bearer $token",
                AttendanceRequest("geofence_config", token)
            )
        } catch (e: Exception) {
            throw AttendanceApiException(
                code = "GEOFENCE_CONFIG_UNAVAILABLE",
                message = ApiErrorExtractor.getMessage(e, "Unable to load the office location settings.")
            )
        }

        if (!response.success || response.geofence == null) {
            throw AttendanceApiException(
                code = response.error,
                message = ApiErrorExtractor.cleanErrorString(
                    response.message ?: "Unable to load the office location settings.",
                    "Unable to load the office location settings."
                )
            )
        }
        response.geofence
    }
}
