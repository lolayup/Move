package com.khaled.move.ui.mapscreen

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.khaled.move.navigation.foot.engine.NavigationUiState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.dp
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Position
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject

@Composable
@MaplibreComposable
fun NavigationRouteLayer(navigationState: NavigationUiState) {
    val route = navigationState.route ?: return

    val routeSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(
                listOf<Feature<Geometry, JsonObject?>>(
                    Feature(
                        geometry = LineString(
                            route.geometry.map { Position(longitude = it.longitude, latitude = it.latitude) }
                        ),
                        properties = buildJsonObject { }
                    )
                )
            )
        )
    )

    LineLayer(
        id = "navigation-route-line",
        source = routeSource,
        color = const(MaterialTheme.colorScheme.primary),
        width = const(6.dp),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round)
    )
}
