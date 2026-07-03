# ADR 0018: Capture no longer touches MediaStore

- **Status:** Accepted
- **Date:** 2026-07-04
- **Related:** Issue #29 (PRD), Issue #30 (Tracer 1 implementation), ADR-0001

## Context

Original `CameraManager.takePhoto` routed the shutter through
`ImageCapture.takePicture(MediaStore.OutputFileOptions, mainExecutor, OnImageSavedCallback)`.
That path had three independent defects, each observable in production:

1. **Main-thread JPEG decode.** The `OnImageSavedCallback` delivered a
   `MediaStore` `Uri`. The shutter code then ran
   `contentResolver.openInputStream(uri)` + `BitmapFactory.decodeStream`
   on the full-resolution JPEG — measured 100–800 ms freeze on the main
   thread between shutter and the confirm sheet appearing. The user's
   "按下去没反应" complaint lived here.
2. **Two copies on disk.** The same JPEG was written once to MediaStore
   (an orphan copy — no code path read it back) and once to the app's
   `filesDir/captures/` via `BitmapStorage.saveCapture`. A single shutter
   produced three physical artifacts: MediaStore entry, internal JPEG,
   in-memory `Bitmap`.
3. **`dismissPending` leak.** `CaptureViewModel.dismissPending` cleared
   the UI state but did not delete the on-disk JPEG, so a user who
   tapped "重拍" left the discarded capture on disk. This contradicts
   `CONTEXT.md:31` ("A capture the user discards before confirmation
   does not take effect and leaves nothing behind") in spirit if not in
   name — nothing the user *intended* was preserved, but the file
   remained.

The orphan in (2) is the same artifact that (3) failed to delete: the
MediaStore copy was never deleted at all, by any path.

## Decision

Capture is written **only** to the app's internal
`filesDir/captures/capture_{ts}.jpg`. The capture path never touches
`MediaStore`, `EXTERNAL_CONTENT_URI`, or any system gallery.

Concretely, the shutter pipeline is now:

1. `CameraManager.takePhoto(onPhotoTaken, onError)` calls
   `ImageCapture.takePicture(captureExecutor, OnImageCapturedCallback)`
   — no `OutputFileOptions`, no URI.
2. The `OnImageCapturedCallback` runs on a new `captureExecutor` (single-
   threaded, separate from the existing `analysisExecutor` so a pending
   shutter callback does not queue behind an in-flight k-means pass).
3. A new private helper `imageProxyToJpegBitmap(image: ImageProxy): Bitmap?`
   copies the JPEG byte buffer out of the `ImageProxy` and decodes via
   `BitmapFactory.decodeByteArray`, wrapped in the Perfetto section
   `capture.decodeJpeg`. `image.close()` runs in `finally`.
4. The resulting `Bitmap` is delivered to `CaptureViewModel.capturePhoto`,
   where `saveCapture` (`Dispatchers.IO`) and
   `colorAnalyzer.extractCapturedColor` (`Dispatchers.Default`) are kicked
   off as parallel `async` jobs under `withContext(Dispatchers.Default)` —
   total time is `max(save, analyze)` rather than `save + analyze`.
5. `BitmapStorage.saveCapture` is the **single on-disk writer** for
   captures. `BitmapStorage.deleteCapture(path: String)` is the
   symmetric delete, best-effort, IO failures swallowed (stale disk is
   a hygiene problem, never a user-visible error).
6. `CaptureViewModel.dismissPending` snapshots `pendingCapture.imagePath`
   into a local, clears the UI state, then launches
   `bitmapStorage.deleteCapture(pending.imagePath)` — restoring the
   "leaves nothing behind" guarantee for the internal copy.

Perfetto trace sections make the pipeline observable in production
traces: `capture.total` (the outer span on the main thread),
`capture.decodeJpeg` (CameraManager), `capture.save` and
`capture.analyze` (parallel async children), `capture.match`
(themeRepository.findMatchingTheme).

The poster export path (`BitmapStorage.saveToGallery`) still uses
`MediaStore` — that is the explicit, user-initiated way to share a
curated result to the system gallery, and is unrelated to the capture
pipeline.

## Consequences

- `CameraManager.takePhoto` no longer takes a `Context` parameter
  (signature: `(onPhotoTaken: (Bitmap) -> Unit, onError: (Exception) -> Unit)`)
  and has no `MediaStore` strings (verifiable by `grep`).
- `BitmapStorage.saveCapture` is the single on-disk writer for
  captures; `BitmapStorage.deleteCapture` is the symmetric delete.
- `CaptureViewModel.dismissPending` now deletes the internal JPEG after
  clearing the UI state, restoring `CONTEXT.md:31`.
- A new private `captureExecutor` keeps JPEG decode off the main thread;
  frame analysis still uses its own `analysisExecutor`, so the two paths
  don't queue on each other. Both are shut down in `cleanup()`.
- Perfetto trace sections (`capture.total`, `capture.decodeJpeg`,
  `capture.save`, `capture.analyze`, `capture.match`) make the pipeline
  observable in production traces; the `capture.total` span on the main
  thread is the user-perceived-latency measurement.
- `saveCapture` and `extractCapturedColor` now run concurrently —
  total shutter-side work drops from `save + analyze` to `max(save, analyze)`.

## Alternatives considered

- **Keep dual copies (MediaStore + internal) + delete orphan.**
  Rejected: the orphan-cleanup adds complexity (track-then-delete on
  confirm and on dismiss) and the primary win is killing the main-thread
  decode, which this does not address.
- **Keep MediaStore writes, add a `saveToGallery` opt-in plus delete.**
  Rejected: introduces a UX concept ("share this capture to gallery"
  surfaced from the confirm sheet) that is not in scope for this PRD —
  see Issue #29 "Out of Scope: 给 Capture 加「分享到相册」按钮". A
  shared-to-gallery flow is a future-product decision and warrants its
  own ADR.
- **Only optimize the existing path (don't touch MediaStore).**
  Rejected: the main-thread decode is the dominant freeze; tweening its
  duration with a smaller JPEG does not eliminate it, and the dual-
  write and `dismissPending` defects remain. The product-story argument
  against putting captures in the gallery is independent of perf and
  still applies.

## Trade-offs

Captures are now an **App-internal** artifact. This is a deliberate
product choice, not a bug:

- **No system-gallery visibility.** A capture that the user confirms
  into a theme lives at `filesDir/captures/capture_{ts}.jpg`; it never
  appears in Google Photos, Files, or any other app that walks MediaStore.
  The product story treats captures as private working material feeding
  the theme-curation loop, not as artifacts the user is meant to keep in
  their personal gallery. A future maintainer who is tempted to "fix"
  the absence by writing captures to MediaStore should reopen this ADR
  instead.
- **No system-share path from the gallery.** A user cannot right-click-
  share a Capture to another app from the system gallery because no
  system-gallery entry exists. The product's intended share path is
  the **poster export** (`BitmapStorage.saveToGallery`) — a curated
  theme composite the user explicitly opts into sharing. Adding a
  "share this single capture" affordance is explicitly out of scope for
  this PRD and would warrant its own ADR + UI surface.
- **`dismissPending` best-effort delete.** If `bitmapStorage.deleteCapture`
  fails (e.g. file already gone), the error is swallowed. The
  consequence is a stale `filesDir/captures/` entry — wasted disk, not a
  user-visible failure. This is preferred over a confirm-prompt error
  UX on a path the user just dismissed.