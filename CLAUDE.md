# Palette Muse

Android app for capturing daily aesthetics and curating personal color themes.
Jetpack Compose + Material 3, Hilt, Room, CameraX, Navigation 3.

## Agent skills

### Issue tracker

Issues live as markdown files under `.scratch/<feature>/` (local-markdown tracker; no git remote). External PRs are not a triage surface — there are no PRs. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`) used as-is — no remapping needed. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: one root `CONTEXT.md` + `docs/adr/` (created lazily by `/domain-modeling`; absent for now). See `docs/agents/domain.md`.
