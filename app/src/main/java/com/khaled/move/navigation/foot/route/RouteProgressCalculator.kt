package com.khaled.move.navigation.foot.route

import com.khaled.move.navigation.foot.eta.EtaCalculator
import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.route.NavigationRoute
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.foot.route.RouteProgress

import kotlin.math.max
import kotlin.math.min

class RouteProgressCalculator(
    private val etaCalculator: EtaCalculator,
) {
    private var activeRouteKey: String? = null
    private var previousRouteDistanceMeters: Double? = null
    private var previousSegmentIndex: Int? = null

    fun reset() {
        activeRouteKey = null
        previousRouteDistanceMeters = null
        previousSegmentIndex = null
    }

    fun calculate(
        route: NavigationRoute,
        location: NavigationLocation,
        speedMetersPerSecond: Double,
    ): RouteProgressSnapshot {
        val routePoints = route.geometry
        val routeKey = routeKey(route)
        if (activeRouteKey != routeKey) {
            activeRouteKey = routeKey
            previousRouteDistanceMeters = null
            previousSegmentIndex = null
            etaCalculator.reset()
        }

        if (routePoints.size < 2) {
            val eta = etaCalculator.calculate(0.0, speedMetersPerSecond)
            val progress = RouteProgress(
                totalRouteDistanceMeters = route.distanceMeters,
                traveledDistanceMeters = route.distanceMeters,
                remainingDistanceMeters = 0.0,
                progressFraction = 1.0,
                currentRouteSegmentIndex = 0,
                snappedLocation = route.destination,
                currentInstruction = route.instructions.lastOrNull(),
                nextInstruction = null,
                distanceToNextInstructionMeters = 0.0,
                estimatedRemainingDurationSeconds = eta.remainingDurationSeconds,
                estimatedArrivalTime = eta.arrivalTime,
            )
            return RouteProgressSnapshot(progress, 0.0, null, 0.0)
        }

        val locationPoint = RoutePoint(location.latitude, location.longitude)
        val prefixGeometryDistances = prefixDistances(routePoints)
        val geometryLengthMeters = prefixGeometryDistances.last().coerceAtLeast(1.0)
        val routeScale = route.distanceMeters.coerceAtLeast(geometryLengthMeters) / geometryLengthMeters

        val candidate = bestRouteMatch(
            locationPoint = locationPoint,
            routePoints = routePoints,
            prefixGeometryDistances = prefixGeometryDistances,
            routeScale = routeScale,
            accuracyMeters = location.accuracyMeters,
        )

        val previousDistance = previousRouteDistanceMeters
        val backtrackAllowance = max(8.0, (location.accuracyMeters ?: 10f) * 0.35)
        val finalMatch =
            if (previousDistance != null && candidate.routeDistanceMeters < previousDistance - backtrackAllowance) {
                routeMatchAtDistance(routePoints, prefixGeometryDistances, previousDistance / routeScale, routeScale)
                    .copy(distanceFromRouteMeters = candidate.distanceFromRouteMeters)
            } else {
                candidate
            }

        previousRouteDistanceMeters = max(previousRouteDistanceMeters ?: 0.0, finalMatch.routeDistanceMeters)
        previousSegmentIndex = finalMatch.segmentIndex

        val remainingDistance = (route.distanceMeters - finalMatch.routeDistanceMeters).coerceAtLeast(0.0)
        val traveledDistance = (route.distanceMeters - remainingDistance).coerceAtLeast(0.0)
        val eta = etaCalculator.calculate(remainingDistance, speedMetersPerSecond)

        val instructionAdvanceMeters = 8.0
        val currentInstructionIndex = route.instructions.indexOfLast { instruction ->
            instructionDistanceMeters(instruction.routePointIndex, prefixGeometryDistances, routeScale) <=
                    finalMatch.routeDistanceMeters + instructionAdvanceMeters
        }
        val nextInstruction = route.instructions.firstOrNull { instruction ->
            instructionDistanceMeters(instruction.routePointIndex, prefixGeometryDistances, routeScale) >
                    finalMatch.routeDistanceMeters + instructionAdvanceMeters
        }
        val currentInstruction = route.instructions.getOrNull(currentInstructionIndex)
        val nextInstructionDistance = nextInstruction?.let {
            (instructionDistanceMeters(
                it.routePointIndex,
                prefixGeometryDistances,
                routeScale
            ) - finalMatch.routeDistanceMeters)
                .coerceAtLeast(0.0)
        } ?: 0.0

        val routeBearing = if (finalMatch.segmentIndex < routePoints.lastIndex) {
            GeoUtils.bearingDegrees(routePoints[finalMatch.segmentIndex], routePoints[finalMatch.segmentIndex + 1])
        } else null

        val progress = RouteProgress(
            totalRouteDistanceMeters = route.distanceMeters,
            traveledDistanceMeters = traveledDistance,
            remainingDistanceMeters = remainingDistance,
            progressFraction = if (route.distanceMeters == 0.0) 1.0 else (traveledDistance / route.distanceMeters).coerceIn(
                0.0,
                1.0,
            ),
            currentRouteSegmentIndex = finalMatch.segmentIndex,
            snappedLocation = finalMatch.snappedPoint,
            currentInstruction = currentInstruction,
            nextInstruction = nextInstruction,
            distanceToNextInstructionMeters = nextInstructionDistance,
            estimatedRemainingDurationSeconds = eta.remainingDurationSeconds,
            estimatedArrivalTime = eta.arrivalTime,
        )
        return RouteProgressSnapshot(
            progress,
            finalMatch.distanceFromRouteMeters,
            routeBearing,
            finalMatch.routeDistanceMeters
        )
    }

    private fun bestRouteMatch(
        locationPoint: RoutePoint,
        routePoints: List<RoutePoint>,
        prefixGeometryDistances: List<Double>,
        routeScale: Double,
        accuracyMeters: Float?,
    ): RouteMatch {
        val previousSegment = previousSegmentIndex
        val primaryRange = if (previousSegment == null) {
            0 until routePoints.lastIndex
        } else {
            max(0, previousSegment - 4)..min(routePoints.lastIndex - 1, previousSegment + 80)
        }
        var best = findBestMatch(locationPoint, routePoints, prefixGeometryDistances, routeScale, primaryRange)
        val fullSearchThreshold = max(55.0, (accuracyMeters ?: 12f) * 2.2)
        if (previousSegment != null && best.distanceFromRouteMeters > fullSearchThreshold) {
            best = findBestMatch(
                locationPoint,
                routePoints,
                prefixGeometryDistances,
                routeScale,
                0 until routePoints.lastIndex
            )
        }
        return best
    }

    private fun findBestMatch(
        locationPoint: RoutePoint,
        routePoints: List<RoutePoint>,
        prefixGeometryDistances: List<Double>,
        routeScale: Double,
        indices: Iterable<Int>,
    ): RouteMatch {
        var bestSegmentIndex = 0
        var bestDistance = Double.MAX_VALUE
        var bestSnap = routePoints.first()
        var bestFraction = 0.0

        for (index in indices) {
            val snapped = GeoUtils.snapToSegment(locationPoint, routePoints[index], routePoints[index + 1])
            if (snapped.distanceMeters < bestDistance) {
                bestDistance = snapped.distanceMeters
                bestSegmentIndex = index
                bestSnap = snapped.point
                bestFraction = snapped.fraction
            }
        }

        val segmentLength = GeoUtils.distanceMeters(routePoints[bestSegmentIndex], routePoints[bestSegmentIndex + 1])
        val geometryDistance = prefixGeometryDistances[bestSegmentIndex] + (segmentLength * bestFraction)
        return RouteMatch(
            segmentIndex = bestSegmentIndex,
            snappedPoint = bestSnap,
            routeDistanceMeters = geometryDistance * routeScale,
            distanceFromRouteMeters = bestDistance,
        )
    }

    private fun routeMatchAtDistance(
        routePoints: List<RoutePoint>,
        prefixGeometryDistances: List<Double>,
        geometryDistanceMeters: Double,
        routeScale: Double,
    ): RouteMatch {
        val clampedGeometryDistance = geometryDistanceMeters.coerceIn(0.0, prefixGeometryDistances.last())
        val segmentIndex = prefixGeometryDistances.indexOfLast { it <= clampedGeometryDistance }
            .coerceIn(0, routePoints.lastIndex - 1)
        val segmentStartDistance = prefixGeometryDistances[segmentIndex]
        val segmentLength =
            GeoUtils.distanceMeters(routePoints[segmentIndex], routePoints[segmentIndex + 1]).coerceAtLeast(1.0)
        val fraction = ((clampedGeometryDistance - segmentStartDistance) / segmentLength).coerceIn(0.0, 1.0)
        return RouteMatch(
            segmentIndex = segmentIndex,
            snappedPoint = GeoUtils.interpolate(routePoints[segmentIndex], routePoints[segmentIndex + 1], fraction),
            routeDistanceMeters = clampedGeometryDistance * routeScale,
            distanceFromRouteMeters = 0.0,
        )
    }

    private fun instructionDistanceMeters(
        routePointIndex: Int,
        prefixGeometryDistances: List<Double>,
        routeScale: Double,
    ): Double = prefixGeometryDistances[routePointIndex.coerceIn(0, prefixGeometryDistances.lastIndex)] * routeScale

    private fun prefixDistances(points: List<RoutePoint>): List<Double> {
        val result = MutableList(points.size) { 0.0 }
        for (i in 1 until points.size) {
            result[i] = result[i - 1] + GeoUtils.distanceMeters(points[i - 1], points[i])
        }
        return result
    }

    private fun routeKey(route: NavigationRoute): String = buildString {
        append(route.geometry.size)
        append(':')
        append(route.distanceMeters.toLong())
        route.geometry.firstOrNull()?.let { append(":${it.latitude},${it.longitude}") }
        route.geometry.lastOrNull()?.let { append(":${it.latitude},${it.longitude}") }
    }
}

private data class RouteMatch(
    val segmentIndex: Int,
    val snappedPoint: RoutePoint,
    val routeDistanceMeters: Double,
    val distanceFromRouteMeters: Double,
)

data class RouteProgressSnapshot(
    val progress: RouteProgress,
    val distanceFromRouteMeters: Double,
    val routeBearingDegrees: Double?,
    val routeDistanceAlongMeters: Double,
)
