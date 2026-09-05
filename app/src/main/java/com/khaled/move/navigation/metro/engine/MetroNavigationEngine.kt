package com.khaled.move.navigation.metro.engine

import com.khaled.move.navigation.foot.engine.NavigationStatus
import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.route.GeoUtils
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.metro.data.CairoMetroRepository
import com.khaled.move.navigation.metro.data.MetroLine
import com.khaled.move.navigation.metro.data.MetroStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class MetroNavigationEngine(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _state = MutableStateFlow(MetroNavigationUiState())
    val state: StateFlow<MetroNavigationUiState> = _state.asStateFlow()

    private var navigationStartTimeMillis: Long? = null
    private var locationJob: Job? = null
    private var tickerJob: Job? = null

    private var routeStations = listOf<MetroStation>()
    private var activeLine: MetroLine? = null

    fun startNavigation(
        lineId: String,
        destinationStationId: String,
        locationUpdates: Flow<NavigationLocation>
    ) {
        val line = CairoMetroRepository.getLine(lineId) ?: return
        val destination = CairoMetroRepository.getStation(destinationStationId) ?: return
        
        activeLine = line
        // Simplified route calculation: just use the line segment
        // In a real app, this would handle transfers
        routeStations = line.stations.mapNotNull { CairoMetroRepository.getStation(it) }
        
        navigationStartTimeMillis = System.currentTimeMillis()
        
        _state.value = MetroNavigationUiState(
            status = NavigationStatus.Navigating,
            currentLine = line,
            destinationStation = destination,
            totalStations = routeStations.size,
            followUser = true
        )

        locationJob?.cancel()
        locationJob = scope.launch {
            locationUpdates.collect { location ->
                processLocation(location)
            }
        }

        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1.seconds)
                _state.update { it.copy(elapsedTimeSeconds = calculateElapsedTime()) }
            }
        }
    }

    fun stopNavigation() {
        locationJob?.cancel()
        tickerJob?.cancel()
        _state.value = MetroNavigationUiState()
        navigationStartTimeMillis = null
    }

    private fun processLocation(location: NavigationLocation) {
        val userPoint = RoutePoint(location.latitude, location.longitude)
        
        // Find closest station on the current line
        val closestStation = routeStations.minByOrNull { 
            GeoUtils.distanceMeters(userPoint, it.point)
        } ?: return

        val distanceToStation = GeoUtils.distanceMeters(userPoint, closestStation.point)
        
        // If within 200m, consider it the "current" station
        val currentStation = if (distanceToStation < 200.0) closestStation else null
        
        val stationIndex = routeStations.indexOf(closestStation)
        val nextStation = routeStations.getOrNull(stationIndex + 1)
        val previousStation = routeStations.getOrNull(stationIndex - 1)

        val remainingDistance = GeoUtils.distanceMeters(userPoint, routeStations.last().point)
        val speed = location.speedMetersPerSecond?.toDouble() ?: 10.0 // Default 10m/s for metro
        
        val remainingSeconds = if (speed > 1.0) remainingDistance / speed else 300.0

        _state.update {
            it.copy(
                currentStation = currentStation,
                nextStation = nextStation,
                previousStation = previousStation,
                remainingDistanceMeters = remainingDistance,
                estimatedRemainingDurationSeconds = remainingSeconds,
                estimatedArrivalTime = Instant.now().plusSeconds(remainingSeconds.toLong()),
                rawLocation = location
            )
        }
    }

    private fun calculateElapsedTime(): Long {
        val start = navigationStartTimeMillis ?: return 0L
        return (System.currentTimeMillis() - start) / 1000L
    }

    fun setFollowUser(follow: Boolean) {
        _state.update { it.copy(followUser = follow) }
    }

    fun setDeviceHeading(heading: Double) {
        // Handle device heading for metro view if needed
    }
}
