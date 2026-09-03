package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.Ticks

/**
 * What a drawn thing is for. The renderer draws every role the same way, glyph or line; the
 * role exists so annotations can tell a notehead from a barline without guessing from
 * geometry.
 */
enum class Role { STAFF_LINE, BARLINE, BRACE, CLEF, KEY_SIGNATURE, TIME_SIGNATURE, NOTEHEAD, STEM, LEDGER, ACCIDENTAL, FLAG, BEAM, REST }

/**
 * One positioned thing in a [SystemLayout].
 *
 * Coordinates are staff spaces: x grows to the right from the layout's left edge, y grows
 * upwards from the bottom line of the system's top staff. [noteId] is set on everything that
 * belongs to one note (its head, stem, flag, ledger lines and accidental) so a whole note can
 * be tinted at once; a beam belongs to several and carries none. [ticks] is the onset of the
 * note or rest the element belongs to, a beam's first note's, and null for the structure
 * around them; a [Mask] hides by it.
 */
sealed interface Element {
    val role: Role
    val noteId: String?
    val ticks: Ticks?
}

/**
 * A font glyph placed by its origin, which SMuFL puts on the baseline at the glyph's left,
 * drawn at [scale] times the staff size. Only the brace is ever scaled.
 */
data class GlyphElement(
    val glyph: Glyph,
    val x: Double,
    val y: Double,
    override val role: Role,
    override val noteId: String? = null,
    override val ticks: Ticks? = null,
    val scale: Double = 1.0,
) : Element

/** A straight line of the given [thickness], centred on the segment from (x1, y1) to (x2, y2). */
data class LineElement(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
    val thickness: Double,
    override val role: Role,
    override val noteId: String? = null,
    override val ticks: Ticks? = null,
) : Element

/**
 * A beam: a filled parallelogram with vertical ends, [thickness] deep, centred on the segment
 * from (x1, y1) to (x2, y2) the way a [LineElement] is, so a horizontal beam is a rectangle
 * and a slanted one keeps its ends square with the stems it joins.
 */
data class BeamElement(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
    val thickness: Double,
    override val ticks: Ticks,
) : Element {
    override val role: Role get() = Role.BEAM
    override val noteId: String? get() = null
}

/** One staff of a system: which clef it carries and where its bottom line sits. */
data class StaffFrame(val index: Int, val clef: Clef, val baselineY: Double)

/** Where one note's head sits, for anything drawn relative to it. */
data class NoteAnchor(
    val noteId: String,
    /** Left edge of the notehead. */
    val x: Double,
    /** On the note's own staff. */
    val position: StaffPosition,
    val headWidth: Double,
    val staff: Int,
    /** The bottom line of the note's staff, in system coordinates. */
    val baselineY: Double,
    val ticks: Ticks,
) {
    val y: Double get() = baselineY + position.y
}

/** The x a notehead sounding at [ticks] would be drawn at. */
data class TimePoint(val ticks: Ticks, val x: Double)

/** Score time from [start] up to but not including [endExclusive]. */
data class TickRange(val start: Ticks, val endExclusive: Ticks) {
    init {
        require(endExclusive > start) { "a range must be non-empty: $start to $endExclusive" }
    }

    operator fun contains(ticks: Ticks): Boolean = ticks >= start && ticks < endExclusive
}

/**
 * A row of measures laid out across every staff of the score, in staff spaces, ready to be
 * scaled and painted.
 *
 * [top] and [bottom] bound everything drawn, ledger lines and stems included; the renderer
 * fits them to its height so the engraving never clips. [timeAxis] maps score time to
 * horizontal position for things that were not in the score, such as notes the player added.
 * [ticks] is the score time the system covers.
 */
data class SystemLayout(
    val width: Double,
    val top: Double,
    val bottom: Double,
    val staves: List<StaffFrame>,
    val measures: IntRange,
    val ticks: TickRange,
    val elements: List<Element>,
    val anchors: Map<String, NoteAnchor>,
    val timeAxis: List<TimePoint>,
) {
    init {
        require(width > 0.0) { "width must be positive" }
        require(top > bottom) { "top must be above bottom" }
        require(staves.isNotEmpty()) { "a system needs a staff" }
        require(!measures.isEmpty()) { "a system needs a measure" }
        require(timeAxis.isNotEmpty()) { "a layout needs at least one time point" }
        require(timeAxis.zipWithNext().all { (a, b) -> a.ticks < b.ticks && a.x <= b.x }) { "time axis must increase" }
    }

    val height: Double get() = top - bottom

    /**
     * The x a notehead starting at [ticks] would be drawn at: on a column when the time is a
     * notated onset, between two columns in proportion otherwise, and clamped to the first
     * column and the end of the system beyond them.
     */
    fun xAtTicks(ticks: Ticks): Double {
        val first = timeAxis.first()
        val last = timeAxis.last()
        if (ticks <= first.ticks) return first.x
        if (ticks >= last.ticks) return last.x
        val index = timeAxis.indexOfFirst { it.ticks > ticks }
        val before = timeAxis[index - 1]
        val after = timeAxis[index]
        val fraction = (ticks.value - before.ticks.value).toDouble() / (after.ticks.value - before.ticks.value)
        return before.x + fraction * (after.x - before.x)
    }
}
