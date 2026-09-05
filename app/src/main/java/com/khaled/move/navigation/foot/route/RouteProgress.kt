package com.khaled.move.navigation.foot.route

import com.khaled.move.navigation.foot.instructions.NavigationInstruction
import java.time.Instant

data class RouteProgress(
    val totalRouteDistanceMeters: Double,
    val traveledDistanceMeters: Double,
    val remainingDistanceMeters: Double,
    val progressFraction: Double,
    val currentRouteSegmentIndex: Int,
    val snappedLocation: RoutePoint,
    val currentInstruction: NavigationInstruction?,
    val nextInstruction: NavigationInstruction?,
    val distanceToNextInstructionMeters: Double,
    val estimatedRemainingDurationSeconds: Double,
    val estimatedArrivalTime: Instant,
)
