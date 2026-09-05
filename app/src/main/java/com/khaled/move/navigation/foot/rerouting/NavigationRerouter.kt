package com.khaled.move.navigation.foot.rerouting

import com.khaled.move.navigation.foot.route.NavigationRoute
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.foot.route.WalkingRoutingRepository

class NavigationRerouter(
    private val routingRepository: WalkingRoutingRepository,
) {
    suspend fun reroute(from: RoutePoint, destination: RoutePoint): Result<NavigationRoute> {
        return routingRepository.getWalkingRoute(from, destination)
    }
}
