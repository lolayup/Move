package com.khaled.move.navigation.foot.rendering

import com.khaled.move.navigation.foot.route.RoutePoint
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

object MapFeaturePlaceResolver {
    fun resolve(features: List<Feature<Geometry, JsonObject?>>, fallbackPoint: RoutePoint): SelectedMapPlace? {
        val ranked = features
            .mapNotNull { feature -> feature.properties?.let { properties -> feature to properties } }
            .mapNotNull { (feature, properties) -> selectedPlaceFor(feature, properties, fallbackPoint) }
            .sortedByDescending { it.score }
        
        val best = ranked.firstOrNull() ?: return null
        
        // Try to find a Polygon/MultiPolygon geometry among all features that matches this name/id
        // to provide "Real Borders" instead of just a dot.
        val areaGeometry = features.find { 
            (it.geometry::class.simpleName?.contains("Polygon") == true) && 
            (it.properties?.firstString("name") == best.place.title || best.place.fromMapFeature)
        }?.geometry

        return best.place.copy(geometry = areaGeometry ?: best.place.geometry)
    }

    fun coordinatePlace(point: RoutePoint): SelectedMapPlace = SelectedMapPlace(
        title = "Selected location",
        subtitle = "Map coordinate",
        point = point,
        properties = listOf(
            PlaceProperty("Latitude", point.latitude.formatCoord()),
            PlaceProperty("Longitude", point.longitude.formatCoord()),
        ),
        fromMapFeature = false,
        geometry = Point(Position(longitude = point.longitude, latitude = point.latitude))
    )

    private fun selectedPlaceFor(
        feature: Feature<Geometry, JsonObject?>,
        properties: JsonObject,
        fallbackPoint: RoutePoint,
    ): ScoredPlace? {
        val name = properties.firstString(
            "name",
            "name:en",
            "name:ar",
            "name_int",
            "ref",
            "house_num",
        )
        val layer = properties.firstString("layer", "source-layer", "class", "type")
        val kind = properties.firstString(
            "subclass",
            "kind",
            "class",
            "type",
            "amenity",
            "shop",
            "tourism",
            "building",
            "place",
        )
        val title = when {
            !name.isNullOrBlank() -> name
            !kind.isNullOrBlank() -> kind.humanize()
            !layer.isNullOrBlank() -> layer.humanize()
            else -> return null
        }
        val subtitle = listOfNotNull(
            kind?.humanize()?.takeIf { it.isNotBlank() && it != title },
            properties.firstString("addr:street", "street")?.takeIf { it.isNotBlank() },
            layer?.humanize()?.takeIf { it.isNotBlank() && it != kind?.humanize() },
        ).distinct().take(2).joinToString(" · ").ifBlank { "OpenStreetMap place" }

        val displayProperties = buildList {
            properties.firstString("amenity")?.let { add(PlaceProperty("Type", it.humanize())) }
            properties.firstString("shop")?.let { add(PlaceProperty("Shop", it.humanize())) }
            properties.firstString("tourism")?.let { add(PlaceProperty("Tourism", it.humanize())) }
            properties.firstString("building")?.let { add(PlaceProperty("Building", it.humanize())) }
            properties.firstString("cuisine")?.let { add(PlaceProperty("Cuisine", it.humanize())) }
            properties.firstString("opening_hours")?.let { add(PlaceProperty("Hours", it)) }
            properties.firstString("phone", "contact:phone")?.let { add(PlaceProperty("Phone", it)) }
            properties.firstString("website", "contact:website")?.let { add(PlaceProperty("Website", it)) }
            properties.firstString("addr:street", "street")?.let { add(PlaceProperty("Street", it)) }
            properties.firstString("addr:housenumber", "house_num")?.let { add(PlaceProperty("Number", it)) }
            add(PlaceProperty("Latitude", fallbackPoint.latitude.formatCoord()))
            add(PlaceProperty("Longitude", fallbackPoint.longitude.formatCoord()))
        }.distinctBy { it.label to it.value }.take(10)

        val score = score(properties, feature, name, kind, layer)
        return ScoredPlace(
            place = SelectedMapPlace(
                title = title,
                subtitle = subtitle,
                point = fallbackPoint,
                properties = displayProperties,
                fromMapFeature = true,
                geometry = feature.geometry,
            ),
            score = score,
        )
    }

    private fun score(
        properties: JsonObject,
        feature: Feature<Geometry, JsonObject?>,
        name: String?,
        kind: String?,
        layer: String?,
    ): Int {
        var score = 0
        if (!name.isNullOrBlank()) score += 100
        if (properties.containsKey("amenity") || properties.containsKey("shop") || properties.containsKey("tourism")) score += 80
        if (properties.containsKey("building")) score += 35
        if (kind?.contains("label", ignoreCase = true) == true) score += 25
        if (layer?.contains("poi", ignoreCase = true) == true) score += 45
        if (layer?.contains("building", ignoreCase = true) == true) score += 30
        if (feature.geometry::class.simpleName?.contains("Point", ignoreCase = true) == true) score += 20
        return score
    }

    private fun JsonObject.firstString(vararg keys: String): String? {
        for (key in keys) {
            val direct = this[key]?.asText()?.takeIf { it.isNotBlank() }
            if (direct != null) return direct
        }
        return null
    }

    private fun JsonElement.asText(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        else -> toString().trim('"')
    }

    private fun String.humanize(): String = replace('_', ' ')
        .replace('-', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

    private fun Double.formatCoord(): String = String.format("%.5f", this)

    private data class ScoredPlace(
        val place: SelectedMapPlace,
        val score: Int,
    )
}
