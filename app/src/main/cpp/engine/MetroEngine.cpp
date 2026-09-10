#include "MetroEngine.h"
#include "GeoUtils.h"
#include <algorithm>

namespace move {

MetroEngine::MetroEngine() {}

void MetroEngine::addStation(const MetroStation& station) {
    stations[station.id] = station;
}

void MetroEngine::addLine(const MetroLine& line) {
    lines[line.id] = line;
}

void MetroEngine::clearData() {
    stations.clear();
    lines.clear();
}

std::optional<MetroStation> MetroEngine::findNearbyStation(double latitude, double longitude, double thresholdMeters) const {
    const MetroStation* best = nullptr;
    double minDistance = thresholdMeters;

    for (const auto& [id, station] : stations) {
        double dist = GeoUtils::distanceMeters(latitude, longitude, station.latitude, station.longitude);
        if (dist < minDistance) {
            minDistance = dist;
            best = &station;
        }
    }

    if (best) return *best;
    return std::nullopt;
}

std::optional<MetroStation> MetroEngine::findStationByFeature(const std::string& name, double latitude, double longitude) const {
    if (!name.empty()) {
        // 1. Exact match (case insensitive would be better, but keeping it simple for now)
        for (const auto& [id, station] : stations) {
            if (station.name == name) return station;
        }

        // 2. Partial match
        for (const auto& [id, station] : stations) {
            if (name.find(station.name) != std::string::npos || station.name.find(name) != std::string::npos) {
                return station;
            }
        }
    }

    // 3. Proximity fallback
    return findNearbyStation(latitude, longitude, 300.0);
}

std::vector<MetroStation> MetroEngine::getAllStations() const {
    std::vector<MetroStation> res;
    for (const auto& [id, station] : stations) res.push_back(station);
    return res;
}

std::vector<MetroLine> MetroEngine::getAllLines() const {
    std::vector<MetroLine> res;
    for (const auto& [id, line] : lines) res.push_back(line);
    return res;
}

std::optional<MetroStation> MetroEngine::getStation(const std::string& id) const {
    auto it = stations.find(id);
    if (it != stations.end()) return it->second;
    return std::nullopt;
}

std::optional<MetroLine> MetroEngine::getLine(const std::string& id) const {
    auto it = lines.find(id);
    if (it != lines.end()) return it->second;
    return std::nullopt;
}

bool MetroEngine::isAtStation(double latitude, double longitude, double thresholdMeters) const {
    return findNearbyStation(latitude, longitude, thresholdMeters).has_value();
}

} // namespace move
