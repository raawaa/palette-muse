package com.palettemuse.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class ColorRole { PRIMARY, SECONDARY, ACCENT }

@Entity(
    tableName = "color_palettes",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class ColorPaletteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val role: ColorRole,
    val hexColor: String,
    val semanticName: String,
    val matchPercentage: Int? = null
)
