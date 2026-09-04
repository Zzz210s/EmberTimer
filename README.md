# EmberTimer

**English | [简体中文](./README.zh-CN.md)**

An Android work/rest cycle timer with a GitHub-style daily focus heatmap. Reliable in the background: exact alarms, kill/reboot self-recovery, cross-midnight bookkeeping.

## Contents

- [Background](#background)
- [Features](#features)
- [Install](#install)
- [Usage](#usage)
- [Architecture](#architecture)
- [Tests](#tests)
- [Acknowledgments](#acknowledgments)
- [License](#license)

## Background

The core problem of pomodoro-style apps is background reliability: lock screen, force-kill, reboot, and midnight crossings must not lose accounting. EmberTimer solves this with a foreground service + exact alarms + a dual-clock engine (monotonic clock for timing, wall clock for reconciliation), and visualizes long-term focus history as a daily heatmap.

## Features

- Work/rest dual durations cycle automatically; keeps running in the background (notification carries a live countdown chronometer, phase progress bar and icon pause/skip/stop actions).
- Full-history daily focus heatmap: virtualized lazy grid with month labels, legend and fused corners; tap any day for a detail card with a per-profile breakdown.
- Multiple named duration profiles, each accumulating its own total.
- Pause/resume/skip/stop; stopping ends the whole cycle and zeroes the loop counter; after a pause, changing the duration restarts immediately with the new one.
- On timeout: sound + vibration alert, stops automatically, no manual dismiss needed.
- Re-opens with automatic reconciliation after a force-kill; resumes timing automatically after a device reboot.
- Daily totals settle on a 60-second cadence with cross-midnight split-day bookkeeping (error <= 60 seconds).
- Material You dynamic color (Android 12+, ember-orange fallback below) with edge-to-edge layout.
- Stroke icon system (Lucide geometry) with a spring play/pause morph, press micro-scale and cycle-badge bump; respects the system "remove animations" setting.

## Install

Requires Android 8.0 (API 26) or newer.

- Download the APK from [Releases](https://github.com/Zzz210s/EmberTimer/releases) (v0.3.0+ is release-signed and installs directly; note that upgrading over a debug-signed v0.2.0 install requires uninstalling first).
- Or build from source:

```bash
git clone https://github.com/Zzz210s/EmberTimer.git
cd EmberTimer
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

For a signed release build: generate a keystore and set the four keys (`storeFile`/`storePassword`/`keyAlias`/`keyPassword`) in `local.properties`; without them the release build falls back to the debug keystore.

## Usage

1. Grant the notification permission on first launch.
2. (Optional) create your own duration profiles on the settings page.
3. Tap "Start" on the home screen to enter the work/rest cycle.
4. Phase endings play a sound and show a notification; pause/skip/stop work both in-app and from the notification.
5. Tap any day on the home-screen heatmap to see its total and per-profile breakdown.

## Architecture

A single-module Compose app with clean layering:

- `timer/` pure Kotlin timing engine (state machine, checkpoint reconciliation, dual-clock recovery) with no Android dependency, JVM-testable.
- `service/` foreground service (event-driven: notifications/alarms/alerts/settlement), exact-alarm scheduling, boot/alarm receivers.
- `data/` Room (profile, daily_total) + DataStore (settings, runtime state).
- `ui/` Compose (Material 3): home (timer card + heatmap), settings.

Timing-correctness design: engine event replay=0 + subscription handshake, a single mutex serializing all driver paths, settlement attribution carried by events (robust to RESET/profile-switch interleaving), drain-aware service teardown.

## Tests

87 unit tests (JVM + Robolectric) covering engine semantics, event policy, receiver gating, and ViewModel contracts:

```bash
./gradlew test
```

## Acknowledgments

- Background-timing reliability design references [adrcotfas/goodtime](https://github.com/adrcotfas/goodtime) (GPL-3.0).
- Heatmap and daily-aggregation data model references [nsh07/Tomato](https://github.com/nsh07/Tomato) (GPL-3.0).

This project is an independent implementation and does not copy their source code (see [NOTICE](NOTICE)).

## License

[GPL-3.0](LICENSE)
