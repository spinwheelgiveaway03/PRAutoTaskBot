package com.prautotaskbot.data.db

import androidx.room.TypeConverter
import com.prautotaskbot.data.model.TaskStatus
import com.prautotaskbot.data.model.TaskType

class Converters {

    @TypeConverter
    fun fromTaskType(value: TaskType): String = value.name

    @TypeConverter
    fun toTaskType(value: String): TaskType = TaskType.valueOf(value)

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus): String = value.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)
}
