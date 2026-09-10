package com.khaled.move.navigation.foot.engine

import android.util.Log
import com.khaled.move.navigation.NativeNavigationEngine
import com.khaled.move.navigation.NativeNavigationResult
import com.khaled.move.navigation.foot.speed.MovementState
import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.route.NavigationRoute
import com.khaled.move.navigation.foot.engine.NavigationStatus
import com.khaled.move.navigation.foot.engine.NavigationUiState
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.foot.route.RouteProgress
import com.khaled.move.navigation.foot.camera.NavigationBearingSmoother
import com.khaled.move.navigation.foot.eta.EtaCalculator
import com.khaled.move.navigation.foot.location.NavigationLocationFilter
import com.khaled.move.navigation.foot.rerouting.NavigationRerouter
import com.khaled.move.navigation.foot.rerouting.OffRouteDetector
import com.khaled.move.navigation.foot.route.GeoUtils
import com.khaled.move.navigation.foot.route.RouteProgressCalculator
import com.khaled.move.navigation.foot.route.RouteProgressSnapshot
import com.khaled.move.navigation.foot.route.WalkingRoutingRepository
import com.khaled.move.navigation.foot.speed.SpeedEstimate
import com.khaled.move.navigation.foot.speed.WalkingSpeedEstimator
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
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

class WalkingNavigationEngine(
    private val routingRepository: WalkingRoutingRepository,
    private val nativeEngine: NativeNavigationEngine,
    private val routeProgressCalculator: RouteProgressCalculator = RouteProgressCalculator(EtaCalculator()),
    private val speedEstimator: WalkingSpeedEstimator = WalkingSpeedEstimator(),
    private val offRouteDetector: OffRouteDetector = OffRouteDetector(),
    private val rerouter: NavigationRerouter = NavigationRerouter(routingRepository),
    private val locationFilter: NavigationLocationFilter = NavigationLocationFilter(),
    private val bearingSmoother: NavigationBearingSmoother = NavigationBearingSmoother(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(NavigationUiState())
    val state: StateFlow<NavigationUiState> = _state.asStateFlow()

    private var activeRoute: NavigationRoute? = null
    private var destination: RoutePoint? = null
    private var locationJob: Job? = null
    private var latestLocation: NavigationLocation? = null
    private var navigationSessionId: Long = 0L
    private var navigationStartTimeMillis: Long? = null
    private var isRerouting: Boolean = false
    private var arrivalConfirmationSamples: Int = 0

    fun startNavigation(
        destination: RoutePoint,
        initialLocation: NavigationLocation?,
        locationUpdates: Flow<NavigationLocation>,
    ) {
        navigationSessionId += 1L
        val sessionId = navigationSessionId
        navigationStartTimeMillis = System.currentTimeMillis()
        latestLocation = initialLocation ?: latestLocation
        Log.d(
            "MoveNav",
            "Engine startNavigation session=$sessionId dest=${destination.latitude},${destination.longitude} latestLocation=$latestLocation"
        )
        this.destination = destination
        activeRoute = null
        isRerouting = false
        arrivalConfirmationSamples = 0
        routeProgressCalculator.reset()
        locationFilter.reset()
        bearingSmoother.reset()
        speedEstimator.reset()
        offRouteDetector.reset()
        nativeEngine.stop()
        
        _state.value = _state.value.copy(
            status = NavigationStatus.Preparing,
            isRecalculating = true,
            errorMessage = null,
            progress = null,
            route = null,
            elapsedTimeSeconds = 0,
            navigationLocation = latestLocation?.let { RoutePoint(it.latitude, it.longitude) },
            navigationBearingDegrees = null,
        )
        locationJob?.cancel()
        locationJob = scope.launch {
            // Ticker for real-time elapsed time updates
            launch {
                while (isActive) {
                    delay(1.seconds)
                    _state.update { it.copy(elapsedTimeSeconds = calculateElapsedTime()) }
                }
            }

            latestLocation?.let { currentLocation ->
                val filtered = locationFilter.onLocation(currentLocation) ?: currentLocation
                requestInitialRoute(sessionId, filtered, destination)
            }
            locationUpdates.collect { rawLocation ->
                if (sessionId != navigationSessionId || _state.value.status == NavigationStatus.Arrived) return@collect
                latestLocation = rawLocation
                val filteredLocation = locationFilter.onLocation(rawLocation) ?: rawLocation
                val route = activeRoute
                if (route == null) {
                    requestInitialRoute(sessionId, filteredLocation, destination)
                } else {
                    processLocation(sessionId, route, rawLocation, filteredLocation)
                }
            }
        }
    }

    fun stopNavigation() {
        navigationSessionId += 1L
        navigationStartTimeMillis = null
        locationJob?.cancel()
        locationJob = null
        activeRoute = null
        destination = null
        isRerouting = false
        arrivalConfirmationSamples = 0
        routeProgressCalculator.reset()
        locationFilter.reset()
        bearingSmoother.reset()
        speedEstimator.reset()
        offRouteDetector.reset()
        nativeEngine.stop()
        _state.value = NavigationUiState()
    }

    fun setFollowUser(follow: Boolean) {
        _state.value = _state.value.copy(followUser = follow)
    }

    fun setDeviceHeading(heading: Double) {
        _state.value = _state.value.copy(deviceHeadingDegrees = heading)
    }

    private suspend fun requestInitialRoute(sessionId: Long, location: NavigationLocation, destination: RoutePoint) {
        Log.d(
            "MoveNav",
            "Engine requestInitialRoute session=$sessionId origin=${location.latitude},${location.longitude} dest=${destination.latitude},${destination.longitude}"
        )
        val routeResult = routingRepository.getWalkingRoute(
            origin = RoutePoint(location.latitude, location.longitude),
            destination = destination,
        )
        if (sessionId != navigationSessionId) return
        routeResult.onSuccess { route ->
            Log.d("MoveNav", "Engine route success points=${route.geometry.size} distance=${route.distanceMeters}")
            activeRoute = route
            offRouteDetector.reset()
            routeProgressCalculator.reset()
            bearingSmoother.reset()
            
            nativeEngine.start(route, location)
            val nativeResult = nativeEngine.process(location)
            
            val progress = mapNativeProgress(nativeResult, route)
            
            val bearing = bearingSmoother.update(
                snappedPoint = RoutePoint(nativeResult.snappedLatitude, nativeResult.snappedLongitude),
                routeBearingDegrees = nativeResult.navigationBearingDegrees,
                gpsBearingDegrees = location.bearingDegrees,
                movementState = MovementState.entries[nativeResult.movementState],
                timestampMillis = location.timestampMillis,
            )
            
            _state.update {
                NavigationUiState(
                    status = NavigationStatus.entries[nativeResult.status],
                    movementState = MovementState.entries[nativeResult.movementState],
                    route = route,
                    progress = progress,
                    speedMetersPerSecond = nativeResult.speedMetersPerSecond,
                    elapsedTimeSeconds = nativeResult.elapsedTimeSeconds,
                    rawLocation = location,
                    filteredLocation = location,
                    snappedLocation = RoutePoint(nativeResult.snappedLatitude, nativeResult.snappedLongitude),
                    navigationLocation = RoutePoint(nativeResult.navigationLatitude, nativeResult.navigationLongitude),
                    navigationBearingDegrees = bearing,
                    followUser = true,
                    isRecalculating = false,
                    errorMessage = null,
                )
            }
        }.onFailure { error ->
            Log.e("MoveNav", "Engine route failure", error)
            _state.update {
                it.copy(
                    status = NavigationStatus.Error,
                    isRecalculating = false,
                    errorMessage = error.message ?: "Unable to calculate route",
                    rawLocation = location,
                    filteredLocation = location,
                    navigationLocation = RoutePoint(location.latitude, location.longitude),
                    elapsedTimeSeconds = calculateElapsedTime(),
                )
            }
        }
    }

    private suspend fun processLocation(
        sessionId: Long,
        route: NavigationRoute,
        rawLocation: NavigationLocation,
        filteredLocation: NavigationLocation,
    ) {
        val nativeResult = nativeEngine.process(filteredLocation)
        val progress = mapNativeProgress(nativeResult, route)
        
        val bearing = bearingSmoother.update(
            snappedPoint = RoutePoint(nativeResult.snappedLatitude, nativeResult.snappedLongitude),
            routeBearingDegrees = nativeResult.navigationBearingDegrees,
            gpsBearingDegrees = filteredLocation.bearingDegrees,
            movementState = MovementState.entries[nativeResult.movementState],
            timestampMillis = filteredLocation.timestampMillis,
        )
        
        if (nativeResult.status == NavigationStatus.Arrived.ordinal) {
            _state.update {
                it.copy(
                    status = NavigationStatus.Arrived,
                    movementState = MovementState.entries[nativeResult.movementState],
                    route = route,
                    progress = progress,
                    speedMetersPerSecond = nativeResult.speedMetersPerSecond,
                    elapsedTimeSeconds = nativeResult.elapsedTimeSeconds,
                    rawLocation = rawLocation,
                    filteredLocation = filteredLocation,
                    snappedLocation = RoutePoint(nativeResult.snappedLatitude, nativeResult.snappedLongitude),
                    navigationLocation = RoutePoint(nativeResult.navigationLatitude, nativeResult.navigationLongitude),
                    navigationBearingDegrees = bearing,
                    isRecalculating = false,
                    errorMessage = null,
                )
            }
            return
        }

        // Distance from route for off-route detection
        val distFromRoute = GeoUtils.distanceMeters(
            RoutePoint(filteredLocation.latitude, filteredLocation.longitude),
            RoutePoint(nativeResult.snappedLatitude, nativeResult.snappedLongitude)
        )

        val offRouteResult = offRouteDetector.evaluate(
            location = filteredLocation,
            distanceFromRouteMeters = distFromRoute,
            routeBearingDegrees = nativeResult.navigationBearingDegrees,
            movementState = MovementState.entries[nativeResult.movementState],
        )

        if (offRouteResult.offRoute && !isRerouting) {
            reroute(sessionId, route, filteredLocation, nativeResult, progress, bearing)
            return
        }

        _state.update {
            it.copy(
                status = when {
                    isRerouting -> NavigationStatus.Rerouting
                    MovementState.entries[nativeResult.movementState] == MovementState.Stationary -> NavigationStatus.Stationary
                    else -> NavigationStatus.Navigating
                },
                movementState = MovementState.entries[nativeResult.movementState],
                route = route,
                progress = progress,
                speedMetersPerSecond = nativeResult.speedMetersPerSecond,
                elapsedTimeSeconds = nativeResult.elapsedTimeSeconds,
                rawLocation = rawLocation,
                filteredLocation = filteredLocation,
                snappedLocation = RoutePoint(nativeResult.snappedLatitude, nativeResult.snappedLongitude),
                navigationLocation = RoutePoint(nativeResult.navigationLatitude, nativeResult.navigationLongitude),
                navigationBearingDegrees = bearing,
                isRecalculating = isRerouting,
                errorMessage = null,
            )
        }
    }

    private suspend fun reroute(
        sessionId: Long,
        currentRoute: NavigationRoute,
        location: NavigationLocation,
        nativeResult: NativeNavigationResult,
        progress: RouteProgress,
        bearing: Double?,
    ) {
        val destination = destination ?: currentRoute.destination
        isRerouting = true
        nativeEngine.setRerouting(true)
        
        _state.update {
            it.copy(
                status = NavigationStatus.Rerouting,
                movementState = MovementState.entries[nativeResult.movementState],
                route = currentRoute,
                progress = progress,
                speedMetersPerSecond = nativeResult.speedMetersPerSecond,
                elapsedTimeSeconds = nativeResult.elapsedTimeSeconds,
                rawLocation = location,
                filteredLocation = location,
                snappedLocation = RoutePoint(nativeResult.snappedLatitude, nativeResult.snappedLongitude),
                navigationLocation = RoutePoint(nativeResult.navigationLatitude, nativeResult.navigationLongitude),
                navigationBearingDegrees = bearing,
                isRecalculating = true,
            )
        }
        rerouter.reroute(RoutePoint(location.latitude, location.longitude), destination)
            .onSuccess { newRoute ->
                if (sessionId != navigationSessionId) return
                activeRoute = newRoute
                isRerouting = false
                nativeEngine.setRerouting(false)
                
                nativeEngine.start(newRoute, location)
                val reroutedNative = nativeEngine.process(location)
                val reroutedProgress = mapNativeProgress(reroutedNative, newRoute)
                
                val reroutedBearing = bearingSmoother.update(
                    snappedPoint = RoutePoint(reroutedNative.snappedLatitude, reroutedNative.snappedLongitude),
                    routeBearingDegrees = reroutedNative.navigationBearingDegrees,
                    gpsBearingDegrees = location.bearingDegrees,
                    movementState = MovementState.entries[reroutedNative.movementState],
                    timestampMillis = location.timestampMillis,
                )
                _state.update {
                    it.copy(
                        status = NavigationStatus.Navigating,
                        route = newRoute,
                        progress = reroutedProgress,
                        elapsedTimeSeconds = reroutedNative.elapsedTimeSeconds,
                        snappedLocation = RoutePoint(reroutedNative.snappedLatitude, reroutedNative.snappedLongitude),
                        navigationLocation = RoutePoint(reroutedNative.navigationLatitude, reroutedNative.navigationLongitude),
                        navigationBearingDegrees = reroutedBearing,
                        isRecalculating = false,
                        errorMessage = null,
                    )
                }
            }
            .onFailure { error ->
                if (sessionId != navigationSessionId) return
                isRerouting = false
                nativeEngine.setRerouting(false)
                _state.update {
                    it.copy(
                        status = NavigationStatus.Navigating,
                        isRecalculating = false,
                        errorMessage = error.message ?: "Unable to reroute. Continuing on current route.",
                        elapsedTimeSeconds = calculateElapsedTime(),
                    )
                }
            }
    }

    private fun mapNativeProgress(native: NativeNavigationResult, route: NavigationRoute): RouteProgress {
        val currentInst = route.instructions.getOrNull(native.currentInstructionIndex)
        val nextInstruction = route.instructions.getOrNull(native.nextInstructionIndex)

        return RouteProgress(
            totalRouteDistanceMeters = route.distanceMeters,
            traveledDistanceMeters = native.traveledDistanceMeters,
            remainingDistanceMeters = native.remainingDistanceMeters,
            progressFraction = native.progressFraction,
            currentRouteSegmentIndex = native.currentRouteSegmentIndex,
            snappedLocation = RoutePoint(native.snappedLatitude, native.snappedLongitude),
            currentInstruction = currentInst,
            nextInstruction = nextInstruction,
            distanceToNextInstructionMeters = native.distanceToNextInstructionMeters,
            estimatedRemainingDurationSeconds = native.estimatedRemainingDurationSeconds,
            estimatedArrivalTime = Instant.ofEpochMilli(native.estimatedArrivalTimeMillis),
        )
    }

    private fun calculateElapsedTime(): Long {
        val start = navigationStartTimeMillis ?: return 0L
        return (System.currentTimeMillis() - start) / 1000L
    }
}

