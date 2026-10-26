# Architecture

Reel Counter is a local-first Android app: a **React Native (TypeScript) UI** over a
**Kotlin native layer** that does all detection, counting, storage, limits and blocking.
There is no backend, account, sync or analytics, and the release build has no `INTERNET`
permission.

```
 ┌──────────────────────────── React Native (src/) ────────────────────────────┐
 │ screens/ ── components/ ── state/trackerStore (zustand, UI cache only)      │
 │                     │                                                       │
 │      domain/ (pure TS: dates, history aggregation, formatting, drafts)       │
 │                     │                                                       │
 │      native/trackerApi.ts (typed facade, JSON validation, change events)     │
 └─────────────────────┼───────────────────────────────────────────────────────┘
                       │  TurboModule (codegen spec src/native/NativeShortVideoTracker.ts)
 ┌─────────────────────┼─────────────── Kotlin (android/app) ──────────────────┐
 │ bridge/ShortVideoTrackerModule ──► tracking/TrackingCoordinator              │
 │                                        │   (single-threaded, owns all state) │
 │ detection/ShortVideoAccessibilityService ──► PlatformDetectors               │
 │ overlay/OverlayController (floating counter + block screen)                  │
 │ usage/UsageStatsReader   notifications/LimitNotifier                         │
 │ permissions/PermissionStatusProvider                                         │
 │ data/ (Room: TrackerDatabase, DAOs, TrackerRepository = source of truth)     │
 └──────────────────────────────────┬──────────────────────────────────────────┘
                                    │
 ┌───────────────────── android/core (pure Kotlin/JVM) ────────────────────────┐
 │ VideoCountingEngine · PagerTransitionInterpreter · UrlClassifier · DayClock │
 │ LimitEvaluator · WarningPlanner · BlockStateCalculator · SettingsValidator  │
 │ PlatformRegistry (versioned detector definitions)                           │
 └─────────────────────────────────────────────────────────────────────────────┘
```

## Module map

The brief asked for these modules. This is where each one lives:

| Requested module | Implementation |
| --- | --- |
| UsageMonitoringModule | `usage/UsageStatsReader.kt` |
| AccessibilityDetectionModule | `detection/ShortVideoAccessibilityService.kt`, `detection/PlatformDetectors.kt` |
| BrowserDetectionModule | `BrowserAddressBarDetector` in `PlatformDetectors.kt` + `core/engine/UrlClassifier.kt` |
| VpnFilteringModule | **Not implemented on purpose.** See [POLICY_EVALUATION.md](POLICY_EVALUATION.md) |
| VideoSessionEngine | `core/engine/VideoCountingEngine.kt` |
| DailyLimitEngine | `core/engine/Limits.kt`, `core/engine/WarningPlanner.kt`, `core/engine/DayClock.kt` |
| BlockingEngine | `core/engine/Blocking.kt` + `overlay/OverlayController.kt` |
| NotificationModule | `notifications/LimitNotifier.kt` |
| LocalStorageModule | `data/` (Room) |
| PermissionStatusModule | `permissions/PermissionStatusProvider.kt` |

The domain layer (`android/core`) has no Android dependency, so it builds and is unit-tested
on a plain JVM (`cd android && ./gradlew -p core test`).

## Data flow for one video

1. The user swipes in YouTube Shorts. Android delivers several `TYPE_VIEW_SCROLLED` events
   for the Shorts pager.
2. `KnownPagerDetector` checks that the source's view id is the registered Shorts pager and
   that the pager has settled on a single item (`fromIndex == toIndex`, full screen). It emits a
   `VideoObservation(videoKey = "pos:12", confidence = B)`. No text or content is read.
3. `TrackingCoordinator` (one single-threaded dispatcher) passes it to `VideoCountingEngine`.
4. The engine applies the counting rules below. Once the video has been on screen for the
   minimum viewing time, it emits a `CountedVideoEvent` with a deterministic deduplication key.
5. `TrackerRepository.insertCountedEvent` inserts with `ON CONFLICT IGNORE` on the unique
   `deduplication_key` index, in a transaction. A duplicate returns `false` and changes nothing.
6. On a real insert the coordinator recomputes limits, warnings and block state **from the
   database**, posts any new warning, updates overlays, and emits `ShortVideoTrackerChanged`
   to React Native, which refetches. The UI never polls.

## Counting rules (VideoCountingEngine)

1. A count is a distinct video view, never an Android callback. Repeated observations of the
   pending video are suppressed and recorded as `duplicate_callback_suppressed`.
2. A new video counts after it stays on screen for **minimum viewing time** (default 3 s,
   configurable 0–60 s). Shorter views are discarded (`short_view_discarded`).
3. **Replay rule:** returning to a video already counted in the same session (scrolling back
   or replaying) does not count again (`replay_not_counted`). A new session starts after the
   configured gap away from the platform (default 5 min) or when the tracking day rolls over;
   in a new session the same video can count again.
4. Deduplication key = `SHA-256(salt, "dedup-v1", platform, session, videoKey)`. The database's
   unique index makes inserts idempotent. Detectors never replay history, and session ids are
   random per process, so a restart cannot inflate totals.
5. Video identifiers are stored only for stable identities (browser URL ids), and only as
   salted one-way hashes. Pager positions are never stored.
6. Confidence is recorded per event. Verified (A) and estimated (B) totals are kept apart in
   every query, API payload and screen. Time (C) is a separate metric and never becomes a count.

## Day boundaries

`DayClock` maps timestamps to tracking days that start at the configured reset time (for
example 04:00), handling DST through `java.time`. Each event stores its `day_key`. Changing the
reset time applies to new events only. Existing history is not re-bucketed.

## Limits, warnings and blocking

- **LimitEvaluator** evaluates the global limit across enabled platforms and each per-platform
  limit. Video limits use verified counts only, unless the user turns on
  *estimated-count enforcement*. Time limits use measured foreground time (Usage Access for
  apps, the address-bar site timer for browsers).
- **WarningPlanner** produces milestone, approaching (default 80 %), reached and optional
  repeat warnings. Each warning has a stable id (`day|scope|kind|threshold`) stored in
  `warnings_sent` with insert-ignore, so it is sent at most once, even across restarts. When
  several thresholds are crossed at once, only the most significant one per scope is shown.
- **BlockStateCalculator** precedence, which is deterministic:
  1. Essential packages are never blocked. That covers the static list, plus the current
     launcher, dialer and SMS app, plus this app.
  2. A target is triggered when the global limit is reached, or when the limit of its linked
     platform is reached. If any applicable limit is reached, the target is blocked ("most
     restrictive wins").
  3. Suspensions are checked in order: blocking disabled, paused for the day (emergency
     escape), temporary override. Suspensions stop enforcement only; counts are unaffected.
- Block state is never persisted as a flag. It is recomputed from events, limits and overrides
  after every change, on start, at the next reset, when an override expires, and every minute
  while a monitored app is in the foreground (for time limits). That is how it survives process
  death and reboots. The accessibility service is restarted by Android after a reboot.

## Threading

All coordinator state is confined to `Dispatchers.Default.limitedParallelism(1)`, so detector
callbacks, UI commands and timers are serialized. Room calls are `suspend`. Overlays run on the
main thread and read only `@Volatile` snapshots.

## Database

Room with `exportSchema = true` (schemas go to `android/app/schemas/` on first build and
should be committed). There is no destructive migration fallback. Tables: `platforms`,
`video_events`, `activity_daily`, `warnings_sent`, `block_targets`, `block_events`,
`overrides`, `diagnostic_counters`, `settings`. See `data/Entities.kt` for columns.

## Adding a platform detector

1. Add a `PlatformDefinition` to `PlatformRegistry` (package names, detector type, view ids,
   capability level, limitations, `detectorVersion = 1`).
2. If it needs new logic, implement `PlatformDetector` and register it in `DetectorFactory`.
3. Add the package to `<queries>` in `AndroidManifest.xml`.
4. Validate it on a device and record the tested app versions in
   [PLATFORM_CAPABILITIES.md](PLATFORM_CAPABILITIES.md).
5. Bump `detectorVersion` whenever its logic or view ids change.
