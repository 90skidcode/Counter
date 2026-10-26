package com.shortvideotracker.core.engine

/**
 * For pagers that report no positions (YouTube Shorts): a swipe makes the pager hold two pages
 * for a moment. Each such moment, debounced, is one transition to a new video, keyed by a
 * running sequence number.
 *
 * Because no position is known, returning to an earlier video also counts as a transition.
 */
class PagerTransitionTracker(private val debounceMillis: Long = 800) {
    private var sequence = 0
    private var lastTransitionAt = Long.MIN_VALUE / 2

    /** Returns the video key of a new transition, or null when nothing changed. */
    fun onPagerChanged(visiblePages: Int, timestamp: Long): String? {
        if (visiblePages < 2) return null
        if (timestamp - lastTransitionAt < debounceMillis) {
            lastTransitionAt = timestamp
            return null
        }
        lastTransitionAt = timestamp
        sequence += 1
        return "seq:$sequence"
    }
}
