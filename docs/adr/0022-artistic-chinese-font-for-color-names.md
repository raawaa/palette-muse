# ADR 0022: Artistic Chinese font for color-name display

- **Status:** Accepted
- **Date:** 2026-07-04
- **Related:** Issue #45

## Context

Color names (`theme.name`) displayed throughout the app — home cards, theme
detail header, capture pill/confirm sheet, and exported posters — are Chinese
phrases drawn from the zhongguose traditional color corpus (e.g. "茉莉黄",
"烟灰", "丁香紫"). These names are poetic and culturally rich, but they are
currently rendered with Latin fonts:

| Location | Current font |
|----------|-------------|
| Home cards | PlusJakartaSans (Latin sans-serif → CJK system fallback) |
| Theme detail header | PlayfairDisplay (Latin serif → CJK system fallback) |
| Capture pill/confirm | Material default body (PlusJakartaSans) |
| Exported poster | Android default SERIF (Canvas) |

None of these fonts were designed for Chinese characters, leaving CJK glyphs
to the system fallback (typically Noto Sans CJK on modern Android). The
result is visually inconsistent with the app's design aspirations and
doesn't reflect the artistic nature of the color names.

The reporter requested using 汇文明朝体 (Huiwen-mincho), an open-source
Chinese serif font, to give the color names a more artistic character.

## Decision

**Use 汇文明朝体 (Huiwen-mincho) for all `theme.name` text rendering.**

Specific rules:

1. **Scope** — Only `theme.name` (the human-readable color name).
   All other UI text retains the existing type system (PlusJakartaSans for
   body, PlayfairDisplay for English headings).

2. **Chinese vs English** — When `theme.name` contains Chinese characters
   (the `"zh"` locale path in `ColorNamer`), render with 汇文明朝体.
   English color names (the `"en"` locale path) keep their existing font.
   In practice the zhongguose dataset always produces Chinese names, so
   this is a defensive distinction.

3. **Mixed text** — When `theme.name` appears inline with surrounding text
   (e.g. `"归入【${themeName}】？"` on the capture confirm sheet, or
   `"${target.name} ${target.matchPct}% Match"` in the target pill), use
   `AnnotatedString` so only the name segment uses the artistic font and
   the surrounding UI text keeps its default.

4. **Font weight** — Only Regular (400) is available. Use it as-is; no
   synthetic bold.

5. **Subsetting** — The original `汇文明朝体.otf` is ~24 MB. Subset it to
   only the CJK characters that appear in the 526 zhongguose color names
   (~300–500 unique characters) plus Basic Latin punctuation. The target
   size is 1–3 MB. This can be done via `pyftsubset` from fonttools.

6. **Integration** — The subset `.otf` is placed in `res/font/` and
   declared as a `FontFamily` in `Type.kt`. Compose `Text` composables
   reference it by `fontFamily`. For the Canvas-based `PosterRenderer`,
   load the typeface via `ResourcesCompat.getFont()` and apply it to
   the `Paint` object.

## Alternatives considered

- **Keep system CJK fallback** — Zero cost, but the status quo is the
  problem being fixed. The fallback is not a designed choice.
- **Use Noto Serif CJK** — Google's official CJK serif, also open-source
  and widely available. It is more conservative/"official" in feel, which
  doesn't match the app's poetic/artistic tone. 汇文明朝体 was specifically
  requested and is more distinctive.
- **Use a variable/weight-rich font** — 汇文明朝体 only has Regular.
  A font with more weights would give design flexibility, but no comparable
  artistic Chinese font offers multiple weights at this quality. Single
  weight is an acceptable constraint.

## Consequences

- **APK size** — Increases by ~1–3 MB (subset) vs ~24 MB (full). Subsetting
  is essential.
- **Character coverage** — If a user renames a theme to a Chinese name
  containing a character not in the subset, it will render in the system
  fallback (not missing-glyph tofu) because Android's font fallback chain
  handles this gracefully.
- **Canvas path (PosterRenderer)** — Requires `ResourcesCompat.getFont()`
  which is an async API on API < 28; the poster renderer targets API 26+,
  so a synchronous fallback path using `Typeface.create(font, ...)` is
  needed for the pre-28 case.
