package com.khaled.move.navigation.foot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.khaled.move.navigation.foot.rendering.PlaceProperty
import com.khaled.move.navigation.foot.rendering.SelectedMapPlace
import com.khaled.move.navigation.foot.ui.NavigationUiFormatters.formatDistance

import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import com.khaled.move.navigation.metro.data.CairoMetroRepository
import com.khaled.move.navigation.metro.data.MetroLine
import com.khaled.move.navigation.metro.data.MetroStation

@Composable
fun SelectedDestinationCard(
    place: SelectedMapPlace,
    distanceFromUserMeters: Double?,
    onNavigate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metroStation = CairoMetroRepository.findStationByFeature(place.title, place.point)
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    NothingGlyph()
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = place.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = place.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        
                        metroStation?.let { station ->
                            val line = CairoMetroRepository.getLine(station.lines.first())
                            line?.let {
                                Text(
                                    text = it.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(android.graphics.Color.parseColor(it.colorHex)),
                                    fontWeight = FontWeight.Bold
                                )
                                
                                val stationIndex = it.stations.indexOf(station.id)
                                val prev = it.stations.getOrNull(stationIndex - 1)?.let { id -> CairoMetroRepository.getStation(id) }
                                val next = it.stations.getOrNull(stationIndex + 1)?.let { id -> CairoMetroRepository.getStation(id) }
                                
                                if (prev != null || next != null) {
                                    Text(
                                        text = listOfNotNull(
                                            prev?.let { "← ${it.name}" },
                                            next?.let { "${it.name} →" }
                                        ).joinToString("  "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        distanceFromUserMeters?.let {
                            Text(
                                text = "${formatDistance(it)} away",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss place", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (place.properties.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    place.properties.take(8).forEach { property ->
                        PlacePropertyRow(property)
                    }
                }
            }

            FilledTonalButton(
                onClick = onNavigate, 
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Start Navigation", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun NothingGlyph() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.inverseSurface),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.inverseOnSurface),
                )
            }
        }
    }
}

@Composable
private fun PlacePropertyRow(property: PlaceProperty) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = property.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = property.value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
