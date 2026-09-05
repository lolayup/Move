package com.khaled.move.navigation.foot.route

import android.util.Log
import com.khaled.move.navigation.foot.instructions.NavigationInstruction
import com.khaled.move.navigation.foot.instructions.NavigationInstructionType
import com.khaled.move.navigation.foot.route.NavigationRoute
import com.khaled.move.navigation.foot.route.RoutePoint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OsrmWalkingRoutingRepository(
    private val baseUrl: String = "https://router.project-osrm.org",
) : WalkingRoutingRepository {
    override suspend fun getWalkingRoute(origin: RoutePoint, destination: RoutePoint): Result<NavigationRoute> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "$baseUrl/route/v1/foot/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}" +
                            "?overview=full&geometries=geojson&steps=true"
                )
                Log.d("MoveNav", "OSRM request url=$url")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 15_000
                }
                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Log.d("MoveNav", "OSRM response code=$statusCode body=${body.take(500)}")
                connection.disconnect()
                require(statusCode in 200..299) { "Routing request failed ($statusCode)" }
                parseRoute(JSONObject(body), destination)
            }
        }

    private fun parseRoute(json: JSONObject, destination: RoutePoint): NavigationRoute {
        val routeJson = json.getJSONArray("routes").getJSONObject(0)
        val coordinates = routeJson
            .getJSONObject("geometry")
            .getJSONArray("coordinates")
        val geometry = buildList {
            for (i in 0 until coordinates.length()) {
                val coord = coordinates.getJSONArray(i)
                add(RoutePoint(latitude = coord.getDouble(1), longitude = coord.getDouble(0)))
            }
        }

        val instructions = mutableListOf<NavigationInstruction>()
        instructions += NavigationInstruction(
            type = NavigationInstructionType.START,
            text = "Start walking",
            routePointIndex = 0,
            coordinate = geometry.first(),
        )

        val legs = routeJson.getJSONArray("legs")
        var geometryCursor = 0
        for (legIndex in 0 until legs.length()) {
            val steps = legs.getJSONObject(legIndex).getJSONArray("steps")
            for (stepIndex in 0 until steps.length()) {
                val step = steps.getJSONObject(stepIndex)
                val maneuver = step.getJSONObject("maneuver")
                val type = instructionTypeFrom(step, maneuver)
                val text = instructionText(type, step)
                val stepGeometry = step.getJSONObject("geometry").getJSONArray("coordinates")
                val routePointIndex = geometryCursor.coerceAtMost(geometry.lastIndex)
                val coordinate = if (routePointIndex in geometry.indices) geometry[routePointIndex] else geometry.last()
                instructions += NavigationInstruction(type, text, routePointIndex, coordinate)
                geometryCursor += maxOf(1, stepGeometry.length() - 1)
            }
        }

        instructions += NavigationInstruction(
            type = NavigationInstructionType.ARRIVE,
            text = "Arrive at destination",
            routePointIndex = geometry.lastIndex,
            coordinate = geometry.last(),
        )

        val route = NavigationRoute(
            geometry = geometry,
            distanceMeters = routeJson.getDouble("distance"),
            durationSeconds = routeJson.optDouble("duration"),
            destination = destination,
            instructions = instructions.distinctBy { it.routePointIndex to it.type },
        )
        Log.d(
            "MoveNav",
            "OSRM parsed route points=${route.geometry.size}, distance=${route.distanceMeters}, instructions=${route.instructions.size}"
        )
        return route
    }

    private fun instructionTypeFrom(step: JSONObject, maneuver: JSONObject): NavigationInstructionType {
        val type = maneuver.optString("type")
        val modifier = maneuver.optString("modifier")
        return when {
            type == "arrive" -> NavigationInstructionType.ARRIVE
            type == "depart" -> NavigationInstructionType.START
            type == "continue" && modifier == "straight" -> NavigationInstructionType.STRAIGHT
            modifier == "slight left" -> NavigationInstructionType.SLIGHT_LEFT
            modifier == "left" -> NavigationInstructionType.LEFT
            modifier == "slight right" -> NavigationInstructionType.SLIGHT_RIGHT
            modifier == "right" -> NavigationInstructionType.RIGHT
            type == "new name" || type == "continue" -> NavigationInstructionType.CONTINUE
            else -> NavigationInstructionType.CONTINUE
        }
    }

    private fun instructionText(type: NavigationInstructionType, step: JSONObject): String {
        val name = step.optString("name").takeIf { it.isNotBlank() }
        return when (type) {
            NavigationInstructionType.START -> "Start walking"
            NavigationInstructionType.STRAIGHT -> if (name != null) "Walk straight on $name" else "Walk straight"
            NavigationInstructionType.LEFT -> if (name != null) "Turn left onto $name" else "Turn left"
            NavigationInstructionType.RIGHT -> if (name != null) "Turn right onto $name" else "Turn right"
            NavigationInstructionType.SLIGHT_LEFT -> if (name != null) "Slight left onto $name" else "Slight left"
            NavigationInstructionType.SLIGHT_RIGHT -> if (name != null) "Slight right onto $name" else "Slight right"
            NavigationInstructionType.CROSS_ROAD -> "Cross the road"
            NavigationInstructionType.ENTER_PEDESTRIAN_PATH -> "Enter pedestrian path"
            NavigationInstructionType.EXIT_PEDESTRIAN_PATH -> "Exit pedestrian path"
            NavigationInstructionType.ARRIVE -> "Arrive at destination"
            NavigationInstructionType.CONTINUE -> if (name != null) "Continue on $name" else "Continue"
        }
    }
}
