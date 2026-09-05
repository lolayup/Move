package com.khaled.move.navigation.foot.route

import com.khaled.move.navigation.foot.route.NavigationRoute
import com.khaled.move.navigation.foot.route.RoutePoint

interface WalkingRoutingRepository {
    suspend fun getWalkingRoute(
        origin: RoutePoint,
        destination: RoutePoint,
    ): Result<NavigationRoute>
}
