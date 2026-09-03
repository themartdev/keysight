package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PageLayoutTest {

    private val fourMeasures = Fixtures.measures(
        4,
        ScoreNote("a", Fixtures.C4, Ticks.ZERO, Ticks.WHOLE),
        ScoreNote("b", Fixtures.D4, Ticks.WHOLE, Ticks.WHOLE),
        ScoreNote("c", Fixtures.E4, Ticks.WHOLE * 2, Ticks.WHOLE),
        ScoreNote("d", Fixtures.F4, Ticks.WHOLE * 3, Ticks.WHOLE),
    )

    @Test
    fun `at natural width everything is on one system at the origin`() {
        val page = ScoreLayoutEngine.layoutPage(fourMeasures, null)
        val system = page.systems.single()

        assertEquals(0.0, system.y)
        assertEquals(0..3, system.layout.measures)
        assertEquals(system.layout.width, page.width)
        assertEquals(system.layout.top, page.top)
        assertEquals(system.layout.bottom, page.bottom)
    }

    @Test
    fun `a narrow page breaks into systems stacked a gap apart, the time signature only on the first`() {
        val natural = ScoreLayoutEngine.layoutPage(fourMeasures, null).width
        val page = ScoreLayoutEngine.layoutPage(fourMeasures, natural * 0.6)

        assertEquals(listOf(0..1, 2..3), page.systems.map { it.layout.measures })
        assertEquals(setOf("a", "b"), page.systems[0].layout.anchors.keys)
        assertEquals(setOf("c", "d"), page.systems[1].layout.anchors.keys)
        assertEquals(2, page.systems[0].layout.elements.count { it.role == Role.TIME_SIGNATURE })
        assertEquals(0, page.systems[1].layout.elements.count { it.role == Role.TIME_SIGNATURE })
        val (first, second) = page.systems
        assertEquals(first.layout.bottom - ScoreLayoutEngine.SYSTEM_GAP - second.layout.top, second.y, 1e-9)
        assertEquals(first.layout.top, page.top)
        assertEquals(second.y + second.layout.bottom, page.bottom)
        assertTrue(page.height > first.layout.height + second.layout.height)
        assertEquals(natural * 0.6, page.width, 1e-9)
        assertEquals(first, page.systemOf("a"))
        assertEquals(second, page.systemOf("d"))
        assertEquals(null, page.systemOf("z"))
    }

    @Test
    fun `a page too narrow for one measure still gets one measure per system`() {
        val page = ScoreLayoutEngine.layoutPage(fourMeasures, 5.0)

        assertEquals(4, page.systems.size)
        page.systems.forEach { assertTrue(it.layout.width > 5.0) }
    }

    /** One measure per system, four systems. */
    private val fourSystems = ScoreLayoutEngine.layoutPage(fourMeasures, 5.0)

    @Test
    fun `systems are found by measure and by time`() {
        assertEquals(listOf(0, 1, 2, 3), (0..3).map(fourSystems::systemOfMeasure))
        assertFailsWith<IllegalArgumentException> { fourSystems.systemOfMeasure(4) }

        assertEquals(0, fourSystems.systemAt(Ticks.ZERO))
        assertEquals(0, fourSystems.systemAt(Ticks(Ticks.WHOLE.value - 1)))
        assertEquals(1, fourSystems.systemAt(Ticks.WHOLE))
        assertEquals(3, fourSystems.systemAt(Ticks.WHOLE * 3))
        assertEquals(3, fourSystems.systemAt(Ticks.WHOLE * 10))
    }

    @Test
    fun `the window turns a system at a time and slides back at the end so the page stays full`() {
        assertEquals(0..1, fourSystems.window(0, 2))
        assertEquals(1..2, fourSystems.window(1, 2))
        assertEquals(2..3, fourSystems.window(2, 2))
        assertEquals(2..3, fourSystems.window(3, 2))
        assertEquals(3..3, fourSystems.window(3, 1))
        assertEquals(0..3, fourSystems.window(2, 6))
        assertFailsWith<IllegalArgumentException> { fourSystems.window(4, 2) }
        assertFailsWith<IllegalArgumentException> { fourSystems.window(0, 0) }
    }

    @Test
    fun `the height of a window spans from the top of its first system to the bottom of its last`() {
        val (first, second) = fourSystems.systems

        assertEquals(first.layout.height, fourSystems.heightOf(0..0), 1e-9)
        assertEquals(first.layout.top - (second.y + second.layout.bottom), fourSystems.heightOf(0..1), 1e-9)
        assertEquals(fourSystems.height, fourSystems.heightOf(0..3), 1e-9)
    }

    @Test
    fun `the cursor sits on a note's head at its onset and moves between onsets`() {
        val page = ScoreLayoutEngine.layoutPage(fourMeasures, null)
        val system = page.systems.single().layout
        val a = system.anchors.getValue("a")
        val b = system.anchors.getValue("b")

        assertEquals(Cursor(0, a.x), page.cursorAt(Ticks.ZERO))
        assertEquals(Cursor(0, b.x), page.cursorAt(Ticks.WHOLE))
        val halfway = page.cursorAt(Ticks.HALF)
        assertEquals((a.x + b.x) / 2, halfway.x, 1e-9)

        val turned = fourSystems.cursorAt(Ticks.WHOLE * 2 + Ticks.QUARTER)
        assertEquals(2, turned.system)
        assertTrue(turned.x > fourSystems.systems[2].layout.anchors.getValue("c").x)
    }
}
