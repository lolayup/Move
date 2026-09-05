package com.khaled.move.ui.mapscreen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.filled.Subway
import com.khaled.move.NavigationProfile
import com.khaled.move.navigation.foot.engine.NavigationUiState
import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.metro.engine.MetroNavigationUiState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.layers.SymbolLayer
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
fun UserLocationLayer(
    location: NavigationLocation?,
    navigationState: NavigationUiState,
    metroNavigationState: MetroNavigationUiState,
    activeProfile: NavigationProfile,
    isNavigating: Boolean
) {
    if (location == null) return

    val userLocationSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(
                listOf<Feature<Geometry, JsonObject?>>(
                    Feature(
                        geometry = Point(
                            Position(
                                longitude = location.longitude,
                                latitude = location.latitude
                            )
                        ),
                        properties = buildJsonObject { }
                    )
                )
            )
        )
    )

    val icon = if (activeProfile == NavigationProfile.METRO) {
        Icons.Default.Subway
    } else {
        Icons.AutoMirrored.Filled.DirectionsWalk
    }

    val userIconPainter = rememberVectorPainter(icon)
    
    val animatedBearing by animateFloatAsState(
        targetValue = if (isNavigating) {
            if (activeProfile == NavigationProfile.METRO) 0f // Metro icon usually doesn't rotate with heading on map
            else (navigationState.navigationBearingDegrees ?: navigationState.deviceHeadingDegrees ?: 0.0).toFloat()
        } else {
            (navigationState.deviceHeadingDegrees ?: 0.0).toFloat()
        },
        label = "user_bearing"
    )

    SymbolLayer(
        id = "user-location-icon",
        source = userLocationSource,
        iconImage = image(userIconPainter),
        iconSize = const(1.5f),
        iconRotate = const(animatedBearing),
        iconRotationAlignment = const(IconRotationAlignment.Map),
        iconColor = const(MaterialTheme.colorScheme.primary),
        iconOpacity = const(1f)
    )
}
