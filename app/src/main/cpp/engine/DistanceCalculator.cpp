#include "DistanceCalculator.h"
#include "GeoUtils.h"
#include <sstream>
#include <algorithm>
#include <limits>

namespace move {

DistanceCalculator::DistanceCalculator(EtaCalculator& etaCalculator) : etaCalculator(etaCalculator) {}

void DistanceCalculator::reset() {
    activeRouteKey = "";
    previousRouteDistanceMeters.reset();
    previousSegmentIndex.reset();
}

RouteProgressSnapshot DistanceCalculator::calculate(const NavigationRoute& route, const NavigationLocation& location, double speedMetersPerSecond) {
    std::string routeKey = getRouteKey(route);
    if (activeRouteKey != routeKey) {
        activeRouteKey = routeKey;
        previousRouteDistanceMeters.reset();
        previousSegmentIndex.reset();
        etaCalculator.reset();
    }

    const auto& routePoints = route.geometry;
    if (routePoints.size() < 2) {
        auto eta = etaCalculator.calculate(0.0, speedMetersPerSecond, location.timestampMillis);
        RouteProgress progress = {
            route.distanceMeters,
            route.distanceMeters,
            0.0,
            1.0,
            0,
            routePoints.empty() ? RoutePoint{0, 0} : routePoints.back(),
            (int)route.instructions.size() - 1,
            -1,
            0.0,
            eta.remainingDurationSeconds,
            eta.arrivalTimeMillis
        };
        return { progress, 0.0, std::nullopt, 0.0 };
    }

    RoutePoint locationPoint = { location.latitude, location.longitude };
    std::vector<double> prefixGeometryDistances = prefixDistances(routePoints);
    double geometryLengthMeters = std::max(1.0, prefixGeometryDistances.back());
    double routeScale = std::max(geometryLengthMeters, route.distanceMeters) / geometryLengthMeters;

    RouteMatch candidate = bestRouteMatch(locationPoint, routePoints, prefixGeometryDistances, routeScale, location.accuracyMeters);

    double backtrackAllowance = std::max(8.0, static_cast<double>(location.accuracyMeters * 0.35f));
    RouteMatch finalMatch = candidate;
    if (previousRouteDistanceMeters.has_value() && candidate.routeDistanceMeters < previousRouteDistanceMeters.value() - backtrackAllowance) {
        finalMatch = routeMatchAtDistance(routePoints, prefixGeometryDistances, previousRouteDistanceMeters.value() / routeScale, routeScale);
        finalMatch.distanceFromRouteMeters = candidate.distanceFromRouteMeters;
    }

    previousRouteDistanceMeters = std::max(previousRouteDistanceMeters.value_or(0.0), finalMatch.routeDistanceMeters);
    previousSegmentIndex = finalMatch.segmentIndex;

    double remainingDistance = std::max(0.0, route.distanceMeters - finalMatch.routeDistanceMeters);
    double traveledDistance = std::max(0.0, route.distanceMeters - remainingDistance);
    auto eta = etaCalculator.calculate(remainingDistance, speedMetersPerSecond, location.timestampMillis);

    // Instructions
    double instructionAdvanceMeters = 8.0;
    int currentInstructionIdx = -1;
    for (int i = 0; i < route.instructions.size(); ++i) {
        if (instructionDistanceMeters(route.instructions[i].routePointIndex, prefixGeometryDistances, routeScale) <= finalMatch.routeDistanceMeters + instructionAdvanceMeters) {
            currentInstructionIdx = i;
        }
    }

    int nextInstructionIdx = -1;
    for (int i = 0; i < route.instructions.size(); ++i) {
        if (instructionDistanceMeters(route.instructions[i].routePointIndex, prefixGeometryDistances, routeScale) > finalMatch.routeDistanceMeters + instructionAdvanceMeters) {
            nextInstructionIdx = i;
            break;
        }
    }

    double distanceToNext = 0.0;
    if (nextInstructionIdx != -1) {
        distanceToNext = std::max(0.0, instructionDistanceMeters(route.instructions[nextInstructionIdx].routePointIndex, prefixGeometryDistances, routeScale) - finalMatch.routeDistanceMeters);
    }

    std::optional<double> routeBearing;
    if (finalMatch.segmentIndex < (int)routePoints.size() - 1) {
        routeBearing = GeoUtils::bearingDegrees(routePoints[finalMatch.segmentIndex], routePoints[finalMatch.segmentIndex + 1]);
    }

    RouteProgress progress = {
        route.distanceMeters,
        traveledDistance,
        remainingDistance,
        (route.distanceMeters == 0.0) ? 1.0 : std::clamp(traveledDistance / route.distanceMeters, 0.0, 1.0),
        finalMatch.segmentIndex,
        finalMatch.snappedPoint,
        currentInstructionIdx,
        nextInstructionIdx,
        distanceToNext,
        eta.remainingDurationSeconds,
        eta.arrivalTimeMillis
    };

    return { progress, finalMatch.distanceFromRouteMeters, routeBearing, finalMatch.routeDistanceMeters };
}

std::string DistanceCalculator::getRouteKey(const NavigationRoute& route) {
    std::stringstream ss;
    ss << route.geometry.size() << ":" << static_cast<long>(route.distanceMeters);
    if (!route.geometry.empty()) {
        ss << ":" << route.geometry.front().latitude << "," << route.geometry.front().longitude;
        ss << ":" << route.geometry.back().latitude << "," << route.geometry.back().longitude;
    }
    return ss.str();
}

std::vector<double> DistanceCalculator::prefixDistances(const std::vector<RoutePoint>& points) {
    std::vector<double> result(points.size(), 0.0);
    for (size_t i = 1; i < points.size(); ++i) {
        result[i] = result[i - 1] + GeoUtils::distanceMeters(points[i - 1], points[i]);
    }
    return result;
}

RouteMatch DistanceCalculator::bestRouteMatch(const RoutePoint& locationPoint, const std::vector<RoutePoint>& routePoints,
                                             const std::vector<double>& prefixDistances, double routeScale, float accuracyMeters) {
    int startIdx = 0;
    int endIdx = static_cast<int>(routePoints.size()) - 1;

    if (previousSegmentIndex.has_value()) {
        startIdx = std::max(0, previousSegmentIndex.value() - 4);
        endIdx = std::min(static_cast<int>(routePoints.size()) - 1, previousSegmentIndex.value() + 80);
    }

    RouteMatch best = findBestMatch(locationPoint, routePoints, prefixDistances, routeScale, startIdx, endIdx);

    double fullSearchThreshold = std::max(55.0, static_cast<double>(accuracyMeters * 2.2f));
    if (previousSegmentIndex.has_value() && best.distanceFromRouteMeters > fullSearchThreshold) {
        best = findBestMatch(locationPoint, routePoints, prefixDistances, routeScale, 0, static_cast<int>(routePoints.size()) - 1);
    }
    return best;
}

RouteMatch DistanceCalculator::findBestMatch(const RoutePoint& locationPoint, const std::vector<RoutePoint>& routePoints,
                                            const std::vector<double>& prefixDistances, double routeScale, int startIdx, int endIdx) {
    int bestSegmentIndex = startIdx;
    double bestDistance = std::numeric_limits<double>::max();
    RoutePoint bestSnap = routePoints[startIdx];
    double bestFraction = 0.0;

    for (int i = startIdx; i < endIdx && i < (int)routePoints.size() - 1; ++i) {
        auto snapped = GeoUtils::snapToSegment(locationPoint, routePoints[i], routePoints[i + 1]);
        if (snapped.distanceMeters < bestDistance) {
            bestDistance = snapped.distanceMeters;
            bestSegmentIndex = i;
            bestSnap = snapped.point;
            bestFraction = snapped.fraction;
        }
    }

    double segmentLength = GeoUtils::distanceMeters(routePoints[bestSegmentIndex], routePoints[bestSegmentIndex + 1]);
    double geometryDistance = prefixDistances[bestSegmentIndex] + (segmentLength * bestFraction);

    return { bestSegmentIndex, bestSnap, geometryDistance * routeScale, bestDistance };
}

RouteMatch DistanceCalculator::routeMatchAtDistance(const std::vector<RoutePoint>& routePoints, const std::vector<double>& prefixDistances,
                                                   double geometryDistanceMeters, double routeScale) {
    double clamped = std::clamp(geometryDistanceMeters, 0.0, prefixDistances.back());
    auto it = std::upper_bound(prefixDistances.begin(), prefixDistances.end(), clamped);
    int segmentIndex = static_cast<int>(std::distance(prefixDistances.begin(), it)) - 1;
    segmentIndex = std::clamp(segmentIndex, 0, static_cast<int>(routePoints.size()) - 2);

    double segmentStartDistance = prefixDistances[segmentIndex];
    double segmentLength = std::max(1.0, GeoUtils::distanceMeters(routePoints[segmentIndex], routePoints[segmentIndex + 1]));
    double fraction = std::clamp((clamped - segmentStartDistance) / segmentLength, 0.0, 1.0);

    return { segmentIndex, GeoUtils::interpolate(routePoints[segmentIndex], routePoints[segmentIndex + 1], fraction), clamped * routeScale, 0.0 };
}

double DistanceCalculator::instructionDistanceMeters(int routePointIndex, const std::vector<double>& prefixDistances, double routeScale) {
    int idx = std::clamp(routePointIndex, 0, static_cast<int>(prefixDistances.size()) - 1);
    return prefixDistances[idx] * routeScale;
}

} // namespace move
