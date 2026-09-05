package com.khaled.move.navigation.foot.location

/** Domain location sample used by the foot navigation engine. */
data class NavigationLocation(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long,
    val accuracyMeters: Float?,
    val bearingDegrees: Float?,
    val speedMetersPerSecond: Float?,
)
