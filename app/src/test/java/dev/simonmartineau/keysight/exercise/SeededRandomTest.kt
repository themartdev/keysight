package dev.simonmartineau.keysight.exercise

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SeededRandomTest {

    @Test
    fun `the stream is SplitMix64, pinned to the algorithm's reference values`() {
        // The first outputs of SplitMix64 for seeds 0 and 1, as any reference implementation gives them.
        val fromZero = SeededRandom(0)
        assertEquals(0xE220A8397B1DCDAFuL.toLong(), fromZero.nextLong())
        assertEquals(0x6E789E6AA1B965F4uL.toLong(), fromZero.nextLong())
        assertEquals(0x06C45D188009454FuL.toLong(), fromZero.nextLong())
        assertEquals(0x910A2DEC89025CC1uL.toLong(), SeededRandom(1).nextLong())
    }

    @Test
    fun `the same seed gives the same sequence`() {
        val a = SeededRandom(42)
        val b = SeededRandom(42)

        repeat(100) { assertEquals(a.nextInt(1000), b.nextInt(1000)) }
    }

    @Test
    fun `ints stay in their bound and booleans come up both ways`() {
        val random = SeededRandom(3)
        val ints = (1..1000).map { random.nextInt(7) }
        assertTrue(ints.all { it in 0 until 7 })
        assertEquals((0 until 7).toSet(), ints.toSet())
        val booleans = (1..100).map { random.nextBoolean() }.toSet()
        assertEquals(setOf(true, false), booleans)
        assertFailsWith<IllegalArgumentException> { random.nextInt(0) }
    }

    @Test
    fun `segment seeds derive from the run seed and the index, and neighbours differ`() {
        val seeds = (1..100).map { segmentSeed(runSeed = 5L, index = it) }

        assertEquals(100, seeds.toSet().size)
        assertEquals(seeds, (1..100).map { segmentSeed(5L, it) })
        assertTrue(seeds[0] != segmentSeed(runSeed = 6L, index = 1))
        assertFailsWith<IllegalArgumentException> { segmentSeed(5L, 0) }
    }
}
