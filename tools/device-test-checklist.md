# Saliency feature — device verification checklist (vivo V2502A)

Run through each block in order. Each item is a `ready-for-*` issue's
"demoable on device" acceptance gate. Tick (✓) or mark (✗) with notes.

**APK:** `app/build/outputs/apk/debug/app-debug.apk` (125 MB)
**Install:** `./gradlew installDebug` (or `adb install -r <apk>`)

---

## Block A — animation visual (#52, #53, #54) ← do this first, 5 min

These are the most visible regressions and the fastest to verify.

### A1. Scanning phase covers inference latency (#54)
- [ ] **Cold start:** uninstall, reinstall, open camera, press shutter
      → white radar-pulse appears **within ~1 frame** of the shutter press
      (no frozen viewfinder, no perceptible delay)
- [ ] The pulse loops (concentric expanding rings) for the duration of
      mask inference — visible as a clear "working" indicator
- [ ] **Warm capture:** take a second photo immediately → scanning pulse
      is shorter (warm inference is faster than cold)

### A2. Bloom renders correctly, no dim pulse (#53)
- [ ] After the scanning phase, a **vivid colored bloom** fills the
      screen and contracts — the extracted color is clearly visible
- [ ] **No dim-pulse artifact:** the screen does NOT flash dim
      (previous bug: full-screen black overlay at 0.3 alpha)
- [ ] The bloom's color matches the captured photo's dominant
- [ ] The bloom is **centered, full-viewport** — not a tiny 240dp box

### A3. Two modes are visibly distinct (#52, #53)
- [ ] **Normal photo** (vivid subject on neutral background):
      - Glow ring appears + bloom
      - This is `SUBJECT_LOCKED` mode
- [ ] **Force fallback** (no subject, e.g. a uniform-color wall):
      - Whole-frame bloom, **no** glow ring
      - This is `FALLBACK` mode
- [ ] Both modes are visibly different

### A4. Confirm sheet does NOT overlap the animation (#53)
- [ ] The confirm sheet (target theme + accept/dismiss) appears
      **after** the extraction animation completes, not during it
- [ ] The sheet **fades in** (AnimatedVisibility), not a hard cut

---

## Block B — subject-mask pipeline (#50, #51) ← 10 min

### B1. Subject color is the subject's, not the background's (#50)
- [ ] Photograph a vivid object on a neutral surface (e.g. red box on
      a beige table) → captured color is the **object's** red, not
      the table's beige
- [ ] Photograph an outfit in front of a wall → captured color is the
      clothing, not the wall
- [ ] This is the issue #47 fix — the user-expected behavior

### B2. Capture never blocks (#50, #51)
- [ ] If you cover the camera lens, capture still completes (no
      mask → fallback to whole-photo, no freeze, no crash)
- [ ] If the model fails to load (try with network disabled, or after
      clearing app data) → capture still works (whole-photo fallback)

### B3. "Unclear color" flag still works (#48)
- [ ] Photograph a genuinely ambiguous scene (cluttered, no clear
      dominant) → confirm sheet shows the "颜色不太明显，要重拍吗？"
      hint, not silently accepting a wrong color

### B4. Persisted photos still match themes (#48 / #49)
- [ ] Photos captured before this build are visible in the gallery
      and match their themes (no reshuffling of the collection)

### B5. Instrumented test on real device (#51) — if you have time
- [ ] Run:
      ```
      ./gradlew connectedDebugAndroidTest --tests "com.palettemuse.core.SubjectMaskProviderInstrumentedTest"
      ```
- [ ] Latency assertion passes (< 2000 ms on NNAPI)
- [ ] 10× same-device bit-determinism check passes
      (if it fails, the prescribed fallback — EMA on mask OR CPU EP for
      the saved path — must be implemented before #51 can close)

---

## Block C — determinism + size verification (#51) ← 5 min

### C1. Same-device bit-determinism
- [ ] Take the same photo 10 times in a row → all 10 capture the
      **same** color (in `tools/subject_diagnostic.txt` via the JVM
      gate, OR visually in the gallery)
- [ ] If colors vary, NNAPI is not bit-deterministic on this device;
      this is the prescribed fallback trigger

### C2. APK size
- [ ] Note the installed app size in Settings → Apps → Palette Muse
- [ ] Compare to a previous build (or the spec's "~+33 MB" budget)
- [ ] If it grew much more than expected, the ONNX runtime is the
      first thing to check (it's the bulk of the delta)

---

## Known limitations to watch for (don't file as bugs)

- **~1/5 of captures** will confidently highlight the *wrong* subject
  (intent-vs-saliency gap — e.g. a salient background object steals
  the mask). The mask-visualizing animation is the v2 fix; for now,
  re-shoot.
- **Thin-stem subjects** (e.g. a single flower stem against sky) often
  have the mask miss entirely — the captured color will be the
  background. The diagnostic shows this as the "玫红 begonia 茎"
  case (ΔE baseline 60).
- **Very large or very small subjects** (> 95% or < 5% of frame)
  fall back to whole-photo by design (the degeneracy band 0.05–0.95
  in `CaptureConfidencePolicy`).

---

## Reporting

When you finish, please share:
1. Block A: any visual regressions or misbehavior
2. Block B1: which photos got the right color, which didn't
3. Block B5: instrumented test results (if run)
4. Block C1: determinism — same color 10× or not?
5. Block C2: APK size

The diagnostic dump at `tools/subject_diagnostic.txt` will also
update when you run the JVM test (uses pre-computed masks generated
by `tools/saliency_prototype/gen_masks.py`).
