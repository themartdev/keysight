package dev.simonmartineau.keysight.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2: evaluator version 2 scores rhythm, and its accuracy joins the summary columns. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE evaluation_results ADD COLUMN rhythmAccuracy REAL")
    }
}

/**
 * Version 3: runs and segments replace attempts.
 *
 * Every attempt becomes a run with the same id and one segment per measure of its score, so
 * its raw MIDI rows move to the run by that id and are not touched; [LegacyAttemptRow.toRun]
 * reads both snapshot shapes the attempt table held. Evaluations move to the segment of the
 * runs that have exactly one; a whole-run evaluation of a longer run has no segment to belong
 * to and is dropped, the MIDI it was computed from being kept.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `runs` (`id` TEXT NOT NULL, `sessionId` TEXT NOT NULL, " +
                "`startedAtEpochMillis` INTEGER NOT NULL, `startedAtNanos` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                "`abortReason` TEXT, `tempoBpm` REAL NOT NULL, `configJson` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_runs_sessionId` ON `runs` (`sessionId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `segments` (`id` TEXT NOT NULL, `runId` TEXT NOT NULL, `segmentIndex` INTEGER NOT NULL, " +
                "`exerciseId` TEXT NOT NULL, `scoreJson` TEXT NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`runId`) REFERENCES `runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_segments_runId` ON `segments` (`runId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_segments_runId_segmentIndex` ON `segments` (`runId`, `segmentIndex`)")

        convertAttempts(db)

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `midi_events_v3` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `runId` TEXT NOT NULL, " +
                "`timestampNanos` INTEGER NOT NULL, `status` INTEGER NOT NULL, `data1` INTEGER NOT NULL, `data2` INTEGER NOT NULL, " +
                "FOREIGN KEY(`runId`) REFERENCES `runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `midi_events_v3` (`id`, `runId`, `timestampNanos`, `status`, `data1`, `data2`) " +
                "SELECT `id`, `attemptId`, `timestampNanos`, `status`, `data1`, `data2` FROM `midi_events`",
        )
        db.execSQL("DROP TABLE `midi_events`")
        db.execSQL("ALTER TABLE `midi_events_v3` RENAME TO `midi_events`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_midi_events_runId` ON `midi_events` (`runId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `evaluation_results_v3` (`segmentId` TEXT NOT NULL, `evaluatorVersion` INTEGER NOT NULL, " +
                "`evaluatedAtEpochMillis` INTEGER NOT NULL, `pitchAccuracy` REAL NOT NULL, `correctCount` INTEGER NOT NULL, " +
                "`expectedCount` INTEGER NOT NULL, `extraCount` INTEGER NOT NULL, `resultJson` TEXT NOT NULL, `rhythmAccuracy` REAL, " +
                "PRIMARY KEY(`segmentId`, `evaluatorVersion`), " +
                "FOREIGN KEY(`segmentId`) REFERENCES `segments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `evaluation_results_v3` (`segmentId`, `evaluatorVersion`, `evaluatedAtEpochMillis`, `pitchAccuracy`, " +
                "`correctCount`, `expectedCount`, `extraCount`, `resultJson`, `rhythmAccuracy`) " +
                "SELECT `attemptId` || ':1', `evaluatorVersion`, `evaluatedAtEpochMillis`, `pitchAccuracy`, " +
                "`correctCount`, `expectedCount`, `extraCount`, `resultJson`, `rhythmAccuracy` FROM `evaluation_results` " +
                "WHERE `attemptId` IN (SELECT `runId` FROM `segments` GROUP BY `runId` HAVING COUNT(*) = 1)",
        )
        db.execSQL("DROP TABLE `evaluation_results`")
        db.execSQL("ALTER TABLE `evaluation_results_v3` RENAME TO `evaluation_results`")

        db.execSQL("DROP TABLE `attempts`")
    }

    private fun convertAttempts(db: SupportSQLiteDatabase) {
        val converted = ArrayList<ConvertedRun>()
        db.query(
            "SELECT `id`, `sessionId`, `exerciseId`, `startedAtEpochMillis`, `startedAtNanos`, `status`, `abortReason`, " +
                "`tempoBpm`, `previewDurationBeats`, `configJson`, `scoreJson` FROM `attempts`",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                converted += LegacyAttemptRow(
                    id = cursor.getString(0),
                    sessionId = cursor.getString(1),
                    exerciseId = cursor.getString(2),
                    startedAtEpochMillis = cursor.getLong(3),
                    startedAtNanos = cursor.getLong(4),
                    status = cursor.getString(5),
                    abortReason = if (cursor.isNull(6)) null else cursor.getString(6),
                    tempoBpm = cursor.getDouble(7),
                    previewDurationBeats = cursor.getDouble(8),
                    configJson = cursor.getString(9),
                    scoreJson = cursor.getString(10),
                ).toRun()
            }
        }
        for ((run, segments) in converted) {
            db.execSQL(
                "INSERT INTO `runs` (`id`, `sessionId`, `startedAtEpochMillis`, `startedAtNanos`, `status`, `abortReason`, `tempoBpm`, `configJson`) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(run.id, run.sessionId, run.startedAtEpochMillis, run.startedAtNanos, run.status.name, run.abortReason?.name, run.tempoBpm, run.configJson),
            )
            for (segment in segments) {
                db.execSQL(
                    "INSERT INTO `segments` (`id`, `runId`, `segmentIndex`, `exerciseId`, `scoreJson`) VALUES (?, ?, ?, ?, ?)",
                    arrayOf<Any?>(segment.id, segment.runId, segment.segmentIndex, segment.exerciseId, segment.scoreJson),
                )
            }
        }
    }
}

/**
 * Version 4: the generator. A run stores the seed its segments derive from, and a segment
 * stores what reproduces it: the generator version, its seed and its configuration, beside the
 * score it already kept. A bundled segment keeps its exercise id, so that column becomes
 * nullable, which SQLite only allows by rebuilding the table; every row is copied as it is and
 * the new columns are null for everything recorded before. Foreign keys are off while a
 * migration runs, so dropping the old table does not cascade into the evaluations.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `runs` ADD COLUMN `seed` INTEGER")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `segments_v4` (`id` TEXT NOT NULL, `runId` TEXT NOT NULL, `segmentIndex` INTEGER NOT NULL, " +
                "`exerciseId` TEXT, `scoreJson` TEXT NOT NULL, `generatorVersion` INTEGER, `seed` INTEGER, `exerciseConfigJson` TEXT, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`runId`) REFERENCES `runs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `segments_v4` (`id`, `runId`, `segmentIndex`, `exerciseId`, `scoreJson`) " +
                "SELECT `id`, `runId`, `segmentIndex`, `exerciseId`, `scoreJson` FROM `segments`",
        )
        db.execSQL("DROP TABLE `segments`")
        db.execSQL("ALTER TABLE `segments_v4` RENAME TO `segments`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_segments_runId` ON `segments` (`runId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_segments_runId_segmentIndex` ON `segments` (`runId`, `segmentIndex`)")
    }
}

val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
