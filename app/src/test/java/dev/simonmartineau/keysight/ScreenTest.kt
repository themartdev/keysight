package dev.simonmartineau.keysight

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ScreenTest {

    private val all = listOf(
        Screen.Home,
        Screen.Play,
        Screen.Settings,
        Screen.Run(from = Screen.Home),
        Screen.Run(from = Screen.Play),
        Screen.History(null),
        Screen.History("s1"),
        Screen.RunDetail("r1", null),
        Screen.RunDetail("r1", "s1"),
    )

    @Test
    fun `back leaves the app from home, returns to home from the rail's pages, and retraces a run and a detail`() {
        assertNull(Screen.Home.back)
        assertEquals(Screen.Home, Screen.Play.back)
        assertEquals(Screen.Home, Screen.Settings.back)
        assertEquals(Screen.Home, Screen.History("s1").back)
        assertEquals(Screen.Home, Screen.Run(from = Screen.Home).back)
        assertEquals(Screen.Play, Screen.Run(from = Screen.Play).back)
        assertEquals(Screen.History("s1"), Screen.RunDetail("r1", "s1").back)
    }

    @Test
    fun `a run starts from home or play only`() {
        assertFailsWith<IllegalArgumentException> { Screen.Run(from = Screen.Settings) }
    }

    @Test
    fun `only a run is without the rail, and every screen lights its tab`() {
        assertEquals(listOf(Screen.Run(from = Screen.Home), Screen.Run(from = Screen.Play)), all.filterNot { it.hasRail })
        assertEquals(Screen.Tab.HISTORY, Screen.RunDetail("r1", null).tab)
        assertEquals(Screen.Tab.PLAY, Screen.Run(from = Screen.Play).tab)
        Screen.Tab.entries.forEach { assertEquals(it, it.screen.tab) }
    }

    @Test
    fun `every screen survives the saver`() {
        val scope = SaverScope { true }
        all.forEach { screen ->
            val saved = with(Screen.Saver) { scope.save(screen) }!!
            assertEquals(screen, Screen.Saver.restore(saved))
        }
    }
}
