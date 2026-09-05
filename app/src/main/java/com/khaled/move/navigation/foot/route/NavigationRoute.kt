package com.khaled.move.navigation.foot.route

import com.khaled.move.navigation.foot.instructions.NavigationInstruction

data class NavigationRoute(
    val geometry: List<RoutePoint>,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val destination: RoutePoint,
    val instructions: List<NavigationInstruction>,
)
