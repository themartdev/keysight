package dev.simonmartineau.keysight.ui.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.history.DayCount
import dev.simonmartineau.keysight.history.PooledCounts
import dev.simonmartineau.keysight.history.RunDigest
import dev.simonmartineau.keysight.history.SessionDigest
import dev.simonmartineau.keysight.history.SessionRecord
import dev.simonmartineau.keysight.midi.MidiConnection
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.ui.theme.KeySightTheme
import java.time.LocalDate

/** Hand-built Home states: the phone and the tablet, with history, without, and without a keyboard. */
private object PreviewHome {
    private const val DAY = 86_400_000L
    val now = 1_788_400_000_000L
    private val today = LocalDate.of(2026, 9, 3)

    val config = RunConfig.DEFAULT.copy(mode = VisibilityMode.READ_AHEAD)
    val content = ContentConfig(KeySignature.C_MAJOR, Hands.RIGHT)

    private fun session(id: String, daysAgo: Int, runs: Int, bars: Int, correct: Int, expected: Int, onTime: Int, matched: Int, mode: VisibilityMode = VisibilityMode.READ_AHEAD): SessionDigest {
        val started = now - daysAgo * DAY - 3_600_000L
        val record = SessionRecord(id, started, started + 1_200_000L)
        return SessionDigest(
            record,
            (1..runs).map { RunDigest("$id-$it", id, started + it * 300_000L, config.copy(mode = mode), KeySignature(if (it % 2 == 0) 1 else 0), Hands.RIGHT, bars / runs, emptyList()) },
        )
    }

    val dashboard = Dashboard(
        hasHistory = true,
        week = PooledCounts(correctCount = 412, expectedCount = 453, onTimeCount = 380, matchedCount = 449),
        weekBars = 112,
        days = listOf(12, 0, 24, 40, 0, 8, 28).mapIndexed { index, bars -> DayCount(today.minusDays((6 - index).toLong()), bars) },
        recent = listOf(
            session("s1", 0, 3, 28, 100, 108, 90, 105),
            session("s2", 1, 1, 8, 28, 32, 29, 31, VisibilityMode.FLASH),
            session("s3", 3, 4, 40, 141, 160, 130, 155, VisibilityMode.OPEN_SCORE),
        ),
    )
}

@Composable
private fun PreviewHomeScreen(dashboard: Dashboard? = PreviewHome.dashboard, connection: MidiConnection = MidiConnection.Connected("Roland FP-30X")) {
    KeySightTheme {
        HomeContent(
            greeting = "Good evening",
            connection = connection,
            config = PreviewHome.config,
            content = PreviewHome.content,
            level = MusicalLevel.DEFAULT,
            dashboard = dashboard,
            now = PreviewHome.now,
            onStart = {},
            onQuickStart = {},
            onChangeSettings = {},
            onHistory = {},
        )
    }
}

@Preview(name = "Home, phone", showBackground = true, widthDp = 764, heightDp = 366)
@Composable
private fun HomePhonePreview() = PreviewHomeScreen()

@Preview(name = "Home, tablet", showBackground = true, widthDp = 1200, heightDp = 776)
@Composable
private fun HomeTabletPreview() = PreviewHomeScreen()

@Preview(name = "Home, dark", showBackground = true, widthDp = 764, heightDp = 366, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeDarkPreview() {
    KeySightTheme(darkTheme = true) {
        HomeContent("Good evening", MidiConnection.Connected("Roland FP-30X"), PreviewHome.config, PreviewHome.content, MusicalLevel.DEFAULT, PreviewHome.dashboard, PreviewHome.now, {}, {}, {}, {})
    }
}

@Preview(name = "Home, nothing recorded, no keyboard", showBackground = true, widthDp = 764, heightDp = 366)
@Composable
private fun HomeEmptyPreview() = PreviewHomeScreen(
    dashboard = Dashboard(hasHistory = false, week = PooledCounts.NONE, weekBars = 0, days = emptyList(), recent = emptyList()),
    connection = MidiConnection.NoDevice,
)
