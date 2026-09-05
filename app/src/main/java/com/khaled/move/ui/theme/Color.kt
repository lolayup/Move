package com.khaled.move.ui.theme

import androidx.compose.ui.graphics.Color

// AMOLED dark palette — true black background so OLED panels can turn
// pixels fully off, with a couple of slightly-lifted surfaces for cards
// and sheets so they still read as "elevated" without ever looking gray.
val AmoledBlack = Color(0xFF000000)
val SurfaceDark = Color(0xFF0B0B0B)
val SurfaceDarkElevated = Color(0xFF161616)
val OutlineDark = Color(0xFF2A2A2A)
val OnSurfaceDark = Color(0xFFF2F2F2)
val OnSurfaceVariantDark = Color(0xFF9C9C9C)

// Light palette — mirrors the dark one so the app reads as the same
// product, just inverted, rather than a different visual identity.
val BackgroundLight = Color(0xFFFAFAFA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceLightElevated = Color(0xFFF1F1F1)
val OutlineLight = Color(0xFFE0E0E0)
val OnSurfaceLight = Color(0xFF121212)
val OnSurfaceVariantLight = Color(0xFF5C5C5C)

// The one spot of color in an otherwise monochrome UI.
val AccentRed = Color(0xFFFF3355)
val AccentRedDim = Color(0xFFB5233C)
val OnAccentRed = Color(0xFFFFFFFF)
