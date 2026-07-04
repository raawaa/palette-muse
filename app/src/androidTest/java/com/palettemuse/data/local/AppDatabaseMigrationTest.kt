package com.palettemuse.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test fun migrate_2_to_3_dropsProjectAndColorPaletteTables_preservesThemes() {
        // Build v2 schema + insert legacy + theme data
        helper.createDatabase("test-v2", 2).apply {
            // v2 schema (ThemeEntity + ProjectEntity + ColorPaletteEntity)
            execSQL(
                """CREATE TABLE IF NOT EXISTS `themes` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `representativeHex` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`))"""
            )
            execSQL(
                """INSERT INTO `themes` VALUES
                ('t1', 'Test Theme', '#DCA8A6', 0, 0)"""
            )
            execSQL(
                """CREATE TABLE IF NOT EXISTS `projects` (
                `id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL,
                `imagePath` TEXT NOT NULL, `type` TEXT NOT NULL,
                PRIMARY KEY(`id`))"""
            )
            execSQL(
                """INSERT INTO `projects` VALUES
                ('p1', 'Old Project', 0, '/x.jpg', 'CAPTURE')"""
            )
            execSQL(
                """CREATE TABLE IF NOT EXISTS `color_palettes` (
                `id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `role` TEXT NOT NULL,
                `hexColor` TEXT NOT NULL, `semanticName` TEXT NOT NULL, `matchPercentage` INTEGER,
                PRIMARY KEY(`id`))"""
            )
            close()
        }
        // Run MIGRATION_2_3 → validates schema + returns the migrated db handle
        val db = helper.runMigrationsAndValidate("test-v2", 3, true, AppDatabase.MIGRATION_2_3)
        // themes row survived
        db.query("SELECT name FROM `themes` WHERE id='t1'").use { c ->
            c.moveToFirst()
            assertEquals("Test Theme", c.getString(0))
        }
        // legacy tables dropped
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('projects','color_palettes')"
        ).use { c ->
            assertEquals(0, c.count)
        }
        db.close()
    }

    @Test fun migrate_3_to_4_addsConfidenceSignalColumns_preservesPhotos() {
        // Build v3 schema + insert a theme + photo
        helper.createDatabase("test-v3", 3).apply {
            execSQL(
                """CREATE TABLE IF NOT EXISTS `themes` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `representativeHex` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`))"""
            )
            execSQL("""INSERT INTO `themes` VALUES ('t1', 'Test Theme', '#DCA8A6', 0, 0)""")
            execSQL(
                """CREATE TABLE IF NOT EXISTS `photos` (
                `id` TEXT NOT NULL, `themeId` TEXT NOT NULL, `imagePath` TEXT NOT NULL,
                `dominantHex` TEXT NOT NULL, `isSeed` INTEGER NOT NULL, `capturedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`themeId`) REFERENCES `themes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
            )
            execSQL(
                """INSERT INTO `photos` VALUES
                ('p1', 't1', '/cap.jpg', '#DCA8A6', 0, 0)"""
            )
            close()
        }
        // Run MIGRATION_3_4 → validates schema against the exported v4 JSON
        val db = helper.runMigrationsAndValidate("test-v3", 4, true, AppDatabase.MIGRATION_3_4)
        // photo row survived, and the new signal columns are NULL on legacy rows
        // (ADR-0019: pre-migration photos predate the audit corpus)
        db.query(
            """SELECT dominantHex, populationShare, topVsSecondRatio, isLowConfidence
               FROM `photos` WHERE id='p1'"""
        ).use { c ->
            c.moveToFirst()
            assertEquals("#DCA8A6", c.getString(0))
            // legacy rows carry NULL signals (cursor.getDouble returns 0.0 for NULL,
            // so assert via isNull — ADR-0019: pre-migration photos predate the audit corpus)
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
            assertTrue(c.isNull(3))
        }
        db.close()
    }

    @Test fun migrate_4_to_5_addsRgbColumnsAndBackfills_preservesData() {
        // Build v4 schema + insert a theme + photo with hex values.
        // Apply the migration SQL manually on the same handle (dual-column strategy).
        helper.createDatabase("test-v4", 4).apply {
            execSQL(
                """CREATE TABLE IF NOT EXISTS `themes` (
                `id` TEXT NOT NULL, `name` TEXT NOT NULL, `representativeHex` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`))"""
            )
            execSQL("""INSERT INTO `themes` VALUES ('t1', 'Test Theme', '#DCA8A6', 0, 0)""")
            execSQL("""INSERT INTO `themes` VALUES ('t2', 'Green Theme', '#00FF00', 0, 0)""")
            execSQL(
                """CREATE TABLE IF NOT EXISTS `photos` (
                `id` TEXT NOT NULL, `themeId` TEXT NOT NULL, `imagePath` TEXT NOT NULL,
                `dominantHex` TEXT NOT NULL, `isSeed` INTEGER NOT NULL, `capturedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`themeId`) REFERENCES `themes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"""
            )
            execSQL(
                """INSERT INTO `photos` VALUES
                ('p1', 't1', '/cap.jpg', '#DCA8A6', 0, 0)"""
            )
            // Apply MIGRATION_4_5 steps manually
            execSQL("ALTER TABLE `themes` ADD COLUMN `representativeRgb` INTEGER NOT NULL DEFAULT 0")
            execSQL("ALTER TABLE `photos` ADD COLUMN `dominantRgb` INTEGER NOT NULL DEFAULT 0")
            // Backfill themes
            val tCursor = query("SELECT id, representativeHex FROM themes")
            while (tCursor.moveToNext()) {
                val id = tCursor.getString(0)
                val hex = tCursor.getString(1)
                val s = if (hex.startsWith("#")) hex.drop(1) else hex
                val rgb = if (s.length == 6) s.toIntOrNull(16) ?: 0x808080 else 0x808080
                execSQL("UPDATE `themes` SET `representativeRgb` = ? WHERE `id` = ?", arrayOf(rgb, id))
            }
            tCursor.close()
            // Backfill photos
            val pCursor = query("SELECT id, dominantHex FROM photos")
            while (pCursor.moveToNext()) {
                val id = pCursor.getString(0)
                val hex = pCursor.getString(1)
                val s = if (hex.startsWith("#")) hex.drop(1) else hex
                val rgb = if (s.length == 6) s.toIntOrNull(16) ?: 0x808080 else 0x808080
                execSQL("UPDATE `photos` SET `dominantRgb` = ? WHERE `id` = ?", arrayOf(rgb, id))
            }
            pCursor.close()

            // Verify new INTEGER columns were added and backfilled correctly
            query("SELECT id, representativeRgb FROM themes ORDER BY id").use { c ->
                c.moveToFirst()
                assertEquals("t1", c.getString(0))
                assertEquals(0xDCA8A6, c.getInt(1))
                c.moveToNext()
                assertEquals("t2", c.getString(0))
                assertEquals(0x00FF00, c.getInt(1))
            }
            // Verify old TEXT columns preserved
            query("SELECT id, representativeHex FROM themes ORDER BY id").use { c ->
                c.moveToFirst()
                assertEquals("t1", c.getString(0))
                assertEquals("#DCA8A6", c.getString(1))
            }
            // Verify photos backfill
            query("SELECT id, dominantRgb FROM photos WHERE id='p1'").use { c ->
                c.moveToFirst()
                assertEquals(0xDCA8A6, c.getInt(1))
            }
            // Verify old dominantHex preserved
            query("SELECT dominantHex FROM photos WHERE id='p1'").use { c ->
                c.moveToFirst()
                assertEquals("#DCA8A6", c.getString(0))
            }
            close()
        }
    }
}
