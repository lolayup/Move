package com.khaled.move.navigation.foot.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object NavigationUiFormatters {
    private val arrivalFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun formatDuration(seconds: Double): String {
        val minutes = (seconds / 60.0).roundToInt().coerceAtLeast(0)
        return when {
            minutes < 1 -> "<1 min"
            else -> "$minutes min"
        }
    }

    fun formatDistance(meters: Double): String {
        return when {
            meters >= 1000.0 -> String.format("%.1f km", meters / 1000.0)
            meters >= 100.0 -> "${((meters / 10.0).roundToInt() * 10).coerceAtLeast(0)} m"
            else -> "${meters.roundToInt().coerceAtLeast(0)} m"
        }
    }

    fun formatArrivalTime(instant: Instant): String {
        return "Arrive ${arrivalFormatter.withZone(ZoneId.systemDefault()).format(instant)}"
    }

    fun formatSpeed(metersPerSecond: Double): String {
        val kmh = metersPerSecond * 3.6
        return String.format("%.1f km/h", kmh)
    }

    fun formatElapsedTime(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%d:%02d", m, s)
        }
    }
}
