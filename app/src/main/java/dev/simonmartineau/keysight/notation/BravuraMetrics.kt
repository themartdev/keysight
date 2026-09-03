package dev.simonmartineau.keysight.notation

/** A point in staff spaces relative to a glyph's origin. */
data class Point(val x: Double, val y: Double)

/**
 * A glyph's bounding box and attachment points, in staff spaces from its origin.
 *
 * SMuFL puts the origin on the baseline at the left of the glyph; a notehead is centred on
 * its baseline so that drawing it at a line's y puts it on that line. [stemUpSE] and
 * [stemDownNW] are where a stem meets the head, null for glyphs that take no stem.
 * [stemUpNW] and [stemDownSW] are a flag's: where the top left corner of an up stem, or the
 * bottom left corner of a down stem, sits on the flag.
 */
data class GlyphMetrics(
    val left: Double,
    val bottom: Double,
    val right: Double,
    val top: Double,
    val stemUpSE: Point? = null,
    val stemDownNW: Point? = null,
    val stemUpNW: Point? = null,
    val stemDownSW: Point? = null,
) {
    val width: Double get() = right - left
    val height: Double get() = top - bottom
}

/**
 * The metrics of the bundled font, Bravura 1.482, copied from its `Bravura.json` metadata
 * (`glyphBBoxes`, `glyphsWithAnchors` and `engravingDefaults`), which the font files do not
 * carry. `BravuraMetricsTest` checks the boxes against the shipped font file.
 *
 * Only the glyphs in [Glyph] are listed; add a row here when adding a glyph there.
 */
object BravuraMetrics {

    const val STAFF_LINE_THICKNESS = 0.13
    const val STEM_THICKNESS = 0.12
    const val LEDGER_LINE_THICKNESS = 0.16
    const val LEDGER_LINE_EXTENSION = 0.4
    const val THIN_BARLINE_THICKNESS = 0.16
    const val THICK_BARLINE_THICKNESS = 0.5
    const val BEAM_THICKNESS = 0.5

    private val STEM_UP_SE = Point(1.18, 0.168)
    private val STEM_DOWN_NW = Point(0.0, -0.168)

    private val glyphs: Map<Glyph, GlyphMetrics> = mapOf(
        Glyph.BRACE to box(0.0, 0.0, 0.277, 4.0),

        Glyph.NOTEHEAD_WHOLE to box(0.0, -0.5, 1.688, 0.5),
        Glyph.NOTEHEAD_HALF to box(0.0, -0.5, 1.18, 0.5, STEM_UP_SE, STEM_DOWN_NW),
        Glyph.NOTEHEAD_BLACK to box(0.0, -0.5, 1.18, 0.5, STEM_UP_SE, STEM_DOWN_NW),

        Glyph.G_CLEF to box(0.0, -2.632, 2.684, 4.392),
        Glyph.F_CLEF to box(-0.02, -2.54, 2.736, 1.048),

        Glyph.TIME_SIG_0 to box(0.08, -1.0, 1.8, 1.004),
        Glyph.TIME_SIG_1 to box(0.08, -1.0, 1.256, 1.004),
        Glyph.TIME_SIG_2 to box(0.08, -1.028, 1.704, 1.016),
        Glyph.TIME_SIG_3 to box(0.08, -1.004, 1.604, 0.996),
        Glyph.TIME_SIG_4 to box(0.08, -1.0, 1.8, 1.004),
        Glyph.TIME_SIG_5 to box(0.08, -1.004, 1.532, 0.984),
        Glyph.TIME_SIG_6 to box(0.08, -0.996, 1.656, 1.004),
        Glyph.TIME_SIG_7 to box(0.08, -1.0, 1.684, 0.996),
        Glyph.TIME_SIG_8 to box(0.08, -1.036, 1.664, 1.036),
        Glyph.TIME_SIG_9 to box(0.08, -0.996, 1.656, 1.004),

        Glyph.ACCIDENTAL_FLAT to box(0.0, -0.7, 0.904, 1.756),
        Glyph.ACCIDENTAL_NATURAL to box(0.0, -1.34, 0.672, 1.364),
        Glyph.ACCIDENTAL_SHARP to box(0.0, -1.392, 0.996, 1.4),
        Glyph.ACCIDENTAL_DOUBLE_SHARP to box(0.0, -0.5, 0.988, 0.508),
        Glyph.ACCIDENTAL_DOUBLE_FLAT to box(0.0, -0.7, 1.644, 1.748),

        Glyph.FLAG_8TH_UP to box(0.0, -3.24, 1.056, 0.036, stemUpNW = Point(0.0, -0.04)),
        Glyph.FLAG_8TH_DOWN to box(0.0, -0.056, 1.224, 3.232, stemDownSW = Point(0.0, 0.132)),

        Glyph.REST_WHOLE to box(0.0, -0.54, 1.128, 0.036),
        Glyph.REST_HALF to box(0.0, -0.008, 1.128, 0.568),
        Glyph.REST_QUARTER to box(0.004, -1.5, 1.08, 1.492),
        Glyph.REST_8TH to box(0.0, -1.004, 0.988, 0.696),
    )

    fun of(glyph: Glyph): GlyphMetrics = glyphs.getValue(glyph)

    init {
        val missing = Glyph.entries.filter { it !in glyphs }
        check(missing.isEmpty()) { "no metrics for $missing" }
    }

    private fun box(
        left: Double,
        bottom: Double,
        right: Double,
        top: Double,
        stemUpSE: Point? = null,
        stemDownNW: Point? = null,
        stemUpNW: Point? = null,
        stemDownSW: Point? = null,
    ) = GlyphMetrics(left, bottom, right, top, stemUpSE, stemDownNW, stemUpNW, stemDownSW)
}
