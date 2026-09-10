#pragma once

#include "Models.h"
#include "EtaCalculator.h"
#include <optional>

namespace move {

struct RouteMatch {
    int segmentIndex;
    RoutePoint snappedPoint;
    double routeDistanceMeters;
    double distanceFromRouteMeters;
};

struct RouteProgressSnapshot {
    RouteProgress progress;
    double distanceFromRouteMeters;
    std::optional<double> routeBearingDegrees;
    double routeDistanceAlongMeters;
};

class DistanceCalculator {
public:
    DistanceCalculator(EtaCalculator& etaCalculator);
    void reset();
    RouteProgressSnapshot calculate(const NavigationRoute& route, const NavigationLocation& location, double speedMetersPerSecond);

private:
    EtaCalculator& etaCalculator;
    std::string activeRouteKey;
    std::optional<double> previousRouteDistanceMeters;
    std::optional<int> previousSegmentIndex;

    std::string getRouteKey(const NavigationRoute& route);
    std::vector<double> prefixDistances(const std::vector<RoutePoint>& points);
    RouteMatch bestRouteMatch(const RoutePoint& locationPoint, const std::vector<RoutePoint>& routePoints,
                              const std::vector<double>& prefixDistances, double routeScale, float accuracyMeters);
    RouteMatch findBestMatch(const RoutePoint& locationPoint, const std::vector<RoutePoint>& routePoints,
                            const std::vector<double>& prefixDistances, double routeScale, int startIdx, int endIdx);
    RouteMatch routeMatchAtDistance(const std::vector<RoutePoint>& routePoints, const std::vector<double>& prefixDistances,
                                   double geometryDistanceMeters, double routeScale);
    double instructionDistanceMeters(int routePointIndex, const std::vector<double>& prefixDistances, double routeScale);
};

} // namespace move
