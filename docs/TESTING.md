# Testing and QA

## Automated

| Suite | Command | Covers |
| --- | --- | --- |
| Domain (Kotlin/JVM) | `cd android && ./gradlew -p core test` | counting engine (duplicates, minimum view, replay, sessions, rollover, hashing), pager interpreter, URL classifier, day clock and DST, limits, warnings, block precedence and suspensions, settings validation |
| JS unit + render | `npm test` | date arithmetic, history aggregation (gaps stay "no data"), dashboard model, formatting (unknown ≠ 0), settings drafts, payload validation, app render and onboarding |
| Lint / types | `npm run lint`, `npm run typecheck` | |
| Room (instrumented) | `cd android && ./gradlew connectedDebugAndroidTest` | idempotent insert, clear today, clear all, delete all (salt rotation), retention |
| Build | `cd android && ./gradlew assembleDebug` | Gradle, Room/KSP, codegen, manifest |

## Manual test matrix (run on at least Android 10, 13 and 15)

### Permissions
- [ ] Fresh install → onboarding; no permission is requested until its card is used.
- [ ] Accessibility: **Open Android settings** refuses until the consent toggle is on.
- [ ] Grant, then revoke each permission in system settings, then return: the status updates
      and the dashboard shows "—" / "Unknown" instead of zero.
- [ ] Android 13+: notification permission prompt; *never ask again* opens settings.

### Counting
- [ ] YouTube Shorts: 10 videos × 5 s → +10 estimated. Swipe back twice → no change.
- [ ] Swipes under 3 s are not counted (diagnostics → "Too-short views discarded").
- [ ] Pause on one Short for 2 minutes → still +1.
- [ ] Leave for under 5 min and return to the same Short → not recounted. Over 5 min → +1.
- [ ] Instagram Reels viewer counts; Reels inside the main feed do not.
- [ ] Brave `m.youtube.com/shorts/<id>` → **verified** count. Reload the same URL → no change.
- [ ] Kill the app process mid-session → no jump in totals after restart.
- [ ] Day rollover at the configured reset time → today resets, yesterday appears in history.

### Warnings
- [ ] Milestone 25 fires once; restart the phone → it does not fire again.
- [ ] Approaching (80 %) and limit-reached show the correct count and limit, and mention
      estimates when included.

### Blocking
- [ ] Limit reached with estimated enforcement off and only estimated counts → **no block**,
      and Limits shows the explanatory warning.
- [ ] With enforcement on → opening YouTube shows the pause screen. Back or **Go to home
      screen** returns to the launcher.
- [ ] Brave on `tiktok.com` with tiktok.com blocked → pause screen. Firefox is not covered
      (documented limitation).
- [ ] Override 10 min → app usable, block returns after expiry; the override count is respected.
- [ ] Emergency pause → nothing blocked until reset; it appears in the log.
- [ ] Dialer, SMS, settings, launcher and Reel Counter can never be selected or blocked.
- [ ] Reboot while blocked → the block is restored once the service reconnects.
- [ ] Disabling the accessibility service always works and removes all overlays.

### Floating counter
- [ ] "On short-video apps": visible only in monitored apps. "On top of all apps": visible
      everywhere except Reel Counter. Drag persists position; tap expands.
- [ ] Hidden while the block screen shows.

### Privacy and data
- [ ] Clear today, clear all, and delete all each remove exactly what they say (check Privacy
      → stored summary and History).
- [ ] Export produces a CSV with no identifiers or URLs.
- [ ] `aapt dump permissions app-release.apk` shows no `android.permission.INTERNET`.

### Accessibility of the app itself
- [ ] TalkBack reads counts, badges (with descriptions), progress and chart bars.
- [ ] Font size 200 %: no clipped text; touch targets ≥ 48 dp.
- [ ] Light and dark theme contrast.
