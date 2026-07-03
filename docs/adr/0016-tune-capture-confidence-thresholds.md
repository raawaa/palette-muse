# ADR 0016: Tune capture confidence thresholds from production data (reserved)

- **Status:** Reserved
- **Date:** —
- **Related:** Issue #17, ADR-0014, ADR-0015

## Context

ADR-0015 builds the data-collection bridge (the `DebugFrameDumper`). This ADR
consumes that data to calibrate the `CaptureConfidencePolicy` thresholds from their
current seed values `(MIN_POPULATION_SHARE = 0.40, MIN_TOP_VS_SECOND_RATIO = 1.5)` to
values grounded in real production input.

## Decision

*TBD — pending corpus collection.*

When the debug-frame corpus is available, this ADR will:

1. Re-run the `ColorAnalyzer` quantization (k=12, 96×96) on each dumped PNG via the
   offline analysis script and compare the re-derived `populationShare` /
   `topVsSecondRatio` against the sidecar JSON to verify round-trip fidelity.
2. Tag each frame with a human judgement ("clear dominant" / "unclear dominant").
3. Fit the threshold boundary to maximise agreement between `isLowConfidence` and
   the human tags.
4. Replace the `(0.40, 1.5)` constants in `CaptureConfidencePolicy.kt`.
5. Update `CaptureConfidencePolicyTest.kt` with the new boundary values.
6. Update `ColorAnalyzerTest.kt` and `CaptureViewModelTest.kt` as needed.
