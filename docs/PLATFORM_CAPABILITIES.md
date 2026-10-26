# Platform capability matrix

Confidence levels: **A** verified distinct video · **B** inferred/estimated transition ·
**C** app or site time only · **D** unavailable.

> **Validation status:** none of the detectors below has been validated on a physical device
> yet. View ids of third-party apps are implementation details that change without notice.
> Until a row has a tested version, treat its counts as unverified, even within its level.

| Platform | Package(s) | Detector | Max level | Identify active package | Individual transitions | Survives UI changes | Background playback | Shorts vs regular | Block granularity | Tested Android / app versions |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| YouTube Shorts | `com.google.android.youtube` | Known pager view id (`reel_recycler`, `reel_player_page_container`) | B | Yes | Inferred from pager position | No, breaks if the view id is renamed | No | Yes (only the Shorts pager is observed) | Whole app | Not yet |
| Instagram Reels | `com.instagram.android` | Known pager view id (`clips_viewer_view_pager`, `clips_viewer_pager`) | B | Yes | Inferred in the Reels viewer only | No | No | Yes (Reels viewer only; Reels in feed/Stories not counted) | Whole app | Not yet |
| Facebook Reels | `com.facebook.katana` | Generic full-screen vertical pager | B (rough) | Yes | Inferred heuristically | Partly (no view id dependency, but may match other pagers) | No | No (Stories pager may be counted) | Whole app | Not yet |
| TikTok | `com.zhiliaoapp.musically`, `com.ss.android.ugc.trill` | Generic full-screen vertical pager | B | Yes | Inferred heuristically | Partly | No | Yes (feed is short video) | Whole app | Not yet |
| Snapchat | `com.snapchat.android` | Usage only | C | Yes | No | n/a | No | No | Whole app | Not yet |
| Brave | `com.brave.browser` | Address bar (`url_bar`) via Accessibility | A when the URL carries an id, otherwise C | Yes | Yes, when the address bar shows `…/shorts/<id>`, `/reel/<id>`, `/@u/video/<id>` | No, breaks if the address-bar id changes | No | Yes, by URL path | Whole domain | Not yet |
| Chrome | `com.android.chrome` | Address bar (`url_bar`) via Accessibility | A/C as Brave | Yes | Only when the full URL is shown (Chrome often shows only the domain) | No | No | Yes, by URL path | Whole domain | Not yet |
| Other browsers | — | None | D | — | — | — | — | — | Not blocked | — |

## Notes per question

- **Active package:** comes from Accessibility window events. Usage Access gives foreground
  time even without the accessibility service.
- **Transitions:** none of these apps exposes a supported API for "the user moved to a new
  video". Pager position is the least invasive signal available. It uses no text, titles or
  content descriptions.
- **UI changes:** the detector version is stored with every event, and *Detection diagnostics*
  shows the last signal per detector, so a silent breakage shows up as "Last signal: never" or
  as a gap in the diagnostics.
- **Background / picture-in-picture playback:** not measured on any platform.
- **Feed-level blocking:** Android offers no supported way to block only the Shorts or Reels
  tab of another app. Blocking always covers the whole app or the whole website.

## Brave on Android

- Brave for Android does **not** support browser extensions, so an extension-based integration
  is impossible on-device.
- A local VPN with DNS filtering could block whole domains in every browser. It could not count
  videos (HTTPS hides paths), so it would add no counting ability. It is not implemented; see
  [POLICY_EVALUATION.md](POLICY_EVALUATION.md).
- The supported integration is reading the Brave address bar through the accessibility service.
  The text is classified in memory by `UrlClassifier` and discarded. Only the platform, the
  confidence and a salted hash of the video id are stored.

## How to validate a detector

1. Install the target app version, then enable monitoring and the detection service.
2. Watch 20 Shorts or Reels, holding each for 5 s, and swipe back twice.
3. Expected result: 20 counted, 2 `replay_not_counted` in diagnostics, and no growth from
   scrolling while paused.
4. Record the device, Android version, app version and result in the table above, and set
   `testedAppVersions` in `PlatformRegistry`.
