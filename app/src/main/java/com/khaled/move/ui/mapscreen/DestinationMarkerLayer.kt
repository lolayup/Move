package com.khaled.move.ui.mapscreen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.khaled.move.navigation.foot.rendering.SelectedMapPlace
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.CirclePitchAlignment
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject

@Composable
@MaplibreComposable
fun DestinationMarkerLayer(selectedDestination: SelectedMapPlace?) {
    if (selectedDestination == null) return

    val geometry = selectedDestination.geometry ?: Point(
        Position(
            longitude = selectedDestination.point.longitude,
            latitude = selectedDestination.point.latitude
        )
    )

    val destinationSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(
                listOf<Feature<Geometry, JsonObject?>>(
                    Feature(
                        geometry = geometry,
                        properties = buildJsonObject { }
                    )
                )
            )
        )
    )

    if (geometry is Point) {
        CircleLayer(
            id = "destination-marker-dot",
            source = destinationSource,
            radius = const(10.dp),
            color = const(Color.White),
            strokeColor = const(MaterialTheme.colorScheme.primary),
            strokeWidth = const(4.dp),
            pitchAlignment = const(CirclePitchAlignment.Map)
        )
    } else {
        LineLayer(
            id = "destination-marker-border",
            source = destinationSource,
            color = const(MaterialTheme.colorScheme.primary),
            width = const(4.dp),
            cap = const(LineCap.Round),
            join = const(LineJoin.Round)
        )
        
        // Still add a small dot at the center for clarity
        val centerSource = rememberGeoJsonSource(
            data = GeoJsonData.Features(
                FeatureCollection(
                    listOf<Feature<Geometry, JsonObject?>>(
                        Feature(
                            geometry = Point(
                                Position(
                                    longitude = selectedDestination.point.longitude,
                                    latitude = selectedDestination.point.latitude
                                )
                            ),
                            properties = buildJsonObject { }
                        )
                    )
                )
            )
        )
        
        CircleLayer(
            id = "destination-marker-center",
            source = centerSource,
            radius = const(6.dp),
            color = const(Color.White),
            strokeColor = const(MaterialTheme.colorScheme.primary),
            strokeWidth = const(2.dp),
            pitchAlignment = const(CirclePitchAlignment.Map)
        )
    }
}
