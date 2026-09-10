#pragma once

#include "Models.h"

namespace move {

struct SnappedPoint {
    RoutePoint point;
    double fraction;
    double distanceMeters;
};

class GeoUtils {
public:
    static double distanceMeters(const RoutePoint& a, const RoutePoint& b);
    static double distanceMeters(double lat1, double lon1, double lat2, double lon2);
    static double bearingDegrees(const RoutePoint& from, const RoutePoint& to);
    static double normalizeBearingDegrees(double value);
    static double shortestAngleDeltaDegrees(double from, double to);
    static RoutePoint interpolate(const RoutePoint& a, const RoutePoint& b, double fraction);
    static SnappedPoint snapToSegment(const RoutePoint& point, const RoutePoint& start, const RoutePoint& end);
    static double toRadians(double degrees);
    static double toDegrees(double radians);

private:
    static constexpr double EarthRadiusMeters = 6371000.0;
};

} // namespace move
