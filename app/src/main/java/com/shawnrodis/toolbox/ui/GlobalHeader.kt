package com.shawnrodis.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v1.0.1 (defect #2) — the source's global <header id="app-header">,
 * shown above the tab content in the mobile shell (the source hides it
 * in Desktop Mode, and so does the native app: DesktopShell never shows
 * this header).
 *
 * Left: brand block (click = switchTab('tab-home')) — accent rounded
 * square with a white fa-toolbox glyph, then the DYNAMIC title/subtitle
 * from switchTab(): home -> "Toolbox" / "Shawn Rodis"; any other tab
 * (including Settings) -> the tool's registry name / "Toolbox Utility".
 *
 * Right: four rounded-xl bordered buttons (bg slate-100 light /
 * zinc-900 dark, border slate-200/zinc-800) in the source's exact order:
 *   1. toggleFullscreen()  fa-expand / fa-compress per state
 *   2. toggleDesktopMode() fa-desktop
 *   3. switchTab('tab-settings') fa-gear
 *   4. toggleToolboxSound() fa-volume-high / fa-volume-xmark per state
 *      (source updateSoundBtns also swaps the title Mute/Unmute)
 */
@Composable
fun GlobalHeader(
    title: String,
    subtitle: String,
    fullscreen: Boolean,
    soundOn: Boolean,
    onBrandClick: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleDesktop: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleSound: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Brand (click -> home).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onBrandClick() }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.Filled.Work,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.padding(start = 9.dp)) {
                    Text(
                        title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        subtitle,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HeaderButton(
                    icon = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = "Toggle fullscreen",
                    onClick = onToggleFullscreen
                )
                HeaderButton(
                    icon = Icons.Filled.DesktopWindows,
                    contentDescription = "Toggle desktop mode",
                    onClick = onToggleDesktop
                )
                HeaderButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings
                )
                HeaderButton(
                    icon = if (soundOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (soundOn) "Mute sounds" else "Unmute sounds",
                    onClick = onToggleSound
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** One source header button: rounded-xl, bordered, theme surface. */
@Composable
private fun HeaderButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(17.dp)
        )
    }
}
