package com.khaled.move.navigation.metro.data

import com.khaled.move.navigation.foot.route.RoutePoint
import org.maplibre.spatialk.geojson.Geometry

data class MetroLine(
    val id: String,
    val name: String,
    val colorHex: String,
    val stations: List<String> // List of station IDs in order
)

data class MetroStation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val lines: List<String>, // IDs of lines passing through
    val geometry: Geometry? = null
) {
    val point: RoutePoint get() = RoutePoint(latitude, longitude)
}

data class MetroNetwork(
    val lines: Map<String, MetroLine>,
    val stations: Map<String, MetroStation>
)
