package com.palettemuse.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.palettemuse.data.model.ColorPaletteEntity

@Dao
interface ColorPaletteDao {
    @Query("SELECT * FROM color_palettes WHERE projectId = :projectId ORDER BY role ASC")
    suspend fun getPalettesForProject(projectId: String): List<ColorPaletteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(palettes: List<ColorPaletteEntity>)

    @Query("DELETE FROM color_palettes WHERE projectId = :projectId")
    suspend fun deleteByProjectId(projectId: String)
}
