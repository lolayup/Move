package com.khaled.move.navigation.foot.route

import android.util.Log
import com.khaled.move.navigation.foot.instructions.NavigationInstruction
import com.khaled.move.navigation.foot.instructions.NavigationInstructionType
import com.khaled.move.navigation.foot.route.NavigationRoute
import com.khaled.move.navigation.foot.route.RoutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Walking router backed by the public OpenStreetMap.de Valhalla service.
 * Valhalla's pedestrian costing uses walkable OSM ways rather than treating
 * the profile path segment as cosmetic, as the public OSRM car server does.
 */
class ValhallaWalkingRoutingRepository(
    private val baseUrl: String = "https://valhalla1.openstreetmap.de",
) : WalkingRoutingRepository {

    override suspend fun getWalkingRoute(
        origin: RoutePoint,
        destination: RoutePoint,
    ): Result<NavigationRoute> = withContext(Dispatchers.IO) {
        runCatching {
            val request = JSONObject()
                .put(
                    "locations",
                    org.json.JSONArray()
                        .put(JSONObject().put("lat", origin.latitude).put("lon", origin.longitude))
                        .put(JSONObject().put("lat", destination.latitude).put("lon", destination.longitude)),
                )
                .put("costing", "pedestrian")
                .put("units", "kilometers")
                .put("language", "en-US")
                .put("directions_options", JSONObject().put("units", "kilometers"))

            val encodedRequest = URLEncoder.encode(request.toString(), StandardCharsets.UTF_8.toString())
            val url = URL("$baseUrl/route?json=$encodedRequest")
            Log.d("MoveNav", "Valhalla pedestrian route request origin=$origin destination=$destination")

            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "move-android/0.1")
                setRequestProperty("Accept", "application/json")
            }

            try {
                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.d("MoveNav", "Valhalla response code=$statusCode body=${body.take(500)}")
                require(statusCode in 200..299) { friendlyHttpError(statusCode, body) }
                parseRoute(JSONObject(body), destination)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseRoute(json: JSONObject, destination: RoutePoint): NavigationRoute {
        val trip = json.getJSONObject("trip")
        require(trip.optInt("status", 0) == 0) {
            trip.optString("status_message", "Unable to calculate walking route")
        }

        val legs = trip.getJSONArray("legs")
        val geometry = mutableListOf<RoutePoint>()
        val instructions = mutableListOf<NavigationInstruction>()

        for (legIndex in 0 until legs.length()) {
            val leg = legs.getJSONObject(legIndex)
            val legGeometry = decodePolyline6(leg.getString("shape"))
            require(legGeometry.size > 1) { "Walking route returned no usable geometry" }

            val geometryWasEmpty = geometry.isEmpty()
            val geometryOffset = if (geometryWasEmpty) 0 else geometry.lastIndex
            geometry += if (geometryWasEmpty) legGeometry else legGeometry.drop(1)

            val maneuvers = leg.getJSONArray("maneuvers")
            for (maneuverIndex in 0 until maneuvers.length()) {
                val maneuver = maneuvers.getJSONObject(maneuverIndex)
                val text = maneuver.optString("instruction", "Continue walking")
                val routePointIndex = (
                        geometryOffset + maneuver.optInt("begin_shape_index", 0)
                        ).coerceIn(0, geometry.lastIndex)
                instructions += NavigationInstruction(
                    type = instructionType(text, maneuverIndex == 0),
                    text = text,
                    routePointIndex = routePointIndex,
                    coordinate = geometry[routePointIndex],
                )
            }
        }

        val summary = trip.getJSONObject("summary")
        val route = NavigationRoute(
            geometry = geometry,
            distanceMeters = summary.getDouble("length") * 1_000.0,
            durationSeconds = summary.optDouble("time"),
            destination = destination,
            instructions = instructions
                .plus(
                    NavigationInstruction(
                        type = NavigationInstructionType.ARRIVE,
                        text = "Arrive at destination",
                        routePointIndex = geometry.lastIndex,
                        coordinate = geometry.last(),
                    ),
                )
                .distinctBy { it.routePointIndex to it.type },
        )
        Log.d(
            "MoveNav",
            "Valhalla parsed pedestrian route points=${route.geometry.size}, distance=${route.distanceMeters}, instructions=${route.instructions.size}",
        )
        return route
    }

    private fun instructionType(text: String, first: Boolean): NavigationInstructionType {
        val normalized = text.lowercase()
        return when {
            first -> NavigationInstructionType.START
            "destination" in normalized || "arrive" in normalized -> NavigationInstructionType.ARRIVE
            "slight left" in normalized -> NavigationInstructionType.SLIGHT_LEFT
            "slight right" in normalized -> NavigationInstructionType.SLIGHT_RIGHT
            "left" in normalized -> NavigationInstructionType.LEFT
            "right" in normalized -> NavigationInstructionType.RIGHT
            "cross" in normalized -> NavigationInstructionType.CROSS_ROAD
            "enter" in normalized && ("path" in normalized || "walkway" in normalized) ->
                NavigationInstructionType.ENTER_PEDESTRIAN_PATH

            "exit" in normalized && ("path" in normalized || "walkway" in normalized) ->
                NavigationInstructionType.EXIT_PEDESTRIAN_PATH

            "straight" in normalized -> NavigationInstructionType.STRAIGHT
            else -> NavigationInstructionType.CONTINUE
        }
    }

    /** Valhalla route shapes use Google's encoded-polyline algorithm at precision 6. */
    private fun decodePolyline6(encoded: String): List<RoutePoint> {
        val points = mutableListOf<RoutePoint>()
        var index = 0
        var latitude = 0L
        var longitude = 0L

        while (index < encoded.length) {
            val latitudeValue = decodeComponent(encoded, index)
            index = latitudeValue.nextIndex
            latitude += latitudeValue.delta

            val longitudeValue = decodeComponent(encoded, index)
            index = longitudeValue.nextIndex
            longitude += longitudeValue.delta

            points += RoutePoint(
                latitude = latitude / 1_000_000.0,
                longitude = longitude / 1_000_000.0,
            )
        }
        return points
    }

    private fun decodeComponent(encoded: String, startIndex: Int): DecodedComponent {
        var index = startIndex
        var result = 0L
        var shift = 0
        var value: Int
        do {
            require(index < encoded.length) { "Invalid walking route geometry" }
            value = encoded[index++].code - 63
            result = result or ((value and 0x1f).toLong() shl shift)
            shift += 5
        } while (value >= 0x20)

        val delta = if ((result and 1L) != 0L) -(result shr 1) - 1L else result shr 1
        return DecodedComponent(delta, index)
    }

    private fun friendlyHttpError(statusCode: Int, body: String): String = when (statusCode) {
        429 -> "Walking route service is busy. Please try again shortly."
        in 500..599 -> "Walking route service is temporarily unavailable."
        400 -> if (body.contains("max distance", ignoreCase = true)) {
            "That destination is too far for a walking route. Pick a closer place."
        } else {
            "Walking route request failed ($statusCode)"
        }

        else -> "Walking route request failed ($statusCode)"
    }

    private data class DecodedComponent(
        val delta: Long,
        val nextIndex: Int,
    )
}
