# Changelog

All notable changes to this project are documented here. See [README.md](README.md) for usage.

The format follows [Keep a Changelog](https://keepachangelog.com/); versions follow [SemVer](https://semver.org/).

## [Unreleased]
- Trunk-based branch model + GitHub Actions CI (test gate + tag-driven release publishing).

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
