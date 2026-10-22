package com.example.auto_voice_recorder.ui.lock

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorLayoutTest {

    private val rows = 5
    private val rowGaps = rows - 1

    private fun newLayout(maxHeight: Dp): CalculatorLayout = calculatorLayout(maxHeight)

    private fun usedHeight(layout: CalculatorLayout): Dp =
        layout.verticalPadding * 2 + layout.displayHeight + layout.displaySpacing +
            layout.keyHeight * rows + layout.rowSpacing * rowGaps

    private fun assertInvariants(maxHeight: Dp) {
        val layout = newLayout(maxHeight)

        assertTrue(
            "expected key height within 48..64 dp at $maxHeight, was ${layout.keyHeight}",
            layout.keyHeight >= 48.dp && layout.keyHeight <= 64.dp,
        )
        if (!layout.scrollable) {
            assertTrue(
                "content should fit at $maxHeight, it uses ${usedHeight(layout)}",
                usedHeight(layout) <= maxHeight,
            )
        }
    }

    @Test
    fun `a tall window is not compact and uses the 64 dp portrait key height`() {
        val layout = newLayout(800.dp)

        assertFalse(layout.compact)
        assertEquals(64.dp, layout.keyHeight)
        assertFalse(layout.scrollable)
        assertInvariants(800.dp)
    }

    @Test
    fun `exactly 480 dp is still not compact`() {
        val layout = newLayout(480.dp)

        assertFalse(layout.compact)
        assertFalse(layout.scrollable)
        assertInvariants(480.dp)
    }

    @Test
    fun `479 dp switches to the compact layout`() {
        val layout = newLayout(479.dp)

        assertTrue(layout.compact)
        assertInvariants(479.dp)
    }

    @Test
    fun `400 dp is compact and fits without scrolling`() {
        val layout = newLayout(400.dp)

        assertTrue(layout.compact)
        assertFalse(layout.scrollable)
        assertInvariants(400.dp)
    }

    @Test
    fun `332 dp is the smallest compact height that still fits`() {
        val layout = newLayout(332.dp)

        assertTrue(layout.compact)
        assertFalse(layout.scrollable)
        assertTrue(layout.keyHeight >= 48.dp)
        assertInvariants(332.dp)
    }

    @Test
    fun `331 dp scrolls with the 48 dp minimum key height`() {
        val layout = newLayout(331.dp)

        assertTrue(layout.compact)
        assertEquals(48.dp, layout.keyHeight)
        assertTrue(layout.scrollable)
        assertInvariants(331.dp)
    }

    @Test
    fun `300 dp and 200 dp scroll with the 48 dp minimum key height`() {
        listOf(300.dp, 200.dp).forEach { maxHeight ->
            val layout = newLayout(maxHeight)

            assertTrue("expected a compact layout at $maxHeight", layout.compact)
            assertEquals("unexpected key height at $maxHeight", 48.dp, layout.keyHeight)
            assertTrue("expected scrolling at $maxHeight", layout.scrollable)
            assertInvariants(maxHeight)
        }
    }

    @Test
    fun `every height keeps keys within the touch-target range and never clips when it fits`() {
        listOf(
            120.dp,
            200.dp,
            300.dp,
            331.dp,
            332.dp,
            400.dp,
            479.dp,
            480.dp,
            600.dp,
            800.dp,
            1200.dp,
        ).forEach { maxHeight -> assertInvariants(maxHeight) }
    }
}
