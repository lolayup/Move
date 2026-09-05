package com.khaled.move.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khaled.move.R

/**
 * The three states the button can honestly represent. It never fakes
 * "tracking" — see the core-goal brief's requirement to distinguish
 * permission-not-granted / granted-but-no-fix / actively-tracking.
 */
enum class LocationButtonState { PERMISSION_NEEDED, WAITING_FOR_FIX, TRACKING }

/**
 * The floating "recenter on me" control from the mockup.
 */
@Composable
fun LocationButton(
    state: LocationButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = when (state) {
        LocationButtonState.PERMISSION_NEEDED -> Icons.Filled.LocationDisabled
        LocationButtonState.WAITING_FOR_FIX -> Icons.Filled.GpsNotFixed
        LocationButtonState.TRACKING -> Icons.Filled.GpsFixed
    }
    val tint = when (state) {
        LocationButtonState.PERMISSION_NEEDED -> MaterialTheme.colorScheme.onSurfaceVariant
        LocationButtonState.WAITING_FOR_FIX -> MaterialTheme.colorScheme.onSurface
        LocationButtonState.TRACKING -> MaterialTheme.colorScheme.primary
    }
    val description = when (state) {
        LocationButtonState.PERMISSION_NEEDED -> stringResource(R.string.location_button_permission_needed)
        LocationButtonState.WAITING_FOR_FIX -> stringResource(R.string.location_button_waiting)
        LocationButtonState.TRACKING -> stringResource(R.string.location_button_tracking)
    }

    FilledIconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = tint,
        ),
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}
