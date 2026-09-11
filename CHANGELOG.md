# Changelog

All notable changes to this project are documented here. See [README.md](README.md) for usage.

The format follows [Keep a Changelog](https://keepachangelog.com/); versions follow [SemVer](https://semver.org/).

## [Unreleased]
- Trunk-based branch model + GitHub Actions CI (test gate + tag-driven release publishing).

## [1.11.1] - 2026-09-11
### Fixed
- **Day-detail span rules restored**: merged spans shorter than **3 minutes** are dropped entirely (not shown,
  not counted); gaps of <=3 minutes merge. Display and daily totals share one function, so the total always
  equals the sum of the visible spans.
- **Negative countdown**: the polling cadence is now anchored to the deadline (it waits the remaining time when
  under 15 s), so a tick always lands at the deadline and the system Chronometer no longer runs past 00:00 into negatives.

## [1.11.0] - 2026-09-11
### Optimized (size / startup / battery / memory)
- **Backup visibility**: a failed write (e.g. the SAF grant was lost) is no longer silent — Settings shows a red
  hint asking to pick the backup folder again. Previously such failures were invisible, looking like "auto backup does nothing".
- **Size**: re-enabled R8 (minify + resource shrinking), dropped useless metadata, kept only arm64/armv7 ABIs.
  APK **8.0 MB → 1.68 MB (-79%)** on a real device (notification layouts and every icon verified present via aapt2).
- **Startup**: WorkManager now initialises on demand (off the Application.onCreate critical path); notification
  channels and the report alarm are armed on a background thread. Cold start **486 ms → 345 ms**.
- **Battery**: adaptive expiry polling — every 15 s when more than 5 s remain, 500 ms near the deadline (was a fixed
  1 s poll), cutting service wake-ups ~15x for long sessions. Exact firing still comes from the EXACT alarm and the
  notification countdown is drawn by the system Chronometer.
- Memory: smaller dex lowers the Java heap footprint (real-device PSS 97 MB → 93 MB; Graphics 53 MB is inherent to Compose).

## [1.10.12] - 2026-09-11
### Changed
- The report period picker now matches the home dropdown: an in-flow full-width panel (expanding pushes the report
  content down) with surface background, dividers, full-width rows and a check mark on the current period, using the
  same animation as home. The Popup overlay is gone.

## [1.10.11] - 2026-09-11
### Fixed
- The notification countdown no longer goes negative: once the deadline has passed while the process was frozen, the
  notification shows a static 00:00 instead of letting the system Chronometer run below zero.
- Day-detail span columns now sit 10dp apart (was 4dp), and the column width uses `widthIn(min = measured)` so text is
  never clipped or covered.
### Added
- The reminder notification ("take a break" / "back to work") now has a **check icon button**: tapping confirms and
  clears that notification without opening the app.

## [1.10.10] - 2026-09-11
### Fixed
- Day-detail spans were clipped: the column width is now **measured at runtime for the current font scale**
  (TextMeasurer) instead of a hard-coded 100dp, so "HH:mm ~ HH:mm" is never cut off and the period label is never
  covered. Gap between the two span columns is 4dp.

## [1.10.9] - 2026-09-11
### Changed
- Day-detail layout: the gap between the period label and the spans grew from 0 to 12dp, and the two span columns now
  use a fixed 100dp width with a 2dp gap instead of each taking half the row (the old layout left a big blank gap for
  short spans). Measured: label to span 11.6dp, span to span 1.8dp.
- Settings: removed all descriptive hint paragraphs (backup / auto-backup / uninstall notes), keeping only section
  headers, controls and status.

## [1.10.8] - 2026-09-11
### Changed
- **Totals and day-detail spans now share one source**: totals are no longer accumulated separately - after each work
  segment the day total equals the sum of that day's per-clock spans merged by the display rule (gaps <= 3 min count as
  continuous), and the stored split threshold now matches the display threshold (3 min). So "grand total == sum of the
  day-detail spans" holds by construction (existing data is recomputed once on upgrade).
- **Deleting a clock cascades**: its focus segments and per-day totals are removed too, so day detail/reports no longer
  show a "deleted" placeholder row.
- **Auto-backup trigger** changed from "work segment finished" to "any data change" (especially totals): a data-table
  heartbeat plus a 20 s quiet period enqueues one backup (same-named WorkManager job coalesces).
- Day-detail period labels (morning/afternoon/...) now use the same text colour as the rest of the card.
### Removed
- The notification-icon cache hint in Settings (and its device-detection code).

## [1.10.7] - 2026-09-11
### Fixed
- **No more two icons side by side**: the app no longer draws its own app icon inside the notification content (added in
  the previous version to keep the icon fresh, it duplicated the system header icon). The header app icon is drawn by
  the system (standard Android 13+ notification, present for every app, not removable) - so "keep only one" means
  not drawing our own.
### Added (Honor/Huawei specific)
- Detects Honor/Huawei (MagicOS/EMUI) and shows an actionable hint in Settings: the system caches the notification icon,
  so after an app update it may still show the old artwork - rebooting or switching the system theme once refreshes it.
- Basis: 10 on-device experiments (transparent/bmp/green-triangle/new-resource small icons, app-icon recolor and icon
  resource rename, setColor, removing the custom layout) had **zero effect** on the system badge, whose colours match the
  old icon exactly - i.e. a system cache the app cannot clear.

## [1.10.6] - 2026-09-11
### Added
- The notification icon is now **rendered by the app itself** (current app icon read at runtime, clipped to a circle), so it
  **updates immediately on app update — no phone reboot needed**. Measured on Honor MagicOS: the icon the system draws in
  the notification comes from its own **cached** app icon, which does not refresh on app update (changing colour/shape/
  resource name, or uninstall+reinstall, had no effect) — only a reboot rebuilds it. We no longer depend on that cache.
- Added a unit test forbidding a notification-owned large icon (avoids duplicating the system header icon).

## [1.10.5] - 2026-09-10
### Fixed
- Two app icons side by side in the notification: removed the extra app-icon bitmap that v1.10.3 injected into the
  notification content (the system already draws the app icon). Added a guard test forbidding a notification-owned
  large icon so future changes cannot re-introduce the duplicate.

## [1.10.4] - 2026-09-10
### Fixed
- Notification/status-bar icon looked nothing like the app icon: the flame artwork now fills ~70% of the
  adaptive-icon safe zone (was 50% of the height). The platform renders the app icon monochromatically in the
  notification area (measured on-device: brand-orange disc + a lighter tint of the flame), so a small flame
  collapsed into a plain orange dot. Enlarged, the notification glyph and the launcher icon now read identically.

## [1.10.3] - 2026-09-10
### Fixed
- Notification icon no longer differs from the app/launcher icon:
  - the notification now embeds the **real app icon bitmap** (from PackageManager, same source as the launcher) and sets it as the large icon;
  - the status-bar small icon ships as **PNG at 5 densities** (white flame silhouette filling 88% of the canvas) instead of relying on vector scaling on some ROMs;
  - the adaptive icon now declares a **monochrome layer** (Android 13+ themed icons / system monochrome rendering), preventing the platform from flattening it into a filled circle with a punched-out shape.

## [1.10.2] - 2026-09-10
### Changed
- Releases are now produced by GitHub Actions: pushing a `v*` tag builds and publishes `EmberTimer-<version>.apk`.
  Signing secrets are configured, so CI artifacts are **properly signed** (same key as local releases, installable as an update).

## [1.10.1] - 2026-09-10
### Fixed
- Settings "Backup" did nothing when the stored SAF grant was lost (e.g. after reinstall) — it failed silently; now success/failure is reported and a folder picker opens to re-grant.
### Changed
- Renamed buttons: "Backup to file/cloud" → "Backup", "Restore from file" → "Restore"; restore now goes through the ViewModel and reports imported rows.
- Day-detail span list: period label in a fixed left column (text only, no chip), spans in two columns, single line (no wrapping).

## [1.10.0] - 2026-09-10
### Added
- Heatmap day detail: **two-column focus-span list**, each span tagged on the left with a coarse period label (late night / early / morning / afternoon / evening).
- Idle notification: the start action is now a **right-side icon button** (custom RemoteViews instead of a text action).

### Fixed
- Notification status-bar icon now uses the **app icon (flame)** — previously a stock system alarm icon — consistent across idle / running / done / placeholder states.

### Changed
- Focus-span display rule replaced by a **display-time merge (gap ≤ 3 min)**; the old "split on pause threshold" no longer decides what you see (storage still records pauses, so gaps > 3 min remain separate entries).

## [1.9.3] - 2026-09-07
### Changed
- Design-system polish: unified `Spacing`/`BarAnim` motion tokens.
- Report trend/slot bars now animate from 0 → target (respects system "disable animations").
- Settings sections card-ized; color-scheme swatches made equal-width.

## [1.9.2] - 2026-09-07
### Fixed
- RemoteViews notification crash: `Space`/bare `View` are not allowed in RemoteViews; replaced with the whitelisted `Chronometer` (weight + `gravity=end`) — start-timer no longer crashes on device.
- Empty-state CTA "Create a clock" now routes to Clock Management instead of Settings.

## [1.9.1] - 2026-09-07
### Added
- Persistent custom notification: icon buttons (Stop | Start/Pause | Skip), countdown on the same row as the phase title (equal width), cycle icon + count; single channel/ID; RemoteViews-safe attributes.
- Report page previous/next period navigation.
- Data export (SAF → JSON) / import (JSON → upsert) in Settings.

## [1.9.0] - 2026-09-07
### Added
- Notification persists on app launch (idle) and switches to timer state on start; single channel/ID.
- GitHub-style heatmap layout (Sunday-first rows, Mon/Wed/Fri labels, month label at the column containing the 1st).

## [1.8.7] - 2026-09-05
- Persistent notification, home repeat icon, button order Stop | Pause/Resume | Skip, Chinese app name "余烬计时", first-launch channels, no "days" clock.

## [1.6.0] - 2026-09-04
- Calmer page transition (pure crossfade); sub-60s focus treated as mis-touch (not accumulated/stored); report lifetime tab renamed "Total"; lifecycle report tab.

## [1.5.0] - 2026-09-03
- Button animation frame-drop fix; redesigned weekly/monthly reports (health-style summary, metrics, time-slot distribution); notification always at most one row.

## [1.0.0] - 2026-09-04
- Full app: 12-item refresh (legend/title/order, clock management page, count-up mode, reports, data inheritance guarantee).

## [0.5.0] - 2026-09-04
- Morph engine (icon path morphing); unified motion/system.

## [0.3.0 - 0.4.0]
- Timer engine (countdown/count-up; cycles); heatmap v2; GitHub Research-informed report structures; motion tokens.
