export const en = {
  'app.name': 'Reel Counter',

  'common.unknown': 'Unknown',
  'common.never': 'Never',
  'common.save': 'Save',
  'common.cancel': 'Cancel',
  'common.retry': 'Retry',
  'common.done': 'Done',
  'common.on': 'On',
  'common.off': 'Off',
  'common.loading': 'Loading…',
  'common.errorTitle': 'Something went wrong',
  'common.openSettings': 'Open Android settings',
  'common.noLimit': 'No limit',

  'duration.lessThanMinute': '< 1 min',
  'duration.minutes': '{n} min',
  'duration.hours': '{n} h',
  'duration.hoursMinutes': '{h} h {m} min',
  'relative.justNow': 'just now',
  'relative.minutesAgo': '{n} min ago',
  'relative.hoursAgo': '{n} h ago',
  'relative.daysAgo': '{n} d ago',

  'confidence.A.label': 'Verified',
  'confidence.A.description':
    'Each video is identified by a stable id, so every count is a distinct video.',
  'confidence.B.label': 'Estimated',
  'confidence.B.description':
    'Moving to a new video is inferred from the app’s feed. Counts are estimates.',
  'confidence.C.label': 'Time only',
  'confidence.C.description':
    'Only time in the app or site is known. Individual videos are not counted.',
  'confidence.D.label': 'Unavailable',
  'confidence.D.description':
    'This platform cannot be measured with the current permissions.',

  'monitoring.active': 'Monitoring',
  'monitoring.needs_accessibility': 'Needs detection service',
  'monitoring.needs_usage_access': 'Needs Usage Access',
  'monitoring.usage_only': 'App time only',
  'monitoring.disabled': 'Turned off',
  'monitoring.paused': 'Monitoring paused',
  'monitoring.not_installed': 'Not installed',

  'tabs.today': 'Today',
  'tabs.platforms': 'Platforms',
  'tabs.history': 'History',
  'tabs.limits': 'Limits',
  'tabs.more': 'More',

  'onboarding.title': 'Understand your short-video habits',
  'onboarding.intro':
    'Reel Counter counts the short videos you watch in apps like YouTube Shorts, Instagram Reels, Facebook Reels and TikTok, and in Brave or Chrome — and helps you stick to limits you choose.',
  'onboarding.measuresTitle': 'What it measures',
  'onboarding.measures':
    'Distinct short videos where a reliable signal exists, time spent in those apps, and which counts are verified versus estimated.',
  'onboarding.privacyTitle': 'Your data stays on this phone',
  'onboarding.privacy':
    'No account, no cloud, no analytics. History is stored only on this device and the release app has no internet permission. You can delete everything at any time.',
  'onboarding.limitsTitle': 'What it cannot do',
  'onboarding.limits':
    'Android does not let any app see every video perfectly. Most counts are estimates inferred from the app’s feed, and some platforms only report time. A YouTube or Instagram update can temporarily stop detection.',
  'onboarding.permissionsTitle': 'Permissions, one at a time',
  'onboarding.permissions':
    'Each permission is explained before you grant it, and the app keeps working with fewer features if you skip one.',
  'onboarding.blockingTitle': 'Blocking is a limit you set',
  'onboarding.blocking':
    'When your limit is reached, Reel Counter can cover selected apps or websites with a pause screen. It blocks whole apps or whole sites, never phone or essential apps, and you can always override, pause or turn it off.',
  'onboarding.continue': 'Set up permissions',
  'onboarding.finish': 'Go to dashboard',
  'onboarding.skip': 'Skip for now',

  'permissions.title': 'Permissions',
  'permissions.intro':
    'Grant only what you want to use. Status is checked again every time you return to this screen.',
  'permissions.status.granted': 'Granted',
  'permissions.status.not_granted': 'Not granted',
  'permissions.status.enabled_not_running': 'Enabled, not running',
  'permissions.status.not_used': 'Not used',
  'permissions.status.requires_accessibility': 'Needs detection service',
  'permissions.usage.title': 'Usage Access',
  'permissions.usage.purpose':
    'Lets Reel Counter read how long each short-video app was open today. This is whole-app time — it is not a video count.',
  'permissions.usage.without':
    'Without it: app time is shown as unknown and time limits cannot be enforced.',
  'permissions.accessibility.title': 'Short-video detection service',
  'permissions.accessibility.purpose':
    'An Android accessibility service, used only to: notice when you swipe to a new video in the apps you monitor (by reading the position of their video feed), read the Brave or Chrome address bar to recognise short-video pages and blocked sites, and show the floating counter and pause screen on top of other apps.',
  'permissions.accessibility.notStored':
    'It does not read messages, passwords, titles or page contents, stores nothing it reads, never taps or scrolls for you, and Reel Counter is not an assistive tool.',
  'permissions.accessibility.without':
    'Without it: no video counts, no floating counter and no blocking. App time from Usage Access still works.',
  'permissions.accessibility.consent':
    'I understand what this service reads and want to turn it on',
  'permissions.accessibility.consentRequired':
    'Please confirm you understand what the service reads before opening Android settings.',
  'permissions.notifications.title': 'Notifications',
  'permissions.notifications.purpose':
    'Used only for the milestone and limit warnings you configure.',
  'permissions.notifications.without':
    'Without it: warnings appear only inside the app and on the floating counter.',
  'permissions.notifications.request': 'Allow notifications',
  'permissions.vpn.title': 'VPN',
  'permissions.vpn.purpose':
    'Not used. Website blocking works through the supported browsers’ address bar, so no network traffic is ever routed through this app.',
  'permissions.overlay.title': 'Floating counter',
  'permissions.overlay.purpose':
    'Shown on top of other apps by the detection service; no separate “display over other apps” permission is needed.',

  'dashboard.title': 'Today',
  'dashboard.verified': 'Verified videos',
  'dashboard.estimated': 'Estimated videos',
  'dashboard.time': 'Time in short-video apps & sites',
  'dashboard.timeNote':
    'App time covers the whole app (for example all of YouTube), not only Shorts.',
  'dashboard.limit': 'Daily limit',
  'dashboard.remaining': 'Remaining',
  'dashboard.remainingBasisVerified': 'Counting verified videos only',
  'dashboard.remainingBasisAll': 'Counting verified and estimated videos',
  'dashboard.limitReached': 'Your daily limit has been reached.',
  'dashboard.estimatesExcluded': '{n} estimated videos are not counted toward your limit. Most apps only allow estimates.',
  'dashboard.countEstimates': 'Count estimated videos toward my limit',
  'dashboard.noCounts':
    'Video counts are unavailable until the detection service is on.',
  'dashboard.coverage': 'Detection coverage',
  'dashboard.coverageValue': '{active} of {total} platforms monitored',
  'dashboard.lastDetection': 'Last detection',
  'dashboard.blocking': 'Blocking',
  'dashboard.blockingOff': 'Off',
  'dashboard.blockingIdle': 'On — nothing blocked yet',
  'dashboard.blockingActive': '{n} blocked until {time}',
  'dashboard.blockingSuspended.override': 'Override active until {time}',
  'dashboard.blockingSuspended.paused_for_day': 'Paused until reset',
  'dashboard.blockingSuspended.disabled': 'Off',
  'dashboard.byPlatform': 'By platform',
  'dashboard.monitoringPaused': 'Monitoring is paused in settings.',
  'dashboard.fixPermissions': 'Finish setup',
  'dashboard.resetAt': 'Day resets at {time}',

  'platforms.title': 'Platforms',
  'platforms.intro':
    'Each platform has its own detector. Counts are only as good as the signal Android exposes.',
  'platforms.videos': 'Videos',
  'platforms.time': 'Time',
  'platforms.confidence': 'Confidence',
  'platforms.status': 'Status',
  'platforms.lastEvent': 'Last detected',
  'platforms.blocking': 'Blocking',
  'platforms.blockWholeApp': 'Whole app only',
  'platforms.blockWholeDomain': 'Whole website only',
  'platforms.limitations': 'Known limitations',
  'platforms.tested': 'Tested versions',
  'platforms.monitor': 'Monitor this platform',
  'platforms.timeSource.usage_stats': 'whole-app time',
  'platforms.timeSource.web_site_timer': 'time on short-video sites',

  'history.title': 'History',
  'history.day': 'Days',
  'history.week': 'Weeks',
  'history.month': 'Months',
  'history.calendar': 'Calendar',
  'history.allPlatforms': 'All',
  'history.videos': 'Videos',
  'history.time': 'Time',
  'history.verified': 'Verified',
  'history.estimated': 'Estimated',
  'history.noData': 'No data',
  'history.empty':
    'No history yet. Counts appear here once detection is running.',
  'history.average': '{n} per day on average',
  'history.weekOf': 'Week of {day}',

  'limits.title': 'Limits',
  'limits.global': 'All platforms',
  'limits.videoLimit': 'Daily video limit',
  'limits.timeLimit': 'Daily time limit (minutes)',
  'limits.perPlatform': 'Per-platform limits',
  'limits.perPlatformHelp':
    'Optional. A platform is blocked when its own limit or the global limit is reached, whichever comes first.',
  'limits.warnings': 'Warnings',
  'limits.milestones': 'Milestones (videos)',
  'limits.milestonesHelp': 'Comma-separated, for example 10, 25.',
  'limits.approaching': 'Warn when this share of a limit is used (%)',
  'limits.repeat': 'After the limit, warn again every N videos',
  'limits.enforceEstimated': 'Use estimated counts for limits',
  'limits.enforceEstimatedHelp':
    'Most platforms only provide estimated counts. When this is off, video limits and blocking use verified counts only, so they may never trigger. Time limits always use measured app time.',
  'limits.reset': 'Day starts at',
  'limits.saved': 'Limits saved.',
  'limits.notSaved': 'Not saved — please fix the highlighted values.',
  'limits.invalidNumber':
    'Enter a whole number greater than 0, or leave empty for no limit.',
  'limits.blockingNote':
    'Blocking is {state}. Configure blocked apps and sites under More → Blocked apps & sites.',

  'blocking.title': 'Blocked apps & sites',
  'blocking.enable': 'Block when a limit is reached',
  'blocking.howTitle': 'How blocking works',
  'blocking.how':
    'When a limit is reached, opening a selected app shows a pause screen on top of it. Websites are recognised from the Brave or Chrome address bar and the whole domain is paused. Other browsers are not covered. This is not tamper-proof: it is a tool for limits you choose.',
  'blocking.apps': 'Apps',
  'blocking.appsEmpty': 'No supported short-video apps are installed.',
  'blocking.domains': 'Websites',
  'blocking.addDomain': 'Add a website (e.g. tiktok.com)',
  'blocking.add': 'Add',
  'blocking.invalidDomain': 'That does not look like a website domain.',
  'blocking.state': 'Current state',
  'blocking.nothingBlocked': 'Nothing is blocked right now.',
  'blocking.blockedUntil': 'Blocked until {time}',
  'blocking.reason.global': 'global limit',
  'blocking.reason.platform': '{name} limit',
  'blocking.override': 'Temporary override ({n} min)',
  'blocking.overrideUsed': '{used} of {max} overrides used today',
  'blocking.overrideActive': 'Override active until {time}',
  'blocking.cancelOverride': 'End override now',
  'blocking.pause': 'Emergency: pause blocking until reset',
  'blocking.pauseConfirm':
    'Pause all blocking until the day resets? This is logged locally.',
  'blocking.paused': 'Blocking is paused until reset.',
  'blocking.log': 'Today’s log',
  'blocking.logEmpty': 'No block events today.',
  'blocking.overrideError.overrides_disabled':
    'Temporary overrides are turned off in settings.',
  'blocking.overrideError.override_limit_reached':
    'You have used all overrides for today.',
  'blocking.essential':
    'Phone, messages, launcher, settings and this app can never be blocked.',

  'diagnostics.title': 'Detection diagnostics',
  'diagnostics.service': 'Detection service',
  'diagnostics.connected': 'Connected',
  'diagnostics.disconnected': 'Not connected',
  'diagnostics.detectors': 'Detectors',
  'diagnostics.lastSignal': 'Last signal',
  'diagnostics.counters': 'Today’s event counters',
  'diagnostics.recent': 'Recent counted events',
  'diagnostics.recentNote':
    'Only platform, time, confidence and detector are kept — never titles, ids or content.',
  'diagnostics.noEvents': 'No events yet.',
  'diagnostics.metric.video_counted': 'Videos counted',
  'diagnostics.metric.duplicate_callback_suppressed':
    'Duplicate callbacks ignored',
  'diagnostics.metric.short_view_discarded': 'Too-short views discarded',
  'diagnostics.metric.replay_not_counted': 'Scroll-backs not recounted',
  'diagnostics.metric.duplicate_event_rejected': 'Duplicate events rejected',
  'diagnostics.metric.event_dropped': 'Events dropped',
  'diagnostics.database': 'Database schema version {n}',

  'privacy.title': 'Privacy & data',
  'privacy.stored': 'Stored on this device',
  'privacy.storedList':
    '• Counted video events: platform, time, confidence, detector, session id and a salted one-way hash (never titles, creators, captions, thumbnails or URLs)\n• Daily app or site time per platform\n• Your limits, block list and settings\n• Warnings sent, block and override log\n• Detection counters for diagnostics',
  'privacy.notStored': 'Never stored or sent',
  'privacy.notStoredList':
    '• Messages, passwords, screen contents, screenshots\n• Video titles, thumbnails, page contents, full browsing history\n• Anything sent to a server — there is no server',
  'privacy.summary':
    '{events} video events · {activity} time records · first day {first}',
  'privacy.retention': 'History is kept for {n} days.',
  'privacy.export': 'Export report (CSV)',
  'privacy.exportNote':
    'Creates a text report of daily totals and opens the Android share sheet. Nothing is sent unless you choose a destination.',
  'privacy.clearToday': 'Clear today’s data',
  'privacy.clearTodayConfirm':
    'Delete all of today’s counts, time and warnings?',
  'privacy.clearAll': 'Clear all history',
  'privacy.clearAllConfirm':
    'Delete all history? Settings and your block list are kept.',
  'privacy.deleteAll': 'Delete all local data',
  'privacy.deleteAllConfirm':
    'Delete everything, including settings and your block list? This cannot be undone.',
  'privacy.deleted': 'Deleted.',
  'privacy.controls': 'Controls',
  'privacy.policy': 'Privacy policy',
  'privacy.policyText':
    'Reel Counter has no account, no backend and no analytics. All information it records is stored in a database on this device, excluded from Android cloud backup, and can be deleted here at any time. The release app does not request the internet permission. The detection service reads only scroll positions of the configured apps’ video feeds and the address bar of supported browsers; that text is classified in memory and discarded. Video identifiers are reduced to salted one-way hashes. Uninstalling the app removes all of its data.',

  'settings.title': 'Settings',
  'settings.general': 'General',
  'settings.monitoring': 'Monitoring',
  'settings.monitoringHelp':
    'Turn off to stop counting without revoking permissions.',
  'settings.warnings': 'Warnings',
  'settings.blocking': 'Blocking',
  'settings.theme': 'Theme',
  'settings.theme.system': 'System',
  'settings.theme.light': 'Light',
  'settings.theme.dark': 'Dark',
  'settings.counter': 'Floating counter',
  'settings.counter.off': 'Off',
  'settings.counter.monitored_apps': 'On short-video apps',
  'settings.counter.all_apps': 'On top of all apps',
  'settings.counterHelp': 'Drag it to move; tap it for details.',
  'settings.detection': 'Detection',
  'settings.minView': 'Minimum viewing time (seconds)',
  'settings.minViewHelp':
    'A video counts only after it stays on screen this long.',
  'settings.sessionGap': 'New session after (minutes away)',
  'settings.sessionGapHelp':
    'Scrolling back to a video in the same session does not count again.',
  'settings.blockingSection': 'Blocking',
  'settings.overrideMinutes': 'Temporary override length (minutes, 0 = off)',
  'settings.maxOverrides': 'Overrides per day',
  'settings.data': 'Data',
  'settings.retention': 'Keep history for (days)',
  'settings.about': 'About',
  'settings.aboutText':
    'Reel Counter {version}. Android only. Local-only storage.',
  'settings.saved': 'Settings saved.',

  'more.title': 'More',
  'more.blocking': 'Blocked apps & sites',
  'more.permissions': 'Permissions',
  'more.diagnostics': 'Detection diagnostics',
  'more.privacy': 'Privacy & data',
  'more.settings': 'Settings',
} as const;

export type StringKey = keyof typeof en;
