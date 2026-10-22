package com.shawnrodis.toolbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Parity pass — toolIconHTML() equivalents. The source renders each tool's
 * Font Awesome glyph (registry icon names in defaultTools()); native uses
 * the closest Material glyph per FA name, drawn in the accent colour inside
 * the source's accent-icon-box (accent-tinted rounded square,
 * --accent-bg-subtle background / --accent-primary foreground).
 *
 * FA name -> glyph mapping:
 *  fa-globe -> Public, fa-clock -> Schedule, fa-cart-shopping -> ShoppingCart,
 *  fa-ruler-combined -> Straighten, fa-calendar-check -> EventAvailable,
 *  fa-gas-pump -> LocalGasStation, fa-receipt -> Receipt,
 *  fa-list-ol -> FormatListNumbered, fa-plane-departure -> FlightTakeoff,
 *  fa-note-sticky -> StickyNote2, fa-file-pdf -> PictureAsPdf,
 *  fa-images -> Collections, fa-gear -> Settings, fa-house -> Home.
 * The SpongeBob tool's icon is the source's inline SVG sponge: a rounded
 * square with darker circular holes, drawn on a Canvas below.
 */
fun toolIconVector(faIcon: String): ImageVector? = when (faIcon) {
    "fa-globe" -> Icons.Filled.Public
    "fa-clock" -> Icons.Filled.Schedule
    "fa-cart-shopping" -> Icons.Filled.ShoppingCart
    "fa-ruler-combined" -> Icons.Filled.Straighten
    "fa-calendar-check" -> Icons.Filled.EventAvailable
    "fa-gas-pump" -> Icons.Filled.LocalGasStation
    "fa-receipt" -> Icons.Filled.Receipt
    "fa-list-ol" -> Icons.Filled.FormatListNumbered
    "fa-plane-departure" -> Icons.Filled.FlightTakeoff
    "fa-note-sticky" -> Icons.Filled.StickyNote2
    "fa-file-pdf" -> Icons.Filled.PictureAsPdf
    "fa-images" -> Icons.Filled.Collections
    "fa-gear" -> Icons.Filled.Settings
    "fa-house" -> Icons.Filled.Home
    // v1.0.1: glyphs for the tool-card header buttons (defect #4).
    "fa-share-nodes" -> Icons.Filled.Share
    "fa-file-image" -> Icons.Filled.Image
    "fa-file-pdf" -> Icons.Filled.PictureAsPdf
    "fa-xmark" -> Icons.Filled.Close
    "fa-rotate" -> Icons.Filled.Refresh
    "fa-trash-can" -> Icons.Filled.Delete
    "fa-trash" -> Icons.Filled.Delete
    "fa-plus" -> Icons.Filled.Add
    else -> null
}

@Composable
fun ToolGlyph(faIcon: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    if (faIcon == "sponge") {
        // Source toolIconHTML 'sponge' SVG: viewBox 24, rounded rect rx 4.5
        // in currentColor, holes in rgba(0,0,0,0.30) at the source's exact
        // centres/radii: (8.2,9.6,1.9) (14.7,8.1,1.55) (12,15.3,1.75)
        // (17.8,14.4,1.4) (6.2,15.9,1.4).
        Canvas(modifier = modifier) {
            val u = size.minDimension / 24f
            drawRoundRect(
                color = tint,
                topLeft = Offset(2f * u, 2f * u),
                size = Size(20f * u, 20f * u),
                cornerRadius = CornerRadius(4.5f * u, 4.5f * u)
            )
            val hole = Color.Black.copy(alpha = 0.30f)
            listOf(
                Triple(8.2f, 9.6f, 1.9f), Triple(14.7f, 8.1f, 1.55f),
                Triple(12f, 15.3f, 1.75f), Triple(17.8f, 14.4f, 1.4f),
                Triple(6.2f, 15.9f, 1.4f)
            ).forEach { (cx, cy, r) ->
                drawCircle(hole, radius = r * u, center = Offset(cx * u, cy * u))
            }
        }
        return
    }
    val vector = toolIconVector(faIcon) ?: Icons.Filled.Settings
    Icon(vector, contentDescription = null, modifier = modifier, tint = tint)
}

/**
 * The source's accent-icon-box: rounded square filled with the accent's
 * subtle background, glyph in the accent colour. [boxDp] is the tile size;
 * the source home tiles use a 56dp (w-14) box with an 18dp-radius, the
 * list cards a smaller padded box — both are this shape.
 */
@Composable
fun ToolIconBox(faIcon: String, boxDp: Dp = 56.dp, glyphDp: Dp = 26.dp, cornerDp: Dp = 18.dp) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(boxDp)
            .clip(RoundedCornerShape(cornerDp))
            .background(accent.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        ToolGlyph(faIcon, modifier = Modifier.size(glyphDp), tint = accent)
    }
}
