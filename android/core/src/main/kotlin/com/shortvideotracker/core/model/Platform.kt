package com.shortvideotracker.core.model

enum class PlatformType(val code: String) { APP("app"), BROWSER("browser") }

enum class DetectorType(val code: String) {
    /** Accessibility scroll events from a known, named pager view. */
    KNOWN_PAGER_VIEW("known_pager_view"),

    /** Accessibility scroll events from any full-screen vertical pager in the app. */
    GENERIC_VERTICAL_PAGER("generic_vertical_pager"),

    /** Accessibility read of the browser address bar, classified in memory only. */
    BROWSER_ADDRESS_BAR("browser_address_bar"),

    /** Usage Access foreground time only. */
    USAGE_ONLY("usage_only"),
}

/**
 * Static description of a monitored platform and the honest limits of its detector.
 *
 * The registry is versioned: bump [detectorVersion] whenever detection logic or the view ids
 * it relies on change, so stored events can be audited per detector version.
 */
data class PlatformDefinition(
    val id: String,
    val displayName: String,
    val packageNames: List<String>,
    val platformType: PlatformType,
    val detectorType: DetectorType,
    /** Best confidence this detector can reach when everything works. */
    val capabilityLevel: ConfidenceLevel,
    val detectorVersion: Int,
    /** Resource ids of the full-screen pager that hosts the short-video feed, if known. */
    val pagerViewIds: Set<String> = emptySet(),
    /** Resource ids of the address bar, for browsers. */
    val addressBarViewIds: Set<String> = emptySet(),
    val canDistinguishShortsFromRegular: Boolean,
    val measuresBackgroundPlayback: Boolean = false,
    /** Android cannot block a single feed inside another app; only the whole app. */
    val blockGranularity: BlockGranularity = BlockGranularity.WHOLE_APP,
    val testedAppVersions: String,
    val knownLimitations: List<String>,
)

enum class BlockGranularity(val code: String) { WHOLE_APP("whole_app"), WHOLE_DOMAIN("whole_domain") }
