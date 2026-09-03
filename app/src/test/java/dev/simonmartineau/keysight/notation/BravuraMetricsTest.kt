package dev.simonmartineau.keysight.notation

import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private companion object {
        const val PX_PER_SPACE = 100f
        const val TOLERANCE = 0.03
    }
}
