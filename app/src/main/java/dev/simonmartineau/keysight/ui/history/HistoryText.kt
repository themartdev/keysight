package dev.simonmartineau.keysight.ui.history

import dev.simonmartineau.keysight.history.SessionRecord
import dev.simonmartineau.keysight.history.SessionSummary
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.ui.practice.abortMessage
import dev.simonmartineau.keysight.ui.practice.barsLabel
import dev.simonmartineau.keysight.ui.practice.percentLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The words of the history screen, kept pure so they can be tested.
 */

/** "Thu 3 Sep 2026, 14:32": when a session or a run started, in the player's zone. */
fun dateTimeLabel(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** "14:32": when a run started, on a day the session line already names. */
fun timeLabel(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("HH:mm", locale).format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** The session's title: "This session" for the one the practice screen is recording into, else when it started. */
fun sessionTitle(session: SessionRecord, currentSessionId: String?, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    if (session.id == currentSessionId) "This session" else dateTimeLabel(session.startedAtEpochMillis, zone, locale)

/** "3 runs   24 bars", or "No runs yet". */
fun sessionCountsLine(summary: SessionSummary): String =
    if (summary.runCount == 0) "No runs yet" else "${runsLabel(summary.runCount)}   ${barsLabel(summary.barCount)}"

/** "Stopped early: the keyboard disconnected." */
fun stoppedLine(reason: AbortReason): String = "Stopped early: " + abortMessage(reason).replaceFirstChar { it.lowercase() }

fun runsLabel(runs: Int): String = if (runs == 1) "1 run" else "$runs runs"

/** "Pitch 91%   Rhythm 84%" pooled over the session; the rhythm only when a note matched; null when no bar was judged. */
fun sessionScoreLine(summary: SessionSummary): String? {
    val pitch = summary.pitchAccuracy ?: return null
    val parts = mutableListOf("Pitch ${percentLabel(pitch)}")
    summary.rhythmAccuracy?.let { parts += "Rhythm ${percentLabel(it)}" }
    return parts.joinToString("   ")
}

/**
 * The level over the session: one "Level: ..." line when it never changed, else where it
 * started and where it ended; nothing when neither is known.
 */
fun sessionLevelLines(summary: SessionSummary): List<String> {
    val start = summary.start?.description
    val end = summary.end?.description
    return when {
        start == null && end == null -> emptyList()
        start == end || end == null -> listOf("Level: ${start ?: end}")
        start == null -> listOf("Level: $end")
        else -> listOf("Started: $start", "Ended: $end")
    }
}
