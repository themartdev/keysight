package dev.simonmartineau.keysight.ui.history

import dev.simonmartineau.keysight.history.PooledCounts
import dev.simonmartineau.keysight.history.RunDigest
import dev.simonmartineau.keysight.history.SessionDigest
import dev.simonmartineau.keysight.history.SessionRecord
import dev.simonmartineau.keysight.history.SessionSummary
import dev.simonmartineau.keysight.run.AbortReason
import dev.simonmartineau.keysight.ui.practice.abortMessage
import dev.simonmartineau.keysight.ui.practice.barsLabel
import dev.simonmartineau.keysight.ui.practice.modeLabel
import dev.simonmartineau.keysight.ui.practice.percentLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

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

/**
 * When a session was, for a row: "Today 18:42", "Yesterday 09:10", then the weekday and time
 * within the week, then the date. [now] is the moment the row is read at.
 */
fun whenLabel(epochMillis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
    val then = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = DateTimeFormatter.ofPattern("HH:mm", locale).format(then)
    return when (val daysAgo = ChronoUnit.DAYS.between(then.toLocalDate(), today)) {
        0L -> "Today $time"
        1L -> "Yesterday $time"
        in 2L..6L -> "${DateTimeFormatter.ofPattern("EEEE", locale).format(then)} $time"
        else -> DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(then)
    }
}

/** What a run was, for a row: "Read ahead · C major · right hand", the lookahead named in Flash. */
fun whatLabel(run: RunDigest): String =
    listOf(modeLabel(run.config), run.keySignature.majorName, run.hands.label.lowercase()).joinToString(" · ")

/** What a session was: its first run's [whatLabel], or "No runs" for one that recorded none. */
fun whatLabel(session: SessionDigest): String = session.first?.let(::whatLabel) ?: "No runs"

/** "91 · 84" for pitch and rhythm, "91" when no timing was judged, "" when nothing was. */
fun accuracyLabel(pooled: PooledCounts): String {
    val pitch = pooled.pitchAccuracy ?: return ""
    val rhythm = pooled.rhythmAccuracy ?: return percentValue(pitch)
    return "${percentValue(pitch)} · ${percentValue(rhythm)}"
}

/** "91", the percentage without its sign: the caption or the column carries the unit. */
fun percentValue(fraction: Double): String = (fraction * 100).roundToInt().toString()
