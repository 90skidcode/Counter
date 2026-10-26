# Privacy policy: Reel Counter

_Last updated: 2026-10-26_

**Short version:** everything stays on your phone. There is no account, server, analytics,
advertising or sync. The release app does not request internet access.

## What is stored (on this device only)

| Data | Contents | Why |
| --- | --- | --- |
| Counted video events | platform, timestamp, tracking day, session id, confidence level, evidence type, detector and version, deduplication key; for browser videos only, a salted one-way hash of the video id | Daily, weekly and monthly counts; preventing double counting; auditing detection quality |
| Daily activity | per platform and day, the foreground milliseconds (Usage Access) or time on short-video sites (browsers) | Time totals and time limits |
| Settings, limits, block list | your configuration | App behaviour |
| Warnings sent | warning id, day, threshold | Never repeating a warning |
| Block and override log | day, target, event type, count, times | Showing what was blocked and when you overrode it |
| Diagnostic counters | per day and platform, counts such as "duplicate callbacks ignored" | Troubleshooting |
| Install salt | random value | Making hashes unlinkable across devices; replaced when you delete all data |

## What is never stored or sent

Messages, passwords, screen contents, screenshots, UI trees, video titles, creators, captions,
thumbnails, page contents, full URLs or browsing history. Nothing is ever transmitted.

## The detection service (Android accessibility)

Off by default; you turn it on in Android Settings after an in-app explanation. It reads only:

- which app is in the foreground;
- in the short-video apps you monitor, the scroll position of their video feed;
- in Brave or Chrome, the address-bar text, which is checked in memory against short-video URL
  patterns and your blocked websites, and then discarded.

It never taps, scrolls or types for you.

## Backups

The database is excluded from Android cloud backup and device-to-device transfer
(`data_extraction_rules.xml`, `allowBackup="false"`).

## Your controls

**More → Privacy & data** lets you view what is stored, export a CSV report (shared only where
you choose), clear today, clear all history, or delete all data. You can also pause monitoring
or blocking there. History older than the retention period (default 365 days) is deleted
automatically. Uninstalling removes everything.
