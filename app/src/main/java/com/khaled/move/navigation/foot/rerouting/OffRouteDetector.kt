package com.khaled.move.navigation.foot.rerouting

import com.khaled.move.navigation.foot.speed.MovementState
import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.route.GeoUtils

class OffRouteDetector {
    private var consecutiveSuspiciousSamples: Int = 0
    private var firstSuspiciousTimestampMillis: Long? = null

    fun reset() {
        consecutiveSuspiciousSamples = 0
        firstSuspiciousTimestampMillis = null
    }

    fun evaluate(
        location: NavigationLocation,
        distanceFromRouteMeters: Double,
        routeBearingDegrees: Double?,
        movementState: MovementState,
    ): OffRouteResult {
        val accuracy = (location.accuracyMeters ?: 12f).coerceAtLeast(6f).toDouble()
        if (movementState != MovementState.Walking || accuracy > MAX_REROUTE_ACCURACY_METERS) {
            return OffRouteResult(false, distanceFromRouteMeters)
        }

        val warningThreshold = accuracy + 18.0
        val confirmationThreshold = accuracy + 32.0
        val bearingMismatch = routeBearingDegrees != null && location.bearingDegrees != null &&
                GeoUtils.angularDifferenceDegrees(routeBearingDegrees, location.bearingDegrees.toDouble()) > 80.0

        val onRouteThreshold = accuracy * 0.9
        if (distanceFromRouteMeters <= onRouteThreshold) {
            reset()
            return OffRouteResult(false, distanceFromRouteMeters)
        }

        val suspicious = distanceFromRouteMeters > confirmationThreshold ||
                (distanceFromRouteMeters > warningThreshold && bearingMismatch)
        if (suspicious) {
            consecutiveSuspiciousSamples += 1
            if (firstSuspiciousTimestampMillis == null) {
                firstSuspiciousTimestampMillis = location.timestampMillis
            }
        } else if (distanceFromRouteMeters < warningThreshold) {
            consecutiveSuspiciousSamples = (consecutiveSuspiciousSamples - 1).coerceAtLeast(0)
            if (consecutiveSuspiciousSamples == 0) firstSuspiciousTimestampMillis = null
        }

        val suspiciousDurationMillis = firstSuspiciousTimestampMillis?.let { location.timestampMillis - it } ?: 0L
        return OffRouteResult(
            offRoute = consecutiveSuspiciousSamples >= MIN_SUSPICIOUS_SAMPLES &&
                    suspiciousDurationMillis >= MIN_SUSPICIOUS_DURATION_MILLIS,
            distanceFromRouteMeters = distanceFromRouteMeters,
        )
    }

    private companion object {
        const val MAX_REROUTE_ACCURACY_METERS = 55.0
        const val MIN_SUSPICIOUS_SAMPLES = 4
        const val MIN_SUSPICIOUS_DURATION_MILLIS = 7_000L
    }
}

data class OffRouteResult(
    val offRoute: Boolean,
    val distanceFromRouteMeters: Double,
)
