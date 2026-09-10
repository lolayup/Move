#include "LocationProcessor.h"
#include <cmath>

namespace move {

LocationProcessor::LocationProcessor() : hasLastProcessed(false) {}

void LocationProcessor::reset() {
    hasLastProcessed = false;
}

bool LocationProcessor::isValid(const NavigationLocation& location) const {
    if (std::abs(location.latitude) > 90.0 || std::abs(location.longitude) > 180.0) return false;
    if (location.accuracyMeters > 200.0f) return false;
    return true;
}

NavigationLocation LocationProcessor::process(const NavigationLocation& location) {
    // For now, simple pass-through if valid, but could implement Kalman filter or similar here
    lastProcessed = location;
    hasLastProcessed = true;
    return location;
}

} // namespace move
