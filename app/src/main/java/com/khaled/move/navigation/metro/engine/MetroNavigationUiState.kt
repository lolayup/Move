package com.khaled.move.navigation.metro.engine

import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.route.RoutePoint
import com.khaled.move.navigation.metro.data.MetroLine
import com.khaled.move.navigation.metro.data.MetroStation
import com.khaled.move.navigation.foot.engine.NavigationStatus
import java.time.Instant

data class MetroNavigationUiState(
    val status: NavigationStatus = NavigationStatus.Idle,
    val currentLine: MetroLine? = null,
    val currentStation: MetroStation? = null,
    val nextStation: MetroStation? = null,
    val previousStation: MetroStation? = null,
    val destinationStation: MetroStation? = null,
    val stationsRemaining: Int = 0,
    val totalStations: Int = 0,
    val progressFraction: Double = 0.0,
    val remainingDistanceMeters: Double = 0.0,
    val estimatedRemainingDurationSeconds: Double = 0.0,
    val estimatedArrivalTime: Instant? = null,
    val elapsedTimeSeconds: Long = 0,
    val rawLocation: NavigationLocation? = null,
    val followUser: Boolean = true,
    val errorMessage: String? = null
)
