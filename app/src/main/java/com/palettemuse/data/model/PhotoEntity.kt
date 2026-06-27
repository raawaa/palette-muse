package com.palettemuse.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "photos",
    foreignKeys = [ForeignKey(
        entity = ThemeEntity::class,
        parentColumns = ["id"],
        childColumns = ["themeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("themeId")]
)
data class PhotoEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val themeId: String,
    val imagePath: String,
    val dominantHex: String,
    val isSeed: Boolean = false,
    val capturedAt: Long = System.currentTimeMillis()
)
