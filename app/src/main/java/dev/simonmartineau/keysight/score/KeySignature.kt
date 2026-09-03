package dev.simonmartineau.keysight.score

import kotlinx.serialization.Serializable

/**
 * Position on the circle of fifths: 0 is C major, positive counts sharps, negative counts
 * flats. Matches the MusicXML `fifths` element.
 *
 * Only major keys are named; a minor key shares its signature with its relative major and
 * nothing in the app yet needs to tell them apart.
 */
@Serializable
@JvmInline
value class KeySignature(val fifths: Int) {

    init {
        require(fifths in -7..7) { "fifths must be within -7..7, was $fifths" }
    }

    /** The altered letters in the order the signature writes them. */
    val alteredSteps: List<Step>
        get() = if (fifths >= 0) SHARP_ORDER.take(fifths) else FLAT_ORDER.take(-fifths)

    /** 1, -1 or 0: what the signature does to every note of [step]. */
    fun alterationOf(step: Step): Int = when {
        step in alteredSteps -> if (fifths > 0) 1 else -1
        else -> 0
    }

    /** The letter of the major key's tonic: a fifth, four letters, per step round the circle. */
    val tonicStep: Step get() = Step.entries[Math.floorMod(fifths * 4, Step.entries.size)]

    val tonicAlteration: Int get() = alterationOf(tonicStep)

    /** Pitch class of the tonic, 0 for C up to 11 for B. */
    val tonicPitchClass: Int get() = Math.floorMod(tonicStep.semitonesAboveC + tonicAlteration, 12)

    /** "C major", "F♯ major", "B♭ major". */
    val majorName: String
        get() = "$tonicStep${ACCIDENTAL_SIGNS.getValue(tonicAlteration)} major"

    override fun toString(): String = majorName

    companion object {
        val C_MAJOR = KeySignature(0)

        /** Every key, C first, then sharps by fifths, then flats by fifths. */
        val ALL: List<KeySignature> = (0..7).map(::KeySignature) + (-1 downTo -7).map(::KeySignature)

        private val SHARP_ORDER = listOf(Step.F, Step.C, Step.G, Step.D, Step.A, Step.E, Step.B)
        private val FLAT_ORDER = SHARP_ORDER.reversed()
        private val ACCIDENTAL_SIGNS = mapOf(-1 to "♭", 0 to "", 1 to "♯")
    }
}
