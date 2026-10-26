# Reel Counter: short-video tracker and blocker for Android

Reel Counter counts the short videos you watch (YouTube Shorts, Instagram Reels, Facebook
Reels, TikTok, and short-video pages in Brave or Chrome), shows a **floating counter on top
of your apps**, warns you at thresholds you choose, and can pause selected apps or websites
when you reach your daily limit.

Everything stays on the device. There is no account, cloud or analytics, and the release build
has no internet permission.

> **Honest by design.** Android does not let any app see every video perfectly. Each count is
> labelled **Verified** (stable video id), **Estimated** (transition inferred from the app's
> feed) or **Time only**, and unknown values are shown as "—", never as 0.
> See [docs/PLATFORM_CAPABILITIES.md](docs/PLATFORM_CAPABILITIES.md).

## Features

- Today dashboard: verified vs estimated videos, app/site time, limit progress, remaining
  allowance, coverage, last detection, blocking state.
- Per-platform cards with confidence level, status, known limitations and block granularity.
- History: days, weeks, months and a calendar, with platform filters, keeping verified and
  estimated separate.
- Limits: global and per-platform video and time limits, milestones, an "approaching" warning,
  optional repeat warnings, a reset time, and an opt-in to enforce limits on estimated counts.
- Blocking: whole apps (installed short-video apps only) and whole domains (via Brave or
  Chrome), with temporary overrides, an emergency "pause until reset" and an auditable log.
  Essential apps are never blocked.
- Floating counter: off, on short-video apps, or on top of all apps. Drag to move, tap for
  details.
- Diagnostics: permission and detector status, last signal, duplicate/short/replay counters,
  and recent events with minimal metadata.
- Privacy and data: what is stored, CSV export, clear today, clear all, delete everything.

## Architecture

React Native 0.86 (TypeScript, new architecture, TurboModule via codegen) for the UI.
Kotlin for detection, counting, Room storage, limits, warnings and blocking. A pure-Kotlin
domain module (`android/core`) holds all counting and limit rules and is unit-tested on the JVM.
Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

```
src/                 React Native app (screens, components, domain helpers, typed native API)
android/core/        Framework-independent domain engine + tests (JVM)
android/app/         Android app: accessibility service, overlays, Room, bridge module
docs/                Architecture, capability matrix, policy evaluation, privacy, QA plan
```

## Build and run

Requirements: Node ≥ 22.11, JDK 17, Android SDK 36 and NDK 27.1 (Android Studio), and a device
or emulator. Expo Go is **not** supported, because the app needs custom native code.

```sh
npm install
npm start               # Metro
npm run android         # builds and installs the debug app
```

Then, in the app: onboarding → **Permissions** → grant Usage Access, turn on the detection
service (after reading the disclosure), and allow notifications.

### Checks

```sh
npm run lint && npm run typecheck && npm test      # JS
npm run test:core                                   # Kotlin domain tests (JVM)
cd android && ./gradlew assembleDebug               # full Android build
cd android && ./gradlew connectedDebugAndroidTest   # Room tests on a device/emulator
```

## Permissions

| Permission | Used for | Without it |
| --- | --- | --- |
| Usage Access | whole-app foreground time, time limits | time shown as unknown |
| Accessibility service (opt-in, disclosed) | video transitions, Brave/Chrome address bar, floating counter, block screen | no counts, no overlay, no blocking |
| Notifications | threshold warnings | in-app status only |
| VPN | **not used**. See [docs/POLICY_EVALUATION.md](docs/POLICY_EVALUATION.md) | n/a |

## Status and known gaps

- Detectors rely on third-party view ids and **have not yet been validated on physical
  devices**. Use *Detection diagnostics* and the steps in
  [docs/PLATFORM_CAPABILITIES.md](docs/PLATFORM_CAPABILITIES.md) to validate them.
- Google Play requires an Accessibility API declaration for non-assistive apps, and approval
  is not guaranteed. See [docs/POLICY_EVALUATION.md](docs/POLICY_EVALUATION.md) for the fallback.
- Website blocking covers Brave and Chrome only, at whole-domain granularity.
- Blocking is a self-control aid, not tamper-proof: you can always disable the service.

Privacy policy: [docs/PRIVACY.md](docs/PRIVACY.md) · QA plan: [docs/TESTING.md](docs/TESTING.md)
