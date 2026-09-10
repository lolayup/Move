#pragma once

#include <vector>
#include <string>

namespace move {

struct RoutePoint {
    double latitude;
    double longitude;
};

struct NavigationLocation {
    double latitude;
    double longitude;
    float accuracyMeters;
    float bearingDegrees;
    float speedMetersPerSecond;
    long long timestampMillis;
};

enum class MovementState {
    Stationary,
    Walking,
    GpsUncertain
};

enum class NavigationStatus {
    Idle,
    Preparing,
    Navigating,
    Stationary,
    OffRoute,
    Rerouting,
    Arrived,
    Error
};

struct RouteInstruction {
    int routePointIndex;
    std::string text;
};

struct NavigationRoute {
    std::vector<RoutePoint> geometry;
    double distanceMeters;
    std::vector<RouteInstruction> instructions;
};

struct MetroStation {
    std::string id;
    std::string name;
    double latitude;
    double longitude;
    std::vector<std::string> lines;
};

struct MetroLine {
    std::string id;
    std::string name;
    std::string colorHex;
    std::vector<std::string> stationIds;
};

struct RouteProgress {
    double totalRouteDistanceMeters;
    double traveledDistanceMeters;
    double remainingDistanceMeters;
    double progressFraction;
    int currentRouteSegmentIndex;
    RoutePoint snappedLocation;
    int currentInstructionIndex;
    int nextInstructionIndex;
    double distanceToNextInstructionMeters;
    double estimatedRemainingDurationSeconds;
    long long estimatedArrivalTimeMillis;
};

struct NavigationResult {
    NavigationStatus status;
    MovementState movementState;
    RouteProgress progress;
    double speedMetersPerSecond;
    long long elapsedTimeSeconds;
    RoutePoint snappedLocation;
    double navigationBearingDegrees;
    bool isRecalculating;
};

} // namespace move
