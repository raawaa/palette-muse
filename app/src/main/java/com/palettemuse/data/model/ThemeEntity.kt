package com.palettemuse.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "themes")
data class ThemeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val representativeRgb: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val representativeHex: String get() = "#%06X".format(representativeRgb and 0xFFFFFF)
}
