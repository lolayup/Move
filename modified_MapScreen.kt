package com.khaled.move.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.khaled.move.MainViewModel
import com.khaled.move.map.MapDefaults
import com.khaled.move.map.MapStyles
import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.engine.NavigationStatus
import com.khaled.move.navigation.foot.rendering.MapFeaturePlaceResolver
import com.khaled.move.navigation.foot.rendering.SelectedMapPlace
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.foot.camera.CameraRouteMath
import com.khaled.move.navigation.foot.route.DestinationMath
import com.khaled.move.navigation.foot.route.GeoUtils
import com.khaled.move.ui.components.LocationButton
import com.khaled.move.ui.components.LocationButtonState
import com.khaled.move.navigation.foot.ui.DestinationMenu
import com.khaled.move.navigation.foot.ui.DestinationOption
import com.khaled.move.navigation.foot.ui.MapSelectionMarker
import com.khaled.move.navigation.foot.ui.NavigationBottomBar
import com.khaled.move.navigation.foot.ui.NavigationTopBanner
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationPuckColors
import org.maplibre.compose.location.LocationPuckSizes
import org.maplibre.compose.location.LocationTrackingStatus
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.units.extensions.inMeters
import kotlin.time.Duration.Companion.milliseconds

private val sampleDestinations = listOf(
    DestinationOption("Cairo Tower", "Landmark · Building", RoutePoint(30.0459, 31.2243)),
    DestinationOption("The Egyptian Museum", "Museum · Building", RoutePoint(30.0478, 31.2336)),
    DestinationOption("Tahrir Square", "Public square · Landmark", RoutePoint(30.0444, 31.2357)),
    DestinationOption("Opera House", "Cultural venue · Building", RoutePoint(30.0420, 31.2249)),
    DestinationOption("Gezira Sporting Club", "Club · Campus", RoutePoint(30.0553, 31.2241)),
)

@Composable
fun MapScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val navigationState by viewModel.navigationState.collectAsStateWithLifecycle()

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = Position(latitude = MapDefaults.CAIRO_LAT, longitude = MapDefaults.CAIRO_LNG),
            zoom = MapDefaults.CITY_ZOOM,
        ),
    )

    val locationProvider = rememberDefaultLocationProvider()
    val locationState = rememberLocationState(provider = locationProvider)
    val mapInsets = WindowInsets.safeDrawing.union(WindowInsets(bottom = 16.dp))
    var shouldCenterOnNextFix by remember { mutableStateOf(false) }
    var pendingDestination by remember { mutableStateOf<RoutePoint?>(null) }
    var showDestinationMenu by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableStateOf<SelectedMapPlace?>(null) }

    LaunchedEffect(locationState.location) {
        locationState.location?.let { fix ->
            viewModel.onLocationUpdate(
                NavigationLocation(
                    latitude = fix.position.value.latitude,
                    longitude = fix.position.value.longitude,
                    timestampMillis = System.currentTimeMillis(),
                    accuracyMeters = fix.position.accuracy?.inMeters?.toFloat(),
                    bearingDegrees = null,
                    speedMetersPerSecond = null,
                ),
            )
            pendingDestination?.let { destination ->
                viewModel.startNavigationTo(destination)
                pendingDestination = null
            }
        }
    }

    val userPoint = locationState.location?.position?.value?.let { RoutePoint(it.latitude, it.longitude) }

    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.moveReason }
            .distinctUntilChanged()
            .collect { reason ->
                if (reason == CameraMoveReason.GESTURE) {
                    viewModel.setFollowUser(false)
                }
            }
    }

    LaunchedEffect(navigationState.route) {
        val route = navigationState.route ?: return@LaunchedEffect
        val routeStart = route.geometry.firstOrNull() ?: userPoint ?: return@LaunchedEffect
        val routeDestination = route.destination
        val focus = CameraRouteMath.midpoint(routeStart, routeDestination)
        cameraState.animateTo(
            finalPosition = CameraPosition(
                target = Position(focus.latitude, focus.longitude),
                zoom = CameraRouteMath.suggestedZoom(routeStart, routeDestination),
            ),
            duration = 650.milliseconds,
        )
    }

    LaunchedEffect(
        navigationState.navigationLocation,
        navigationState.navigationBearingDegrees,
        shouldCenterOnNextFix,
        navigationState.followUser,
        navigationState.status,
    ) {
        val navigationIsActive = navigationState.status != NavigationStatus.Idle &&
                navigationState.status != NavigationStatus.Preparing &&
                navigationState.status != NavigationStatus.Error
        val activePoint = navigationState.navigationLocation
            ?: locationState.location?.position?.value?.let { RoutePoint(it.latitude, it.longitude) }
            ?: return@LaunchedEffect
        val forceRecenter = shouldCenterOnNextFix
        if (!forceRecenter && (!navigationIsActive || !navigationState.followUser)) return@LaunchedEffect

        shouldCenterOnNextFix = false
        val bearing = navigationState.navigationBearingDegrees
        val cameraTargetPoint = if (navigationIsActive && bearing != null && !forceRecenter) {
            GeoUtils.destinationPoint(activePoint, bearing, 18.0)
        } else {
            activePoint
        }
        cameraState.animateTo(
            finalPosition = cameraState.position.copy(
                target = Position(latitude = cameraTargetPoint.latitude, longitude = cameraTargetPoint.longitude),
                zoom = if (forceRecenter) {
                    MapDefaults.CURRENT_LOCATION_ZOOM
                } else {
                    cameraState.position.zoom
                },
                bearing = if (navigationIsActive && bearing != null && navigationState.followUser) {
                    bearing
                } else {
                    cameraState.position.bearing
                },
                tilt = if (navigationIsActive && navigationState.followUser) {
                    MapDefaults.NAVIGATION_PITCH
                } else {
                    cameraState.position.tilt
                },
            ),
            duration = if (forceRecenter) 450.milliseconds else 800.milliseconds,
        )
    }

    val routeFeatures: List<Feature<LineString, JsonObject?>> =
        navigationState.route?.geometry?.takeIf { it.size > 1 }?.let { geometry ->
            listOf(
                Feature(
                    geometry = LineString(
                        geometry.map { point -> Position(latitude = point.latitude, longitude = point.longitude) },
                    ),
                    properties = null,
                ),
            )
        } ?: emptyList()


    val destinationFeatures: List<Feature<Point, JsonObject?>> =
        listOfNotNull((selectedDestination?.point ?: navigationState.route?.destination)?.let { point ->
            Feature(
                geometry = Point(Position(latitude = point.latitude, longitude = point.longitude)),
                properties = null,
            )
        })

    val navigationPointFeatures: List<Feature<Point, JsonObject?>> =
        listOfNotNull(navigationState.navigationLocation?.let { point ->
            Feature(
                geometry = Point(Position(latitude = point.latitude, longitude = point.longitude)),
                properties = null,
            )
        })

    val navigationBearingFeatures: List<Feature<LineString, JsonObject?>> =
        listOfNotNull(
            navigationState.navigationLocation?.let { point ->
                navigationState.navigationBearingDegrees?.let { bearing ->
                    val headingPoint = GeoUtils.destinationPoint(point, bearing, 16.0)
                    Feature(
                        geometry = LineString(
                            listOf(
                                Position(latitude = point.latitude, longitude = point.longitude),
                                Position(latitude = headingPoint.latitude, longitude = headingPoint.longitude),
                            ),
                        ),
                        properties = null,
                    )
                }
            },
        )

    val selectedDistance = DestinationMath.distanceMeters(userPoint, selectedDestination?.point)

    Box(modifier = modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = BaseStyle.Uri(MapStyles.LIBERTY),
            cameraState = cameraState,
            contentWindowInsets = mapInsets,
            cameraPadding = mapInsets.asPaddingValues(),
            onMapClick = { position, screenOffset ->
                viewModel.setFollowUser(false)
                showDestinationMenu = false
                scope.launch {
                    selectedDestination = resolveSelectedPlace(
                        cameraState = cameraState,
                        position = position,
                        screenOffset = screenOffset,
                    )
                }
                org.maplibre.compose.util.ClickResult.Consume
            },
            onMapLongClick = { position, screenOffset ->
                viewModel.setFollowUser(false)
                showDestinationMenu = false
                scope.launch {
                    selectedDestination = resolveSelectedPlace(
                        cameraState = cameraState,
                        position = position,
                        screenOffset = screenOffset,
                    )
                }
                org.maplibre.compose.util.ClickResult.Consume
            },
        ) {
            if (navigationState.navigationLocation == null) {
                LocationPuck(
                    idPrefix = "user",
                    location = locationState.location,
                    cameraState = cameraState,
                    colors = LocationPuckColors(
                        dotFillColorCurrentLocation = MaterialTheme.colorScheme.primary,
                        dotFillColorOldLocation = MaterialTheme.colorScheme.onSurfaceVariant,
                        dotStrokeColor = MaterialTheme.colorScheme.onPrimary,
                        accuracyStrokeColor = MaterialTheme.colorScheme.primary,
                        bearingColor = MaterialTheme.colorScheme.primary,
                    ),
                    sizes = LocationPuckSizes(
                        dotRadius = 8.dp,
                        dotStrokeWidth = 3.dp,
                        shadowSize = 4.dp,
                        accuracyStrokeWidth = 2.dp,
                    ),
                    showBearing = false,
                    showBearingAccuracy = false,
                )
            }

            if (destinationFeatures.isNotEmpty()) {
                val destinationSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(FeatureCollection(destinationFeatures)),
                )
                CircleLayer(
                    id = "destination-halo",
                    source = destinationSource,
                    color = const(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    radius = const(16.dp),
                    blur = const(0.2f),
                )
                CircleLayer(
                    id = "destination-point",
                    source = destinationSource,
                    color = const(MaterialTheme.colorScheme.primary),
                    radius = const(8.dp),
                    strokeColor = const(MaterialTheme.colorScheme.onPrimary),
                    strokeWidth = const(3.dp),
                )
            }

            if (navigationState.route != null && routeFeatures.isNotEmpty()) {
                val routeSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(FeatureCollection(routeFeatures)),
                )
                LineLayer(
                    id = "active-walking-route-halo",
                    source = routeSource,
                    color = const(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)),
                    width = const(10.dp),
                    opacity = const(0.9f),
                )
                LineLayer(
                    id = "active-walking-route",
                    source = routeSource,
                    color = const(MaterialTheme.colorScheme.primary),
                    width = const(6.dp),
                    opacity = const(1f),
                )
            }

            if (navigationBearingFeatures.isNotEmpty()) {
                val navigationBearingSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(FeatureCollection(navigationBearingFeatures)),
                )
                LineLayer(
                    id = "navigation-heading-line",
                    source = navigationBearingSource,
                    color = const(MaterialTheme.colorScheme.primary),
                    width = const(5.dp),
                    opacity = const(0.95f),
                )
            }

            if (navigationPointFeatures.isNotEmpty()) {
                val navigationPointSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(FeatureCollection(navigationPointFeatures)),
                )
                CircleLayer(
                    id = "navigation-user-shadow",
                    source = navigationPointSource,
                    color = const(MaterialTheme.colorScheme.scrim.copy(alpha = 0.26f)),
                    radius = const(15.dp),
                    blur = const(0.5f),
                )
                CircleLayer(
                    id = "navigation-user-dot",
                    source = navigationPointSource,
                    color = const(MaterialTheme.colorScheme.primary),
                    radius = const(9.dp),
                    strokeColor = const(MaterialTheme.colorScheme.onPrimary),
                    strokeWidth = const(3.dp),
                )
            }
        }

        if (navigationState.status != NavigationStatus.Idle) {
            if (navigationState.progress != null) {
                NavigationTopBanner(
                    state = navigationState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(mapInsets.asPaddingValues())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )

                NavigationBottomBar(
                    state = navigationState,
                    onEndNavigation = {
                        viewModel.stopNavigation()
                        selectedDestination = null
                    },
                    onResumeFollow = {
                        viewModel.setFollowUser(true)
                        shouldCenterOnNextFix = true
                    },
                    onOpenMenu = { showDestinationMenu = true },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            } else if (navigationState.errorMessage != null) {
                Text(
                    text = navigationState.errorMessage ?: "",
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(mapInsets.asPaddingValues())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Removed the "Tap any location on the map to select it, then press Navigate" text
            // This removes the recommendation text
        }

        // Remove the SelectedDestinationCard completely - no navigation recommendations

        // Remove the FilledIconButton that shows "Open destinations"
        // The entire icon button section is removed from the original file

        LocationButton(
            state = when {
                locationState.permission !is LocationPermission.Granted -> LocationButtonState.PERMISSION_NEEDED
                navigationState.status == NavigationStatus.Rerouting -> LocationButtonState.WAITING_FOR_FIX
                locationState.location == null || locationState.status is LocationTrackingStatus.Starting -> LocationButtonState.WAITING_FOR_FIX
                else -> LocationButtonState.TRACKING
            },
            onClick = {
                viewModel.setFollowUser(true)
                if (locationState.permission !is LocationPermission.Granted) {
                    shouldCenterOnNextFix = true
                    locationState.requestPermission()
                } else {
                    locationState.location?.let { fix ->
                        shouldCenterOnNextFix = false
                        scope.launch {
                            cameraState.animateTo(
                                finalPosition = cameraState.position.copy(
                                    target = fix.position.value,
                                    zoom = MapDefaults.CURRENT_LOCATION_ZOOM,
                                ),
                                duration = 400.milliseconds,
                            )
                        }
                    } ?: run {
                        shouldCenterOnNextFix = true
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(mapInsets.asPaddingValues())
                .padding(
                    end = 16.dp,
                    bottom = if (navigationState.progress != null || selectedDestination != null) 112.dp else 16.dp
                ),
        )

        // Remove the destination menu entirely
        // if (showDestinationMenu) {
        //     DestinationMenu(
        //         options = sampleDestinations,
        //         onSelect = { option ->
        //             showDestinationMenu = false
        //             selectedDestination = SelectedMapPlace(
        //                 title = option.title,
        //                 subtitle = option.subtitle,
        //                 point = option.point,
        //                 fromMapFeature = true,
        //             )
        //         },
        //         onDismiss = { showDestinationMenu = false },
        //         modifier = Modifier.align(Alignment.BottomCenter),
        //     )
        // }

        // Remove the MapSelectionMarker
        // if (selectedDestination != null && navigationState.status == NavigationStatus.Idle) {
        //     MapSelectionMarker(
        //         modifier = Modifier
        //             .align(Alignment.Center)
        //             .padding(bottom = 56.dp),
        //     )
        // }
    }
}

private suspend fun resolveSelectedPlace(
    cameraState: CameraState,
    position: Position,
    screenOffset: DpOffset,
): SelectedMapPlace {
    val fallbackPoint = RoutePoint(position.latitude, position.longitude)
    val tapRadius = 22.dp
    val queryBounds = DpRect(
        left = screenOffset.x - tapRadius,
        top = screenOffset.y - tapRadius,
        right = screenOffset.x + tapRadius,
        bottom = screenOffset.y + tapRadius,
    )
    return runCatching {
        val features = cameraState.queryRenderedFeatures(queryBounds)
        MapFeaturePlaceResolver.resolve(features, fallbackPoint)
    }.getOrNull() ?: MapFeaturePlaceResolver.coordinatePlace(fallbackPoint)
}
