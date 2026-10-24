package com.skypass.skytrack

import com.skypass.skytrack.data.location.GeofenceEvaluator
import com.skypass.skytrack.data.location.GeofenceFeedback.Kind
import com.skypass.skytrack.data.location.LocationFix
import com.skypass.skytrack.data.location.LocationResult
import com.skypass.skytrack.data.remote.GeofenceConfigDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceEvaluatorTest {

    private val officeLat = 9.9312
    private val officeLng = 76.2673

    private val activeConfig = GeofenceConfigDto(
        configured = true,
        active = true,
        latitude = officeLat,
        longitude = officeLng,
        radiusMeters = 100,
        maxAccuracyMeters = 50,
        maxLocationAgeSeconds = 120
    )

    private fun fixNorthOfOffice(
        meters: Double,
        accuracy: Float = 15f,
        ageMs: Long = 1_000L,
        isMock: Boolean = false
    ) = LocationFix(
        latitude = officeLat + meters / 111195.0,
        longitude = officeLng,
        accuracyMeters = accuracy,
        capturedAtEpochMs = 1_760_000_000_000L,
        ageMs = ageMs,
        isMock = isMock
    )

    @Test
    fun distanceMatchesKnownOffsets() {
        assertEquals(0.0, GeofenceEvaluator.distanceMeters(officeLat, officeLng, officeLat, officeLng), 0.001)
        val oneKm = GeofenceEvaluator.distanceMeters(officeLat, officeLng, officeLat + 1000 / 111195.0, officeLng)
        assertEquals(1000.0, oneKm, 1.0)
    }

    @Test
    fun insideAndOutsideRadius() {
        val inside = GeofenceEvaluator.evaluate(activeConfig, fixNorthOfOffice(40.0))
        assertEquals(Kind.INSIDE, inside.kind)
        assertEquals(40, inside.distanceMeters)
        assertEquals(100, inside.radiusMeters)

        val edge = GeofenceEvaluator.evaluate(activeConfig, fixNorthOfOffice(100.0))
        assertEquals(Kind.INSIDE, edge.kind)

        val outside = GeofenceEvaluator.evaluate(activeConfig, fixNorthOfOffice(150.0))
        assertEquals(Kind.OUTSIDE, outside.kind)
        assertEquals(150, outside.distanceMeters)
    }

    @Test
    fun radiusChangeIsReflectedImmediately() {
        val widened = activeConfig.copy(radiusMeters = 200)
        assertEquals(Kind.INSIDE, GeofenceEvaluator.evaluate(widened, fixNorthOfOffice(150.0)).kind)
    }

    @Test
    fun poorAccuracyIsReportedEvenWhenPointLooksInside() {
        val result = GeofenceEvaluator.evaluate(activeConfig, fixNorthOfOffice(10.0, accuracy = 80f))
        assertEquals(Kind.POOR_ACCURACY, result.kind)
        assertEquals(80, result.accuracyMeters)
        assertEquals(50, result.limit)
    }

    @Test
    fun staleAndMockFixes() {
        assertEquals(Kind.STALE_LOCATION, GeofenceEvaluator.evaluate(activeConfig, fixNorthOfOffice(10.0, ageMs = 121_000L)).kind)
        assertEquals(Kind.MOCK_LOCATION, GeofenceEvaluator.evaluate(activeConfig, fixNorthOfOffice(10.0, isMock = true)).kind)
    }

    @Test
    fun missingOrInactiveConfiguration() {
        assertEquals(Kind.NOT_CONFIGURED, GeofenceEvaluator.unavailableFeedback(GeofenceConfigDto(configured = false))?.kind)
        assertEquals(Kind.NOT_CONFIGURED, GeofenceEvaluator.unavailableFeedback(activeConfig.copy(latitude = null))?.kind)
        assertEquals(Kind.INACTIVE, GeofenceEvaluator.unavailableFeedback(activeConfig.copy(active = false))?.kind)
        assertNull(GeofenceEvaluator.unavailableFeedback(activeConfig))
        assertEquals(Kind.INACTIVE, GeofenceEvaluator.evaluate(activeConfig.copy(active = false), fixNorthOfOffice(0.0)).kind)
    }

    @Test
    fun serverDefaultsUsedWhenLimitsMissing() {
        val noLimits = activeConfig.copy(maxAccuracyMeters = null, maxLocationAgeSeconds = null)
        assertEquals(Kind.INSIDE, GeofenceEvaluator.evaluate(noLimits, fixNorthOfOffice(10.0, accuracy = 50f)).kind)
        assertEquals(Kind.POOR_ACCURACY, GeofenceEvaluator.evaluate(noLimits, fixNorthOfOffice(10.0, accuracy = 51f)).kind)
    }

    @Test
    fun locationFailuresMapToFeedbackAndMessages() {
        assertEquals(Kind.PERMISSION_REQUIRED, GeofenceEvaluator.fromLocationFailure(LocationResult.PermissionDenied).kind)
        assertEquals(Kind.SERVICES_DISABLED, GeofenceEvaluator.fromLocationFailure(LocationResult.ServicesDisabled).kind)
        assertEquals(Kind.TIMEOUT, GeofenceEvaluator.fromLocationFailure(LocationResult.Timeout).kind)
        assertEquals(Kind.LOCATION_UNAVAILABLE, GeofenceEvaluator.fromLocationFailure(LocationResult.Unavailable("x")).kind)

        assertTrue(GeofenceEvaluator.actionBlockedMessage(LocationResult.PermissionDenied, isCheckIn = true).contains("check in"))
        assertTrue(GeofenceEvaluator.actionBlockedMessage(LocationResult.ServicesDisabled, isCheckIn = false).contains("check out"))
    }

    @Test
    fun payloadCarriesFixMetadata() {
        val payload = fixNorthOfOffice(10.0, accuracy = 12.5f, ageMs = 2_500L).toPayload()
        assertEquals(12.5f, payload.accuracy)
        assertEquals(2_500L, payload.ageMs)
        assertEquals("android", payload.platform)
        assertEquals("2025-10-09T08:53:20Z", payload.capturedAt)
    }
}
