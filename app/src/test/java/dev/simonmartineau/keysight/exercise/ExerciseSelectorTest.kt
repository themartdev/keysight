package dev.simonmartineau.keysight.exercise

import dev.simonmartineau.keysight.Fixtures
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ExerciseSelectorTest {

    private val pack = (1..6).map { Exercise("e$it", Fixtures.cdef, 1) }

    @Test
    fun `never repeats the previous exercise`() {
        val selector = ExerciseSelector(pack, Random(1))
        var previous: Exercise? = null
        repeat(1000) {
            val next = selector.next(previous)
            assertNotEquals(previous?.id, next.id)
            previous = next
        }
    }

    @Test
    fun `every exercise shows up`() {
        val selector = ExerciseSelector(pack, Random(7))
        val seen = mutableSetOf<String>()
        var previous: Exercise? = null
        repeat(1000) { previous = selector.next(previous).also { seen += it.id } }

        assertEquals(pack.map { it.id }.toSet(), seen)
    }

    @Test
    fun `a single exercise is offered again rather than nothing`() {
        val only = pack.take(1)
        assertEquals(only.single(), ExerciseSelector(only, Random(1)).next(only.single()))
        assertFailsWith<IllegalArgumentException> { ExerciseSelector(emptyList(), Random(1)) }
    }

    @Test
    fun `a run's exercises never repeat consecutively and start away from the previous one`() {
        val selector = ExerciseSelector(pack, Random(3))
        val previous = pack[2]

        val run = selector.nextRun(50, previous)

        assertEquals(50, run.size)
        assertNotEquals(previous.id, run.first().id)
        run.zipWithNext().forEach { (a, b) -> assertNotEquals(a.id, b.id) }
        assertFailsWith<IllegalArgumentException> { selector.nextRun(0) }
    }
}
