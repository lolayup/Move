#pragma once

#include "Models.h"
#include "DistanceCalculator.h"
#include "SpeedCalculator.h"
#include "EtaCalculator.h"
#include "LocationProcessor.h"
#include "MetroEngine.h"
#include <memory>
#include <mutex>

namespace move {

class NavigationEngine {
public:
    NavigationEngine();

    void startNavigation(const NavigationRoute& route, const NavigationLocation& initialLocation);
    void stopNavigation();

    NavigationResult processLocation(const NavigationLocation& rawLocation);

    void updateReroutingStatus(bool isRerouting);

    MetroEngine& getMetroEngine() { return metroEngine; }

private:
    std::mutex engineMutex;

    NavigationRoute activeRoute;
    bool hasRoute;

    NavigationStatus currentStatus;
    MovementState currentMovementState;
    long long navigationStartTimeMillis;
    bool isRerouting;
    int arrivalConfirmationSamples;

    EtaCalculator etaCalculator;
    DistanceCalculator distanceCalculator;
    SpeedCalculator speedCalculator;
    LocationProcessor locationProcessor;
    MetroEngine metroEngine;

    bool checkArrival(const NavigationRoute& route, const NavigationLocation& location, const RouteProgressSnapshot& snapshot);
    long long calculateElapsedTime(long long currentTimestampMillis);
    RoutePoint navigationPoint(const RouteProgressSnapshot& snapshot, const NavigationLocation& location);

    static constexpr double ARRIVAL_ROUTE_DISTANCE_METERS = 16.0;
    static constexpr double ARRIVAL_DESTINATION_DISTANCE_METERS = 10.0;
    static constexpr int ARRIVAL_CONFIRMATION_SAMPLES_REQUIRED = 2;
};

} // namespace move
