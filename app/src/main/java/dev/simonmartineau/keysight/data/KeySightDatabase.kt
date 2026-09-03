package dev.simonmartineau.keysight.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.simonmartineau.keysight.data.dao.DifficultyDao
import dev.simonmartineau.keysight.data.dao.RunDao
import dev.simonmartineau.keysight.data.dao.SessionDao
import dev.simonmartineau.keysight.data.entity.DifficultyStateEntity
import dev.simonmartineau.keysight.data.entity.EvaluationResultEntity
import dev.simonmartineau.keysight.data.entity.MidiEventEntity
import dev.simonmartineau.keysight.data.entity.RunEntity
import dev.simonmartineau.keysight.data.entity.SegmentEntity
import dev.simonmartineau.keysight.data.entity.SessionEntity

/**
 * Local practice history: sessions, runs, their segments, their raw MIDI, the evaluation of
 * every segment, and the difficulty controller's state.
 *
 * Content is generated, not stored as such: a segment keeps what reproduces it beside its
 * score, and otherwise only what the player did lives here. Enums are stored by name through
 * Room's built-in conversion.
 */
@Database(
    entities = [
        SessionEntity::class,
        RunEntity::class,
        SegmentEntity::class,
        MidiEventEntity::class,
        EvaluationResultEntity::class,
        DifficultyStateEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class KeySightDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    abstract fun runDao(): RunDao

    abstract fun difficultyDao(): DifficultyDao

    companion object {
        const val NAME = "keysight.db"

        fun build(context: Context): KeySightDatabase =
            Room.databaseBuilder(context.applicationContext, KeySightDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
