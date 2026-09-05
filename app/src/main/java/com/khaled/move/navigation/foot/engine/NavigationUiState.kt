package com.khaled.move.navigation.foot.engine

import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.route.NavigationRoute
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.foot.route.RouteProgress
import com.khaled.move.navigation.foot.speed.MovementState

data class NavigationUiState(
    val status: NavigationStatus = NavigationStatus.Idle,
    val movementState: MovementState = MovementState.GpsUncertain,
    val route: NavigationRoute? = null,
    val progress: RouteProgress? = null,
    val speedMetersPerSecond: Double = 0.0,
    val elapsedTimeSeconds: Long = 0,
    val deviceHeadingDegrees: Double? = null,
    val rawLocation: NavigationLocation? = null,
    val filteredLocation: NavigationLocation? = null,
    val snappedLocation: RoutePoint? = null,
    val navigationLocation: RoutePoint? = null,
    val navigationBearingDegrees: Double? = null,
    val followUser: Boolean = true,
    val isRecalculating: Boolean = false,
    val errorMessage: String? = null,
)
