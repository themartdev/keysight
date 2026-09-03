package dev.simonmartineau.keysight.notation

import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The metrics table is copied by hand from Bravura's metadata; this checks it against the
 * font file that actually ships, so a typo fails here rather than on the phone.
 */
class BravuraMetricsTest {

    /** At 400 px per em one staff space is [PX_PER_SPACE] px, since an em is four spaces. */
    private val font: Font by lazy {
        val file = File("src/main/res/font/bravura.otf")
        assertTrue(file.isFile, "run from the app module: ${file.absolutePath}")
        Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(4f * PX_PER_SPACE)
    }

    @Test
    fun `every glyph is in the font`() {
        Glyph.entries.forEach { glyph ->
            assertTrue(font.canDisplay(glyph.codepoint), glyph.name)
        }
    }

    @Test
    fun `the bounding boxes match the shipped font`() {
        val context = FontRenderContext(null, true, true)
        Glyph.entries.forEach { glyph ->
            val expected = BravuraMetrics.of(glyph)
            val bounds = font.createGlyphVector(context, glyph.text).getGlyphVisualBounds(0).bounds2D
            assertEquals(expected.left, bounds.minX / PX_PER_SPACE, TOLERANCE, "${glyph.name} left")
            assertEquals(expected.right, bounds.maxX / PX_PER_SPACE, TOLERANCE, "${glyph.name} right")
            assertEquals(expected.top, -bounds.minY / PX_PER_SPACE, TOLERANCE, "${glyph.name} top")
            assertEquals(expected.bottom, -bounds.maxY / PX_PER_SPACE, TOLERANCE, "${glyph.name} bottom")
        }
    }

    /**
     * The anchors come from the metadata too and the font cannot confirm them, but a flag's
     * stem anchor is on the glyph's left edge at the end the stem meets, so a typo shows.
     */
    @Test
    fun `the flag anchors sit on the left edge at the stem end and the beam is Bravura's`() {
        val up = BravuraMetrics.of(Glyph.FLAG_8TH_UP)
        val upAnchor = assertNotNull(up.stemUpNW)
        assertEquals(up.left, upAnchor.x, TOLERANCE)
        assertTrue(up.top - upAnchor.y in 0.0..0.25, "the flag's top is just above the stem tip")
        assertNull(up.stemDownSW)

        val down = BravuraMetrics.of(Glyph.FLAG_8TH_DOWN)
        val downAnchor = assertNotNull(down.stemDownSW)
        assertEquals(down.left, downAnchor.x, TOLERANCE)
        assertTrue(downAnchor.y - down.bottom in 0.0..0.25, "the flag's bottom is just below the stem tip")
        assertNull(down.stemUpNW)

        assertEquals(0.5, BravuraMetrics.BEAM_THICKNESS)
        assertTrue(BravuraMetrics.BEAM_THICKNESS > BravuraMetrics.STEM_THICKNESS)
        Glyph.entries.filter { it != Glyph.FLAG_8TH_UP && it != Glyph.FLAG_8TH_DOWN }.forEach { glyph ->
            assertNull(BravuraMetrics.of(glyph).stemUpNW, glyph.name)
            assertNull(BravuraMetrics.of(glyph).stemDownSW, glyph.name)
        }
    }

    private companion object {
        const val PX_PER_SPACE = 100f
        const val TOLERANCE = 0.03
    }
}
