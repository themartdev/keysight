package dev.simonmartineau.keysight.difficulty

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicalLevelTest {

    private val thirds = MusicalLevel.DEFAULT
    private val fourths = thirds.copy(maxInterval = 3)

    @Test
    fun `a change names the dimensions that moved, in walk order, and whether the first got harder`() {
        val up = thirds.changeTo(fourths)!!
        assertEquals(listOf(Dimension.INTERVAL), up.dimensions)
        assertTrue(up.harder)
        assertEquals("up to fourths", up.what)
        assertEquals(fourths, up.after)

        val down = fourths.copy(rests = true).changeTo(thirds)!!
        assertEquals(listOf(Dimension.INTERVAL, Dimension.RESTS), down.dimensions)
        assertFalse(down.harder)
        assertEquals("up to thirds, no rests", down.what)
    }

    @Test
    fun `no change is null`() {
        assertNull(thirds.changeTo(thirds))
        assertNull(thirds.changeTo(thirds.copy(rightHandRange = Ladders.RANGE.easiest.right)), "the same width is the same rung")
    }
}
