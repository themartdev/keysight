package dev.simonmartineau.keysight.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeSettingsTest {

    @Test
    fun `system follows the system and the others override it`() {
        assertFalse(ThemeMode.SYSTEM.resolvesDark(systemDark = false))
        assertTrue(ThemeMode.SYSTEM.resolvesDark(systemDark = true))
        assertFalse(ThemeMode.LIGHT.resolvesDark(systemDark = true))
        assertTrue(ThemeMode.DARK.resolvesDark(systemDark = false))
    }

    @Test
    fun `starts on the system theme and observes updates`() {
        val settings = InMemoryThemeSettings()
        assertEquals(ThemeMode.SYSTEM, settings.mode.value)

        settings.update(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, settings.mode.value)
    }
}
