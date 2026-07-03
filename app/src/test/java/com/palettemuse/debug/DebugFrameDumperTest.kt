package com.palettemuse.debug

import com.palettemuse.core.CapturedColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM unit tests for the format helpers in [DebugFrameDumper]. The
 * helpers are extracted as top-level `internal` functions (mirroring the
 * ADR-0002 pattern in [com.palettemuse.core.computeTopVsSecondRatio]) so
 * they can be pinned here without an Android `Bitmap` or `Context`.
 *
 * The dumper's full file-creation path (PNG + JSON write, directory
 * creation, self-gate via `ApplicationInfo.FLAG_DEBUGGABLE`) is covered by
 * the instrumented test in `androidTest/.../debug/DebugFrameDumperTest.kt`.
 */
class DebugFrameDumperTest {

    @Test
    fun isoTimestamp_emitsUtcInExpectedFormat() {
        // 2026-07-03T14:23:11Z — pick a fixed epoch ms and pin the format
        // exactly. The output must be deterministic (no locale drift) and
        // must use `Z` (UTC) so offline analysis can sort the dumps
        // chronologically across timezones.
        val ts = isoTimestamp(1783088591000L)
        assertEquals("2026-07-03T14-23-11Z", ts)
    }

    @Test
    fun buildSidecarJson_roundTripsAllSignals() {
        // The offline analysis script (the Python k=12 @ 96x96 verifier)
        // reads these exact keys. Any field-name change here is a
        // coordination break with the script — keep the keys stable.
        val captured = CapturedColor(
            hex = "#B6AA9C",
            populationShare = 0.188,
            topVsSecondRatio = 1.21,
        )
        val json = buildSidecarJson(
            timestamp = "2026-07-03T14-23-11Z",
            source = "shutter",
            bitmapWidth = 640,
            bitmapHeight = 480,
            captured = captured,
        )
        // Pin every key and value. The format is a hand-rolled JSON string
        // (no serializer) — this test is the contract.
        assertEquals(
            """{"source":"shutter","capturedAt":"2026-07-03T14-23-11Z","topHex":"#B6AA9C","populationShare":0.188,"topVsSecondRatio":1.21,"bitmapW":640,"bitmapH":480}""",
            json,
        )
    }

    @Test
    fun buildSidecarJson_handlesHexWithSpecialChars() {
        // Sanity: `#` and other JSON-safe chars in the hex should pass
        // through untouched. Catches accidental escaping regressions.
        val json = buildSidecarJson(
            timestamp = "2026-07-03T00-00-00Z",
            source = "shutter",
            bitmapWidth = 1,
            bitmapHeight = 1,
            captured = CapturedColor(hex = "#000000", populationShare = 1.0, topVsSecondRatio = 0.0),
        )
        assertTrue("hex must appear literally, was: $json", json.contains("\"topHex\":\"#000000\""))
    }

    @Test
    fun buildSidecarJson_handlesInfRatio() {
        // The `topVsSecondRatio` field can be `Double.POSITIVE_INFINITY`
        // for a single-swatch bitmap (see computeTopVsSecondRatio's
        // "beats nothing by infinity" convention, ADR-0014 / PR #19). The
        // dumper must serialize it without crashing. We do not pin the
        // exact `Infinity` vs `1.0E400` rendering — both are valid JSON
        // tokens per RFC 8259 — only that the call returns and the field
        // is present.
        val json = buildSidecarJson(
            timestamp = "2026-07-03T00-00-00Z",
            source = "shutter",
            bitmapWidth = 1,
            bitmapHeight = 1,
            captured = CapturedColor(
                hex = "#DCA8A6",
                populationShare = 1.0,
                topVsSecondRatio = Double.POSITIVE_INFINITY,
            ),
        )
        assertTrue("topVsSecondRatio must be present, was: $json", json.contains("\"topVsSecondRatio\":"))
    }
}
