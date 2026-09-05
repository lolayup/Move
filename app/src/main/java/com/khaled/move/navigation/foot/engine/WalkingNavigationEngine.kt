package com.khaled.move.navigation.foot.engine

import android.util.Log
import com.khaled.move.navigation.foot.speed.MovementState
import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.route.NavigationRoute
import com.khaled.move.navigation.foot.engine.NavigationStatus
import com.khaled.move.navigation.foot.engine.NavigationUiState
import com.khaled.move.navigation.foot.route.RoutePoint
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
            val speedEstimate = speedEstimator.onLocation(location)
            val progressSnapshot =
                routeProgressCalculator.calculate(route, location, speedEstimate.speedMetersPerSecond)
            val navigationPoint = navigationPoint(progressSnapshot, location)
            val bearing = bearingSmoother.update(
                snappedPoint = progressSnapshot.progress.snappedLocation,
                routeBearingDegrees = progressSnapshot.routeBearingDegrees,
                gpsBearingDegrees = location.bearingDegrees,
                movementState = speedEstimate.movementState,
                timestampMillis = location.timestampMillis,
            )
            _state.update {
                NavigationUiState(
                    status = if (speedEstimate.movementState == MovementState.Stationary) NavigationStatus.Stationary else NavigationStatus.Navigating,
                    movementState = speedEstimate.movementState,
                    route = route,
                    progress = progressSnapshot.progress,
                    speedMetersPerSecond = speedEstimate.speedMetersPerSecond,
                    elapsedTimeSeconds = calculateElapsedTime(),
                    rawLocation = location,
                    filteredLocation = location,
                    snappedLocation = progressSnapshot.progress.snappedLocation,
                    navigationLocation = navigationPoint,
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
        val speedEstimate = speedEstimator.onLocation(filteredLocation)
        val progressSnapshot =
            routeProgressCalculator.calculate(route, filteredLocation, speedEstimate.speedMetersPerSecond)
        val navigationPoint = navigationPoint(progressSnapshot, filteredLocation)
        val bearing = bearingSmoother.update(
            snappedPoint = progressSnapshot.progress.snappedLocation,
            routeBearingDegrees = progressSnapshot.routeBearingDegrees,
            gpsBearingDegrees = filteredLocation.bearingDegrees,
            movementState = speedEstimate.movementState,
            timestampMillis = filteredLocation.timestampMillis,
        )
        if (isArrived(route, filteredLocation, progressSnapshot)) {
            arrivalConfirmationSamples += 1
        } else {
            arrivalConfirmationSamples = 0
        }
        if (arrivalConfirmationSamples >= ARRIVAL_CONFIRMATION_SAMPLES) {
            _state.update {
                it.copy(
                    status = NavigationStatus.Arrived,
                    movementState = speedEstimate.movementState,
                    route = route,
                    progress = progressSnapshot.progress.copy(
                        remainingDistanceMeters = 0.0,
                        estimatedRemainingDurationSeconds = 0.0,
                    ),
                    speedMetersPerSecond = speedEstimate.speedMetersPerSecond,
                    elapsedTimeSeconds = calculateElapsedTime(),
                    rawLocation = rawLocation,
                    filteredLocation = filteredLocation,
                    snappedLocation = progressSnapshot.progress.snappedLocation,
                    navigationLocation = navigationPoint,
                    navigationBearingDegrees = bearing,
                    isRecalculating = false,
                    errorMessage = null,
                )
            }
            return
        }

        val offRouteResult = offRouteDetector.evaluate(
            location = filteredLocation,
            distanceFromRouteMeters = progressSnapshot.distanceFromRouteMeters,
            routeBearingDegrees = progressSnapshot.routeBearingDegrees,
            movementState = speedEstimate.movementState,
        )

        if (offRouteResult.offRoute && !isRerouting) {
            reroute(sessionId, route, filteredLocation, speedEstimate, progressSnapshot, navigationPoint, bearing)
            return
        }

        _state.update {
            it.copy(
                status = when {
                    isRerouting -> NavigationStatus.Rerouting
                    speedEstimate.movementState == MovementState.Stationary -> NavigationStatus.Stationary
                    else -> NavigationStatus.Navigating
                },
                movementState = speedEstimate.movementState,
                route = route,
                progress = progressSnapshot.progress,
                speedMetersPerSecond = speedEstimate.speedMetersPerSecond,
                elapsedTimeSeconds = calculateElapsedTime(),
                rawLocation = rawLocation,
                filteredLocation = filteredLocation,
                snappedLocation = progressSnapshot.progress.snappedLocation,
                navigationLocation = navigationPoint,
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
        speedEstimate: SpeedEstimate,
        progressSnapshot: RouteProgressSnapshot,
        navigationPoint: RoutePoint,
        bearing: Double?,
    ) {
        val destination = destination ?: currentRoute.destination
        isRerouting = true
        _state.update {
            it.copy(
                status = NavigationStatus.Rerouting,
                movementState = speedEstimate.movementState,
                route = currentRoute,
                progress = progressSnapshot.progress,
                speedMetersPerSecond = speedEstimate.speedMetersPerSecond,
                elapsedTimeSeconds = calculateElapsedTime(),
                rawLocation = location,
                filteredLocation = location,
                snappedLocation = progressSnapshot.progress.snappedLocation,
                navigationLocation = navigationPoint,
                navigationBearingDegrees = bearing,
                isRecalculating = true,
            )
        }
        rerouter.reroute(RoutePoint(location.latitude, location.longitude), destination)
            .onSuccess { newRoute ->
                if (sessionId != navigationSessionId) return
                activeRoute = newRoute
                isRerouting = false
                arrivalConfirmationSamples = 0
                offRouteDetector.reset()
                routeProgressCalculator.reset()
                bearingSmoother.reset()
                val reroutedProgress =
                    routeProgressCalculator.calculate(newRoute, location, speedEstimate.speedMetersPerSecond)
                val reroutedPoint = navigationPoint(reroutedProgress, location)
                val reroutedBearing = bearingSmoother.update(
                    snappedPoint = reroutedProgress.progress.snappedLocation,
                    routeBearingDegrees = reroutedProgress.routeBearingDegrees,
                    gpsBearingDegrees = location.bearingDegrees,
                    movementState = speedEstimate.movementState,
                    timestampMillis = location.timestampMillis,
                )
                _state.update {
                    it.copy(
                        status = NavigationStatus.Navigating,
                        route = newRoute,
                        progress = reroutedProgress.progress,
                        elapsedTimeSeconds = calculateElapsedTime(),
                        snappedLocation = reroutedProgress.progress.snappedLocation,
                        navigationLocation = reroutedPoint,
                        navigationBearingDegrees = reroutedBearing,
                        isRecalculating = false,
                        errorMessage = null,
                    )
                }
            }
            .onFailure { error ->
                if (sessionId != navigationSessionId) return
                isRerouting = false
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

    private fun calculateElapsedTime(): Long {
        val start = navigationStartTimeMillis ?: return 0L
        return (System.currentTimeMillis() - start) / 1000L
    }

    private fun navigationPoint(snapshot: RouteProgressSnapshot, location: NavigationLocation): RoutePoint {
        val rawPoint = RoutePoint(location.latitude, location.longitude)
        val accuracy = (location.accuracyMeters ?: 12f).toDouble()
        val snapThreshold = (accuracy + 14.0).coerceAtMost(42.0)
        return if (snapshot.distanceFromRouteMeters <= snapThreshold) {
            snapshot.progress.snappedLocation
        } else {
            rawPoint
        }
    }

    private fun isArrived(
        route: NavigationRoute,
        location: NavigationLocation,
        progressSnapshot: RouteProgressSnapshot,
    ): Boolean {
        val locationPoint = RoutePoint(location.latitude, location.longitude)
        val distanceToDestination = GeoUtils.distanceMeters(locationPoint, route.destination)
        val accuracy = (location.accuracyMeters ?: 12f).toDouble()
        return progressSnapshot.progress.remainingDistanceMeters <= ARRIVAL_ROUTE_DISTANCE_METERS ||
                distanceToDestination <= (accuracy + ARRIVAL_DESTINATION_DISTANCE_METERS)
    }

    private companion object {
        const val ARRIVAL_ROUTE_DISTANCE_METERS = 16.0
        const val ARRIVAL_DESTINATION_DISTANCE_METERS = 10.0
        const val ARRIVAL_CONFIRMATION_SAMPLES = 2
    }
}
