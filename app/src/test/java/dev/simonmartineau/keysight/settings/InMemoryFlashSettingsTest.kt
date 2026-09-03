package dev.simonmartineau.keysight.settings

import dev.simonmartineau.keysight.attempt.FlashConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryFlashSettingsTest {

    @Test
    fun `starts at the default and observes updates`() {
        val settings = InMemoryFlashSettings()
        assertEquals(FlashConfig.DEFAULT, settings.config.value)

        settings.update(FlashConfig.DEFAULT.copy(previewDurationBeats = 0.5))

        assertEquals(0.5, settings.config.value.previewDurationBeats)
    }

    @Test
    fun `the offered choices are valid configurations`() {
        assertTrue(FlashChoices.PREVIEW_BEATS.all { it in 0.0..4.0 })
        assertTrue(FlashChoices.TEMPOS_BPM.zipWithNext().all { (a, b) -> a < b })
        assertTrue(FlashConfig.DEFAULT.tempoBpm in FlashChoices.TEMPOS_BPM)
        assertTrue(FlashConfig.DEFAULT.previewDurationBeats in FlashChoices.PREVIEW_BEATS)
    }
}
