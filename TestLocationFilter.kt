// This file contains tests for our enhanced NavigationLocationFilter implementation

import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.location.NavigationLocationFilter
import org.junit.Test
import org.junit.Assert.*

class TestLocationFilter {

    @Test
    fun testNormalForwardWalking() {
        val filter = NavigationLocationFilter()

        // Create a sequence of normal walking locations
        val loc1 = NavigationLocation(
            latitude = 40.7128,
            longitude = -74.0060,
            timestampMillis = 1000,
            accuracyMeters = 8f,
            bearingDegrees = 45f,
            speedMetersPerSecond = 1.3f
        )

        val loc2 = NavigationLocation(
            latitude = 40.7130,
            longitude = -74.0058,
            timestampMillis = 2000,
            accuracyMeters = 10f,
            bearingDegrees = 46f,
            speedMetersPerSecond = 1.2f
        )

        val filtered1 = filter.onLocation(loc1)
        assertNotNull(filtered1)
        assertEquals(loc1.latitude, filtered1!!.latitude, 0.00001)
        assertEquals(loc1.longitude, filtered1.longitude, 0.00001)

        val filtered2 = filter.onLocation(loc2)
        assertNotNull(filtered2)
        // Should be filtered but still valid
    }

    @Test
    fun testSmallGPSJitterWhileWalking() {
        val filter = NavigationLocationFilter()

        val baseLoc = NavigationLocation(
            latitude = 40.7128,
            longitude = -74.0060,
            timestampMillis = 1000,
            accuracyMeters = 12f,
            bearingDegrees = 45f,
            speedMetersPerSecond = 1.3f
        )

        // Create noisy locations around base
        val loc1 = baseLoc.copy(latitude = 40.71281, longitude = -74.00601) // Small jitter
        val loc2 = baseLoc.copy(latitude = 40.71279, longitude = -74.00599) // More jitter

        val filtered1 = filter.onLocation(loc1)
        assertNotNull(filtered1)

        val filtered2 = filter.onLocation(loc2)
        assertNotNull(filtered2)
    }

    @Test
    fun testStationaryGPSNoise() {
        val filter = NavigationLocationFilter()

        val baseLoc = NavigationLocation(
            latitude = 40.7128,
            longitude = -74.0060,
            timestampMillis = 1000,
            accuracyMeters = 30f, // Poor accuracy
            bearingDegrees = 45f,
            speedMetersPerSecond = null
        )

        // Create locations that are very close to each other (noise)
        val loc1 = baseLoc.copy(latitude = 40.712801, longitude = -74.006001) // Very small movement
        val loc2 = baseLoc.copy(latitude = 40.712802, longitude = -74.006002) // Slightly more noise

        val filtered1 = filter.onLocation(loc1)
        assertNotNull(filtered1)

        val filtered2 = filter.onLocation(loc2)
        assertNotNull(filtered2)
    }

    @Test
    fun testPoorGPSAccuracy() {
        val filter = NavigationLocationFilter()

        val loc1 = NavigationLocation(
            latitude = 40.7128,
            longitude = -74.0060,
            timestampMillis = 1000,
            accuracyMeters = 80f, // Very poor accuracy
            bearingDegrees = 45f,
            speedMetersPerSecond = null
        )

        val loc2 = NavigationLocation(
            latitude = 40.7130,
            longitude = -74.0058,
            timestampMillis = 2000,
            accuracyMeters = 85f, // Even worse accuracy
            bearingDegrees = 46f,
            speedMetersPerSecond = null
        )

        val filtered1 = filter.onLocation(loc1)
        assertNotNull(filtered1)

        val filtered2 = filter.onLocation(loc2)
        assertNotNull(filtered2)
    }

    @Test
    fun testImpossiblLargeGPSJump() {
        val filter = NavigationLocationFilter()

        val loc1 = NavigationLocation(
            latitude = 40.7128,
            longitude = -74.0060,
            timestampMillis = 1000,
            accuracyMeters = 10f,
            bearingDegrees = 45f,
            speedMetersPerSecond = null
        )

        // Large jump that should be rejected (impossible for walking)
        val loc2 = NavigationLocation(
            latitude = 40.7500, // Jump of ~4km
            longitude = -74.0500,
            timestampMillis = 2000,
            accuracyMeters = 12f,
            bearingDegrees = 46f,
            speedMetersPerSecond = null
        )

        val filtered1 = filter.onLocation(loc1)
        assertNotNull(filtered1)

        val filtered2 = filter.onLocation(loc2)
        // Should return previous location since jump was rejected
        assertEquals(filtered1, filtered2)
    }

    @Test
    fun testInvalidTimestampHandling() {
        val filter = NavigationLocationFilter()

        val loc1 = NavigationLocation(
            latitude = 40.7128,
            longitude = -74.0060,
            timestampMillis = 0, // Invalid timestamp
            accuracyMeters = 10f,
            bearingDegrees = 45f,
            speedMetersPerSecond = null
        )

        val filtered1 = filter.onLocation(loc1)
        assertNull(filtered1) // Should return null for invalid input

        val loc2 = NavigationLocation(
            latitude = 40.7130,
            longitude = -74.0058,
            timestampMillis = 1000,
            accuracyMeters = 10f,
            bearingDegrees = 46f,
            speedMetersPerSecond = null
        )

        val filtered2 = filter.onLocation(loc2)
        assertNotNull(filtered2) // Should accept valid location
    }
}
