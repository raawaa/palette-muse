package com.palettemuse.debug

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import com.palettemuse.core.CapturedColor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only dumper for the bitmap that [com.palettemuse.core.ColorAnalyzer]
 * actually sees on the production capture path. Writes the raw bitmap as PNG
 * plus a sidecar JSON with the analyzer's observed signals (hex, share, ratio)
 * so the analyzer's output can be re-derived offline and compared to the
 * in-app verdict.
 *
 * The dumper is self-gated by [ApplicationInfo.FLAG_DEBUGGABLE] — in release
 * builds [dump] is a no-op. This keeps the production path zero-cost without
 * requiring a `BuildConfig.DEBUG` flag (the project disables BuildConfig
 * generation in `app/build.gradle.kts`).
 *
 * The dump is **best-effort**: any IO error is swallowed so a debug helper
 * can never break the user's capture path. See
 * `docs/adr/0015-capture-debug-frame-dumper.md` for the motivation.
 */
@Singleton
class DebugFrameDumper @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Dump [bitmap] and the analyzer's [captured] verdict under
     * `${getExternalFilesDir(null)}/debug-frames/frame-{ISO8601-UTC}-{source}.{png,json}`.
     * No-op when the host application is not debuggable (i.e. release builds).
     * Best-effort: errors are swallowed.
     */
    fun dump(bitmap: Bitmap, captured: CapturedColor, source: String) {
        if (!isDebuggable()) return
        val dir = File(context.getExternalFilesDir(null), DIR_NAME)
        if (!dir.exists() && !dir.mkdirs()) return
        val timestamp = isoTimestamp()
        val base = "frame-$timestamp-$source"
        val pngFile = File(dir, "$base.png")
        val jsonFile = File(dir, "$base.json")
        try {
            FileOutputStream(pngFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
            jsonFile.writeText(
                buildSidecarJson(
                    timestamp = timestamp,
                    source = source,
                    bitmapWidth = bitmap.width,
                    bitmapHeight = bitmap.height,
                    captured = captured,
                )
            )
        } catch (_: Exception) {
            // best-effort: never break the capture path
        }
    }

    private fun isDebuggable(): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private companion object {
        const val DIR_NAME = "debug-frames"
    }
}

// Top-level so the format is JVM-testable without an Android Context.
// Mirrors the ADR-0002 pattern in ColorAnalyzer.computeTopVsSecondRatio.
internal fun isoTimestamp(now: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH-mm-ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(now))

/**
 * Build the sidecar JSON document. Manual string assembly is intentional:
 * this is a debug helper, not a public API surface, and the format only has
 * to round-trip with the offline analysis script — no schema, no versioning.
 *
 * `bitmapWidth` and `bitmapHeight` are passed as primitives (not via
 * [Bitmap.width] / [Bitmap.height]) so this helper is JVM-testable without
 * pulling in the Android `Bitmap` class. Mirrors the ADR-0002
 * top-level-fn testability pattern.
 */
internal fun buildSidecarJson(
    timestamp: String,
    source: String,
    bitmapWidth: Int,
    bitmapHeight: Int,
    captured: CapturedColor,
): String =
    """{"source":"$source","capturedAt":"$timestamp","topHex":"${captured.hex}","populationShare":${captured.populationShare},"topVsSecondRatio":${captured.topVsSecondRatio},"bitmapW":$bitmapWidth,"bitmapH":$bitmapHeight}"""
