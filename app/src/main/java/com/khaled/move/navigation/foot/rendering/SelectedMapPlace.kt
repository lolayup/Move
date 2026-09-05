package com.khaled.move.navigation.foot.rendering

import com.khaled.move.navigation.foot.route.RoutePoint
import org.maplibre.spatialk.geojson.Geometry

data class SelectedMapPlace(
    val title: String,
    val subtitle: String,
    val point: RoutePoint,
    val properties: List<PlaceProperty> = emptyList(),
    val fromMapFeature: Boolean = false,
    val geometry: Geometry? = null,
)

data class PlaceProperty(
    val label: String,
    val value: String,
)
