package jp.gr.java_conf.SenseMusicClock.Music.Data

import androidx.room.TypeConverter
import java.util.Date

class DateConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time // これでミリ秒（Long）として保存される
    }
}