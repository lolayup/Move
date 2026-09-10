#pragma once

#include "Models.h"

namespace move {

class LocationProcessor {
public:
    LocationProcessor();
    void reset();
    bool isValid(const NavigationLocation& location) const;
    NavigationLocation process(const NavigationLocation& location);

private:
    NavigationLocation lastProcessed;
    bool hasLastProcessed;
};

} // namespace move
