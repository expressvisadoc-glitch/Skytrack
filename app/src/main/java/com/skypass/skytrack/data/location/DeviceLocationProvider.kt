package com.skypass.skytrack.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.skypass.skytrack.data.remote.LocationPayloadDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtEpochMs: Long,
    val ageMs: Long,
    val isMock: Boolean
) {
    fun toPayload(): LocationPayloadDto = LocationPayloadDto(
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracyMeters,
        capturedAt = Instant.ofEpochMilli(capturedAtEpochMs).toString(),
        ageMs = ageMs,
        isMock = isMock
    )
}

sealed interface LocationResult {
    data class Success(val fix: LocationFix) : LocationResult
    data object PermissionDenied : LocationResult
    data object ServicesDisabled : LocationResult
    data object Timeout : LocationResult
    data class Unavailable(val reason: String? = null) : LocationResult
}

/**
 * One-shot, foreground-only location access. SkyTrack never requests background location.
 */
interface DeviceLocationProvider {
    fun hasPermission(): Boolean
    fun hasPrecisePermission(): Boolean
    fun isLocationEnabled(): Boolean
    suspend fun getCurrentLocation(timeoutMs: Long = DEFAULT_TIMEOUT_MS): LocationResult

    companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}

@Singleton
class FusedDeviceLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedClient: FusedLocationProviderClient
) : DeviceLocationProvider {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasPermission(): Boolean =
        hasPrecisePermission() || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun hasPrecisePermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun isLocationEnabled(): Boolean =
        LocationManagerCompat.isLocationEnabled(locationManager)

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(timeoutMs: Long): LocationResult {
        if (!hasPermission()) return LocationResult.PermissionDenied
        if (!isLocationEnabled()) return LocationResult.ServicesDisabled

        val request = CurrentLocationRequest.Builder()
            .setPriority(
                if (hasPrecisePermission()) Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            )
            // Only accept a cached fix if it is very recent; otherwise a fresh fix is computed.
            .setMaxUpdateAgeMillis(MAX_CACHED_FIX_AGE_MS)
            .setDurationMillis(timeoutMs)
            .build()

        return try {
            // Wrapped so a timeout (null) can be told apart from "no fix available" (Attempt(null)).
            val attempt = withTimeoutOrNull(timeoutMs) {
                Attempt(awaitCurrentLocation(request))
            }
            when {
                attempt == null -> LocationResult.Timeout
                attempt.location == null -> LocationResult.Unavailable()
                else -> LocationResult.Success(attempt.location.toFix())
            }
        } catch (_: SecurityException) {
            LocationResult.PermissionDenied
        } catch (e: Exception) {
            LocationResult.Unavailable(e.message)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitCurrentLocation(request: CurrentLocationRequest): Location? =
        suspendCancellableCoroutine { continuation ->
            val cancellation = CancellationTokenSource()
            fusedClient.getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { location -> continuation.resume(location) }
                .addOnFailureListener { error -> continuation.resumeWithException(error) }
            continuation.invokeOnCancellation { cancellation.cancel() }
        }

    private fun Location.toFix(): LocationFix {
        // Age from the monotonic clock, so it is correct even if the device wall clock is wrong.
        val ageNanos = SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos
        return LocationFix(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
            capturedAtEpochMs = time,
            ageMs = maxOf(0L, ageNanos / 1_000_000L),
            isMock = LocationCompat.isMock(this)
        )
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private data class Attempt(val location: Location?)

    private companion object {
        const val MAX_CACHED_FIX_AGE_MS = 10_000L
    }
}
