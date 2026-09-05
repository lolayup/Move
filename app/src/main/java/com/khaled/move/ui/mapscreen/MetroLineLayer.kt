package com.khaled.move.ui.mapscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.khaled.move.navigation.metro.data.CairoMetroRepository
import com.khaled.move.navigation.metro.data.MetroLine
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject

@Composable
@MaplibreComposable
fun MetroLineLayer(selectedLine: MetroLine?) {
    if (selectedLine == null) return

    val stations = selectedLine.stations.mapNotNull { CairoMetroRepository.getStation(it) }
    if (stations.isEmpty()) return

    val lineSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(
                listOf<Feature<Geometry, JsonObject?>>(
                    Feature(
                        geometry = LineString(
                            stations.map { Position(longitude = it.longitude, latitude = it.latitude) }
                        ),
                        properties = buildJsonObject { }
                    )
                )
            )
        )
    )

    val stationDotsSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(
                stations.map { station ->
                    Feature(
                        geometry = Point(Position(longitude = station.longitude, latitude = station.latitude)),
                        properties = buildJsonObject { }
                    )
                }
            )
        )
    )

    val lineColor = Color(android.graphics.Color.parseColor(selectedLine.colorHex))

    LineLayer(
        id = "metro-line-path",
        source = lineSource,
        color = const(lineColor),
        width = const(8.dp),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round)
    )

    CircleLayer(
        id = "metro-line-stations",
        source = stationDotsSource,
        radius = const(4.dp),
        color = const(Color.White),
        strokeColor = const(lineColor),
        strokeWidth = const(2.dp)
    )
}
