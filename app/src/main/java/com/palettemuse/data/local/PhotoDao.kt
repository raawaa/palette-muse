package com.palettemuse.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.palettemuse.data.model.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE themeId = :themeId ORDER BY capturedAt DESC, id DESC")
    fun observePhotosForTheme(themeId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE themeId = :themeId ORDER BY capturedAt DESC, id DESC")
    suspend fun getPhotosForThemeOnce(themeId: String): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE themeId = :themeId")
    suspend fun deleteByThemeId(themeId: String)
}
