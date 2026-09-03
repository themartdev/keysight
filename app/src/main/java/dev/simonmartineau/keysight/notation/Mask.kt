package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Ticks

/**
 * Which score time is hidden from the player.
 *
 * The staff, clefs, signatures and barlines are always drawn; a mask only removes the notes
 * and rests whose onset falls in one of its [hidden] ranges, so a hidden bar is a blank bar on
 * a real score. Round 6 derives the mask from the visibility policy on every frame; until
 * then the two extremes are all the practice screen needs.
 */
data class Mask(val hidden: List<TickRange>) {

    fun hides(ticks: Ticks): Boolean = hidden.any { ticks in it }

    /** True when the element is a note or rest in a hidden range. */
    fun hides(element: Element): Boolean = element.ticks?.let(::hides) ?: false

    companion object {
        val NONE = Mask(emptyList())
        val ALL = Mask(listOf(TickRange(Ticks.ZERO, Ticks(Int.MAX_VALUE))))
    }
}
