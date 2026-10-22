package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.ui.ToolboxHaptics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * v1.2.1 — long-press haptics: the shared helper behind every
 * long-press trigger (home tiles, desktop icons, desktop blank
 * space, the Home FAB) must fire exactly one tick per call when the
 * toolbox_haptics toggle is on, and none when it is off — the same
 * gate as the beep-hook tick. The platform vibrate call sits behind
 * ToolboxHaptics.performer, replaced here with a counter (no Android
 * runtime needed).
 */
class V121LongPressHapticsTest {

    private var ticks = 0
    private lateinit var originalPerformer: () -> Unit
    private var originalEnabled: Boolean = true

    @Before
    fun setUp() {
        ticks = 0
        originalPerformer = ToolboxHaptics.performer
        originalEnabled = ToolboxHaptics.enabled
        ToolboxHaptics.performer = { ticks++ }
    }

    @After
    fun tearDown() {
        ToolboxHaptics.performer = originalPerformer
        ToolboxHaptics.enabled = originalEnabled
    }

    @Test
    fun longPressTick_firesOncePerCall_whenEnabled() {
        ToolboxHaptics.enabled = true
        ToolboxHaptics.longPressTick()
        assertEquals(1, ticks)
        ToolboxHaptics.longPressTick()
        assertEquals(2, ticks)
    }

    @Test
    fun longPressTick_silent_whenToggleOff() {
        ToolboxHaptics.enabled = false
        ToolboxHaptics.longPressTick()
        ToolboxHaptics.longPressTick()
        assertEquals(0, ticks)
    }

    @Test
    fun beepTick_sharesTheSameGate() {
        ToolboxHaptics.enabled = false
        ToolboxHaptics.tick()
        assertEquals(0, ticks)
        ToolboxHaptics.enabled = true
        ToolboxHaptics.tick()
        assertEquals(1, ticks)
    }
}
