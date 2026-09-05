package com.khaled.move.navigation.foot.route

import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.route.RoutePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    private const val EarthRadiusMeters = 6371000.0

    fun distanceMeters(a: RoutePoint, b: RoutePoint): Double =
        distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    fun distanceMeters(a: NavigationLocation, b: NavigationLocation): Double =
        distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                sin(dLon / 2) * sin(dLon / 2) * cos(rLat1) * cos(rLat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EarthRadiusMeters * c
    }

    fun bearingDegrees(from: RoutePoint, to: RoutePoint): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0)
    }

    fun angularDifferenceDegrees(first: Double, second: Double): Double {
        val diff = abs(first - second) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }

    fun normalizeBearingDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0

    fun shortestAngleDeltaDegrees(from: Double, to: Double): Double {
        val delta = normalizeBearingDegrees(to) - normalizeBearingDegrees(from)
        return when {
            delta > 180.0 -> delta - 360.0
            delta < -180.0 -> delta + 360.0
            else -> delta
        }
    }

    fun interpolateBearingDegrees(from: Double, to: Double, fraction: Double): Double =
        normalizeBearingDegrees(
            normalizeBearingDegrees(from) + shortestAngleDeltaDegrees(from, to) * fraction.coerceIn(
                0.0,
                1.0
            )
        )

    fun interpolate(a: RoutePoint, b: RoutePoint, fraction: Double): RoutePoint = RoutePoint(
        latitude = a.latitude + (b.latitude - a.latitude) * fraction,
        longitude = a.longitude + (b.longitude - a.longitude) * fraction,
    )

    fun destinationPoint(start: RoutePoint, bearingDegrees: Double, distanceMeters: Double): RoutePoint {
        val angularDistance = distanceMeters / EarthRadiusMeters
        val bearing = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        val lat2 = kotlin.math.asin(
            sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearing)
        )
        val lon2 = lon1 + atan2(
            sin(bearing) * sin(angularDistance) * cos(lat1),
            cos(angularDistance) - sin(lat1) * sin(lat2),
        )
        return RoutePoint(
            latitude = Math.toDegrees(lat2),
            longitude = Math.toDegrees(lon2),
        )
    }

    fun snapToSegment(point: RoutePoint, start: RoutePoint, end: RoutePoint): SnappedPoint {
        val scaleLat = 111320.0
        val scaleLon =
            cos(Math.toRadians((start.latitude + end.latitude + point.latitude) / 3.0)).coerceAtLeast(0.01) * 111320.0
        val px = point.longitude * scaleLon
        val py = point.latitude * scaleLat
        val ax = start.longitude * scaleLon
        val ay = start.latitude * scaleLat
        val bx = end.longitude * scaleLon
        val by = end.latitude * scaleLat
        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay
        val abLengthSquared = abx * abx + aby * aby
        val t = if (abLengthSquared == 0.0) 0.0 else (apx * abx + apy * aby) / abLengthSquared
        val clamped = min(1.0, max(0.0, t))
        val snappedX = ax + abx * clamped
        val snappedY = ay + aby * clamped
        val snapped = RoutePoint(
            latitude = snappedY / scaleLat,
            longitude = snappedX / scaleLon,
        )
        return SnappedPoint(
            point = snapped,
            fraction = clamped,
            distanceMeters = distanceMeters(point, snapped),
        )
    }
}

data class SnappedPoint(
    val point: RoutePoint,
    val fraction: Double,
    val distanceMeters: Double,
)
