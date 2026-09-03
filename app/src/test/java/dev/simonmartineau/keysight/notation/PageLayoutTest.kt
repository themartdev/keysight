package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.Fixtures
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.Ticks
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
