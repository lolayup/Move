package com.khaled.move.navigation

import com.khaled.move.navigation.foot.location.NavigationLocation
import com.khaled.move.navigation.foot.route.NavigationRoute
import com.khaled.move.navigation.foot.engine.NavigationStatus
import com.khaled.move.navigation.foot.speed.MovementState
import com.khaled.move.navigation.foot.route.RouteProgress
import com.khaled.move.navigation.foot.instructions.NavigationInstruction
import java.time.Instant

class NativeNavigationEngine {

    companion object {
        init {
            System.loadLibrary("move-engine")
        }
    }

    private var nativePtr: Long = createNativeInstance()

    private external fun createNativeInstance(): Long
    private external fun destroyNativeInstance(ptr: Long)
    private external fun startNavigation(ptr: Long, route: NavigationRoute, initialLocation: NavigationLocation)
    private external fun stopNavigation(ptr: Long)
    private external fun processLocation(ptr: Long, location: NavigationLocation): NativeNavigationResult
    private external fun updateReroutingStatus(ptr: Long, isRerouting: Boolean)

    // Metro methods
    private external fun addMetroStation(ptr: Long, id: String, name: String, lat: Double, lon: Double, lines: Array<String>)
    private external fun addMetroLine(ptr: Long, id: String, name: String, color: String, stationIds: Array<String>)
    private external fun findNearbyMetroStation(ptr: Long, lat: Double, lon: Double, threshold: Double): String?
    private external fun isAtMetroStation(ptr: Long, lat: Double, lon: Double, threshold: Double): Boolean

    fun start(route: NavigationRoute, initialLocation: NavigationLocation) {
        startNavigation(nativePtr, route, initialLocation)
    }

    fun stop() {
        stopNavigation(nativePtr)
    }

    fun process(location: NavigationLocation): NativeNavigationResult {
        return processLocation(nativePtr, location)
    }

    fun setRerouting(isRerouting: Boolean) {
        updateReroutingStatus(nativePtr, isRerouting)
    }

    fun addStation(id: String, name: String, lat: Double, lon: Double, lines: List<String>) {
        addMetroStation(nativePtr, id, name, lat, lon, lines.toTypedArray())
    }

    fun addLine(id: String, name: String, color: String, stationIds: List<String>) {
        addMetroLine(nativePtr, id, name, color, stationIds.toTypedArray())
    }

    fun findNearbyStation(lat: Double, lon: Double, threshold: Double = 500.0): String? {
        return findNearbyMetroStation(nativePtr, lat, lon, threshold)
    }

    fun isAtStation(lat: Double, lon: Double, threshold: Double = 50.0): Boolean {
        return isAtMetroStation(nativePtr, lat, lon, threshold)
    }

    protected fun finalize() {
        destroyNativeInstance(nativePtr)
    }
}

data class NativeNavigationResult(
    val status: Int,
    val movementState: Int,
    val traveledDistanceMeters: Double,
    val remainingDistanceMeters: Double,
    val progressFraction: Double,
    val currentRouteSegmentIndex: Int,
    val snappedLatitude: Double,
    val snappedLongitude: Double,
    val currentInstructionIndex: Int,
    val nextInstructionIndex: Int,
    val distanceToNextInstructionMeters: Double,
    val estimatedRemainingDurationSeconds: Double,
    val estimatedArrivalTimeMillis: Long,
    val speedMetersPerSecond: Double,
    val elapsedTimeSeconds: Long,
    val navigationLatitude: Double,
    val navigationLongitude: Double,
    val navigationBearingDegrees: Double,
    val isRecalculating: Boolean
)
