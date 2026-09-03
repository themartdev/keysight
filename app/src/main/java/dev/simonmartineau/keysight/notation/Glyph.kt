package dev.simonmartineau.keysight.notation

/**
 * The SMuFL glyphs the engraving uses, by codepoint in the Private Use Area.
 *
 * SMuFL fixes the codepoints, so any compliant font renders these; Bravura is the one
 * bundled. The double accidentals are listed so that a hand-written score carrying one still
 * draws; the generator never writes one, since transposition respells a double as the plain
 * accidental of the neighbouring letter.
 */
enum class Glyph(val codepoint: Int) {
    BRACE(0xE000),

    NOTEHEAD_WHOLE(0xE0A2),
    NOTEHEAD_HALF(0xE0A3),
    NOTEHEAD_BLACK(0xE0A4),

    G_CLEF(0xE050),
    F_CLEF(0xE062),

    TIME_SIG_0(0xE080),
    TIME_SIG_1(0xE081),
    TIME_SIG_2(0xE082),
    TIME_SIG_3(0xE083),
    TIME_SIG_4(0xE084),
    TIME_SIG_5(0xE085),
    TIME_SIG_6(0xE086),
    TIME_SIG_7(0xE087),
    TIME_SIG_8(0xE088),
    TIME_SIG_9(0xE089),

    ACCIDENTAL_FLAT(0xE260),
    ACCIDENTAL_NATURAL(0xE261),
    ACCIDENTAL_SHARP(0xE262),
    ACCIDENTAL_DOUBLE_SHARP(0xE263),
    ACCIDENTAL_DOUBLE_FLAT(0xE264),

    FLAG_8TH_UP(0xE240),
    FLAG_8TH_DOWN(0xE241),

    REST_WHOLE(0xE4E3),
    REST_HALF(0xE4E4),
    REST_QUARTER(0xE4E5),
    REST_8TH(0xE4E6),
    ;

    /** The glyph as a string, which is what a text painter draws. */
    val text: String = String(Character.toChars(codepoint))

    companion object {
        private val TIME_SIG_DIGITS = listOf(
            TIME_SIG_0, TIME_SIG_1, TIME_SIG_2, TIME_SIG_3, TIME_SIG_4,
            TIME_SIG_5, TIME_SIG_6, TIME_SIG_7, TIME_SIG_8, TIME_SIG_9,
        )

        fun timeSigDigit(digit: Int): Glyph {
            require(digit in 0..9) { "not a digit: $digit" }
            return TIME_SIG_DIGITS[digit]
        }

        /** The digits of [number] as time signature glyphs, most significant first. */
        fun timeSigDigits(number: Int): List<Glyph> {
            require(number > 0) { "time signature numbers are positive, was $number" }
            return number.toString().map { timeSigDigit(it - '0') }
        }

        /**
         * The accidental that writes a [dev.simonmartineau.keysight.score.SpelledPitch.alteration]
         * out, natural included. Whether one is needed at all is [AccidentalState]'s call.
         */
        fun accidentalGlyph(alteration: Int): Glyph = when (alteration) {
            0 -> ACCIDENTAL_NATURAL
            1 -> ACCIDENTAL_SHARP
            -1 -> ACCIDENTAL_FLAT
            2 -> ACCIDENTAL_DOUBLE_SHARP
            -2 -> ACCIDENTAL_DOUBLE_FLAT
            else -> throw IllegalArgumentException("alteration out of range: $alteration")
        }
    }
}
