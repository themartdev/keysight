package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.data.keySightJson
import dev.simonmartineau.keysight.notation.ScoreLayoutEngine
import dev.simonmartineau.keysight.score.Clef
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Pitch
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The content pack is checked here, on every build, rather than discovered broken on a device. */
class BundledExercisesTest {

    private val assets = File("src/main/assets")
    private val repository = BundledExerciseRepository(FileAssetSource(assets), keySightJson)

    @Test
    fun `the pack loads and has enough exercises`() = runTest {
        assertTrue(assets.isDirectory, "run from the app module: ${assets.absolutePath}")
        assertTrue(repository.all().size >= 16)
    }

    @Test
    fun `ids are unique and match file names`() = runTest {
        val files = FileAssetSource(assets).list(BundledExerciseRepository.DIRECTORY).sorted()

        assertEquals(files.map { it.removeSuffix(".json") }, repository.all().map { it.id })
        assertEquals(files.size, repository.all().map { it.id }.toSet().size)
    }

    @Test
    fun `every exercise is within the V1 vocabulary`() = runTest {
        val range = Pitch(60)..Pitch(69)
        val allowedDurations = setOf(Ticks.QUARTER, Ticks.HALF, Ticks.WHOLE)

        repository.all().forEach { exercise ->
            val score = exercise.score
            assertEquals(TimeSignature.FOUR_FOUR, score.timeSignature, exercise.id)
            assertEquals(Clef.TREBLE, score.clef, exercise.id)
            assertEquals(KeySignature.C_MAJOR, score.keySignature, exercise.id)
            assertEquals(1, score.measureCount, exercise.id)
            assertTrue(exercise.musicalDifficulty in 1..3, exercise.id)
            assertTrue(score.notes.isNotEmpty(), exercise.id)
            score.notes.forEach { note ->
                assertTrue(note.pitch in range, "${exercise.id}: ${note.spelling} out of range")
                assertTrue(note.duration in allowedDurations, "${exercise.id}: ${note.duration}")
                assertEquals(0, note.spelling.alteration, exercise.id)
            }
            assertEquals(score.notes.size, score.chordsInPerformanceOrder.size, "${exercise.id} must be monophonic")
            assertEquals(score.totalTicks, score.notes.maxOf { it.end }, "${exercise.id} must fill the measure")
        }
    }

    @Test
    fun `every exercise lays out inside the fixed envelope`() = runTest {
        repository.all().forEach { exercise ->
            val layout = ScoreLayoutEngine.layout(exercise.score)
            assertEquals(ScoreLayoutEngine.ENVELOPE_TOP, layout.top, exercise.id)
            assertEquals(ScoreLayoutEngine.ENVELOPE_BOTTOM, layout.bottom, exercise.id)
            assertEquals(exercise.score.notes.map { it.id }.toSet(), layout.anchors.keys, exercise.id)
        }
    }

    @Test
    fun `byId finds an exercise and nothing else`() = runTest {
        assertEquals("m04-stepwise-up", repository.byId("m04-stepwise-up")?.id)
        assertEquals(null, repository.byId("nope"))
    }

    @Test
    fun `a malformed file fails loudly with its name`() = runTest {
        val dir = File(System.getProperty("java.io.tmpdir"), "keysight-bad-pack-${System.nanoTime()}").apply {
            File(this, BundledExerciseRepository.DIRECTORY).mkdirs()
            File(this, "${BundledExerciseRepository.DIRECTORY}/broken.json").writeText("{")
        }
        try {
            val error = assertFailsWith<IllegalStateException> { BundledExerciseRepository(FileAssetSource(dir), keySightJson).all() }
            assertTrue("broken.json" in error.message.orEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
