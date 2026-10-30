package com.shawnrodis.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * v1.3.7 — replaces the old top GlobalHeader AND the floating home
 * button (Shawn's call: the top should be entirely clear). The mobile
 * shell's chrome now lives in one bottom bar, used as the Scaffold's
 * bottomBar slot so content is padded above it and nothing ever slides
 * underneath it.
 *
 * Left: the four old header buttons in the source's exact order:
 *   1. toggleFullscreen()  fa-expand / fa-compress per state
 *   2. toggleDesktopMode() fa-desktop
 *   3. switchTab('tab-settings') fa-gear
 *   4. toggleToolboxSound() fa-volume-high / fa-volume-xmark per state
 * Right: the home button (same 52dp primary circle as the old FAB) —
 * tap goes home, long-press opens the quick switcher (with its haptic).
 * It shows on every route including Home, where tap is a no-op.
 *
 * The old header's brand/title ("Settings / Toolbox Utility") is gone:
 * every screen already titles itself in its content, and the brand's
 * tap-to-home is redundant with the home button. Desktop Mode is
 * untouched — DesktopShell never showed the header and shows no bar.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GlobalBottomBar(
    fullscreen: Boolean,
    soundOn: Boolean,
    onToggleFullscreen: () -> Unit,
    onToggleDesktop: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleSound: () -> Unit,
    onHome: () -> Unit,
    onHomeLongPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // v1.3.7: tight 4dp top padding so the bar hugs the
                // 52dp home circle — its top edge sits just above the
                // circle instead of floating in a tall bar. v1.3.8:
                // bottom padding is 0dp, so the bar's bottom edge sits
                // flush with the circle's bottom too; only the system
                // navigation inset remains below the bar.
                .padding(horizontal = 12.dp)
                .padding(top = 4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                BottomBarButton(
                    icon = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = "Toggle fullscreen",
                    onClick = onToggleFullscreen
                )
                BottomBarButton(
                    icon = Icons.Filled.DesktopWindows,
                    contentDescription = "Toggle desktop mode",
                    onClick = onToggleDesktop
                )
                BottomBarButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings
                )
                BottomBarButton(
                    icon = if (soundOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (soundOn) "Mute sounds" else "Unmute sounds",
                    onClick = onToggleSound
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(
                        onClick = onHome,
                        onLongClick = onHomeLongPress
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Home,
                    contentDescription = "Home — hold for quick switcher",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/** One old header button, restyled for the bar: rounded-xl, bordered. */
@Composable
private fun BottomBarButton(
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
