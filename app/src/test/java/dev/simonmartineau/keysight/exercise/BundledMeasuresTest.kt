package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.data.keySightJson
import dev.simonmartineau.keysight.notation.ScoreLayoutEngine
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shape of a bundled measure file: the content of the rounds before the generator. */
@Serializable
data class BundledMeasure(val id: String, val score: Score, val musicalDifficulty: Int)

/**
 * The eighteen hand-written measures of the rounds before the generator, kept as fixtures:
 * real content the layout engine and the generator's constraints are held to. They are read
 * from the source tree, so run this from the app module.
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

    /** The vocabulary the measures were written in: a sixth from middle C, up to fifths, quarters to wholes. */
    private val vocabulary = ExerciseConfig.DEFAULT.copy(
        rightHandRange = PitchRange(SpelledPitch(Step.C, octave = 4), SpelledPitch(Step.A, octave = 4)),
        maxInterval = 4,
    )

    @Test
    fun `the fixtures load, are named by their files and are eighteen`() {
        assertEquals(18, measures.size)
        assertEquals(directory.list()!!.sorted().map { it.removeSuffix(".json") }, measures.map { it.id })
        assertEquals(measures.size, measures.map { it.id }.toSet().size)
        assertEquals("m04-stepwise-up", measures[3].id)
    }

    @Test
    fun `every fixture is an instance of the vocabulary it was written in`() {
        measures.forEach { measure ->
            assertTrue(measure.musicalDifficulty in 1..3, measure.id)
            val problems = vocabulary.violations(measure.score)
            assertTrue(problems.isEmpty(), "${measure.id}: $problems")
        }
    }

    @Test
    fun `every fixture lays out inside the fixed envelope`() {
        measures.forEach { measure ->
            val layout = ScoreLayoutEngine.layoutSystem(measure.score, 0, null, showTimeSignature = true)
            assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, layout.top, measure.id)
            assertEquals(ScoreLayoutEngine.ENVELOPE_BOTTOM, layout.bottom, measure.id)
            assertEquals(measure.score.notes.map { it.id }.toSet(), layout.anchors.keys, measure.id)
        }
    }
}
