package dev.simonmartineau.keysight.notation

import dev.simonmartineau.keysight.score.Ticks

/** A system and where its origin (left edge, top staff's bottom line) sits on the page. */
data class PlacedSystem(val layout: SystemLayout, val y: Double)

/**
 * Systems stacked top to bottom, in staff spaces, the first system's origin at (0, 0) and
 * y growing upwards, so every system below the first sits at a negative [PlacedSystem.y].
 *
 * Systems may differ in width; the renderer centres a narrower one on the page's [width].
 */
data class PageLayout(val systems: List<PlacedSystem>) {
    init {
        require(systems.isNotEmpty()) { "a page needs a system" }
    }

    val width: Double get() = systems.maxOf { it.layout.width }

    val top: Double get() = systems.first().let { it.y + it.layout.top }

    val bottom: Double get() = systems.last().let { it.y + it.layout.bottom }

    val height: Double get() = top - bottom

    /** The system that draws [noteId], or null when no system does. */
    fun systemOf(noteId: String): PlacedSystem? = systems.firstOrNull { noteId in it.layout.anchors }

    /** The index of the system holding [measure]. */
    fun systemOfMeasure(measure: Int): Int {
        val index = systems.indexOfFirst { measure in it.layout.measures }
        require(index >= 0) { "no system holds measure $measure" }
        return index
    }

    /** The index of the system whose time [ticks] falls in; the first before the score, the last after it. */
    fun systemAt(ticks: Ticks): Int = systems.indexOfLast { ticks >= it.layout.ticks.start }.coerceAtLeast(0)

    /**
     * The [size] consecutive systems to show around [system]: it and the ones after it, sliding
     * back at the end of the score so the page stays full. This is the page turn: the window
     * moves one system at a time as the cursor enters the next system.
     */
    fun window(system: Int, size: Int): IntRange {
        require(size > 0) { "a window needs a system" }
        require(system in systems.indices) { "no system $system in ${systems.size}" }
        val first = minOf(system, systems.size - size).coerceAtLeast(0)
        return first..minOf(first + size - 1, systems.lastIndex)
    }

    /** The vertical extent of [window], in staff spaces. */
    fun heightOf(window: IntRange): Double {
        val first = systems[window.first]
        val last = systems[window.last]
        return (first.y + first.layout.top) - (last.y + last.layout.bottom)
    }

    /** Where a cursor at [ticks] sits: on the system holding that time, at its x on that system's time axis. */
    fun cursorAt(ticks: Ticks): Cursor {
        val system = systemAt(ticks)
        return Cursor(system, systems[system].layout.xAtTicks(ticks))
    }
}

/** The thin line marking the current beat: on which system, and how far along it, in staff spaces. */
data class Cursor(val system: Int, val x: Double)
