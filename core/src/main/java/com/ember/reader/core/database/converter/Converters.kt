package com.ember.reader.core.database.converter

import androidx.room.TypeConverter
import com.ember.reader.core.model.BookFormat
import com.ember.reader.core.model.HighlightColor
import java.time.Instant

class Converters {

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun fromBookFormat(value: BookFormat): String = value.name

    @TypeConverter
    fun toBookFormat(value: String): BookFormat = BookFormat.valueOf(value)

    @TypeConverter
    fun fromHighlightColor(value: HighlightColor): String = value.name

    @TypeConverter
    fun toHighlightColor(value: String): HighlightColor = HighlightColor.valueOf(value)
}
