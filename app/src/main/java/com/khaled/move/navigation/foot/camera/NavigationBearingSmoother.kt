package com.khaled.move.navigation.foot.camera

import com.khaled.move.navigation.foot.speed.MovementState
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.foot.route.GeoUtils

/** Produces stable map/user heading without wraparound spins or stationary jitter. */
class NavigationBearingSmoother {
    private var lastSnappedPoint: RoutePoint? = null
    private var smoothedBearing: Double? = null
    private var lastTimestampMillis: Long? = null

    fun reset() {
        lastSnappedPoint = null
        smoothedBearing = null
        lastTimestampMillis = null
    }

    fun update(
        snappedPoint: RoutePoint,
        routeBearingDegrees: Double?,
        gpsBearingDegrees: Float?,
        movementState: MovementState,
        timestampMillis: Long,
    ): Double? {
        val movementBearing = lastSnappedPoint
            ?.takeIf { GeoUtils.distanceMeters(it, snappedPoint) >= MIN_MOVEMENT_FOR_BEARING_METERS }
            ?.let { GeoUtils.bearingDegrees(it, snappedPoint) }

        val target = when (movementState) {
            MovementState.Walking -> movementBearing ?: routeBearingDegrees ?: gpsBearingDegrees?.toDouble()
            MovementState.GpsUncertain -> routeBearingDegrees ?: smoothedBearing ?: gpsBearingDegrees?.toDouble()
            MovementState.Stationary -> smoothedBearing ?: routeBearingDegrees ?: gpsBearingDegrees?.toDouble()
        } ?: return smoothedBearing

        val previous = smoothedBearing
        val result = if (previous == null) {
            GeoUtils.normalizeBearingDegrees(target)
        } else {
            val dtSeconds = ((timestampMillis - (lastTimestampMillis ?: timestampMillis)).coerceAtLeast(1L)) / 1000.0
            val maxTurn = when (movementState) {
                MovementState.Walking -> 95.0 * dtSeconds
                MovementState.GpsUncertain -> 30.0 * dtSeconds
                MovementState.Stationary -> 12.0 * dtSeconds
            }
            val delta = GeoUtils.shortestAngleDeltaDegrees(previous, target)
            if (kotlin.math.abs(delta) < BEARING_DEADBAND_DEGREES && movementState != MovementState.Walking) {
                previous
            } else {
                GeoUtils.normalizeBearingDegrees(previous + delta.coerceIn(-maxTurn, maxTurn))
            }
        }

        lastSnappedPoint = snappedPoint
        lastTimestampMillis = timestampMillis
        smoothedBearing = result
        return result
    }

    private companion object {
        const val MIN_MOVEMENT_FOR_BEARING_METERS = 2.5
        const val BEARING_DEADBAND_DEGREES = 7.0
    }
}
