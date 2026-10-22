package com.shawnrodis.toolbox.ui

import androidx.compose.runtime.mutableStateOf

/**
 * Parity pass — the source's fullscreen state (isFullscreen() /
 * toggleFullscreen()): browser fullscreen is a runtime state, not a stored
 * setting, so this is a process-wide observable the Activity observes to
 * enter/exit immersive mode (hide/show the system bars). The Settings
 * "Fullscreen" row and the Desktop taskbar fullscreen button both flip it.
 */
object FullscreenState {
    val active = mutableStateOf(false)
    fun toggle() {
        active.value = !active.value
    }
}
