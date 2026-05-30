# OpenWhoop — Design System

A design system for **OpenWhoop**: an independent, open-source, **local-first companion app for a WHOOP 4.0 band**. It reads *your own* biometrics from *your own* strap over Bluetooth LE, decodes them on-device, and keeps the data on hardware you control. The project is an unofficial reverse-engineering effort — **not affiliated with, endorsed by, or sponsored by WHOOP, Inc.** This design system describes and extends the look of OpenWhoop's own client apps; it does not reproduce any WHOOP, Inc. artwork, logo, font, or proprietary UI.

This pack was built to **redesign the Android client** (`android/app/.../MainActivity.kt`) into a more modern, more stats-dense, more "whoop-like" experience while staying true to the project's existing dark, neon-accented aesthetic.

## What OpenWhoop is

- **Hardware:** WHOOP 4.0 band only (other generations use different BLE protocols).
- **Pipeline:** strap → reassemble BLE frames → decode (schema-driven `whoop_protocol.json`) → durable decoded streams on-device → optional bidirectional sync with a self-hosted server (FastAPI + TimescaleDB) that does the heavy metric analysis (recovery, strain, sleep staging, workouts).
- **Clients:** a native iOS app (SwiftUI), an **Android app (Jetpack Compose)** — *the surface this system targets* — and a Mac BLE reference/inspection tool.
- **The viewer is charts-first.** The phone shows the *union of phone-decoded + server-pulled* data. No raw hex in the primary UX. Every sensor metric is viewable with charts.

### App surface (5 tabs — the Android client)

| Tab | Contents |
|---|---|
| **Today** | Recovery ring (% + band color), Day Strain (0–21), last-night sleep one-liner, HRV, Resting HR. Live HR + strap battery when connected. Each card → its calculation/detail sheet. |
| **Sleep** | Efficiency headline, hypnogram, stage breakdown (deep/REM/light/awake), in-sleep signals (RHR/HRV/Resp/SpO₂/Skin-temp), 7-night chart, Smart Alarm. |
| **Trends** | 7/30/90-day line charts for Recovery / HRV / RHR / Strain, rolling live-HR sparkline, chronological day list. |
| **Workouts** | Auto-detected workouts (duration, avg/peak HR, strain, calories, HR-zone breakdown, %HRR). |
| **Device** | BLE connection/bond/battery chips, live HR pulse, server config, curated command panel (sync, haptics, capture IMU, wipe), BLE log console. |

### Metric semantics (drives all color use)

- **Recovery** is a 0–100% readiness score → **green ≥67%, yellow 34–66%, red <34%**. This banding is the most important color rule in the system.
- **Strain** is a 0–21 logarithmic cardiovascular-load scale → **blue/electric**.
- **Sleep** → **blue** family; stages: deep = indigo, REM = purple, light = blue, awake = red.
- **HRV / RHR / Resp / SpO₂ / Skin-temp** are nightly signals shown as supporting stats.

## Sources (for the reader)

This system was reverse-built from a single attached GitHub repository. You may not have access; links retained in case you do. **Explore these repos to build higher-fidelity OpenWhoop designs.**

- **Primary repo:** `Feikovandijk/my-whoop` — https://github.com/Feikovandijk/my-whoop
  - `android/app/src/main/java/com/openwhoop/MainActivity.kt` — the entire current Android Compose UI (the redesign target).
  - `android/app/src/main/java/com/openwhoop/database/Entities.kt` — the data model (`DailyMetricEntity`, sleep/hr/rr/workout fields) that the UI binds to.
  - `docs/specs/2026-05-27-app-ux.md` — the canonical tab/screen map and data contract.
  - `docs/specs/2026-05-23-openwhoop-ios-app-design.md` — the cross-platform app design spec.
  - `android/app/src/main/java/com/openwhoop/sync/MetricExplanation*.kt` — product copy for the "how this is calculated" sheets.
- **Upstream community RE projects** (credited in the repo): `bWanShiTong/openwhoop`, `jogolden/whoomp`.

> The source repo contains **no logo, brand artwork, or custom fonts** — it deliberately avoids WHOOP trademarks. The wordmark, ring mark, and typeface choices in this system are original substitutions for OpenWhoop (see Visual Foundations → Logo and Iconography).

---

## CONTENT FUNDAMENTALS

How OpenWhoop writes copy, derived from the app strings, metric-explanation copy, and docs.

- **Voice: precise, technical, honest, never hype.** The product is built by and for someone who wants the real numbers. Copy states method and limits plainly. Example metric-sheet lines: `Status: …`, `Method: HRR + Edwards TRIMP + logarithmic 0-21 scale`, `Inputs: 8,420 HR samples, 1 exercise session`, `Limit: …`.
- **Intellectual honesty is a brand value.** Uncertain metrics are *labeled* uncertain: "raw / experimental", "Strain is cardiovascular load only until calibrated against WHOOP reference data.", "Profile needed" (when calories can't be computed), "Pending" (recovery cold-start). Never overclaim. Never imply medical validity.
- **Casing:** Screen titles are **Title Case** single words ("Today", "Sleep", "Trends", "Workouts", "Device"). Metric micro-labels are **UPPERCASE with wide tracking** ("RECOVERY", "DAY STRAIN", "LIVE BPM"). Card headings are Title Case ("Sleep Stages Breakdown", "Curated Command Panel"). Body copy is sentence case.
- **Person:** mostly **impersonal / second-person ("you", "your")** when addressing the user ("Wake up silently with a gentle wrist vibration", "Your sleep was highly efficient"). System/technical strings are descriptive, not chatty.
- **Numbers carry units, always.** `62 ms`, `48 bpm`, `7h 32m asleep`, `14.2` (strain, unitless 0–21), `96.8%`, `+0.3°C`, `420 kcal`. Tabular figures; one decimal for strain/HRV-ish, integers for bpm and %.
- **No emoji. No exclamation marks. No marketing adjectives.** The tone is a flight instrument, not a coach cheering you on. (The one allowed warmth is a short, factual sleep/recovery summary sentence.)
- **Acronyms are used freely and assumed known** by the audience: HRV, RHR, RR, SpO₂, BLE, IMU, PPG, SWS, REM, %HRR, TRIMP.

---

## VISUAL FOUNDATIONS

The OpenWhoop look is a **dark athletic instrument panel**: deep cool-black canvas, charcoal cards, and a few vivid, *meaningful* metric colors. Color is never decorative — it always encodes a recovery band, a strain level, a sleep stage, or a connection state.

- **Background & surfaces.** App canvas is a near-black cool tone (`--bg-0 #07090B`). Cards sit one step up in charcoal (`--bg-1 #0E1216`) with a 1px translucent-white hairline (`--line-1 rgba(255,255,255,0.07)`) — *not* a heavy shadow. Inputs and pressed states step up further (`--bg-2`, `--bg-3`). The device log console goes darkest (`--console #0A0C0E`). No light mode — this is a dark-only system.
- **Color vibe.** Cool, electric, high-contrast. Vivid neon-green and electric-blue as the two protagonists, with yellow/red reserved for recovery-band and alert semantics. Imagery is essentially absent — the product is data, not photography; if a hero visual is ever needed it should be a dark, cool, high-contrast data render, never a warm lifestyle photo.
- **Typography.** Hero numbers use **Saira Condensed** (athletic scoreboard numerals, tabular) — recovery %, strain, BPM. UI text/headings/labels use **Archivo** (a sturdy grotesque). Technical/console readouts use **JetBrains Mono**. The signature is the **uppercase, wide-tracked micro-label** above every metric. (Source app used Compose system defaults; these are flagged substitutions.)
- **Numerals.** Always `font-variant-numeric: tabular-nums` so values don't jitter as they update live.
- **Rings & arcs.** The core motif. A thin (≈8–16px) rounded-cap arc on a faint track, filling clockwise from 12 o'clock, colored by the recovery band. Used at three sizes: hero (220px, Today), medium (90px, sleep card), and inline (45px, day rows). Arcs animate in over ~1200ms with an ease-out / `FastOutSlowInEasing` curve.
- **Bars.** Progress and stage bars are rounded (`r 5–6px` track at 6–10px tall) on a faint white track; strain uses a blue→green horizontal gradient fill; sleep stages use solid stage colors; HR-zone bars are a single segmented rail (Z0→Z5 colors).
- **Charts.** Line charts: thin (2px) colored stroke, soft vertical gradient fill beneath (color→transparent, ~20% top), faint horizontal gridlines (`--line-3`), and small filled dots with a bg-colored inner punch. Sparklines are a bare 3px stroke, no fill. Charts are clipped to card bounds.
- **Corner radii.** Cards 20px, tiles 16px, inputs 12px, chips/pills fully round (999px). Generous and modern, never sharp.
- **Borders, not shadows, define cards.** Elevation reads from the 1px hairline + subtle inset highlight; drop shadows are deep and soft only for popovers/sheets (`--shadow-pop`). Bright metrics may carry a colored glow (`--glow-green/blue`) when "live."
- **Chips & badges.** Pill-shaped, a 15%-alpha tint of their semantic color as fill, a 50%-alpha border, and the full-strength color as text ("Connected" green, "Bonded" blue, "Disconnected"/"Unbonded" red/yellow).
- **Buttons.** Primary = solid brand green with near-black text. Secondary = solid blue with white text. Tertiary = charcoal surface + hairline + light-gray text. Destructive = translucent/solid red. Text buttons use the relevant accent color ("How strain is built" in yellow, "Inputs" in blue).
- **Hover / press.** Touch surface: press darkens via stepping to `--bg-3` and/or a brief scale-down (~0.97); the live-HR pulse uses an infinite scale 1.0→1.25 reverse-repeat with fading opacity. Motion is purposeful and short (`--dur-ui 200ms`); entry arcs are the one longer flourish.
- **Transparency & blur.** Used sparingly: tint fills for chips/zones (8–20% alpha), gridlines, radial glow behind the live pulse. No heavy glassmorphism.
- **Layout rules.** Single-column, vertically-scrolling lists of cards with a fixed bottom navigation bar (5 tabs). 16px screen gutters, 16px gap between cards, 20px card padding. Content is dense but breathable; hero element first, supporting stats below.
- **Animation.** Arc fills and bar fills animate on data load (ease-out, ~1200ms). The connection pulse loops. Tab/state changes are quick fades/instant. No bounces, no parallax, no decorative motion.

---

## ICONOGRAPHY

- **System: Material Symbols / Material Icons.** The source Android app uses Jetpack Compose's `androidx.compose.material.icons.filled.*` — i.e. Google's **Material Icons (filled)**. The bottom nav uses `Home` (Today), `Favorite` (Sleep), `DateRange` (Trends), `Star` (Workouts), `Info` (Device). To stay faithful, **use Material Symbols** (filled, rounded) — available from the Google Fonts CDN; no custom icon font exists in the repo.
  - In HTML, load `<link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,1,0" rel="stylesheet">` and render `<span class="material-symbols-rounded">favorite</span>`.
  - This system maps the nav to clearer Symbols where it improves legibility (e.g. `bedtime` for Sleep, `monitoring` for Trends, `exercise`/`fitness_center` for Workouts, `settings` / `watch` for Device) — documented in the UI kit. The redesign uses Material Symbols throughout for stat glyphs (heart, bolt, bed, etc.).
- **No emoji, ever.** Not in copy, not as iconography. (The flight-instrument tone forbids it.)
- **No custom SVG illustration.** The brand has no illustration language. The one original vector asset is the **ring/pulse mark** (`assets/openwhoop-mark.svg`) — a recovery-ring arc with a pulse spike, an original geometric device, not a recreation of any existing logo.
- **Unicode used inline only** for compact units/deltas: `°C`, `±`, `·` (mid-dot separators in stat rows), `→`. Never as decorative icons.

---

## INDEX — what's in this folder

| Path | What it is |
|---|---|
| `README.md` | This file — product context, sources, content + visual foundations, iconography. |
| `colors_and_type.css` | All design tokens: color vars, type roles, radii, spacing, motion. Import this everywhere. |
| `SKILL.md` | Agent-Skills front-matter wrapper so this pack works as a downloadable Claude skill. |
| `assets/openwhoop-mark.svg` | The original ring/pulse brand mark. |
| `preview/` | Design-system cards (color, type, components) rendered for the Design System tab. |
| `ui_kits/android-app/` | The redesigned Android client UI kit — `index.html` (interactive 5-tab prototype) + JSX components. Start at its `README.md`. |

### UI kits
- **`ui_kits/android-app/`** — the modern, stats-dense redesign of the OpenWhoop Android Compose app. Interactive click-through of all five tabs in an Android device frame.

> **Font substitution flagged:** the source app ships no typefaces (Compose system defaults). This system substitutes **Saira Condensed + Archivo + JetBrains Mono** (Google Fonts). If you have brand-preferred fonts, send them and they'll be swapped in.
