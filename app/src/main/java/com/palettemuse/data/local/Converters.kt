package com.palettemuse.data.local

import androidx.room.TypeConverter
import com.palettemuse.data.model.ColorRole
import com.palettemuse.data.model.ProjectType

class Converters {
    @TypeConverter
    fun fromProjectType(value: ProjectType): String = value.name

    @TypeConverter
    fun toProjectType(value: String): ProjectType = ProjectType.valueOf(value)

    @TypeConverter
    fun fromColorRole(value: ColorRole): String = value.name

    @TypeConverter
    fun toColorRole(value: String): ColorRole = ColorRole.valueOf(value)
}
