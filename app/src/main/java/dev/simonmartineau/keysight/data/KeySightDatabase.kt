package dev.simonmartineau.keysight.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.simonmartineau.keysight.data.dao.AttemptDao
import dev.simonmartineau.keysight.data.dao.SessionDao
import dev.simonmartineau.keysight.data.entity.AttemptEntity
import dev.simonmartineau.keysight.data.entity.EvaluationResultEntity
import dev.simonmartineau.keysight.data.entity.MidiEventEntity
import dev.simonmartineau.keysight.data.entity.SessionEntity

/**
 * Local practice history: sessions, attempts, their raw MIDI and their evaluations.
 *
 * Exercises are bundled content, not rows; only what the player did lives here. Enums are
 * stored by name through Room's built-in conversion.
 */
@Database(
    entities = [
        SessionEntity::class,
        AttemptEntity::class,
        MidiEventEntity::class,
        EvaluationResultEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KeySightDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    abstract fun attemptDao(): AttemptDao

    companion object {
        const val NAME = "keysight.db"

        fun build(context: Context): KeySightDatabase =
            Room.databaseBuilder(context.applicationContext, KeySightDatabase::class.java, NAME).build()
    }
}
