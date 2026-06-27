package com.palettemuse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity

@Database(
    entities = [
        ThemeEntity::class,
        PhotoEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun themeDao(): ThemeDao
    abstract fun photoDao(): PhotoDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `color_palettes`")
                db.execSQL("DROP TABLE IF EXISTS `projects`")
            }
        }
    }
}