# ADR 0024: Salient-subject segmentation for captured color (InSPyReNet, post-shutter)

- **Status:** Accepted
- **Date:** 2026-07-05
- **Related:** Issue #47, ADR-0001, ADR-0014, ADR-0017, ADR-0019, ADR-0023 (superseded)

## Context

Issue #47 (vivid subject on a neutral background → wrong captured color) is not
solvable by re-weighting color-family scores. ADR-0023's chroma-boost experiment
proved this on real data: a saturation multiplier on family population cannot
surface a small subject that k-means has already absorbed into a background
cluster. A desktop bake-off (10 photos × 4 methods) then confirmed two things:

1. A modern salient-object-detection model at deployable size fixes the "leaky
   mask" cases. InSPyReNet (Res2Net50, uint8 ONNX, 27.9 MB — under the 50 MB
   budget and reachable from `hf-mirror.com` in a no-GMS environment) took mean
   ΔE from 61 (k-means only) / 53 (u2netp 4.6 MB) to 30 on the 5 photos with
   known expected colors. The app has no Google services, so MLKit is out;
   ONNX→TFLite/NCNN conversion risk is not worth it for a model that is already
   a validated ONNX — ONNX Runtime Mobile runs it as-is.
2. No SOD model fixes the "wrong object" cases. The begonia photo fails on every
   method (ΔE 57+): the user wants a side stem, every model segments the more
   salient central plant. This is an **intent-vs-saliency gap**, not a model
   quality gap. Roughly 1/5 of cases are a hard ceiling regardless of model size.

## Decision

Add a salient-subject segmentation stage (InSPyReNet via ONNX Runtime Mobile) to
the **post-shutter capture path only**. Eight decisions, each resolved under
grilling (`/grill-with-docs`, 2026-07-05):

1. **Mask as pixel filter, not family weight.** k-means runs only on salient
   pixels; "captured color" becomes the dominant of the **subject region**, not
   of the whole photo. Rejected: saliency-as-family-weight — structurally the
   same shape as the dead chroma-boost (a scalar reweighting cannot recover a
   subject k-means has already absorbed).
2. **Confidence signals auto-redefine to mask-internal.** Because the filter is
   pre-k-means, `populationShare` / `topVsSecondRatio` are now measured over the
   subject region when one is locked (coherent with the reported color). A new
   `maskCoverage` signal (mask area fraction) flags degenerate masks. The
   `(0.40, 1.5)` thresholds become calibration seeds pending re-tuning against
   the ADR-0019 corpus.
3. **Migration: clean break.** A nullable `maskCoverage: Double?` column is
   added (MIGRATION_5_6). Old rows keep full-image-defined signals; new rows
   carry mask-internal signals. `maskCoverage IS NULL` is the honest marker for
   pre-saliency rows. Old photos are NOT recomputed — recompute would shift
   `dominantRgb` and invalidate `ThemeMatcher` clustering.
4. **NNAPI everywhere; determinism right-sized.** ADR-0017's cross-device
   bit-determinism is downgraded to **same-device run-determinism**. Rationale:
   this is a single-user app with no cross-device theme sync, so cross-device
   bit-equality is not load-bearing; ADR-0017's actual motivating pain
   (viewfinder jitter) is addressed by same-device run-determinism, which NNAPI
   provides. The on-device spike MUST verify 10× repeated inference on the same
   image is bit-identical; if a vendor driver is non-deterministic, the EMA
   smoother (ADR-0013) must extend to the mask, or the saved path falls back to
   CPU EP. CPU EP is retained for JVM tests.
5. **Post-shutter only.** The viewfinder keeps the current whole-photo behavior;
   saliency runs once per shutter press. This dissolves the viewfinder==shutter
   continuity tension (ADR-0001's resolution invariant holds at the k-means
   input; the two paths legitimately differ because only the shutter applies a
   mask) and gives a one-shot latency budget for the model.
6. **Honest degradation, never blocking.** If the model fails to load, inference
   throws, or the mask is degenerate (`maskCoverage < 0.05` or `> 0.95`), the
   mask is treated as `null` and the pipeline falls back to whole-photo dominant.
   The user never loses a photo to a model fault.
7. **Post-shutter effect visualizes the mask.** Two render modes: mask present →
   subject highlight + color bloom from the subject; mask null → full-frame color
   bloom, no highlight. The effect plays during the shutter→confirm-sheet
   transition, covering inference latency. It cannot distinguish "correct
   subject" from "wrong subject" (begonia) — the intent gap is made visible, not
   hidden. Fine-grained confidence encoding is deferred to v2.
8. **Test seam: mask as a `BooleanArray?` parameter.** `analyzePixels(pixels,
   mask)` stays pure-Kotlin and JVM-testable; the Android-dependent
   `SaliencyMasker` produces the mask from the full-res bitmap and downscales to
   96×96. Three tiers: JVM unit tests (synthetic pixels + masks), JVM diagnostic
   test (10-photo corpus via desktop ORT, ΔE regression baseline), Android
   instrumented test (NNAPI engagement + latency + 10× determinism).
   `useChromaBoost` and `extractCapturedColorLegacy` are removed — the experiment
   is over.

## Consequences

- **APK +~33 MB** (27.9 MB model in `assets/` + ~5 MB ONNX Runtime Mobile).
- **~1/5 of captures will confidently highlight the wrong subject** (intent
  gap). Accepted. A future "swatch confirm" fallback can trigger on this; the
  mask-visualizing effect is its entry point.
- **Amends** ADR-0014 (third signal + mask-internal base), ADR-0017
  (determinism scope), ADR-0019 (schema + semantic break). **Supersedes**
  ADR-0023 (chroma boost).
- **Calibration debt:** the `(0.40, 1.5)` confidence thresholds and the
  `maskCoverage` degeneracy band (0.05, 0.95) are seeds — re-tune against the
  audit corpus once post-integration rows accumulate.
- **Open, gated on the on-device spike:** NNAPI latency on the target device
  (vivo V2502A) and the 10× bit-identical check. Both must pass before the
  instrumented-test tier lands.
