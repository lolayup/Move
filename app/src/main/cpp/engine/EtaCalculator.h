#pragma once

#include "Models.h"

namespace move {

struct EtaEstimate {
    double remainingDurationSeconds;
    long long arrivalTimeMillis;
};

class EtaCalculator {
public:
    EtaCalculator();
    void reset();
    EtaEstimate calculate(double remainingDistanceMeters, double speedMetersPerSecond, long long currentTimestampMillis);

private:
    double previousRemainingSeconds;
    bool hasPrevious;

    static constexpr double MIN_WALKING_SPEED = 0.35;
    static constexpr double MAX_WALKING_SPEED = 2.35;
    static constexpr double STATIONARY_EFFECTIVE_SPEED = 0.65;
    static constexpr double ETA_DEADBAND_SECONDS = 18.0;
    static constexpr double ETA_SMOOTHING_ALPHA = 0.35;
};

} // namespace move
