package dev.simonmartineau.keysight.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2: evaluator version 2 scores rhythm, and its accuracy joins the summary columns. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE evaluation_results ADD COLUMN rhythmAccuracy REAL")
    }
}

val MIGRATIONS = arrayOf(MIGRATION_1_2)
