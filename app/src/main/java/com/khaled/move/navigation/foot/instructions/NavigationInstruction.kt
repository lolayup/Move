package com.khaled.move.navigation.foot.instructions

import com.khaled.move.navigation.foot.route.RoutePoint

enum class NavigationInstructionType {
    START,
    CONTINUE,
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    CROSS_ROAD,
    ENTER_PEDESTRIAN_PATH,
    EXIT_PEDESTRIAN_PATH,
    ARRIVE,
}

data class NavigationInstruction(
    val type: NavigationInstructionType,
    val text: String,
    val routePointIndex: Int,
    val coordinate: RoutePoint,
)
