package com.khaled.move

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khaled.move.navigation.foot.engine.NavigationStatus
import com.khaled.move.navigation.foot.rendering.SelectedMapPlace
import com.khaled.move.navigation.foot.engine.WalkingNavigationEngine
import com.khaled.move.navigation.foot.location.MapLibreLocationTracker
import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.engine.NavigationUiState
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.foot.route.ValhallaWalkingRoutingRepository
import com.khaled.move.navigation.metro.data.CairoMetroRepository
import com.khaled.move.navigation.metro.data.MetroLine
import com.khaled.move.navigation.metro.engine.MetroNavigationEngine
import com.khaled.move.navigation.metro.engine.MetroNavigationUiState
import com.khaled.move.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class NavigationProfile { FOOT, METRO }

data class LocationState(
    val location: NavigationLocation? = null,
    val isPermissionGranted: Boolean = false,
    val isLocationTracking: Boolean = false,
)

class MainViewModel : ViewModel() {

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _location = MutableStateFlow(LocationState())
    val location: StateFlow<LocationState> = _location.asStateFlow()

    private val _selectedDestination = MutableStateFlow<SelectedMapPlace?>(null)
    val selectedDestination: StateFlow<SelectedMapPlace?> = _selectedDestination.asStateFlow()

    private val _showDestinationMenu = MutableStateFlow(false)
    val showDestinationMenu: StateFlow<Boolean> = _showDestinationMenu.asStateFlow()

    private val _activeProfile = MutableStateFlow(NavigationProfile.FOOT)
    val activeProfile: StateFlow<NavigationProfile> = _activeProfile.asStateFlow()

    private val _selectedMetroLine = MutableStateFlow<MetroLine?>(null)
    val selectedMetroLine: StateFlow<MetroLine?> = _selectedMetroLine.asStateFlow()

    private val locationTracker = MapLibreLocationTracker()
    
    private val footNavigationEngine = WalkingNavigationEngine(
        routingRepository = ValhallaWalkingRoutingRepository(),
        scope = viewModelScope,
    )

    private val metroNavigationEngine = MetroNavigationEngine(
        scope = viewModelScope
    )

    val footNavigationState: StateFlow<NavigationUiState> = footNavigationEngine.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = NavigationUiState(),
        )

    val metroNavigationState: StateFlow<MetroNavigationUiState> = metroNavigationEngine.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = MetroNavigationUiState(),
        )

    // Backward compatibility for existing UI
    val navigationState: StateFlow<NavigationUiState> = footNavigationState

    val followUser: Boolean
        get() = if (_activeProfile.value == NavigationProfile.METRO) 
            metroNavigationState.value.followUser 
        else footNavigationState.value.followUser

    init {
        // Automatic profile switching logic
        viewModelScope.launch {
            _location.collect { state ->
                val loc = state.location ?: return@collect
                val nearbyStation = CairoMetroRepository.findNearbyStation(RoutePoint(loc.latitude, loc.longitude))
                
                if (nearbyStation != null && _activeProfile.value == NavigationProfile.FOOT) {
                    if (footNavigationState.value.status == NavigationStatus.Idle) {
                        _activeProfile.value = NavigationProfile.METRO
                    }
                } else if (nearbyStation == null && _activeProfile.value == NavigationProfile.METRO) {
                    if (metroNavigationState.value.status == NavigationStatus.Idle) {
                        _activeProfile.value = NavigationProfile.FOOT
                    }
                }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun onLocationUpdate(newLocation: NavigationLocation) {
        locationTracker.onLocation(newLocation)
        _location.value = _location.value.copy(location = newLocation)
    }

    fun onHeadingUpdate(headingDegrees: Double) {
        footNavigationEngine.setDeviceHeading(headingDegrees)
        metroNavigationEngine.setDeviceHeading(headingDegrees)
    }

    fun handleLocationButtonClick() {
        if (!_location.value.isLocationTracking) {
            _location.value = _location.value.copy(isLocationTracking = true)
            resumeFollowUser()
        } else {
            toggleFollowUser()
        }
    }

    fun toggleLocationTracking() {
        val current = _location.value.isLocationTracking
        _location.value = _location.value.copy(isLocationTracking = !current)
        if (!current) {
            resumeFollowUser()
        }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _location.value = _location.value.copy(
            isPermissionGranted = granted,
            isLocationTracking = granted // Start tracking if granted
        )
    }

    fun selectPlace(place: SelectedMapPlace) {
        _selectedDestination.value = place
        
        val metroStation = CairoMetroRepository.findStationByFeature(place.title, place.point)
        if (metroStation != null) {
            _selectedMetroLine.value = CairoMetroRepository.getLine(metroStation.lines.first())
            _activeProfile.value = NavigationProfile.METRO
        } else {
            _selectedMetroLine.value = null
        }
    }

    fun deselectPlace() {
        _selectedDestination.value = null
        _selectedMetroLine.value = null
        if (footNavigationState.value.status != NavigationStatus.Idle) {
            stopNavigation()
        }
        if (metroNavigationState.value.status != NavigationStatus.Idle) {
            stopMetroNavigation()
        }
    }

    fun startNavigation() {
        val destination = _selectedDestination.value ?: return
        
        if (_activeProfile.value == NavigationProfile.METRO) {
            val metroStation = CairoMetroRepository.findStationByFeature(destination.title, destination.point)
            val line = _selectedMetroLine.value
            if (metroStation != null && line != null) {
                metroNavigationEngine.startNavigation(
                    lineId = line.id,
                    destinationStationId = metroStation.id,
                    locationUpdates = locationTracker.locations
                )
            }
        } else {
            footNavigationEngine.startNavigation(
                destination = destination.point,
                initialLocation = locationTracker.latestLocation,
                locationUpdates = locationTracker.locations,
            )
        }
    }

    fun stopNavigation() {
        footNavigationEngine.stopNavigation()
    }

    fun stopMetroNavigation() {
        metroNavigationEngine.stopNavigation()
    }

    fun openDestinationMenu() {
        _showDestinationMenu.value = true
    }

    fun closeDestinationMenu() {
        _showDestinationMenu.value = false
    }

    fun resumeFollowUser() {
        if (_activeProfile.value == NavigationProfile.METRO) {
            metroNavigationEngine.setFollowUser(true)
        } else {
            footNavigationEngine.setFollowUser(true)
        }
    }

    fun toggleFollowUser() {
        val current = if (_activeProfile.value == NavigationProfile.METRO) 
            metroNavigationState.value.followUser 
        else footNavigationState.value.followUser
        
        setFollowUser(!current)
    }

    fun setFollowUser(follow: Boolean) {
        if (_activeProfile.value == NavigationProfile.METRO) {
            metroNavigationEngine.setFollowUser(follow)
        } else {
            footNavigationEngine.setFollowUser(follow)
        }
    }

    fun selectMetroLine(lineId: String) {
        _selectedMetroLine.value = CairoMetroRepository.getLine(lineId)
    }
}
