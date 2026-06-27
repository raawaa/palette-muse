package com.palettemuse.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
}