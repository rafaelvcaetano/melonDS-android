package me.magnum.melonds.database.converters

import androidx.room.TypeConverter
import java.time.Instant

class InstantConverter {

    @TypeConverter
    fun instantToTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @TypeConverter
    fun timestampToInstant(timestamp: Long?): Instant? {
        return timestamp?.let {
            Instant.ofEpochMilli(it)
        }
    }
}