package dev.simonmartineau.keysight.run

import dev.simonmartineau.keysight.exercise.Accompaniment
import dev.simonmartineau.keysight.exercise.ExerciseConfig
import dev.simonmartineau.keysight.exercise.ExerciseGenerator
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.exercise.segmentSeed
import dev.simonmartineau.keysight.score.KeySignature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class GeneratedSegmentSourceTest {

    private val config = ExerciseConfig(KeySignature(1), Hands.BOTH, Accompaniment.HELD_NOTE)
    private val source = GeneratedSegmentSource(runSeed = 99L, config)

    @Test
    fun `segments are numbered from one and an extension continues the same run`() {
        val first = source.next(5, firstIndex = 1)
        val more = source.next(2, firstIndex = 4)

        assertEquals(first.drop(3), more)
        assertEquals(first, GeneratedSegmentSource(99L, config).next(5, 1))
        assertFailsWith<IllegalArgumentException> { source.next(0, 1) }
    }

    @Test
    fun `every segment carries what reproduces it and chains into one run`() {
        val segments = source.next(6, firstIndex = 1)

        segments.forEachIndexed { index, segment ->
            val origin = assertIs<SegmentOrigin.Generated>(segment.origin)
            assertEquals(segmentSeed(99L, index + 1), origin.seed)
            assertEquals(ExerciseGenerator.GENERATOR_VERSION, origin.generatorVersion)
            assertEquals(config, origin.config)
            assertEquals(ExerciseGenerator.generate(origin.config, origin.seed), segment.score)
            assertEquals(KeySignature(1), segment.score.keySignature)
        }
        val run = RunContext(segments, RunConfig.DEFAULT.copy(segmentCount = 6), seed = 99L)
        assertEquals(7, run.score.measureCount)
        assertEquals(99L, run.seed)
    }
}
