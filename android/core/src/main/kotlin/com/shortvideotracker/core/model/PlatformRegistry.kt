package com.shortvideotracker.core.model

/**
 * Versioned registry of supported platforms. Each platform has its own detector configuration;
 * nothing here assumes one detector works for every app.
 *
 * View ids are third-party implementation details that change without notice. They are listed
 * as candidates and must be re-validated on real devices (see docs/PLATFORM_CAPABILITIES.md).
 */
object PlatformRegistry {

    private const val NOT_VALIDATED = "Not yet validated on a physical device"

    val YOUTUBE_SHORTS = PlatformDefinition(
        id = "youtube_shorts",
        displayName = "YouTube Shorts",
        packageNames = listOf("com.google.android.youtube"),
        platformType = PlatformType.APP,
        detectorType = DetectorType.KNOWN_PAGER_TRANSITIONS,
        capabilityLevel = ConfidenceLevel.B_INFERRED,
        detectorVersion = 2,
        pagerViewIds = setOf("com.google.android.youtube:id/reel_recycler"),
        canDistinguishShortsFromRegular = true,
        testedAppVersions = "YouTube 21.42.243 on Android 17 (motorola edge 60 fusion)",
        knownLimitations = listOf(
            "Counts are inferred from Shorts pager transitions; YouTube exposes neither the position nor the video identity.",
            "Scrolling back to an earlier Short counts it again.",
            "Regular YouTube videos are not counted; only the Shorts pager is observed.",
            "A YouTube UI update that renames the Shorts pager stops detection until the detector is updated.",
            "Background or picture-in-picture playback is not measured.",
        ),
    )

    val INSTAGRAM_REELS = PlatformDefinition(
        id = "instagram_reels",
        displayName = "Instagram Reels",
        packageNames = listOf("com.instagram.android"),
        platformType = PlatformType.APP,
        detectorType = DetectorType.KNOWN_PAGER_VIEW,
        capabilityLevel = ConfidenceLevel.B_INFERRED,
        detectorVersion = 1,
        pagerViewIds = setOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_pager",
        ),
        canDistinguishShortsFromRegular = true,
        testedAppVersions = "Instagram 453.0.0.0.72 on Android 17 (motorola edge 60 fusion)",
        knownLimitations = listOf(
            "Counts are inferred from the Reels viewer position; the reel identity is not exposed.",
            "Reels opened inside the main feed or Stories are not counted.",
            "An Instagram UI update can stop detection until the detector is updated.",
        ),
    )

    val FACEBOOK_REELS = PlatformDefinition(
        id = "facebook_reels",
        displayName = "Facebook Reels",
        packageNames = listOf("com.facebook.katana"),
        platformType = PlatformType.APP,
        detectorType = DetectorType.GENERIC_VERTICAL_PAGER,
        capabilityLevel = ConfidenceLevel.B_INFERRED,
        detectorVersion = 1,
        canDistinguishShortsFromRegular = false,
        testedAppVersions = NOT_VALIDATED,
        knownLimitations = listOf(
            "Facebook does not expose stable view ids; a generic full-screen vertical pager heuristic is used.",
            "Other full-screen vertical pagers in the app (for example Stories) may be counted.",
            "Treat Facebook counts as rough estimates.",
        ),
    )

    val TIKTOK = PlatformDefinition(
        id = "tiktok",
        displayName = "TikTok",
        packageNames = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        platformType = PlatformType.APP,
        detectorType = DetectorType.GENERIC_VERTICAL_PAGER,
        capabilityLevel = ConfidenceLevel.B_INFERRED,
        detectorVersion = 1,
        canDistinguishShortsFromRegular = true,
        testedAppVersions = NOT_VALIDATED,
        knownLimitations = listOf(
            "TikTok view ids are obfuscated; a generic full-screen vertical pager heuristic is used.",
            "Counts are estimates; very fast swipes may be merged into one view.",
        ),
    )

    val BRAVE = PlatformDefinition(
        id = "brave",
        displayName = "Brave (short-video sites)",
        packageNames = listOf("com.brave.browser"),
        platformType = PlatformType.BROWSER,
        detectorType = DetectorType.BROWSER_ADDRESS_BAR,
        capabilityLevel = ConfidenceLevel.A_VERIFIED,
        detectorVersion = 1,
        addressBarViewIds = setOf("com.brave.browser:id/url_bar"),
        canDistinguishShortsFromRegular = true,
        blockGranularity = BlockGranularity.WHOLE_DOMAIN,
        testedAppVersions = NOT_VALIDATED,
        knownLimitations = listOf(
            "Brave on Android does not support browser extensions, so the address bar is read via Accessibility.",
            "Only counts when the address bar shows the full path (for example youtube.com/shorts/<id>).",
            "When the address bar is hidden or shows only the domain, only site activity is known.",
            "Website blocking applies to the whole domain, not a single feed.",
        ),
    )

    val CHROME = PlatformDefinition(
        id = "chrome",
        displayName = "Chrome (short-video sites)",
        packageNames = listOf("com.android.chrome"),
        platformType = PlatformType.BROWSER,
        detectorType = DetectorType.BROWSER_ADDRESS_BAR,
        capabilityLevel = ConfidenceLevel.A_VERIFIED,
        detectorVersion = 1,
        addressBarViewIds = setOf("com.android.chrome:id/url_bar"),
        canDistinguishShortsFromRegular = true,
        blockGranularity = BlockGranularity.WHOLE_DOMAIN,
        testedAppVersions = NOT_VALIDATED,
        knownLimitations = listOf(
            "Chrome often shows only the domain in the address bar; then only site activity is known.",
            "Website blocking applies to the whole domain, not a single feed.",
        ),
    )

    val SNAPCHAT_SPOTLIGHT = PlatformDefinition(
        id = "snapchat_spotlight",
        displayName = "Snapchat",
        packageNames = listOf("com.snapchat.android"),
        platformType = PlatformType.APP,
        detectorType = DetectorType.USAGE_ONLY,
        capabilityLevel = ConfidenceLevel.C_USAGE_ONLY,
        detectorVersion = 1,
        canDistinguishShortsFromRegular = false,
        testedAppVersions = NOT_VALIDATED,
        knownLimitations = listOf(
            "No reliable Spotlight transition signal is known; only app time is reported.",
        ),
    )

    val ALL: List<PlatformDefinition> = listOf(
        YOUTUBE_SHORTS,
        INSTAGRAM_REELS,
        FACEBOOK_REELS,
        TIKTOK,
        SNAPCHAT_SPOTLIGHT,
        BRAVE,
        CHROME,
    )

    private val byPackage: Map<String, PlatformDefinition> =
        ALL.flatMap { p -> p.packageNames.map { it to p } }.toMap()

    fun byId(id: String): PlatformDefinition? = ALL.firstOrNull { it.id == id }

    fun byPackage(packageName: String): PlatformDefinition? = byPackage[packageName]

    val allPackages: Set<String> get() = byPackage.keys
}
