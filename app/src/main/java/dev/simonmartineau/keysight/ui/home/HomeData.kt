package dev.simonmartineau.keysight.ui.home

import dev.simonmartineau.keysight.difficulty.MusicalLevel
import dev.simonmartineau.keysight.history.DayCount
import dev.simonmartineau.keysight.history.PooledCounts
import dev.simonmartineau.keysight.history.RunDigest
import dev.simonmartineau.keysight.history.SessionDigest
import dev.simonmartineau.keysight.history.SessionRecord
import dev.simonmartineau.keysight.history.barsPerDay
import dev.simonmartineau.keysight.history.sessionDigests
import dev.simonmartineau.keysight.history.since
import dev.simonmartineau.keysight.history.windowStartMillis
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.settings.ContentConfig
import dev.simonmartineau.keysight.settings.NotesLadder
import dev.simonmartineau.keysight.ui.practice.barsLabel
import dev.simonmartineau.keysight.ui.practice.modeLabel
import dev.simonmartineau.keysight.ui.practice.tempoLabel
import dev.simonmartineau.keysight.exercise.Hands
import dev.simonmartineau.keysight.run.MetronomeMode
import java.time.LocalDate
import java.time.ZoneId

/*
 * What the Home screen shows, computed from history and the settings, kept pure so it can
 * be tested: the week's numbers, the days, the recent sessions and the words.
 */

/** How many days the dashboard looks back, today included. */
const val DASHBOARD_DAYS = 7

/** How many recent sessions Home lists. */
const val RECENT_SESSIONS = 3

/**
 * The right column of Home. [week] is pooled over every bar judged in the last
 * [DASHBOARD_DAYS] days and [weekBars] every bar read in them; [days] is one count per day,
 * oldest first; [recent] the last [RECENT_SESSIONS] sessions, newest first. [hasHistory] is
 * false when nothing was ever recorded, and the column says so instead of showing zeros.
 */
data class Dashboard(
    val hasHistory: Boolean,
    val week: PooledCounts,
    val weekBars: Int,
    val days: List<DayCount>,
    val recent: List<SessionDigest>,
)

fun dashboardOf(sessions: List<SessionRecord>, runs: List<RunDigest>, today: LocalDate, zone: ZoneId): Dashboard {
    val window = runs.since(windowStartMillis(DASHBOARD_DAYS, today, zone))
    return Dashboard(
        hasHistory = runs.isNotEmpty(),
        week = window.fold(PooledCounts.NONE) { sum, run -> sum + run.pooled },
        weekBars = window.sumOf { it.barCount },
        days = barsPerDay(window, DASHBOARD_DAYS, today, zone),
        recent = sessionDigests(sessions.sortedByDescending { it.startedAtEpochMillis }, runs).filter { it.runCount > 0 }.take(RECENT_SESSIONS),
    )
}

/** "Good morning" until noon, "Good afternoon" until six, "Good evening" after. */
fun greeting(hour: Int): String = when {
    hour < 12 -> "Good morning"
    hour < 18 -> "Good afternoon"
    else -> "Good evening"
}

/** The settings read back as prose: "Read ahead · C major · right hand". */
fun resumeLead(config: RunConfig, content: ContentConfig): String =
    listOf(modeLabel(config), content.keySignature.majorName, content.hands.label.lowercase()).joinToString(" · ")

/** "8 bars at 72 bpm, count-in only. Up to thirds, quarter notes." The level is the controller's when it adapts, else the player's. */
fun resumeBody(config: RunConfig, level: MusicalLevel): String {
    val length = config.segmentCount?.let(::barsLabel) ?: "Open run"
    val click = if (config.metronome == MetronomeMode.THROUGHOUT) "click throughout" else "count-in only"
    return "$length at ${tempoLabel(config.tempoBpm)}, $click. ${NotesLadder.shortLabel(level).replace(" · ", ", ")}."
}

/** The one setting each quick-start chip writes, and what it says. */
sealed interface QuickStart {
    val label: String

    data class Mode(val mode: VisibilityMode) : QuickStart {
        override val label: String get() = mode.label
    }

    data class WithHands(val hands: Hands) : QuickStart {
        override val label: String get() = hands.label
    }
}

/** The chips of "Or start something else": every mode but the current one, then the other way round for the hands. */
fun quickStarts(config: RunConfig, content: ContentConfig): List<QuickStart> =
    VisibilityMode.entries.filter { it != config.mode }.map { QuickStart.Mode(it) } +
        QuickStart.WithHands(if (content.hands == Hands.BOTH) Hands.RIGHT else Hands.BOTH)

/** The modes that are not built yet, named on a chip nobody can tap. */
const val UNBUILT_MODES = "Hanon · Scales · Chords"
