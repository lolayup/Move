package com.khaled.move.ui.mapscreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.khaled.move.MainViewModel
import com.khaled.move.LocationState
import com.khaled.move.NavigationProfile
import com.khaled.move.navigation.foot.engine.NavigationUiState
import com.khaled.move.navigation.foot.rendering.SelectedMapPlace
import com.khaled.move.navigation.foot.route.DestinationMath
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.foot.ui.DestinationMenu
import com.khaled.move.navigation.foot.ui.DestinationOption
import com.khaled.move.navigation.foot.ui.SelectedDestinationCard
import com.khaled.move.ui.components.LocationButton
import com.khaled.move.ui.components.LocationButtonState
import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.metro.engine.MetroNavigationUiState
import kotlinx.coroutines.CoroutineScope

@Composable
fun MapScreenOverlays(
    viewModel: MainViewModel,
    navigationState: NavigationUiState,
    metroNavigationState: MetroNavigationUiState,
    activeProfile: NavigationProfile,
    locationState: LocationState,
    currentLoc: NavigationLocation?,
    selectedDestination: SelectedMapPlace?,
    showDestinationMenu: Boolean,
    isNavigating: Boolean,
    mapInsets: WindowInsets,
    sampleDestinations: List<DestinationOption>,
    scope: CoroutineScope
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Location button - only show when not navigating
        if (!isNavigating) {
            LocationButton(
                state = when {
                    !locationState.isPermissionGranted -> LocationButtonState.PERMISSION_NEEDED
                    currentLoc == null && locationState.isLocationTracking -> LocationButtonState.WAITING_FOR_FIX
                    !locationState.isLocationTracking -> LocationButtonState.PERMISSION_NEEDED 
                    else -> {
                        val follow = if (activeProfile == NavigationProfile.METRO) metroNavigationState.followUser else navigationState.followUser
                        if (!follow) LocationButtonState.WAITING_FOR_FIX else LocationButtonState.TRACKING
                    }
                },
                onClick = {
                    viewModel.handleLocationButtonClick()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (selectedDestination != null) 320.dp else 32.dp, end = 16.dp)
                    .padding(WindowInsets.navigationBars.asPaddingValues())
            )
        }

        // Navigation panels
        when {
            selectedDestination != null && !showDestinationMenu && !isNavigating -> {
                SelectedDestinationCard(
                    place = selectedDestination,
                    distanceFromUserMeters = selectedDestination.let { dest ->
                        val userPoint = currentLoc?.let { RoutePoint(it.latitude, it.longitude) }
                        if (userPoint != null) {
                            DestinationMath.distanceMeters(userPoint, dest.point)
                        } else {
                            null
                        }
                    },
                    onNavigate = { viewModel.startNavigation() },
                    onDismiss = {
                        viewModel.deselectPlace()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(mapInsets.asPaddingValues())
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }

            showDestinationMenu -> {
                DestinationMenu(
                    options = sampleDestinations,
                    onSelect = { destination ->
                        viewModel.selectPlace(
                            SelectedMapPlace(
                                title = destination.title,
                                subtitle = destination.subtitle,
                                point = destination.point,
                                properties = emptyList(),
                                fromMapFeature = false
                            )
                        )
                        viewModel.closeDestinationMenu()
                    },
                    onDismiss = {
                        viewModel.closeDestinationMenu()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(mapInsets.asPaddingValues())
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        }

        if (isNavigating) {
            if (activeProfile == NavigationProfile.METRO) {
                NavigationBottomBar(
                    state = navigationState.copy(
                        followUser = metroNavigationState.followUser,
                        elapsedTimeSeconds = metroNavigationState.elapsedTimeSeconds,
                        speedMetersPerSecond = 10.0
                    ),
                    onEndNavigation = { viewModel.stopMetroNavigation() },
                    onToggleFollow = { viewModel.toggleFollowUser() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            } else {
                NavigationTopBanner(
                    state = navigationState,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(mapInsets.asPaddingValues())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )

                NavigationBottomBar(
                    state = navigationState,
                    onEndNavigation = { viewModel.stopNavigation() },
                    onToggleFollow = { viewModel.toggleFollowUser() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
