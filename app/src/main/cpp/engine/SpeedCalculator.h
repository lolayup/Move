#pragma once

#include "Models.h"
#include <deque>

namespace move {

struct SpeedEstimate {
    double speedMetersPerSecond;
    MovementState movementState;
};

class SpeedCalculator {
public:
    SpeedCalculator();
    void reset();
    SpeedEstimate onLocation(const NavigationLocation& location);

private:
    struct SpeedSample {
        long long timestampMillis;
        double speedMetersPerSecond;
    };

    std::deque<SpeedSample> samples;
    NavigationLocation previousLocation;
    bool hasPreviousLocation;
    int stationaryCounter;

    void addSample(long long timestampMillis, double speedMetersPerSecond);
    double currentSmoothedSpeed() const;
    bool isPlausibleWalkingSpeed(double speed) const;

    static constexpr double STATIONARY_SPEED_THRESHOLD = 0.28;
    static constexpr double MAX_PLAUSIBLE_WALKING_SPEED = 3.0;
    static constexpr float MAX_SPEED_ACCURACY = 65.0f;
    static constexpr long long HISTORY_WINDOW_MILLIS = 45000;
    static constexpr size_t MAX_SAMPLES = 14;
};

} // namespace move
