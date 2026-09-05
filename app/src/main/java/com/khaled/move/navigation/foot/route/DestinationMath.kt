package com.khaled.move.navigation.foot.route

import com.khaled.move.navigation.foot.route.RoutePoint

object DestinationMath {
    fun distanceMeters(from: RoutePoint?, to: RoutePoint?): Double? {
        if (from == null || to == null) return null
        return GeoUtils.distanceMeters(from, to)
    }
}
