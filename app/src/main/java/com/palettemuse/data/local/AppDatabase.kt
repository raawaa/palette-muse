package com.palettemuse.data.local

import androidx.room.RoomDatabase

abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun colorPaletteDao(): ColorPaletteDao
}
