package dev.simonmartineau.keysight.score

import kotlinx.serialization.Serializable

/**
 * Position on the circle of fifths: 0 is C major, positive counts sharps, negative counts
 * flats. Matches the MusicXML `fifths` element.
 */
@Serializable
@JvmInline
value class KeySignature(val fifths: Int) {

    init {
        require(fifths in -7..7) { "fifths must be within -7..7, was $fifths" }
    }

    companion object {
        val C_MAJOR = KeySignature(0)
    }
}
