cmake_minimum_required(VERSION 3.22.1)

project("move-engine")

add_library(${PROJECT_NAME} SHARED
    jni/navigation_jni.cpp
    engine/NavigationEngine.cpp
    engine/DistanceCalculator.cpp
    engine/SpeedCalculator.cpp
    engine/EtaCalculator.cpp
    engine/LocationProcessor.cpp
    engine/MetroEngine.cpp
    utils/GeoUtils.cpp
)

target_include_directories(${PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/engine
    ${CMAKE_CURRENT_SOURCE_DIR}/utils
)

find_library(log-lib log)

target_link_libraries(${PROJECT_NAME}
    ${log-lib}
)
