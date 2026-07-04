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
    val dominantRgb: Int,
    val isSeed: Boolean = false,
    val capturedAt: Long = System.currentTimeMillis(),
    /**
     * Captured-color confidence signals, recorded at the shutter press that
     * produced this photo so a low-confidence verdict can be audited after the
     * fact. See ADR-0019 and CONTEXT.md "Captured color confidence".
     *
     * Nullable: pre-ADR-0019 rows (and test fixtures) carry no signals. The
     * capture path always fills all three for newly-confirmed photos.
     */
    val populationShare: Double? = null,
    val topVsSecondRatio: Double? = null,
    val isLowConfidence: Boolean? = null
) {
    val dominantHex: String get() = "#%06X".format(dominantRgb and 0xFFFFFF)
}
