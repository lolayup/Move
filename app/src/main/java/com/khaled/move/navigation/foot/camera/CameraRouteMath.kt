package com.khaled.move.navigation.foot.camera

import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.foot.route.GeoUtils

object CameraRouteMath {
    fun midpoint(a: RoutePoint, b: RoutePoint): RoutePoint = RoutePoint(
        latitude = (a.latitude + b.latitude) / 2.0,
        longitude = (a.longitude + b.longitude) / 2.0,
    )

    fun suggestedZoom(user: RoutePoint?, destination: RoutePoint?): Double {
        if (user == null || destination == null) return 15.0
        val distance = GeoUtils.distanceMeters(user, destination)
        return when {
            distance < 150 -> 17.0
            distance < 400 -> 16.0
            distance < 900 -> 15.0
            distance < 1800 -> 14.0
            else -> 13.0
        }
    }
}
