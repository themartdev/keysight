package dev.simonmartineau.keysight.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.simonmartineau.keysight.run.MetronomeMode
import dev.simonmartineau.keysight.run.RunConfig
import dev.simonmartineau.keysight.run.VisibilityMode
import dev.simonmartineau.keysight.run.runScore
import dev.simonmartineau.keysight.score.KeySignature
import dev.simonmartineau.keysight.score.Score
import dev.simonmartineau.keysight.score.ScoreNote
import dev.simonmartineau.keysight.score.SpelledPitch
import dev.simonmartineau.keysight.score.Step
import dev.simonmartineau.keysight.score.Ticks
import dev.simonmartineau.keysight.score.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every schema change is exercised against a database written at the previous version. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KeySightDatabase::class.java)

    private fun measure(step: Step) = Score(
        timeSignature = TimeSignature.FOUR_FOUR,
        keySignature = KeySignature.C_MAJOR,
        measureCount = 1,
        notes = listOf(ScoreNote("n1", SpelledPitch(step, octave = 4), Ticks.ZERO, Ticks.WHOLE)),
    )

    private fun scoreJson(score: Score) = keySightJson.encodeToString(Score.serializer(), score)

    private val flashConfigJson = """{"tempoBpm":60.0,"countInMeasures":1,"previewDurationBeats":2.0,"metronomeDuringAttempt":false}"""

    private val runConfig = RunConfig(60.0, MetronomeMode.THROUGHOUT, VisibilityMode.READ_AHEAD, 4.0, segmentCount = 3)

    private val evaluationJson = """{"evaluatorVersion":1,"pitch":{"outcomes":[]}}"""

    private fun SupportSQLiteDatabase.insertAttempt(id: String, exerciseId: String, configJson: String, score: Score, previewBeats: Double = 2.0) {
        execSQL(
            "INSERT INTO attempts (id, sessionId, exerciseId, startedAtEpochMillis, startedAtNanos, status, abortReason, " +
                "tempoBpm, previewDurationBeats, configJson, scoreJson) VALUES (?, ?, ?, 7, 10000000000, 'COMPLETED', NULL, 60.0, ?, ?, ?)",
            arrayOf(id, exerciseId, previewBeats, configJson, scoreJson(score)),
        )
    }

    private fun SupportSQLiteDatabase.insertMidi(attemptId: String, timestampNanos: Long, status: Int, data1: Int, data2: Int) {
        execSQL(
            "INSERT INTO midi_events (attemptId, timestampNanos, status, data1, data2) VALUES (?, ?, ?, ?, ?)",
            arrayOf(attemptId, timestampNanos, status, data1, data2),
        )
    }

    private fun SupportSQLiteDatabase.insertEvaluation(attemptId: String) {
        execSQL(
            "INSERT INTO evaluation_results (attemptId, evaluatorVersion, evaluatedAtEpochMillis, pitchAccuracy, " +
                "correctCount, expectedCount, extraCount, resultJson, rhythmAccuracy) VALUES (?, 1, 0, 1.0, 4, 4, 0, ?, NULL)",
            arrayOf(attemptId, evaluationJson),
        )
    }

    private fun SupportSQLiteDatabase.count(sql: String): Int = query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private fun SupportSQLiteDatabase.strings(sql: String): List<String> = query(sql).use { cursor ->
        generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toList()
    }

    @Test
    fun migrate1To2KeepsEvaluationsWithNoRhythmAccuracy() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO sessions (id, startedAtEpochMillis, endedAtEpochMillis) VALUES ('s1', 0, NULL)")
            db.execSQL(
                "INSERT INTO attempts (id, sessionId, exerciseId, startedAtEpochMillis, startedAtNanos, status, abortReason, " +
                    "tempoBpm, previewDurationBeats, configJson, scoreJson) " +
                    "VALUES ('a1', 's1', 'e1', 0, 0, 'COMPLETED', NULL, 72.0, 4.0, '{}', '{}')",
            )
            db.execSQL(
                "INSERT INTO evaluation_results (attemptId, evaluatorVersion, evaluatedAtEpochMillis, pitchAccuracy, " +
                    "correctCount, expectedCount, extraCount, resultJson) " +
                    "VALUES ('a1', 1, 0, 1.0, 4, 4, 0, '{\"evaluatorVersion\":1,\"pitch\":{\"outcomes\":[]}}')",
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT rhythmAccuracy, pitchAccuracy FROM evaluation_results WHERE attemptId = 'a1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals(1.0, cursor.getDouble(1), 0.0)
        }
    }

    @Test
    fun migrate2To3TurnsAttemptsIntoRunsWithSegmentsAndMovesTheirMidiAndEvaluations() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL("INSERT INTO sessions (id, startedAtEpochMillis, endedAtEpochMillis) VALUES ('s1', 0, NULL)")
            // A Round 2 to 5 attempt: a FlashConfig snapshot and one measure.
            db.insertAttempt("a1", "m01", flashConfigJson, measure(Step.C))
            db.insertMidi("a1", 14_000_000_000, 0x90, 60, 90)
            db.insertMidi("a1", 15_000_000_000, 0x80, 60, 0)
            db.insertEvaluation("a1")
            // A Round 6 run: a RunConfig snapshot and the run score of three segments.
            db.insertAttempt(
                "a2",
                "m01,m07,m03",
                keySightJson.encodeToString(RunConfig.serializer(), runConfig),
                runScore(listOf(measure(Step.C), measure(Step.D), measure(Step.E))),
                previewBeats = Double.POSITIVE_INFINITY,
            )
            db.insertMidi("a2", 20_000_000_000, 0x90, 62, 80)
            db.insertEvaluation("a2")
            // A row nothing can read.
            db.insertAttempt("a3", "m05", "garbage", measure(Step.G))
            db.execSQL("UPDATE attempts SET scoreJson = '{oops' WHERE id = 'a3'")
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        assertEquals(3, db.count("SELECT COUNT(*) FROM runs"))
        assertEquals(listOf("a1:1", "a2:1", "a2:2", "a2:3", "a3:1"), db.strings("SELECT id FROM segments ORDER BY id"))
        assertEquals(listOf("m01", "m01", "m07", "m03", "m05"), db.strings("SELECT exerciseId FROM segments ORDER BY runId, segmentIndex"))

        val a1Config = keySightJson.decodeFromString(RunConfig.serializer(), db.strings("SELECT configJson FROM runs WHERE id = 'a1'").single())
        assertEquals(RunConfig(60.0, MetronomeMode.COUNT_IN_ONLY, VisibilityMode.FLASH, 2.0, segmentCount = 1), a1Config)
        val a2Config = keySightJson.decodeFromString(RunConfig.serializer(), db.strings("SELECT configJson FROM runs WHERE id = 'a2'").single())
        assertEquals(runConfig, a2Config)
        val a2Segments = db.strings("SELECT scoreJson FROM segments WHERE runId = 'a2' ORDER BY segmentIndex")
            .map { keySightJson.decodeFromString(Score.serializer(), it) }
        assertEquals(listOf(measure(Step.C), measure(Step.D), measure(Step.E)), a2Segments)
        assertEquals("{oops", db.strings("SELECT scoreJson FROM segments WHERE runId = 'a3'").single())

        assertEquals(listOf("a1", "a1", "a2"), db.strings("SELECT runId FROM midi_events ORDER BY id"))
        assertEquals(3, db.count("SELECT COUNT(*) FROM midi_events WHERE data1 IN (60, 62)"))
        assertEquals(listOf("a1:1"), db.strings("SELECT segmentId FROM evaluation_results"))
        assertEquals(listOf(evaluationJson), db.strings("SELECT resultJson FROM evaluation_results"))
        assertFalse(db.strings("SELECT name FROM sqlite_master WHERE type = 'table'").contains("attempts"))
    }

    @Test
    fun migrate1To3ChainsBothSteps() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL("INSERT INTO sessions (id, startedAtEpochMillis, endedAtEpochMillis) VALUES ('s1', 0, NULL)")
            db.execSQL(
                "INSERT INTO attempts (id, sessionId, exerciseId, startedAtEpochMillis, startedAtNanos, status, abortReason, " +
                    "tempoBpm, previewDurationBeats, configJson, scoreJson) VALUES ('a1', 's1', 'm01', 0, 0, 'ABORTED', 'MIDI_DISCONNECTED', 72.0, 4.0, ?, ?)",
                arrayOf(flashConfigJson, scoreJson(measure(Step.C))),
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        assertEquals(listOf("ABORTED"), db.strings("SELECT status FROM runs"))
        assertEquals(listOf("MIDI_DISCONNECTED"), db.strings("SELECT abortReason FROM runs"))
        assertEquals(listOf("a1:1"), db.strings("SELECT id FROM segments"))
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
