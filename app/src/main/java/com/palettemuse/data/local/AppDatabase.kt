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
    version = 5,
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

        // ADR-0020 / Issue #36-39: add Int RGB columns alongside the existing
        // hex TEXT columns (dual-column coexistence strategy). Backfill the new
        // columns from the old hex values; keep the old columns so existing data
        // is never lost. The Room entities now use the Int columns as primary;
        // the hex columns are preserved as computed properties.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `themes` ADD COLUMN `representativeRgb` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `dominantRgb` INTEGER NOT NULL DEFAULT 0")

                // Backfill themes.representativeRgb from existing representativeHex values.
                // SQLite cannot natively parse hex strings, so we use a cursor to do it in Kotlin.
                db.query("SELECT id, representativeHex FROM themes").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val hex = cursor.getString(1)
                        val rgb = tryParseHexToInt(hex)
                        db.execSQL("UPDATE `themes` SET `representativeRgb` = ? WHERE `id` = ?", arrayOf(rgb, id))
                    }
                }

                // Backfill photos.dominantRgb from existing dominantHex values
                db.query("SELECT id, dominantHex FROM photos").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val hex = cursor.getString(1)
                        val rgb = tryParseHexToInt(hex)
                        db.execSQL("UPDATE `photos` SET `dominantRgb` = ? WHERE `id` = ?", arrayOf(rgb, id))
                    }
                }
            }

            private fun tryParseHexToInt(hex: String): Int {
                val s = if (hex.startsWith("#")) hex.drop(1) else hex
                return if (s.length == 6) s.toIntOrNull(16) ?: 0x808080 else 0x808080
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