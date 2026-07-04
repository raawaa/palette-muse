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
    version = 4,
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

        // ADR-0019: persist the captured-color confidence signals on each
        // confirmed photo so a low-confidence verdict can be audited later
        // (adb run-as + sqlite3). Existing rows keep NULL — they predate the
        // signals and are not part of the audit corpus.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `populationShare` REAL")
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `topVsSecondRatio` REAL")
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `isLowConfidence` INTEGER")
            }
        }
    }
}