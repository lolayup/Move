package com.khaled.move.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khaled.move.R

/**
 * Minimal +/- zoom control, drawn as one small pill rather than two loose
 * floating buttons, so it reads as a single lightweight control rather
 * than a cluster of chrome (see "keep controls visually subtle" in the
 * brief).
 */
@Composable
fun ZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            IconButton(onClick = onZoomIn, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.zoom_in),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outline,
            )
            IconButton(onClick = onZoomOut, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = stringResource(R.string.zoom_out),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
