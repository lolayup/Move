package com.khaled.move.map

/**
 * Central, swappable configuration for the map's tile/style source.
 *
 * The renderer (MapLibre Compose) can point at any MapLibre-style vector
 * tile source. [OpenFreeMap](https://openfreemap.org/) is used by default:
 * it redistributes OpenStreetMap + OpenMapTiles data as free vector tiles
 * with no API key and no request limits. Swapping providers later
 * (self-hosted tiles, MapTiler, Protomaps, etc.) is a one-line change here.
 *
 * Attribution: OpenFreeMap requires crediting OpenFreeMap, OpenMapTiles and
 * OpenStreetMap. MapLibre Compose reads that credit directly out of the
 * style JSON and renders it through the map's default overlay, so as long
 * as `MapOverlay.Default` stays included in MapScreen, attribution stays
 * correct automatically — nothing is hard-coded.
 */
object MapStyles {
    const val AMOLED_DARK = "https://tiles.openfreemap.org/styles/dark"
    const val LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
    const val BRIGHT = "https://tiles.openfreemap.org/styles/bright"
    const val POSITRON = "https://tiles.openfreemap.org/styles/positron"
}

object MapDefaults {
    // Cairo, Egypt — sensible default center before a location fix arrives,
    // and the initial focus of the Egypt-metro work in a later phase.
    const val CAIRO_LAT = 30.0444
    const val CAIRO_LNG = 31.2357

    const val CITY_ZOOM = 11.0
    const val WALKING_ZOOM = 16.0
    const val NAVIGATION_ZOOM = 18.4
    const val NAVIGATION_PITCH = 35.0
    const val CURRENT_LOCATION_ZOOM = 20.0
    const val MIN_ZOOM = 3.0
    const val MAX_ZOOM = 20.0
}
