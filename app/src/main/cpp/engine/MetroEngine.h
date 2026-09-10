#pragma once

#include "Models.h"
#include <vector>
#include <string>
#include <optional>
#include <unordered_map>

namespace move {

class MetroEngine {
public:
    MetroEngine();

    void addStation(const MetroStation& station);
    void addLine(const MetroLine& line);
    void clearData();

    std::optional<MetroStation> findNearbyStation(double latitude, double longitude, double thresholdMeters = 500.0) const;
    std::optional<MetroStation> findStationByFeature(const std::string& name, double latitude, double longitude) const;

    std::vector<MetroStation> getAllStations() const;
    std::vector<MetroLine> getAllLines() const;

    std::optional<MetroStation> getStation(const std::string& id) const;
    std::optional<MetroLine> getLine(const std::string& id) const;

    bool isAtStation(double latitude, double longitude, double thresholdMeters = 50.0) const;

private:
    std::unordered_map<std::string, MetroStation> stations;
    std::unordered_map<std::string, MetroLine> lines;

    // Performance optimization: Spatial index could be added here if station count grows significantly
};

} // namespace move
