package me.magnum.melonds.database.converters

import androidx.room.TypeConverter
import kotlin.time.Instant

class InstantConverter {

    @TypeConverter
    fun instantToTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilliseconds()
    }

    @TypeConverter
    fun timestampToInstant(timestamp: Long?): Instant? {
        return timestamp?.let {
            Instant.fromEpochMilliseconds(it)
        }
    }
}