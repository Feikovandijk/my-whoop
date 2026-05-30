---
name: openwhoop-design
description: Use this skill to generate well-branded interfaces and assets for OpenWhoop (an independent, local-first WHOOP 4.0 companion app), either for production or throwaway prototypes/mocks/etc. Contains essential design guidelines, colors, type, fonts, assets, and UI kit components for prototyping the dark, neon-accented recovery-dashboard look.
user-invocable: true
---

Read the `README.md` file within this skill, and explore the other available files.

If creating visual artifacts (slides, mocks, throwaway prototypes, etc), copy assets out and create static HTML files for the user to view. If working on production code, you can copy assets and read the rules here to become an expert in designing with this brand.

Key files:
- `README.md` — product context, content + visual foundations, iconography, index.
- `colors_and_type.css` — all design tokens (import everywhere).
- `assets/openwhoop-mark.svg` — the brand mark.
- `ui_kits/android-app/` — interactive recreation of the Android client; lift its components.
- `preview/` — design-system reference cards.

Core rules to honor: dark-only instrument-panel UI; color always encodes meaning (recovery green/yellow/red, strain & sleep blue); Saira Condensed hero numerals + Archivo UI + JetBrains Mono console; the signature uppercase wide-tracked micro-label; thin animated rings; Material Symbols icons; no emoji; precise, honest, unit-bearing copy.

If the user invokes this skill without any other guidance, ask them what they want to build or design, ask some questions, and act as an expert designer who outputs HTML artifacts _or_ production code, depending on the need.
