package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Ticks

/**
 * What a drawn thing is for. The renderer draws every role the same way, glyph or line; the
 * role exists so annotations can tell a notehead from a barline without guessing from
 * geometry.
 */
enum class Role { STAFF_LINE, BARLINE, CLEF, TIME_SIGNATURE, NOTEHEAD, STEM, LEDGER, ACCIDENTAL, FLAG, REST }

/**
 * One positioned thing in a [StaffLayout].
 *
 * Coordinates are staff spaces: x grows to the right from the layout's left edge, y grows
 * upwards from the bottom staff line. [noteId] is set on everything that belongs to a note
 * (its head, stem, ledger lines and accidental) so a whole note can be tinted at once.
 */
sealed interface Element {
    val role: Role
    val noteId: String?
}

/** A font glyph placed by its origin, which SMuFL puts on the baseline at the glyph's left. */
data class GlyphElement(
    val glyph: Glyph,
    val x: Double,
    val y: Double,
    override val role: Role,
    override val noteId: String? = null,
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
) : Element

/** Where one note's head sits, for anything drawn relative to it. */
data class NoteAnchor(
    val noteId: String,
    /** Left edge of the notehead. */
    val x: Double,
    val position: StaffPosition,
    val headWidth: Double,
) {
    val y: Double get() = position.y
}

/** The x a notehead sounding at [ticks] would be drawn at. */
data class TimePoint(val ticks: Ticks, val x: Double)

/**
 * A laid-out score, in staff spaces, ready to be scaled and painted.
 *
 * [top] and [bottom] bound everything drawn, ledger lines and stems included; the renderer
 * fits them to its height so the engraving never clips. [timeAxis] maps score time to
 * horizontal position for things that were not in the score, such as notes the player added.
 */
data class StaffLayout(
    val width: Double,
    val top: Double,
    val bottom: Double,
    val elements: List<Element>,
    val anchors: Map<String, NoteAnchor>,
    val timeAxis: List<TimePoint>,
) {
    init {
        require(width > 0.0) { "width must be positive" }
        require(top > bottom) { "top must be above bottom" }
        require(timeAxis.isNotEmpty()) { "a layout needs at least one time point" }
        require(timeAxis.zipWithNext().all { (a, b) -> a.ticks < b.ticks && a.x <= b.x }) { "time axis must increase" }
    }

    val height: Double get() = top - bottom

    /**
     * The x a notehead starting at [ticks] would be drawn at: on a column when the time is a
     * notated onset, between two columns in proportion otherwise, and clamped to the first
     * column and the end of the measure beyond them.
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
