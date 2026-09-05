package com.khaled.move.navigation.foot.speed

import com.khaled.move.navigation.foot.speed.MovementState
import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.route.GeoUtils
import kotlin.math.roundToInt

class WalkingSpeedEstimator {
    private data class SpeedSample(
        val timestampMillis: Long,
        val speedMetersPerSecond: Double,
    )

    private val samples = ArrayDeque<SpeedSample>()
    private var previousAcceptedLocation: NavigationLocation? = null
    private var stationaryCounter: Int = 0

    fun reset() {
        samples.clear()
        previousAcceptedLocation = null
        stationaryCounter = 0
    }

    fun onLocation(location: NavigationLocation): SpeedEstimate {
        if (location.accuracyMeters != null && location.accuracyMeters > MAX_SPEED_ACCURACY_METERS) {
            return SpeedEstimate(currentSmoothedSpeed(), MovementState.GpsUncertain)
        }

        val previous = previousAcceptedLocation
        previousAcceptedLocation = location

        if (previous == null) {
            location.speedMetersPerSecond?.toDouble()?.takeIf(::isPlausibleWalkingSpeed)
                ?.let { addSample(location.timestampMillis, it) }
            return SpeedEstimate(currentSmoothedSpeed(), MovementState.GpsUncertain)
        }

        val dtSeconds = ((location.timestampMillis - previous.timestampMillis).coerceAtLeast(1L)).toDouble() / 1000.0
        val distanceMeters = GeoUtils.distanceMeters(previous, location)
        val derivedSpeed = distanceMeters / dtSeconds
        val reportedSpeed = location.speedMetersPerSecond?.toDouble()
        val candidate = listOfNotNull(
            reportedSpeed?.takeIf(::isPlausibleWalkingSpeed),
            derivedSpeed.takeIf(::isPlausibleWalkingSpeed),
        ).averageOrNull()

        val movementThreshold = ((location.accuracyMeters ?: 10f).toDouble() * 0.12).coerceIn(0.8, 3.0)
        if (distanceMeters < movementThreshold || candidate == null || candidate < STATIONARY_SPEED_METERS_PER_SECOND) {
            stationaryCounter += 1
            if (stationaryCounter >= 2) { // Faster response to stopping
                addSample(location.timestampMillis, 0.0)
                return SpeedEstimate(0.0, MovementState.Stationary)
            }
            return SpeedEstimate(currentSmoothedSpeed(), MovementState.GpsUncertain)
        }

        stationaryCounter = 0
        addSample(location.timestampMillis, candidate)
        val speed = currentSmoothedSpeed()
        val state = if (speed < STATIONARY_SPEED_METERS_PER_SECOND) MovementState.Stationary else MovementState.Walking
        return SpeedEstimate(speed, state)
    }

    private fun addSample(timestampMillis: Long, speedMetersPerSecond: Double) {
        val rounded = (speedMetersPerSecond * 100.0).roundToInt() / 100.0
        samples.addLast(SpeedSample(timestampMillis, rounded))
        val cutoff = timestampMillis - HISTORY_WINDOW_MILLIS
        while (samples.isNotEmpty() && samples.first().timestampMillis < cutoff) {
            samples.removeFirst()
        }
        while (samples.size > MAX_SAMPLES) {
            samples.removeFirst()
        }
    }

    private fun currentSmoothedSpeed(): Double {
        if (samples.isEmpty()) return 0.0
        val recent = samples.takeLast(6)
        var weightSum = 0
        val weightedSum = recent.mapIndexed { index, sample ->
            val weight = index + 1
            weightSum += weight
            sample.speedMetersPerSecond * weight
        }.sum()
        return weightedSum / weightSum
    }

    private fun isPlausibleWalkingSpeed(speed: Double): Boolean =
        speed in 0.0..MAX_PLAUSIBLE_WALKING_SPEED_METERS_PER_SECOND

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private companion object {
        const val STATIONARY_SPEED_METERS_PER_SECOND = 0.28
        const val MAX_PLAUSIBLE_WALKING_SPEED_METERS_PER_SECOND = 3.0
        const val MAX_SPEED_ACCURACY_METERS = 65f
        const val HISTORY_WINDOW_MILLIS = 45_000L
        const val MAX_SAMPLES = 14
    }
}

data class SpeedEstimate(
    val speedMetersPerSecond: Double,
    val movementState: MovementState,
)
