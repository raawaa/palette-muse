package com.palettemuse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity

@Database(
    entities = [
        ThemeEntity::class,
        PhotoEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun themeDao(): ThemeDao
    abstract fun photoDao(): PhotoDao
}
