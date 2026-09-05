package com.khaled.move.navigation.foot.eta

import java.time.Clock
import java.time.Instant

class EtaCalculator(
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private var previousRemainingSeconds: Double? = null

    fun reset() {
        previousRemainingSeconds = null
    }

    fun calculate(remainingDistanceMeters: Double, speedMetersPerSecond: Double): EtaEstimate {
        val effectiveSpeed = when {
            remainingDistanceMeters <= 0.0 -> 0.0
            speedMetersPerSecond < 0.2 -> STATIONARY_EFFECTIVE_SPEED_METERS_PER_SECOND
            else -> speedMetersPerSecond.coerceIn(
                MIN_WALKING_SPEED_METERS_PER_SECOND,
                MAX_WALKING_SPEED_METERS_PER_SECOND
            )
        }
        val rawRemainingSeconds = if (remainingDistanceMeters <= 0.0) 0.0 else remainingDistanceMeters / effectiveSpeed
        val previous = previousRemainingSeconds
        val remainingSeconds = if (previous == null || remainingDistanceMeters <= 0.0) {
            rawRemainingSeconds
        } else {
            val delta = rawRemainingSeconds - previous
            if (kotlin.math.abs(delta) < ETA_DEADBAND_SECONDS) {
                previous
            } else {
                previous + delta * ETA_SMOOTHING_ALPHA
            }
        }
        previousRemainingSeconds = remainingSeconds
        return EtaEstimate(
            remainingDurationSeconds = remainingSeconds,
            arrivalTime = Instant.ofEpochMilli(clock.millis() + (remainingSeconds * 1000).toLong()),
        )
    }

    private companion object {
        const val MIN_WALKING_SPEED_METERS_PER_SECOND = 0.35
        const val MAX_WALKING_SPEED_METERS_PER_SECOND = 2.35
        const val STATIONARY_EFFECTIVE_SPEED_METERS_PER_SECOND = 0.65
        const val ETA_DEADBAND_SECONDS = 18.0
        const val ETA_SMOOTHING_ALPHA = 0.35
    }
}

data class EtaEstimate(
    val remainingDurationSeconds: Double,
    val arrivalTime: Instant,
)
