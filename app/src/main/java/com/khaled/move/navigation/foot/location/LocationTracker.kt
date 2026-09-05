package com.khaled.move.navigation.foot.location

import com.khaled.move.navigation.foot.location.NavigationLocation
import kotlinx.coroutines.flow.Flow

interface LocationTracker {
    val locations: Flow<NavigationLocation>
    val latestLocation: NavigationLocation?
}
