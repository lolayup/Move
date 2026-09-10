#include "GeoUtils.h"
#include <cmath>
#include <algorithm>

namespace move {

double GeoUtils::toRadians(double degrees) {
    return degrees * M_PI / 180.0;
}

double GeoUtils::toDegrees(double radians) {
    return radians * 180.0 / M_PI;
}

double GeoUtils::distanceMeters(const RoutePoint& a, const RoutePoint& b) {
    return distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude);
}

double GeoUtils::distanceMeters(double lat1, double lon1, double lat2, double lon2) {
    double dLat = toRadians(lat2 - lat1);
    double dLon = toRadians(lon2 - lon1);
    double rLat1 = toRadians(lat1);
    double rLat2 = toRadians(lat2);

    double a = std::sin(dLat / 2) * std::sin(dLat / 2) +
               std::sin(dLon / 2) * std::sin(dLon / 2) * std::cos(rLat1) * std::cos(rLat2);
    double c = 2 * std::atan2(std::sqrt(a), std::sqrt(1 - a));
    return EarthRadiusMeters * c;
}

double GeoUtils::bearingDegrees(const RoutePoint& from, const RoutePoint& to) {
    double lat1 = toRadians(from.latitude);
    double lat2 = toRadians(to.latitude);
    double dLon = toRadians(to.longitude - from.longitude);

    double y = std::sin(dLon) * std::cos(lat2);
    double x = std::cos(lat1) * std::sin(lat2) - std::sin(lat1) * std::cos(lat2) * std::cos(dLon);
    return normalizeBearingDegrees(toDegrees(std::atan2(y, x)));
}

double GeoUtils::normalizeBearingDegrees(double value) {
    double res = std::fmod(value, 360.0);
    if (res < 0) res += 360.0;
    return res;
}

double GeoUtils::shortestAngleDeltaDegrees(double from, double to) {
    double delta = normalizeBearingDegrees(to) - normalizeBearingDegrees(from);
    if (delta > 180.0) return delta - 360.0;
    if (delta < -180.0) return delta + 360.0;
    return delta;
}

RoutePoint GeoUtils::interpolate(const RoutePoint& a, const RoutePoint& b, double fraction) {
    return {
        a.latitude + (b.latitude - a.latitude) * fraction,
        a.longitude + (b.longitude - a.longitude) * fraction
    };
}

SnappedPoint GeoUtils::snapToSegment(const RoutePoint& point, const RoutePoint& start, const RoutePoint& end) {
    double scaleLat = 111320.0;
    double scaleLon = std::max(0.01, std::cos(toRadians((start.latitude + end.latitude + point.latitude) / 3.0))) * 111320.0;

    double px = point.longitude * scaleLon;
    double py = point.latitude * scaleLat;
    double ax = start.longitude * scaleLon;
    double ay = start.latitude * scaleLat;
    double bx = end.longitude * scaleLon;
    double by = end.latitude * scaleLat;

    double abx = bx - ax;
    double aby = by - ay;
    double apx = px - ax;
    double apy = py - ay;

    double abLengthSquared = abx * abx + aby * aby;
    double t = (abLengthSquared == 0.0) ? 0.0 : (apx * abx + apy * aby) / abLengthSquared;
    double clamped = std::max(0.0, std::min(1.0, t));

    double snappedX = ax + abx * clamped;
    double snappedY = ay + aby * clamped;

    RoutePoint snapped = { snappedY / scaleLat, snappedX / scaleLon };
    return { snapped, clamped, distanceMeters(point, snapped) };
}

} // namespace move
