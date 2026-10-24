package com.skypass.skytrack.data.location

import com.skypass.skytrack.data.remote.GeofenceConfigDto
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * In-app guidance about whether the employee appears to be inside the office geofence.
 * This is advisory only: the server re-reads the latest settings and makes the final decision.
 */
data class GeofenceFeedback(
    val kind: Kind,
    val distanceMeters: Int? = null,
    val radiusMeters: Int? = null,
    val accuracyMeters: Int? = null,
    val limit: Int? = null,
    val detail: String? = null
) {
    enum class Kind {
        CHECKING,
        INSIDE,
        OUTSIDE,
        POOR_ACCURACY,
        STALE_LOCATION,
        MOCK_LOCATION,
        NOT_CONFIGURED,
        INACTIVE,
        PERMISSION_REQUIRED,
        SERVICES_DISABLED,
        TIMEOUT,
        LOCATION_UNAVAILABLE,
        CONFIG_UNAVAILABLE,
        OFFLINE
    }
}

object GeofenceEvaluator {

    // Defaults mirror the server's office_geofence_settings defaults.
    const val DEFAULT_MAX_ACCURACY_METERS = 50
    const val DEFAULT_MAX_LOCATION_AGE_SECONDS = 120
    private const val EARTH_RADIUS_METERS = 6371008.8

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    /** NOT_CONFIGURED / INACTIVE when the settings block every check-in, otherwise null. */
    fun unavailableFeedback(config: GeofenceConfigDto): GeofenceFeedback? = when {
        !config.configured || config.latitude == null || config.longitude == null || config.radiusMeters == null ->
            GeofenceFeedback(GeofenceFeedback.Kind.NOT_CONFIGURED)
        !config.active -> GeofenceFeedback(GeofenceFeedback.Kind.INACTIVE)
        else -> null
    }

    fun evaluate(config: GeofenceConfigDto, fix: LocationFix): GeofenceFeedback {
        unavailableFeedback(config)?.let { return it }
        val latitude = config.latitude!!
        val longitude = config.longitude!!
        val radius = config.radiusMeters!!

        val maxAccuracy = config.maxAccuracyMeters ?: DEFAULT_MAX_ACCURACY_METERS
        val maxAgeSeconds = config.maxLocationAgeSeconds ?: DEFAULT_MAX_LOCATION_AGE_SECONDS
        val accuracy = fix.accuracyMeters.coerceAtMost(Int.MAX_VALUE.toFloat()).roundToInt()

        if (fix.isMock) {
            return GeofenceFeedback(GeofenceFeedback.Kind.MOCK_LOCATION)
        }
        if (fix.ageMs > maxAgeSeconds * 1000L) {
            return GeofenceFeedback(
                GeofenceFeedback.Kind.STALE_LOCATION,
                limit = maxAgeSeconds
            )
        }
        if (fix.accuracyMeters > maxAccuracy) {
            return GeofenceFeedback(
                GeofenceFeedback.Kind.POOR_ACCURACY,
                accuracyMeters = accuracy,
                limit = maxAccuracy
            )
        }

        val distance = distanceMeters(latitude, longitude, fix.latitude, fix.longitude).roundToInt()
        return GeofenceFeedback(
            kind = if (distance <= radius) GeofenceFeedback.Kind.INSIDE else GeofenceFeedback.Kind.OUTSIDE,
            distanceMeters = distance,
            radiusMeters = radius,
            accuracyMeters = accuracy
        )
    }

    fun fromLocationFailure(result: LocationResult): GeofenceFeedback = when (result) {
        LocationResult.PermissionDenied -> GeofenceFeedback(GeofenceFeedback.Kind.PERMISSION_REQUIRED)
        LocationResult.ServicesDisabled -> GeofenceFeedback(GeofenceFeedback.Kind.SERVICES_DISABLED)
        LocationResult.Timeout -> GeofenceFeedback(GeofenceFeedback.Kind.TIMEOUT)
        is LocationResult.Unavailable -> GeofenceFeedback(GeofenceFeedback.Kind.LOCATION_UNAVAILABLE, detail = result.reason)
        is LocationResult.Success -> GeofenceFeedback(GeofenceFeedback.Kind.CHECKING)
    }

    /** Message shown when a check-in / check-out cannot even be attempted because no fix was obtained. */
    fun actionBlockedMessage(result: LocationResult, isCheckIn: Boolean): String {
        val verb = if (isCheckIn) "check in" else "check out"
        return when (result) {
            LocationResult.PermissionDenied ->
                "Location permission is required to $verb. Please allow location access for SkyTrack and try again."
            LocationResult.ServicesDisabled ->
                "Location services are turned off. Please turn on location to $verb."
            LocationResult.Timeout ->
                "Could not get your location in time. Move to an open area or near a window and try again."
            is LocationResult.Unavailable ->
                "Your location is currently unavailable. Please check that location is on and try again."
            is LocationResult.Success -> "Unable to $verb. Please try again."
        }
    }
}
