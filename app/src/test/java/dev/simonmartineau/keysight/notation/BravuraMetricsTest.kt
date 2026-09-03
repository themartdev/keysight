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

    /**
     * The rests are placed by their origin: the whole rest at the fourth line, which it hangs
     * from, the others at the middle line, which the half rest sits on and the quarter and
     * eighth rests straddle. The font must agree, or a rest would sit in the wrong space.
     */
    @Test
    fun `the rests hang from, sit on or straddle their origin as the engraving assumes`() {
        val whole = BravuraMetrics.of(Glyph.REST_WHOLE)
        assertTrue(whole.top in 0.0..0.1 && whole.bottom in -0.6..-0.4, "the whole rest hangs below its origin: $whole")

        val half = BravuraMetrics.of(Glyph.REST_HALF)
        assertTrue(half.bottom in -0.1..0.0 && half.top in 0.4..0.6, "the half rest sits on its origin: $half")

        val quarter = BravuraMetrics.of(Glyph.REST_QUARTER)
        assertTrue(quarter.top > 1.0 && quarter.bottom < -1.0, "the quarter rest straddles its origin: $quarter")
        assertTrue(quarter.top + quarter.bottom in -0.1..0.1)

        val eighth = BravuraMetrics.of(Glyph.REST_8TH)
        assertTrue(eighth.top > 0.5 && eighth.bottom < -0.5, "the eighth rest straddles its origin: $eighth")

        listOf(whole, half, quarter, eighth).forEach { rest ->
            assertTrue(rest.top <= 2.0 && rest.bottom >= -2.0, "a rest at the middle line stays inside the staff: $rest")
            assertNull(rest.stemUpSE)
            assertNull(rest.stemDownNW)
        }
    }

    /**
     * An accidental is placed by its origin at the head's line. The sharp and the natural
     * straddle it, the flat sits its loop on it and rises, and all three are narrower than a
     * head, which is what the cue room in [Spacing.MIN_ADVANCE] assumes.
     */
    @Test
    fun `the accidentals straddle or sit on their origin and are narrower than a head`() {
        val sharp = BravuraMetrics.of(Glyph.ACCIDENTAL_SHARP)
        assertTrue(sharp.top > 1.0 && sharp.bottom < -1.0, "the sharp straddles its origin: $sharp")
        assertTrue(sharp.top + sharp.bottom in -0.1..0.1)

        val natural = BravuraMetrics.of(Glyph.ACCIDENTAL_NATURAL)
        assertTrue(natural.top > 1.0 && natural.bottom < -1.0, "the natural straddles its origin: $natural")
        assertTrue(natural.top + natural.bottom in -0.1..0.1)

        val flat = BravuraMetrics.of(Glyph.ACCIDENTAL_FLAT)
        assertTrue(flat.bottom in -0.8..-0.6 && flat.top > 1.5, "the flat's loop hangs half a space under its origin and its stem rises: $flat")

        val head = BravuraMetrics.of(Glyph.NOTEHEAD_BLACK)
        listOf(sharp, natural, flat).forEach { accidental ->
            assertTrue(accidental.width < head.width, "narrower than a head: $accidental")
            assertEquals(0.0, accidental.left, TOLERANCE, "the origin is at the left edge")
            assertNull(accidental.stemUpSE)
            assertNull(accidental.stemDownNW)
        }
        assertEquals(Spacing.ACCIDENTAL_GAP + head.width + Spacing.CUE_GAP + sharp.width + Spacing.CUE_GAP + head.width * Spacing.CUE_SCALE, Spacing.MIN_ADVANCE, 1e-9)
    }

    private companion object {
        const val PX_PER_SPACE = 100f
        const val TOLERANCE = 0.03
    }
}
