# OpenWhoop — Android App UI Kit

A modern, stats-dense redesign of the **OpenWhoop Android client** (the Jetpack Compose app in `Feikovandijk/my-whoop` → `android/app/.../MainActivity.kt`). This is a high-fidelity, interactive **recreation** of the app's look and flows in HTML/React — cosmetic, not production code. It reuses the project's own dark, neon-accented visual language and pushes it toward a more "whoop-like" instrument-panel feel.

## Run it
Open `index.html`. It renders the app inside an Android device frame with a working 5-tab bottom nav. State (tab, strap connection, live HR) is real React state; biometric numbers are mock data (`data.js`) shaped exactly like the app's `DailyMetricEntity` + workout model.

## The five tabs (matches the source app's screen map)
- **Today** — hero recovery ring (band-colored), recovery-driver strip, Day Strain (0–21) with delta, last-night sleep, and a dense recovery-signals grid (HRV / RHR / Resp / SpO₂ / Skin temp).
- **Sleep** — efficiency headline + **hypnogram**, stage breakdown bars, in-sleep signals grid, 7-night bar chart, Smart Alarm toggle.
- **Trends** — 7/30/90-day range + metric selector, big gradient line chart with AVG/MIN/MAX/LATEST strip, all-metric small-multiples grid, 24h heart-rate stream, daily log list with inline recovery rings.
- **Workouts** — weekly summary (count / avg strain / time / calories) + auto-detected workout cards; tap to expand the HR-zone breakdown (%HRR, peak, HRmax).
- **Device** — connection/bond/battery chips, animated live-HR pulse, hardware + storage info, ingest-server fields, curated command panel (sync / haptics / capture IMU / wipe), and a live BLE log console.

## Interactions to try
- Tap the bottom nav to switch tabs.
- On **Device**, tap **Disconnect / Connect & bond** — the live-HR pulse, Today's connection chips, and the log console all respond.
- On **Trends**, switch range (7/30/90) and metric (Recovery/Strain/HRV/RHR); tap a small-multiple to focus it.
- On **Workouts**, tap a card to expand its zone breakdown.
- On **Sleep**, toggle the Smart Alarm.
- Command buttons on **Device** append realistic frames to the log console.

## Files
| File | Role |
|---|---|
| `index.html` | Mounts React + Babel + Material Symbols, loads everything in order. |
| `data.js` | Mock biometrics (30 days, sleep stages, workouts, HR stream) on `window.OW`. |
| `components.jsx` | Shared primitives: `Ring`, `Stat`, `Card`, `MicroLabel`, `Bar`, `ZoneRail`, `Chip`, `LineChart`, `Sparkline`, `BottomNav`, `Screen`. |
| `TodayScreen.jsx` `SleepScreen.jsx` `TrendsScreen.jsx` `WorkoutsScreen.jsx` `DeviceScreen.jsx` | One file per tab. |
| `app.jsx` | Root: tab + strap state, live-HR wander, device frame. |
| `android-frame.jsx` | Device bezel / status bar / gesture nav (starter component, dark mode). |

All visuals pull tokens from the repo-root `colors_and_type.css`. Icons are **Material Symbols (Rounded, filled)** — faithful to the source app's Compose Material Icons.

> Fidelity note: this recreates the app's structure and visual system, not every string or edge state. Numbers are illustrative mock data. Fonts are substituted (Saira Condensed / Archivo / JetBrains Mono) — see the root README.
