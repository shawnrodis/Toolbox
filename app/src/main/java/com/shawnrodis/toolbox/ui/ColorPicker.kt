package com.shawnrodis.toolbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Parity pass — the source's FULL custom colour picker (__cpRender /
 * __cpBind): a saturation/value square for the current hue, a hue strip
 * (the source's hue bar with its draggable dot), a live preview swatch,
 * the hex value, and Apply. The math is ColorMath's verbatim __hsvToRgb /
 * __rgbToHsv / __rgbToHex ports. Applying mirrors __applyCustomAccentVars:
 * the chosen colour becomes the custom accent (toolbox_custom_accent) and
 * the accent selection switches to "custom" (setAccentTheme('custom')).
 */
@Composable
fun HsvColorPicker(
    initialHex: String,
    onApply: (String) -> Unit
) {
    val initial = hexToRgb(initialHex) ?: Rgb(0x63, 0x66, 0xF1)
    var hsv by remember(initialHex) {
        val c = rgbToHsv(initial.r, initial.g, initial.b)
        mutableStateOf(c)
    }
    val current = hsvToRgb(hsv.h, hsv.s, hsv.v)
    val currentColor = current.toColor()
    val hueColor = hsvToRgb(hsv.h, 1f, 1f).toColor()

    Column {
        // Saturation (x) / Value (y) square for the current hue.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(listOf(Color.White, hueColor))
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val s = (change.position.x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        hsv = hsv.copy(s = s, v = v)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val s = (pos.x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (pos.y / size.height).coerceIn(0f, 1f)
                        hsv = hsv.copy(s = s, v = v)
                    }
                }
        ) {
            // Value shading (transparent -> black) over the hue/sat layer.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                    )
            )
            // Picker dot at (s, 1-v).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    val cx = hsv.s * size.width
                    val cy = (1f - hsv.v) * size.height
                    drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()))
                    drawCircle(currentColor, radius = 6.dp.toPx(), center = Offset(cx, cy))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Hue strip (0..360) with the full spectrum gradient.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(255, 0, 0), Color(255, 255, 0), Color(0, 255, 0),
                            Color(0, 255, 255), Color(0, 0, 255), Color(255, 0, 255),
                            Color(255, 0, 0)
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        hsv = hsv.copy(h = (change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        hsv = hsv.copy(h = (pos.x / size.width).coerceIn(0f, 1f) * 360f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(22.dp)) {
                val cx = (hsv.h / 360f) * size.width
                drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx()))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Live preview swatch (source cp-preview).
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(currentColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                rgbToHex(current.r, current.g, current.b).uppercase(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { onApply(rgbToHex(current.r, current.g, current.b)) }) {
                Text("Apply")
            }
        }
    }
}
