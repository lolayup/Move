#include "SpeedCalculator.h"
#include "GeoUtils.h"
#include <cmath>
#include <numeric>
#include <algorithm>

namespace move {

SpeedCalculator::SpeedCalculator() : hasPreviousLocation(false), stationaryCounter(0) {}

void SpeedCalculator::reset() {
    samples.clear();
    hasPreviousLocation = false;
    stationaryCounter = 0;
}

SpeedEstimate SpeedCalculator::onLocation(const NavigationLocation& location) {
    if (location.accuracyMeters > MAX_SPEED_ACCURACY) {
        return { currentSmoothedSpeed(), MovementState::GpsUncertain };
    }

    if (!hasPreviousLocation) {
        if (isPlausibleWalkingSpeed(location.speedMetersPerSecond)) {
            addSample(location.timestampMillis, location.speedMetersPerSecond);
        }
        previousLocation = location;
        hasPreviousLocation = true;
        return { currentSmoothedSpeed(), MovementState::GpsUncertain };
    }

    double dtSeconds = std::max(1LL, location.timestampMillis - previousLocation.timestampMillis) / 1000.0;
    double distance = GeoUtils::distanceMeters(previousLocation.latitude, previousLocation.longitude,
                                             location.latitude, location.longitude);
    double derivedSpeed = distance / dtSeconds;
    double reportedSpeed = location.speedMetersPerSecond;

    double candidate = 0.0;
    int count = 0;
    if (isPlausibleWalkingSpeed(reportedSpeed)) {
        candidate += reportedSpeed;
        count++;
    }
    if (isPlausibleWalkingSpeed(derivedSpeed)) {
        candidate += derivedSpeed;
        count++;
    }

    bool useCandidate = count > 0;
    if (useCandidate) {
        candidate /= count;
    }

    double movementThreshold = std::max(0.8, std::min(3.0, location.accuracyMeters * 0.12));

    if (distance < movementThreshold || !useCandidate || candidate < STATIONARY_SPEED_THRESHOLD) {
        stationaryCounter++;
        if (stationaryCounter >= 2) {
            addSample(location.timestampMillis, 0.0);
            previousLocation = location;
            return { 0.0, MovementState::Stationary };
        }
        previousLocation = location;
        return { currentSmoothedSpeed(), MovementState::GpsUncertain };
    }

    stationaryCounter = 0;
    addSample(location.timestampMillis, candidate);
    previousLocation = location;

    double speed = currentSmoothedSpeed();
    MovementState state = (speed < STATIONARY_SPEED_THRESHOLD) ? MovementState::Stationary : MovementState::Walking;
    return { speed, state };
}

void SpeedCalculator::addSample(long long timestampMillis, double speedMetersPerSecond) {
    double rounded = std::round(speedMetersPerSecond * 100.0) / 100.0;
    samples.push_back({ timestampMillis, rounded });

    long long cutoff = timestampMillis - HISTORY_WINDOW_MILLIS;
    while (!samples.empty() && samples.front().timestampMillis < cutoff) {
        samples.pop_front();
    }
    while (samples.size() > MAX_SAMPLES) {
        samples.pop_front();
    }
}

double SpeedCalculator::currentSmoothedSpeed() const {
    if (samples.empty()) return 0.0;

    size_t count = std::min(samples.size(), size_t(6));
    double weightedSum = 0.0;
    int weightSum = 0;

    for (size_t i = 0; i < count; ++i) {
        int weight = i + 1;
        weightedSum += samples[samples.size() - count + i].speedMetersPerSecond * weight;
        weightSum += weight;
    }

    return weightedSum / weightSum;
}

bool SpeedCalculator::isPlausibleWalkingSpeed(double speed) const {
    return speed >= 0.0 && speed <= MAX_PLAUSIBLE_WALKING_SPEED;
}

} // namespace move
