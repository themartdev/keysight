package dev.simonmartineau.keysight.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every schema change is exercised against a database written at the previous version. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KeySightDatabase::class.java)

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

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
