package com.palettemuse.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.palettemuse.data.model.ThemeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThemeDao {
    @Query("SELECT * FROM themes ORDER BY updatedAt DESC")
    fun getAllThemes(): Flow<List<ThemeEntity>>

    @Query("SELECT * FROM themes WHERE id = :id")
    suspend fun getTheme(id: String): ThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(theme: ThemeEntity)

    @Update
    suspend fun update(theme: ThemeEntity)

    @Query("UPDATE themes SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, name: String, updatedAt: Long)

    @Query("UPDATE themes SET representativeHex = :hex, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateColor(id: String, hex: String, updatedAt: Long)

    @Query("DELETE FROM themes WHERE id = :id")
    suspend fun deleteById(id: String)
}
