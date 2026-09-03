package dev.simonmartineau.keysight.settings

import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunSettingsTest {

    @Test
    fun `starts at the default and observes updates`() {
        val settings = InMemoryRunSettings()
        assertEquals(RunConfig.DEFAULT, settings.config.value)

        settings.update(RunConfig.DEFAULT.copy(mode = VisibilityMode.READ_AHEAD, lookaheadBeats = 0.5))

        assertEquals(VisibilityMode.READ_AHEAD, settings.config.value.mode)
        assertEquals(0.5, settings.config.value.lookaheadBeats)
    }

    @Test
    fun `the offered choices are valid configurations`() {
        assertTrue(RunChoices.LOOKAHEAD_BEATS.all { it in 0.0..4.0 })
        assertTrue(RunChoices.LOOKAHEAD_BEATS.zipWithNext().all { (a, b) -> a > b })
        assertTrue(RunChoices.TEMPOS_BPM.zipWithNext().all { (a, b) -> a < b })
        assertTrue(RunConfig.DEFAULT.tempoBpm in RunChoices.TEMPOS_BPM)
        assertTrue(RunConfig.DEFAULT.lookaheadBeats in RunChoices.LOOKAHEAD_BEATS)
        assertTrue(RunConfig.DEFAULT.segmentCount in RunChoices.SEGMENT_COUNTS)
        assertTrue(null in RunChoices.SEGMENT_COUNTS, "an open-ended run is on offer")
        assertTrue(RunChoices.SEGMENT_COUNTS.filterNotNull().zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `an open-ended run has no segment count`() {
        val open = RunConfig.DEFAULT.copy(segmentCount = null)

        assertTrue(open.isOpenEnded)
        assertTrue(!RunConfig.DEFAULT.isOpenEnded)
        assertFailsWith<IllegalArgumentException> { RunConfig.DEFAULT.copy(segmentCount = 0) }
    }
}
