package com.palettemuse.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.palettemuse.data.model.PhotoEntity

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE themeId = :themeId ORDER BY capturedAt DESC, id DESC")
    suspend fun getPhotosForThemeOnce(themeId: String): List<PhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity)
}
