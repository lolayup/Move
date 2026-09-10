package com.khaled.move.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khaled.move.MainViewModel
import com.khaled.move.NavigationProfile
import com.khaled.move.map.MapDefaults
import com.khaled.move.map.MapStyles
import com.khaled.move.navigation.foot.engine.NavigationStatus
import com.khaled.move.navigation.foot.rendering.MapFeaturePlaceResolver
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.foot.ui.DestinationOption
import com.khaled.move.ui.mapscreen.DestinationMarkerLayer
import com.khaled.move.ui.mapscreen.MapScreenOverlays
import com.khaled.move.ui.mapscreen.NavigationRouteLayer
import com.khaled.move.ui.mapscreen.UserLocationLayer
import com.khaled.move.ui.mapscreen.MetroLineLayer
import com.khaled.move.ui.mapscreen.MetroStationsLayer
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Position

@Composable
fun MapScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val navigationState by viewModel.navigationState.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(
                longitude = MapDefaults.CAIRO_LNG,
                latitude = MapDefaults.CAIRO_LAT,
            ),
            zoom = MapDefaults.CITY_ZOOM,
        )
    )

    val mapInsets = WindowInsets.safeDrawing.union(WindowInsets.navigationBars)
    val bottomPadding = mapInsets.asPaddingValues(density).calculateBottomPadding()

    val locationState by viewModel.location.collectAsStateWithLifecycle()
    val selectedDestination by viewModel.selectedDestination.collectAsStateWithLifecycle()
    val showDestinationMenu by viewModel.showDestinationMenu.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val selectedMetroLine by viewModel.selectedMetroLine.collectAsStateWithLifecycle()
    val metroNavigationState by viewModel.metroNavigationState.collectAsStateWithLifecycle()
    val showMetroStations by viewModel.showMetroStations.collectAsStateWithLifecycle()

    val isNavigating = if (activeProfile == NavigationProfile.METRO) {
        metroNavigationState.status != NavigationStatus.Idle
    } else {
        navigationState.status == NavigationStatus.Navigating || 
        navigationState.status == NavigationStatus.Stationary ||
        navigationState.status == NavigationStatus.Rerouting
    }

    // Proactive Camera follow logic
    val currentLoc = locationState.location
    val navLoc = if (activeProfile == NavigationProfile.METRO) {
        metroNavigationState.currentStation?.point ?: currentLoc?.let { RoutePoint(it.latitude, it.longitude) }
    } else {
        navigationState.navigationLocation ?: currentLoc?.let { RoutePoint(it.latitude, it.longitude) }
    }
    
    LaunchedEffect(navLoc, navigationState.navigationBearingDegrees, navigationState.deviceHeadingDegrees, navigationState.followUser, isNavigating, activeProfile) {
        val follow = if (activeProfile == NavigationProfile.METRO) metroNavigationState.followUser else navigationState.followUser
        if (follow && navLoc != null) {
            val targetBearing = if (isNavigating) {
                if (activeProfile == NavigationProfile.METRO) 0.0
                else if (navigationState.speedMetersPerSecond > 0.6) {
                    navigationState.navigationBearingDegrees ?: navigationState.deviceHeadingDegrees ?: 0.0
                } else {
                    navigationState.deviceHeadingDegrees ?: navigationState.navigationBearingDegrees ?: 0.0
                }
            } else 0.0

            cameraState.animateTo(
                CameraPosition(
                    target = Position(longitude = navLoc.longitude, latitude = navLoc.latitude),
                    zoom = if (isNavigating) MapDefaults.NAVIGATION_ZOOM else MapDefaults.CURRENT_LOCATION_ZOOM,
                    bearing = targetBearing,
                    tilt = if (isNavigating) MapDefaults.NAVIGATION_PITCH else 0.0
                ),
                duration = 500.milliseconds // Faster snap
            )
        }
    }

    // Detect user interaction to stop following
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isCameraMoving }
            .filter { it && cameraState.moveReason == CameraMoveReason.GESTURE }
            .collect {
                val follow = if (activeProfile == NavigationProfile.METRO) metroNavigationState.followUser else navigationState.followUser
                if (follow) {
                    viewModel.setFollowUser(false)
                }
            }
    }

    val sampleDestinations = remember {
        listOf(
            DestinationOption(
                title = "Cairo Tower",
                subtitle = "Iconic landmark",
                point = RoutePoint(latitude = 30.0444, longitude = 31.2357),
            ),
            DestinationOption(
                title = "Pyramids of Giza",
                subtitle = "Ancient wonder",
                point = RoutePoint(latitude = 29.9792, longitude = 31.1344),
            ),
            DestinationOption(
                title = "Egyptian Museum",
                subtitle = "Historical artifacts",
                point = RoutePoint(latitude = 30.0081, longitude = 31.1417),
            )
        )
    }

    // REMOVED: Automatic resume follow on location update to prevent camera stealing.
    // Follow mode should only be enabled by explicit user action (buttons).

    Box(modifier = modifier.fillMaxSize()) {
        MaplibreMap(
            baseStyle = BaseStyle.Uri(MapStyles.LIBERTY),
            cameraState = cameraState,
            cameraPadding = if (isNavigating || selectedDestination != null) {
                PaddingValues(bottom = bottomPadding + 260.dp) 
            } else {
                PaddingValues(0.dp)
            },
            modifier = Modifier.fillMaxSize(),
            onMapClick = { point, screenPoint ->
                if (selectedDestination == null && !showDestinationMenu) {
                    scope.launch {
                        val features = cameraState.queryRenderedFeatures(screenPoint)
                        val place = MapFeaturePlaceResolver.resolve(
                            features = features,
                            fallbackPoint = RoutePoint(point.latitude, point.longitude)
                        )
                        if (place != null) {
                            viewModel.selectPlace(place)
                            viewModel.setFollowUser(false)
                        } else {
                            viewModel.selectPlace(
                                MapFeaturePlaceResolver.coordinatePlace(
                                    RoutePoint(point.latitude, point.longitude)
                                )
                            )
                            viewModel.setFollowUser(false)
                        }
                    }
                    ClickResult.Consume
                } else {
                    ClickResult.Pass
                }
            }
        ) {
            UserLocationLayer(
                viewModel = viewModel,
                location = currentLoc,
                navigationState = navigationState,
                metroNavigationState = metroNavigationState,
                activeProfile = activeProfile,
                isNavigating = isNavigating
            )

            NavigationRouteLayer(navigationState = navigationState)

            DestinationMarkerLayer(selectedDestination = selectedDestination)

            MetroLineLayer(selectedLine = selectedMetroLine)

            if (showMetroStations) {
                MetroStationsLayer(viewModel = viewModel)
            }
        }

        MapScreenOverlays(
            viewModel = viewModel,
            navigationState = navigationState,
            metroNavigationState = metroNavigationState,
            activeProfile = activeProfile,
            locationState = locationState,
            currentLoc = currentLoc,
            selectedDestination = selectedDestination,
            showDestinationMenu = showDestinationMenu,
            isNavigating = isNavigating,
            mapInsets = mapInsets,
            sampleDestinations = sampleDestinations,
            scope = scope
        )
    }
}
