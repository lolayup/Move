#include "NavigationEngine.h"
#include "GeoUtils.h"
#include <algorithm>

namespace move {

NavigationEngine::NavigationEngine()
    : hasRoute(false),
      currentStatus(NavigationStatus::Idle),
      currentMovementState(MovementState::Stationary),
      navigationStartTimeMillis(0),
      isRerouting(false),
      arrivalConfirmationSamples(0),
      distanceCalculator(etaCalculator) {}

void NavigationEngine::startNavigation(const NavigationRoute& route, const NavigationLocation& initialLocation) {
    std::lock_guard<std::mutex> lock(engineMutex);

    activeRoute = route;
    hasRoute = true;
    navigationStartTimeMillis = initialLocation.timestampMillis;
    currentStatus = NavigationStatus::Navigating;
    isRerouting = false;
    arrivalConfirmationSamples = 0;

    etaCalculator.reset();
    distanceCalculator.reset();
    speedCalculator.reset();
    locationProcessor.reset();
}

void NavigationEngine::stopNavigation() {
    std::lock_guard<std::mutex> lock(engineMutex);
    hasRoute = false;
    currentStatus = NavigationStatus::Idle;
}

NavigationResult NavigationEngine::processLocation(const NavigationLocation& rawLocation) {
    std::lock_guard<std::mutex> lock(engineMutex);

    if (!hasRoute) {
        return { NavigationStatus::Error, MovementState::GpsUncertain, {}, 0.0, 0, {0,0}, 0.0, false };
    }

    if (!locationProcessor.isValid(rawLocation)) {
        // Return last known state or error
        return { currentStatus, currentMovementState, {}, 0.0, calculateElapsedTime(rawLocation.timestampMillis), {rawLocation.latitude, rawLocation.longitude}, 0.0, isRerouting };
    }

    NavigationLocation filtered = locationProcessor.process(rawLocation);
    SpeedEstimate speedEstimate = speedCalculator.onLocation(filtered);
    currentMovementState = speedEstimate.movementState;

    RouteProgressSnapshot snapshot = distanceCalculator.calculate(activeRoute, filtered, speedEstimate.speedMetersPerSecond);

    RoutePoint navPoint = navigationPoint(snapshot, filtered);
    double bearing = snapshot.routeBearingDegrees.value_or(0.0); // Simplified

    if (checkArrival(activeRoute, filtered, snapshot)) {
        arrivalConfirmationSamples++;
    } else {
        arrivalConfirmationSamples = 0;
    }

    if (arrivalConfirmationSamples >= ARRIVAL_CONFIRMATION_SAMPLES_REQUIRED) {
        currentStatus = NavigationStatus::Arrived;
        RouteProgress progress = snapshot.progress;
        progress.remainingDistanceMeters = 0.0;
        progress.estimatedRemainingDurationSeconds = 0.0;

        return { currentStatus, currentMovementState, progress, speedEstimate.speedMetersPerSecond,
                 calculateElapsedTime(rawLocation.timestampMillis), navPoint, bearing, false };
    }

    currentStatus = isRerouting ? NavigationStatus::Rerouting :
                    (currentMovementState == MovementState::Stationary ? NavigationStatus::Stationary : NavigationStatus::Navigating);

    return { currentStatus, currentMovementState, snapshot.progress, speedEstimate.speedMetersPerSecond,
             calculateElapsedTime(rawLocation.timestampMillis), navPoint, bearing, isRerouting };
}

void NavigationEngine::updateReroutingStatus(bool rerouting) {
    std::lock_guard<std::mutex> lock(engineMutex);
    isRerouting = rerouting;
}

bool NavigationEngine::checkArrival(const NavigationRoute& route, const NavigationLocation& location, const RouteProgressSnapshot& snapshot) {
    RoutePoint locationPoint = { location.latitude, location.longitude };
    double distanceToDestination = GeoUtils::distanceMeters(locationPoint, route.geometry.back());
    double accuracy = static_cast<double>(location.accuracyMeters);

    return snapshot.progress.remainingDistanceMeters <= ARRIVAL_ROUTE_DISTANCE_METERS ||
           distanceToDestination <= (accuracy + ARRIVAL_DESTINATION_DISTANCE_METERS);
}

long long NavigationEngine::calculateElapsedTime(long long currentTimestampMillis) {
    if (navigationStartTimeMillis == 0) return 0;
    return (currentTimestampMillis - navigationStartTimeMillis) / 1000;
}

RoutePoint NavigationEngine::navigationPoint(const RouteProgressSnapshot& snapshot, const NavigationLocation& location) {
    double accuracy = static_cast<double>(location.accuracyMeters);
    double snapThreshold = std::min(42.0, accuracy + 14.0);

    if (snapshot.distanceFromRouteMeters <= snapThreshold) {
        return snapshot.progress.snappedLocation;
    } else {
        return { location.latitude, location.longitude };
    }
}

} // namespace move
