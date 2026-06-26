package com.palettemuse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.palettemuse.data.model.ColorPaletteEntity
import com.palettemuse.data.model.ProjectEntity

@Database(
    entities = [ProjectEntity::class, ColorPaletteEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun colorPaletteDao(): ColorPaletteDao
}
