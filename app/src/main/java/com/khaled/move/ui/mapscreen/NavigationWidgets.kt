package com.khaled.move.ui.mapscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.khaled.move.navigation.foot.engine.NavigationStatus
import com.khaled.move.navigation.foot.engine.NavigationUiState
import com.khaled.move.navigation.foot.ui.NavigationUiFormatters
import com.khaled.move.navigation.metro.engine.MetroNavigationUiState
import androidx.compose.material.icons.filled.Subway

@Composable
fun MetroNavigationTopBanner(
    state: MetroNavigationUiState,
    modifier: Modifier = Modifier,
) {
    val currentStation = state.currentStation ?: return
    val nextStation = state.nextStation
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Subway,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = if (nextStation != null) "Heading to ${nextStation.name}" else "Arrived at ${currentStation.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (nextStation != null) {
                    Text(
                        text = "Current: ${currentStation.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
fun NavigationTopBanner(
    state: NavigationUiState,
    modifier: Modifier = Modifier,
) {
    if (state.status == NavigationStatus.Arrived) {
        ArrivedTopBanner(modifier = modifier)
        return
    }

    val progress = state.progress ?: return
    val instruction = progress.nextInstruction ?: progress.currentInstruction ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = instruction.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = NavigationUiFormatters.formatDistance(progress.distanceToNextInstructionMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArrivedTopBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "You have arrived",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun NavigationBottomBar(
    state: NavigationUiState,
    onEndNavigation: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = state.progress ?: return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Main row with primary info and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.status == NavigationStatus.Arrived) {
                    Text(
                        text = "Arrived",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = NavigationUiFormatters.formatDuration(progress.estimatedRemainingDurationSeconds),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${NavigationUiFormatters.formatDistance(progress.remainingDistanceMeters)} left",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.status != NavigationStatus.Arrived) {
                        FilledIconButton(
                            onClick = onToggleFollow,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (state.followUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (state.followUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = "Toggle follow")
                        }
                    }

                    FilledIconButton(
                        onClick = onEndNavigation,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (state.status == NavigationStatus.Arrived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.errorContainer,
                            contentColor = if (state.status == NavigationStatus.Arrived) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "End navigation")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Secondary info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricItem(
                    icon = Icons.Default.AccessTime,
                    label = "Arrive",
                    value = NavigationUiFormatters.formatArrivalTime(progress.estimatedArrivalTime).replace("Arrive ", "")
                )
                MetricItem(
                    icon = Icons.Default.Timer,
                    label = "Elapsed",
                    value = NavigationUiFormatters.formatElapsedTime(state.elapsedTimeSeconds)
                )
                MetricItem(
                    icon = Icons.Default.Speed,
                    label = "Speed",
                    value = NavigationUiFormatters.formatSpeed(state.speedMetersPerSecond)
                )
            }

            if (state.status == NavigationStatus.Rerouting || state.isRecalculating) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Recalculating walking route…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
