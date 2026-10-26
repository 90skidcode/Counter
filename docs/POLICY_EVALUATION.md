# Android capability and policy evaluation

This document records why each Android mechanism is used or not used. It is engineering
analysis, not legal advice. Re-check current Google Play policy before you publish.

## Usage Access (`UsageStatsManager`): **used**

- **Purpose:** whole-app foreground time per monitored app; the input for time limits.
- **Grant:** the user grants it in system settings (`PACKAGE_USAGE_STATS`, a special-access
  permission). The app reads `AppOpsManager` on every check, so a revoked grant is noticed and
  shown as "unknown" rather than zero.
- **Limits:** foreground time is not watching time, and it is whole-app time (all of YouTube).
  It is never converted into a video count.
- **Polling:** queried on demand, cached for 60 s, and refreshed once a minute only while a
  monitored app is in the foreground.
- **Play policy:** `PACKAGE_USAGE_STATS` is allowed for digital-wellbeing use cases with a
  clear in-app explanation.

## AccessibilityService: **used, opt-in, with prominent disclosure**

- **Why it is needed:** no other on-device API reveals video transitions inside third-party
  apps, or the address bar of Brave, which has no extension support. Without it the app is
  limited to time (C) for every platform.
- **Minimisation:**
  - Event types: window state, view scrolled, window content changed. No gestures
    (`canPerformGestures="false"`), no key filtering, no text-editing capabilities.
  - For non-monitored packages, only `event.packageName` of window changes is read, to know
    that a monitored app left the foreground. Nothing else is inspected.
  - For monitored apps: scroll indices, plus the view id and bounds of the scrolled node.
  - For Brave and Chrome: address-bar text, classified in memory and immediately discarded.
    It is skipped while the user is typing in the bar.
  - Nothing read is stored except a salted hash of a URL video id.
  - The service never clicks, scrolls or manipulates other apps. The only global action is
    *Home*, and only when the user taps **Go to home screen** on the block screen.
- **Disclosure:** the in-app card explains what is read and why, and requires the user to
  tick an affirmative consent toggle before the app opens Android's accessibility settings.
  The service description in Android Settings says the same.
- **Not disguised:** `android:isAccessibilityTool="false"`, and the description states plainly
  that Reel Counter is not an assistive tool.
- **Distribution:** Google Play requires apps that use the Accessibility API without being
  accessibility tools to complete the Accessibility API declaration in Play Console, and to
  show a prominent disclosure with consent. Approval is not guaranteed for this use case.
  Plan for either outcome:
  - If approved: ship as is.
  - If rejected: distribute outside Play (direct APK or F-Droid), or ship a Play flavour with
    the service removed. That flavour falls back to Usage Access time only (level C for every
    platform) and app blocking is impossible, because there is no compliant overlay trigger.
    The UI already handles that state, since every platform then reports `needs_accessibility`.

## Overlays: **accessibility overlays, no `SYSTEM_ALERT_WINDOW`**

The floating counter ("on top of all apps") and the block screen use
`TYPE_ACCESSIBILITY_OVERLAY` windows owned by the service, so no "display over other apps"
permission is requested. The block screen does not cover the status bar or system
navigation. Home and recents gestures still work, and phone, dialer, SMS, launcher, settings,
permission controller, keyboards and this app are never blocked.

## Foreground service: **not used**

The accessibility service is bound and kept alive by the system, so a separate foreground
service would add a permanent notification with no benefit. Timers run inside the
coordinator while the process lives. After process death, the system rebinds the service and
the block state is recomputed from the database.

## VpnService: **not used (deferred)**

- A local VPN can block whole domains in every browser.
- It **cannot** count videos: HTTPS hides URL paths, DNS names do not identify videos, and
  inspecting traffic would require installing a certificate and intercepting HTTPS, which this
  project rules out.
- It would also occupy the user's single VPN slot, conflict with real VPNs, and attract
  extra Play review (VpnService policy).
- Domain blocking for Brave and Chrome is already provided through the address bar. Blocking in
  other browsers is the documented gap. If that gap matters later, a DNS-only `VpnService`
  that drops queries for the user's blocked domains (with no traffic inspection) is the
  compliant design to evaluate.

## Things the app must never do (and does not)

- Force-stop other apps, disable their components, or use device-admin or root tricks.
- Prevent the user from disabling the service, revoking permissions or uninstalling.
- Block emergency calls, the dialer, SMS, settings or the system UI.
- Claim to be tamper-proof.
- Send any data off the device. The release manifest has no `INTERNET` permission.
