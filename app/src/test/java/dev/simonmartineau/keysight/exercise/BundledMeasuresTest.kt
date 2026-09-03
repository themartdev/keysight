package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.data.keySightJson
import dev.simonmartineau.keysight.difficulty.Ladders
import dev.simonmartineau.keysight.notation.BeamElement
import dev.simonmartineau.keysight.notation.Glyph
import dev.simonmartineau.keysight.notation.GlyphElement
import dev.simonmartineau.keysight.notation.Role
import dev.simonmartineau.keysight.notation.ScoreLayoutEngine
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.transposed
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shape of a bundled measure file: the content of the rounds before the generator, the
 * eighth-note measures written for round 9 and the rest measures of round 10.
 * [musicalDifficulty] 1 to 3 is the quarter vocabulary, 4 has eighths, 5 has rests. A
 * [syncopated] measure holds a longer note off the beat, which the generator never writes:
 * it is held to the layout, not to the vocabulary.
 */
@Serializable
data class BundledMeasure(val id: String, val score: Score, val musicalDifficulty: Int, val syncopated: Boolean = false)

/**
 * The eighteen hand-written measures of the rounds before the generator, the four eighth-note
 * measures and the four rest measures, kept as fixtures: real content the layout engine and
 * the generator's constraints are held to. A fixture in a key is validated as its C major
 * original. They are read from the source tree, so run this from the app module.
 */
class BundledMeasuresTest {

    private val directory = File("src/test/resources/exercises")

    private val measures: List<BundledMeasure> by lazy {
        assertTrue(directory.isDirectory, "run from the app module: ${directory.absolutePath}")
        directory.listFiles { file -> file.name.endsWith(".json") }!!.sortedBy { it.name }.map { file ->
            runCatching { keySightJson.decodeFromString(BundledMeasure.serializer(), file.readText()) }
                .getOrElse { throw IllegalStateException("bundled measure ${file.name} is invalid", it) }
        }
    }

    /**
     * The vocabulary a measure was written in: the first eighteen a sixth from middle C, up to
     * fifths, quarters to wholes; the eighth-note measures the ladder's widest ranges on the
     * staff they use, up to fifths, eighths to wholes; the rest measures the same with rests.
     */
    private fun vocabularyOf(measure: BundledMeasure): ExerciseConfig {
        val hands = if (measure.score.staves.single().clef == Clef.BASS) Hands.LEFT else Hands.RIGHT
        return if (measure.musicalDifficulty <= 3) {
            ExerciseConfig.DEFAULT.copy(
                hands = hands,
                rightHandRange = PitchRange(SpelledPitch(Step.C, octave = 4), SpelledPitch(Step.A, octave = 4)),
                maxInterval = 4,
            )
        } else {
            ExerciseConfig.DEFAULT.copy(
                hands = hands,
                rightHandRange = Ladders.RANGE.hardest.right,
                leftHandRange = Ladders.RANGE.hardest.left,
                noteValues = Ladders.RHYTHM.hardest,
                rests = measure.musicalDifficulty == 5,
                maxInterval = 4,
            )
        }
    }

    private fun layoutOf(measure: BundledMeasure) = ScoreLayoutEngine.layoutSystem(measure.score, 0, null, showTimeSignature = true)

    @Test
    fun `the fixtures load, are named by their files and are twenty-six`() {
        assertEquals(26, measures.size)
        assertEquals(directory.list()!!.sorted().map { it.removeSuffix(".json") }, measures.map { it.id })
        assertEquals(measures.size, measures.map { it.id }.toSet().size)
        assertEquals("m04-stepwise-up", measures[3].id)
        assertEquals(setOf(KeySignature(0), KeySignature(-2), KeySignature(2), KeySignature(1), KeySignature(-1)), measures.map { it.score.keySignature }.toSet())
        assertEquals(setOf(Clef.TREBLE, Clef.BASS), measures.map { it.score.staves.single().clef }.toSet())
    }

    @Test
    fun `every fixture is an instance of the vocabulary it was written in`() {
        measures.forEach { measure ->
            assertTrue(measure.musicalDifficulty in 1..5, measure.id)
            val problems = vocabularyOf(measure).violations(measure.score.transposed(KeySignature.C_MAJOR))
            if (measure.syncopated) {
                assertTrue(problems.isNotEmpty() && problems.all { "off the beat" in it }, "${measure.id}: $problems")
            } else {
                assertTrue(problems.isEmpty(), "${measure.id}: $problems")
            }
        }
        assertEquals(listOf("m22-syncopated-eighths"), measures.filter { it.syncopated }.map { it.id })
    }

    @Test
    fun `every fixture lays out inside the fixed envelope`() {
        measures.forEach { measure ->
            val layout = layoutOf(measure)
            assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, layout.top, measure.id)
            assertEquals(ScoreLayoutEngine.ENVELOPE_BOTTOM, layout.bottom, measure.id)
            assertEquals(measure.score.notes.map { it.id }.toSet(), layout.anchors.keys, measure.id)
        }
    }

    @Test
    fun `the eighth-note fixtures beam their pairs and flag their lone eighths`() {
        fun beams(id: String) = layoutOf(measures.single { it.id == id }).elements.filterIsInstance<BeamElement>()
        fun flags(id: String) = layoutOf(measures.single { it.id == id }).elements.filterIsInstance<GlyphElement>().filter { it.role == Role.FLAG }

        listOf("m19-eighth-pairs", "m20-eighth-pairs-bass-b-flat", "m21-eighth-pairs-down-d").forEach { id ->
            assertEquals(2, beams(id).size, id)
            assertEquals(emptyList(), flags(id), id)
        }
        assertEquals(emptyList(), beams("m22-syncopated-eighths"))
        assertEquals(listOf("n1", "n3"), flags("m22-syncopated-eighths").map { it.noteId })
        assertEquals(listOf(Glyph.FLAG_8TH_DOWN, Glyph.FLAG_8TH_UP), flags("m22-syncopated-eighths").map { it.glyph })
        measures.filter { it.musicalDifficulty <= 3 }.forEach { measure ->
            assertTrue(beams(measure.id).isEmpty() && flags(measure.id).isEmpty(), measure.id)
        }
    }

    @Test
    fun `the rest fixtures draw one rest per silence, the others none within a measure`() {
        fun rests(id: String) = layoutOf(measures.single { it.id == id }).elements.filterIsInstance<GlyphElement>().filter { it.role == Role.REST }

        assertEquals(listOf(Glyph.REST_QUARTER), rests("m23-quarter-rest").map { it.glyph })
        assertEquals(listOf(Glyph.REST_HALF), rests("m24-half-rest-bass-g").map { it.glyph })
        assertEquals(listOf(Glyph.REST_8TH, Glyph.REST_8TH), rests("m25-eighth-rests-f").map { it.glyph })
        assertEquals(listOf("n2", "n4"), layoutOf(measures.single { it.id == "m25-eighth-rests-f" }).elements.filterIsInstance<GlyphElement>().filter { it.role == Role.FLAG }.map { it.noteId })
        assertEquals(listOf(Glyph.REST_QUARTER, Glyph.REST_QUARTER), rests("m26-rests-mixed-bass").map { it.glyph })
        assertEquals(1, layoutOf(measures.single { it.id == "m26-rests-mixed-bass" }).elements.filterIsInstance<BeamElement>().size)
        measures.filter { it.musicalDifficulty == 5 }.forEach { measure ->
            rests(measure.id).forEach { rest ->
                assertTrue(rest.ticks != null && rest.ticks!! < measure.score.timeSignature.ticksPerMeasure, "${measure.id}: a rest inside the bar carries its onset, so the mask hides it")
                assertEquals(null, rest.noteId, measure.id)
            }
        }
        measures.filter { it.musicalDifficulty < 5 }.forEach { measure -> assertEquals(emptyList(), rests(measure.id), measure.id) }
        assertEquals(setOf(Clef.TREBLE, Clef.BASS), measures.filter { it.musicalDifficulty == 5 }.map { it.score.staves.single().clef }.toSet())
        assertEquals(setOf(KeySignature(0), KeySignature(1), KeySignature(-1)), measures.filter { it.musicalDifficulty == 5 }.map { it.score.keySignature }.toSet())
    }
}
