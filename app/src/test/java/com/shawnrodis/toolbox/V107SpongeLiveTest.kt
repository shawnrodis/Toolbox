package com.shawnrodis.toolbox

import com.shawnrodis.toolbox.tools.sbmGenerateOutput
import com.shawnrodis.toolbox.tools.sbmSpongeTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.7: SpongeBob Meme Generator regenerates LIVE while typing,
 * mirroring the source's sbmHandleInput() — every keystroke re-runs
 * sbmGenerateMeme(). These tests pin the generate semantics the input
 * handler calls on each change:
 *  - blank input (empty or whitespace-only, the source's `!trim()`
 *    check) -> the placeholder, via sbmGenerateOutput;
 *  - alternating mode: letter cases strictly alternate (the first
 *    letter's case is random per transform in the source, so the
 *    assertions are property-based, not fixed strings);
 *  - non-letters are copied unchanged and don't disturb alternation;
 *  - a simulated typing sequence (regenerate after every prefix)
 *    always satisfies the same properties.
 */
class V107SpongeLiveTest {

    private val placeholder = "gEnErAtEd tExT HeRe"

    @Test
    fun blankInput_showsPlaceholder() {
        assertEquals(placeholder, sbmGenerateOutput("", randomMode = false))
        assertEquals(placeholder, sbmGenerateOutput("", randomMode = true))
        // Whitespace-only: the source's sbmGenerateMeme checks !input.trim().
        assertEquals(placeholder, sbmGenerateOutput("   ", randomMode = false))
        assertEquals(placeholder, sbmGenerateOutput(" \t ", randomMode = true))
        assertEquals(placeholder, sbmSpongeTransform("", randomMode = false))
    }

    @Test
    fun alternatingMode_lettersStrictlyAlternate_nonLettersUntouched() {
        val input = "hello, world! 123"
        val out = sbmGenerateOutput(input, randomMode = false)
        assertEquals(input.length, out.length)
        assertEquals(input.lowercase(), out.lowercase())
        var lastWasUpper: Boolean? = null
        for (i in input.indices) {
            val c = input[i]
            if (c.isLetter()) {
                val isUpper = out[i].isUpperCase()
                lastWasUpper?.let { assertTrue("letter $i must flip case", isUpper != it) }
                lastWasUpper = isUpper
            } else {
                assertEquals("non-letter at $i unchanged", c, out[i])
            }
        }
    }

    @Test
    fun typingSimulation_everyPrefixRegeneratesValidOutput() {
        // The live handler regenerates after every keystroke; each
        // intermediate prefix must produce a well-formed transform.
        val full = "i can't do this"
        for (end in 1..full.length) {
            val prefix = full.substring(0, end)
            val out = sbmGenerateOutput(prefix, randomMode = false)
            assertEquals(prefix.length, out.length)
            assertEquals(prefix.lowercase(), out.lowercase())
            var lastWasUpper: Boolean? = null
            for (i in prefix.indices) {
                if (prefix[i].isLetter()) {
                    val isUpper = out[i].isUpperCase()
                    lastWasUpper?.let { assertTrue(isUpper != it) }
                    lastWasUpper = isUpper
                } else {
                    assertEquals(prefix[i], out[i])
                }
            }
        }
        // Deleting back to empty (also a keystroke path) restores placeholder.
        assertEquals(placeholder, sbmGenerateOutput("", randomMode = false))
    }

    @Test
    fun randomMode_lettersRandomCased_nonLettersUntouched() {
        val input = "SpongeBob says hi! (ok)"
        repeat(5) {
            val out = sbmGenerateOutput(input, randomMode = true)
            assertEquals(input.length, out.length)
            assertEquals(input.lowercase(), out.lowercase())
            for (i in input.indices) {
                if (!input[i].isLetter()) assertEquals(input[i], out[i])
            }
        }
    }
}
