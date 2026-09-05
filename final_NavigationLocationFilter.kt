package com.khaled.move.navigation.foot.location

import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.foot.route.GeoUtils
import kotlin.math.exp

/**
 * Converts noisy raw GPS fixes into a responsive navigation fix.
 *
 * This implementation considers multiple factors for robust filtering:
 * 1. Reported GPS accuracy
 * 2. Distance between consecutive fixes
 * 3. Elapsed time between fixes
 * 4. Implied speed
 * 5. Previous filtered movement
 * 6. Stationary detection
 * 7. Timestamp irregularities
 *
 * This is intentionally lightweight: reject unusable/spiky fixes, then apply a
 * timestamp-aware low-pass filter. Good GPS follows quickly; poor GPS moves more
 * cautiously so the map does not visibly bounce between neighboring positions.
 */
class NavigationLocationFilter {
    private var lastRawLocation: NavigationLocation? = null
    private var smoothedLocation: NavigationLocation? = null
    private var lastValidLocation: NavigationLocation? = null
    private var consecutiveRejectedSamples: Int = 0

    fun reset() {
        lastRawLocation = null
        smoothedLocation = null
        lastValidLocation = null
        consecutiveRejectedSamples = 0
    }

    fun onLocation(location: NavigationLocation): NavigationLocation? {
        // Handle invalid timestamp or coordinate data
        if (location.timestampMillis <= 0 ||
            location.latitude.isNaN() || location.latitude.isInfinite() ||
            location.longitude.isNaN() || location.longitude.isInfinite()
        ) {
            return smoothedLocation
        }

        val accuracy = location.accuracyMeters

        // Check if we should completely reject this location based on accuracy
        if (accuracy != null && accuracy > MAX_USABLE_ACCURACY_METERS) {
            lastRawLocation = location
            consecutiveRejectedSamples++
            return smoothedLocation
        }

        val previousRaw = lastRawLocation
        lastRawLocation = location

        // Handle first location case
        if (previousRaw == null) {
            smoothedLocation = location
            lastValidLocation = location
            consecutiveRejectedSamples = 0
            return location
        }

        // Calculate time delta with safety checks for invalid timestamps
        val dtMillis = location.timestampMillis - previousRaw.timestampMillis
        if (dtMillis <= 0) {
            // Invalid or duplicate timestamp, use previous smoothed location
            return smoothedLocation
        }
        val dtSeconds = dtMillis / 1000.0

        // Calculate distance between consecutive locations
        val rawDistance = GeoUtils.distanceMeters(previousRaw, location)

        // Check for physically impossible movement (speed spike)
        val rawSpeed = rawDistance / dtSeconds

        // Compute thresholds based on accuracy and movement characteristics
        val accuracyThreshold = accuracy?.toDouble() ?: 12.0
        val previousAccuracy = previousRaw.accuracyMeters?.toDouble() ?: 12.0
        val combinedAccuracy = accuracyThreshold + previousAccuracy

        // Adaptive spike detection that considers GPS quality
        val adaptiveSpikeThreshold = when {
            accuracy != null -> {
                // For better accuracy, use more sensitive thresholds
                if (accuracy <= 8f) 12.0  // Very good GPS
                else if (accuracy <= 20f) 10.0  // Good GPS
                else if (accuracy <= 40f) 8.0   // Fair GPS
                else 6.0                        // Poor GPS - be more tolerant
            }

            else -> 8.0  // Default threshold for unknown accuracy
        }

        val spikeAllowance = combinedAccuracy + 20.0

        // More sophisticated spike detection that considers multiple factors
        val isSpike = rawSpeed > adaptiveSpikeThreshold &&
                rawDistance > spikeAllowance

        // Additional checks for suspicious movement patterns
        val bearingChange = if (previousRaw.bearingDegrees != null && location.bearingDegrees != null) {
            GeoUtils.angularDifferenceDegrees(
                previousRaw.bearingDegrees.toDouble(),
                location.bearingDegrees.toDouble()
            )
        } else 0.0

        // If we detect a significant spike, assess how to handle it
        if (isSpike) {
            // If the user was previously stationary and now makes a large jump,
            // or if bearing changes dramatically with speed, reject this location
            val isMovementPatternSuspicious = bearingChange > 120.0 && rawSpeed > adaptiveSpikeThreshold * 1.5

            if (isMovementPatternSuspicious) {
                consecutiveRejectedSamples++
                return smoothedLocation
            }
        }

        // Check for stationary state - if movement is very small, consider it as potentially noise
        val isStationary = rawDistance < (accuracyThreshold * 0.3).coerceAtLeast(1.0)

        // For very low accuracy measurements, be more cautious about accepting movement
        val shouldAcceptMovement = when {
            accuracy == null -> true  // No accuracy data, accept it
            accuracy <= 8f -> true   // Very good GPS - normal acceptance
            accuracy <= 20f -> rawSpeed < 6.0 || rawDistance > (accuracyThreshold * 0.5)
            accuracy <= 40f -> rawSpeed < 4.0 || rawDistance > (accuracyThreshold * 0.7)
            else -> rawSpeed < 3.0 || rawDistance > (accuracyThreshold * 1.0)  // Poor GPS - only accept if distance is significant
        }

        if (!shouldAcceptMovement) {
            consecutiveRejectedSamples++
            return smoothedLocation
        }

        // Reset rejection counter for valid samples
        consecutiveRejectedSamples = maxOf(0, consecutiveRejectedSamples - 1)

        // Apply low-pass filter with adaptive alpha based on accuracy and time
        val previousSmooth = smoothedLocation ?: run {
            smoothedLocation = location
            lastValidLocation = location
            return location
        }

        // Calculate adaptive smoothing factor based on accuracy and time
        val dtSecondsForSmoothing =
            ((location.timestampMillis - previousSmooth.timestampMillis).coerceAtLeast(1L)) / 1000.0
        val accuracyFactor = when {
            accuracy == null -> 1.0
            accuracy <= 8f -> 1.0
            accuracy <= 20f -> 0.75
            accuracy <= 40f -> 0.55
            else -> 0.35
        }

        // Use time-based adaptive alpha with additional consideration for GPS quality
        val alpha = (1.0 - exp(-dtSecondsForSmoothing / SMOOTHING_TIME_CONSTANT_SECONDS)) * accuracyFactor

        // Further refine alpha to prevent excessive smoothing when GPS is poor
        val refinedAlpha = if (accuracy != null && accuracy > 40f) {
            // For poor GPS, use less smoothing to maintain responsiveness
            alpha.coerceAtMost(0.6)
        } else {
            alpha.coerceIn(0.18, 0.85)
        }

        val smoothedPoint = GeoUtils.interpolate(
            RoutePoint(previousSmooth.latitude, previousSmooth.longitude),
            RoutePoint(location.latitude, location.longitude),
            refinedAlpha,
        )

        // Create the new smoothed location
        val filteredLocation = location.copy(
            latitude = smoothedPoint.latitude,
            longitude = smoothedPoint.longitude,
        ).also {
            smoothedLocation = it
            lastValidLocation = it
        }

        return filteredLocation
    }

    private companion object {
        const val MAX_USABLE_ACCURACY_METERS = 85f
        const val SMOOTHING_TIME_CONSTANT_SECONDS = 1.6

        /**
         * Maximum plausible walking speed in meters per second.
         * This is used to detect and filter out impossible GPS spikes.
         */
        private const val MAX_PLAUSIBLE_WALKING_SPEED_METERS_PER_SECOND = 3.0

        /**
         * Minimum acceptable distance for a location to be considered as valid movement.
         * Helps filter out GPS noise when user is stationary.
         */
        private const val MIN_MOVEMENT_DISTANCE_METERS = 0.5
    }
}
