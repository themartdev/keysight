package dev.simonmartineau.keysight.notation

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
}
