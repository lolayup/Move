package com.khaled.move.ui.mapscreen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.khaled.move.MainViewModel
import com.khaled.move.navigation.metro.data.CairoMetroRepository
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

@Composable
@MaplibreComposable
fun MetroStationsLayer(viewModel: MainViewModel) {
    val stations = CairoMetroRepository.getAllStations()
    val source = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(
                stations.map { station ->
                    Feature(
                        geometry = Point(Position(longitude = station.longitude, latitude = station.latitude)),
                        properties = buildJsonObject { 
                            put("name", kotlinx.serialization.json.JsonPrimitive(station.name))
                            put("type", kotlinx.serialization.json.JsonPrimitive("metro_station"))
                        }
                    )
                }
            )
        )
    )

    val iconPainter = rememberVectorPainter(Icons.Default.Subway)

    SymbolLayer(
        id = "metro-stations-layer",
        source = source,
        iconImage = image(iconPainter),
        iconSize = const(1.2f),
        iconColor = const(MaterialTheme.colorScheme.secondary),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true)
    )
}
