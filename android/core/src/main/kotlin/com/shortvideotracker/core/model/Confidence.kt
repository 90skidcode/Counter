package com.shortvideotracker.core.model

/**
 * How much a detection method (or a single event) can be trusted.
 *
 * Every detector declares the best level it can reach; every stored event records the level
 * it was actually observed at. Aggregations must never silently merge levels.
 */
enum class ConfidenceLevel(val code: String) {
    /** A supported signal exposes a stable identity for a distinct video. */
    A_VERIFIED("A"),

    /** A distinct video transition is inferred from UI signals; the count is an estimate. */
    B_INFERRED("B"),

    /** Only app or website usage is known; individual videos cannot be identified. */
    C_USAGE_ONLY("C"),

    /** The platform cannot be measured with the current permissions or detector. */
    D_UNAVAILABLE("D");

    /** Levels that produce video counts (C and D only ever produce time or nothing). */
    val producesVideoCounts: Boolean get() = this == A_VERIFIED || this == B_INFERRED

    companion object {
        fun fromCode(code: String): ConfidenceLevel =
            entries.firstOrNull { it.code == code } ?: D_UNAVAILABLE
    }
}

/** What kind of evidence produced an event. Stored for auditability, never content. */
enum class EvidenceType(val code: String) {
    /** A stable video identifier parsed from a URL the user is viewing. */
    URL_VIDEO_ID("url_video_id"),

    /** A full-screen pager settled on a new adapter position inside a known view id. */
    PAGER_POSITION_KNOWN_VIEW("pager_known_view"),

    /** A full-screen vertical pager settled on a new position; view id is not known. */
    PAGER_POSITION_GENERIC("pager_generic"),

    /** Foreground time reported by Android Usage Access. */
    USAGE_STATS("usage_stats");

    companion object {
        fun fromCode(code: String): EvidenceType? = entries.firstOrNull { it.code == code }
    }
}

/** Kind of normalized event emitted by the counting engine. */
enum class VideoEventType(val code: String) {
    /** The user moved to a distinct video and stayed past the minimum viewing duration. */
    VIDEO_VIEW("video_view"),
}
