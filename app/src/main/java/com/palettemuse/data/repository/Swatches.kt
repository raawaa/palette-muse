package com.palettemuse.data.repository

import com.palettemuse.data.model.PhotoEntity
import com.palettemuse.data.model.ThemeEntity

/**
 * Builds the swatches (色样) for a theme: its representative color first, then
 * up to two distinct non-representative dominant hexes actually captured into
 * it, in photo insertion order.
 *
 * Per CONTEXT.md, a theme's swatches are "its representative color plus up to
 * two standout colors actually captured into it". Note the current rule uses
 * "insertion-order first-2 distinct" as the operational definition of
 * "standout" — these may diverge once standout semantics are sharpened (see
 * follow-up issue). Pinned as-is here so the existing UI behavior is
 * preserved.
 */
internal fun buildSwatches(theme: ThemeEntity, photos: List<PhotoEntity>): List<String> {
    val distinct = photos.map { it.dominantHex }
        .distinct()
        .filter { it != theme.representativeHex }
    return listOf(theme.representativeHex) + distinct.take(2)
}
