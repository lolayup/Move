package com.khaled.move.navigation.foot.location


import com.khaled.move.navigation.foot.location.NavigationLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MapLibreLocationTracker : LocationTracker {
    private val mutableLocations = MutableSharedFlow<NavigationLocation>(extraBufferCapacity = 32)
    override val locations: Flow<NavigationLocation> = mutableLocations.asSharedFlow()
    override var latestLocation: NavigationLocation? = null
        private set

    fun onLocation(location: NavigationLocation) {
        latestLocation = location
        mutableLocations.tryEmit(location)
    }
}
