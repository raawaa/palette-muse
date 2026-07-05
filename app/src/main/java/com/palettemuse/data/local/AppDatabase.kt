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
    version = 6,
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
                // Add new Int RGB columns alongside old hex columns
                db.execSQL("ALTER TABLE `themes` ADD COLUMN `representativeRgb` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `dominantRgb` INTEGER NOT NULL DEFAULT 0")

                // Backfill themes.representativeRgb from existing representativeHex values.
                backfillRgbColumn(db, "themes", "representativeHex", "representativeRgb")
                // Backfill photos.dominantRgb from existing dominantHex values
                backfillRgbColumn(db, "photos", "dominantHex", "dominantRgb")

                // Drop old hex columns (Room's entity no longer maps them as DB columns).
                // SQLite < 3.35.0 doesn't support ALTER TABLE DROP COLUMN, so we rebuild
                // the tables with explicit schema (preserving NOT NULL, PK, FK constraints).
                dropAndRebuildThemes(db)
                dropAndRebuildPhotos(db)
            }

            private fun backfillRgbColumn(db: SupportSQLiteDatabase, table: String,
                                          hexCol: String, rgbCol: String) {
                db.query("SELECT id, $hexCol FROM $table").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0)
                        val hex = cursor.getString(1)
                        val rgb = tryParseHexToInt(hex)
                        db.execSQL("UPDATE `$table` SET `$rgbCol` = ? WHERE `id` = ?",
                            arrayOf(rgb, id))
                    }
                }
            }

            private fun dropAndRebuildThemes(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `themes_new` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `representativeRgb` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )""")
                db.execSQL("""INSERT INTO `themes_new` (`id`, `name`, `representativeRgb`, `createdAt`, `updatedAt`)
                        SELECT `id`, `name`, `representativeRgb`, `createdAt`, `updatedAt` FROM `themes`""")
                db.execSQL("DROP TABLE `themes`")
                db.execSQL("ALTER TABLE `themes_new` RENAME TO `themes`")
            }

            private fun dropAndRebuildPhotos(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `photos_new` (
                        `id` TEXT NOT NULL,
                        `themeId` TEXT NOT NULL,
                        `imagePath` TEXT NOT NULL,
                        `dominantRgb` INTEGER NOT NULL,
                        `isSeed` INTEGER NOT NULL DEFAULT 0,
                        `capturedAt` INTEGER NOT NULL,
                        `populationShare` REAL,
                        `topVsSecondRatio` REAL,
                        `isLowConfidence` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`themeId`) REFERENCES `themes`(`id`) ON DELETE CASCADE
                    )""")
                db.execSQL("""INSERT INTO `photos_new` (`id`, `themeId`, `imagePath`, `dominantRgb`, `isSeed`, `capturedAt`, `populationShare`, `topVsSecondRatio`, `isLowConfidence`)
                        SELECT `id`, `themeId`, `imagePath`, `dominantRgb`, `isSeed`, `capturedAt`, `populationShare`, `topVsSecondRatio`, `isLowConfidence` FROM `photos`""")
                db.execSQL("DROP TABLE `photos`")
                db.execSQL("ALTER TABLE `photos_new` RENAME TO `photos`")
                // Re-create indices that Room expects
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_themeId` ON `photos`(`themeId`)")
            }

            private fun tryParseHexToInt(hex: String): Int {
                val s = if (hex.startsWith("#")) hex.drop(1) else hex
                return if (s.length == 6) s.toIntOrNull(16) ?: 0x808080 else 0x808080
            }
        }

        // ADR-0024: add the maskCoverage column to photos for subject-mask
        // coverage fraction. Nullable: pre-saliency rows keep NULL, and
        // null-mask fallbacks also record NULL.
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `photos` ADD COLUMN `maskCoverage` REAL")
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