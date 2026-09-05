package com.khaled.move.navigation.metro.data

import com.khaled.move.navigation.foot.route.RoutePoint
import kotlin.math.sqrt

object CairoMetroRepository {

    private val stations = listOf(
        // Line 1
        MetroStation("l1_helwan", "Helwan", 29.8492, 31.3340, listOf("L1")),
        MetroStation("l1_maadi", "Maadi", 29.9603, 31.2566, listOf("L1")),
        MetroStation("sadat", "Sadat", 30.0444, 31.2357, listOf("L1", "L2")),
        MetroStation("al_shohadaa", "Al-Shohadaa", 30.0633, 31.2467, listOf("L1", "L2")),
        MetroStation("l1_el_marg", "New El Marg", 30.1517, 31.3361, listOf("L1")),
        
        // Line 2
        MetroStation("l2_shobra", "Shobra El Kheima", 30.1221, 31.2454, listOf("L2")),
        MetroStation("attaba", "Attaba", 30.0526, 31.2461, listOf("L2", "L3")),
        MetroStation("l2_cairo_uni", "Cairo University", 30.0255, 31.2066, listOf("L2", "L3")),
        MetroStation("l2_el_mounib", "El Mounib", 29.9814, 31.2125, listOf("L2")),

        // Line 3
        MetroStation("l3_adly_mansour", "Adly Mansour", 30.1528, 31.4239, listOf("L3")),
        MetroStation("l3_heliopolis", "Heliopolis", 30.1030, 31.3414, listOf("L3")),
        MetroStation("l3_abbassia", "Abbassia", 30.0658, 31.2858, listOf("L3")),
        MetroStation("nasser", "Nasser", 30.0528, 31.2396, listOf("L1", "L3")),
        MetroStation("l3_kit_kat", "Kit Kat", 30.0621, 31.2136, listOf("L3"))
    ).associateBy { it.id }

    private val lines = mapOf(
        "L1" to MetroLine(
            "L1", "Line 1", "#EE1C25", 
            listOf("l1_helwan", "l1_maadi", "sadat", "al_shohadaa", "l1_el_marg")
        ),
        "L2" to MetroLine(
            "L2", "Line 2", "#F68B1F", 
            listOf("l2_shobra", "al_shohadaa", "attaba", "sadat", "l2_cairo_uni", "l2_el_mounib")
        ),
        "L3" to MetroLine(
            "L3", "Line 3", "#00A651", 
            listOf("l3_adly_mansour", "l3_heliopolis", "l3_abbassia", "attaba", "nasser", "l3_kit_kat", "l2_cairo_uni")
        )
    )

    fun getStation(id: String) = stations[id]
    
    fun getLine(id: String) = lines[id]

    fun getAllStations() = stations.values.toList()
    
    fun getAllLines() = lines.values.toList()

    fun findStationByFeature(name: String?, point: RoutePoint): MetroStation? {
        // 1. Try exact name match
        if (name != null) {
            val exactMatch = stations.values.find { it.name.equals(name, ignoreCase = true) }
            if (exactMatch != null) return exactMatch
            
            // 2. Try partial name match (e.g. "Sadat Metro" matches "Sadat")
            val partialMatch = stations.values.find { 
                name.contains(it.name, ignoreCase = true) || it.name.contains(name, ignoreCase = true) 
            }
            if (partialMatch != null) return partialMatch
        }

        // 3. Fallback to proximity search (if it's a station layer or tagged as metro)
        return findNearbyStation(point, thresholdMeters = 300.0)
    }

    fun findNearbyStation(point: RoutePoint, thresholdMeters: Double = 500.0): MetroStation? {
        return stations.values.minByOrNull { 
            distance(point.latitude, point.longitude, it.latitude, it.longitude) 
        }?.takeIf { 
            distance(point.latitude, point.longitude, it.latitude, it.longitude) < thresholdMeters 
        }
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Rough approximation in meters
        val dx = (lon1 - lon2) * 111000 * Math.cos(Math.toRadians(lat1))
        val dy = (lat1 - lat2) * 111000
        return sqrt(dx * dx + dy * dy)
    }
}
