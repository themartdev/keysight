package dev.simonmartineau.keysight.exercise

/**
 * The generator's source of chance: SplitMix64, written out so that a seed names the same
 * sequence on every platform and in every Kotlin version. A segment stored with its seed and
 * configuration must regenerate identically for as long as its generator version exists, and
 * the standard library's random makes no such promise.
 */
class SeededRandom(seed: Long) {

    private var state: Long = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        return mix(state)
    }

    /** Uniform in `0 until bound`. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        return ((nextLong() ushr 33) % bound).toInt()
    }

    fun nextBoolean(): Boolean = nextLong() < 0

    companion object {
        private const val GOLDEN_GAMMA = -0x61c8864680b583ebL

        /** SplitMix64's finaliser: every bit of the output depends on every bit of the input. */
        fun mix(value: Long): Long {
            var z = value
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}

/**
 * The seed of segment [index] of the run seeded [runSeed]: one stored run seed and the
 * segment indices reproduce every segment, and neighbouring segments share nothing.
 */
fun segmentSeed(runSeed: Long, index: Int): Long {
    require(index > 0) { "segments are numbered from 1, not $index" }
    return SeededRandom.mix(runSeed + index * SEGMENT_STRIDE)
}

private const val SEGMENT_STRIDE = 0x2545F4914F6CDD1DL
