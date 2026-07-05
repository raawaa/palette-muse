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
    val isLowConfidence: Boolean? = null,

    /**
     * Subject-mask coverage fraction — the fraction of the downscaled pixel
     * area covered by the subject mask at capture time. `null` when no mask
     * was applied (pre-saliency captures and null-mask fallbacks). Added in
     * ADR-0024 / MIGRATION_5_6. Existing rows from earlier migrations keep
     * `maskCoverage IS NULL`.
     */
    val maskCoverage: Double? = null
) {
    val dominantHex: String get() = "#%06X".format(dominantRgb and 0xFFFFFF)
}
