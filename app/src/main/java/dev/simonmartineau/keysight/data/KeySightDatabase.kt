package dev.simonmartineau.keysight.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.simonmartineau.keysight.data.dao.AttemptDao
import dev.simonmartineau.keysight.data.dao.SessionDao
import dev.simonmartineau.keysight.data.dao.UserDifficultyStateDao
import dev.simonmartineau.keysight.data.entity.AttemptEntity
import dev.simonmartineau.keysight.data.entity.EvaluationResultEntity
import dev.simonmartineau.keysight.data.entity.MidiEventEntity
import dev.simonmartineau.keysight.data.entity.SessionEntity
import dev.simonmartineau.keysight.data.entity.UserDifficultyStateEntity
import dev.simonmartineau.keysight.midi.MidiEventType

/**
 * Local practice history. Exercises themselves are bundled content, not rows: only what the
 * player did lives here.
 */
@Database(
    entities = [
        SessionEntity::class,
        AttemptEntity::class,
        MidiEventEntity::class,
        EvaluationResultEntity::class,
        UserDifficultyStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(KeySightConverters::class)
abstract class KeySightDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    abstract fun attemptDao(): AttemptDao

    abstract fun userDifficultyStateDao(): UserDifficultyStateDao

    companion object {
        private const val NAME = "keysight.db"

        fun build(context: Context): KeySightDatabase =
            Room.databaseBuilder(context.applicationContext, KeySightDatabase::class.java, NAME)
                .build()
    }
}

class KeySightConverters {

    @TypeConverter
    fun midiEventTypeToString(type: MidiEventType): String = type.name

    @TypeConverter
    fun stringToMidiEventType(value: String): MidiEventType = MidiEventType.valueOf(value)
}
