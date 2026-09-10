#include "EtaCalculator.h"
#include <cmath>
#include <algorithm>

namespace move {

EtaCalculator::EtaCalculator() : previousRemainingSeconds(0.0), hasPrevious(false) {}

void EtaCalculator::reset() {
    hasPrevious = false;
}

EtaEstimate EtaCalculator::calculate(double remainingDistanceMeters, double speedMetersPerSecond, long long currentTimestampMillis) {
    double effectiveSpeed;
    if (remainingDistanceMeters <= 0.0) {
        effectiveSpeed = 0.0;
    } else if (speedMetersPerSecond < 0.2) {
        effectiveSpeed = STATIONARY_EFFECTIVE_SPEED;
    } else {
        effectiveSpeed = std::max(MIN_WALKING_SPEED, std::min(MAX_WALKING_SPEED, speedMetersPerSecond));
    }

    double rawRemainingSeconds = (remainingDistanceMeters <= 0.0) ? 0.0 : remainingDistanceMeters / effectiveSpeed;

    double remainingSeconds;
    if (!hasPrevious || remainingDistanceMeters <= 0.0) {
        remainingSeconds = rawRemainingSeconds;
    } else {
        double delta = rawRemainingSeconds - previousRemainingSeconds;
        if (std::abs(delta) < ETA_DEADBAND_SECONDS) {
            remainingSeconds = previousRemainingSeconds;
        } else {
            remainingSeconds = previousRemainingSeconds + delta * ETA_SMOOTHING_ALPHA;
        }
    }

    previousRemainingSeconds = remainingSeconds;
    hasPrevious = true;

    return {
        remainingSeconds,
        currentTimestampMillis + static_cast<long long>(remainingSeconds * 1000.0)
    };
}

} // namespace move
