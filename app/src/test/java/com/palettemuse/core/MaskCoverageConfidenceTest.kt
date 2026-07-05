package com.palettemuse.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the [CaptureConfidencePolicy] mask-coverage degeneracy arm
 * (ADR-0024). A mask covering < 5% or > 95% of the pixel area is
 * degenerate and triggers low-confidence regardless of the share/ratio
 * signals.
 */
class MaskCoverageConfidenceTest {

    private val policy = CaptureConfidencePolicy()

    @Test
    fun `null maskCoverage is not degenerate`() {
        // Pre-saliency rows have null maskCoverage — the classic two-signal
        // test applies without a degeneracy penalty.
        val c = captured(share = 0.75, ratio = 5.0, maskCoverage = null)
        assertFalse(policy.isLowConfidence(c))
    }

    @Test
    fun `valid maskCoverage in range is not degenerate`() {
        val c = captured(share = 0.75, ratio = 5.0, maskCoverage = 0.5)
        assertFalse(policy.isLowConfidence(c))
    }

    @Test
    fun `maskCoverage just above min threshold is not degenerate`() {
        val c = captured(share = 0.75, ratio = 5.0, maskCoverage = 0.06)
        assertFalse(policy.isLowConfidence(c))
    }

    @Test
    fun `maskCoverage just below max threshold is not degenerate`() {
        val c = captured(share = 0.75, ratio = 5.0, maskCoverage = 0.94)
        assertFalse(policy.isLowConfidence(c))
    }

    @Test
    fun `maskCoverage exactly at min threshold is not degenerate`() {
        // Strictly < min, so exactly at 0.05 is okay
        val c = captured(share = 0.75, ratio = 5.0, maskCoverage = 0.05)
        assertFalse(policy.isLowConfidence(c))
    }

    @Test
    fun `maskCoverage exactly at max threshold is not degenerate`() {
        // Strictly > max, so exactly at 0.95 is okay
        val c = captured(share = 0.75, ratio = 5.0, maskCoverage = 0.95)
        assertFalse(policy.isLowConfidence(c))
    }

    @Test
    fun `maskCoverage below min is degenerate even with high share and ratio`() {
        val c = captured(share = 0.90, ratio = 10.0, maskCoverage = 0.04)
        assertTrue(policy.isLowConfidence(c))
    }

    @Test
    fun `maskCoverage above max is degenerate even with high share and ratio`() {
        val c = captured(share = 0.90, ratio = 10.0, maskCoverage = 0.96)
        assertTrue(policy.isLowConfidence(c))
    }

    @Test
    fun `degenerate maskCoverage combined with low signals is still low`() {
        val c = captured(share = 0.25, ratio = 1.0, maskCoverage = 0.02)
        assertTrue(policy.isLowConfidence(c))
    }

    @Test
    fun `maskCoverage at zero is degenerate`() {
        val c = captured(share = 0.90, ratio = 10.0, maskCoverage = 0.0)
        assertTrue(policy.isLowConfidence(c))
    }

    @Test
    fun `maskCoverage at one is degenerate`() {
        val c = captured(share = 0.90, ratio = 10.0, maskCoverage = 1.0)
        assertTrue(policy.isLowConfidence(c))
    }

    private fun captured(share: Double, ratio: Double, maskCoverage: Double? = null) =
        CapturedColor(
            rgb = 0x000000,
            populationShare = share,
            topVsSecondRatio = ratio,
            maskCoverage = maskCoverage
        )
}
