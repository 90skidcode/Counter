package com.shortvideotracker.core.engine

/**
 * The minimal facts read from an Android `TYPE_VIEW_SCROLLED` accessibility event. No text,
 * content descriptions or child nodes are involved.
 */
data class PagerScrollSignal(
    val fromIndex: Int,
    val toIndex: Int,
    val itemCount: Int,
    /** Height of the scrolled view relative to the screen height (0..1). */
    val heightFraction: Float,
)

/**
 * Interprets pager scroll events as "the feed settled on item N".
 *
 * A full-screen short-video pager shows exactly one item once a swipe settles, so the event
 * reports `fromIndex == toIndex`. While a swipe is in flight two items are visible and the
 * event is ignored, which prevents counting half-swipes.
 */
object PagerTransitionInterpreter {

    /** Pagers shorter than this are not full-screen feeds (e.g. carousels inside a post). */
    const val MIN_FULL_SCREEN_FRACTION = 0.6f

    fun settledPosition(signal: PagerScrollSignal, requireFullScreen: Boolean): Int? {
        if (signal.itemCount <= 0) return null
        if (signal.fromIndex < 0 || signal.toIndex < 0) return null
        if (signal.fromIndex != signal.toIndex) return null
        if (requireFullScreen && signal.heightFraction < MIN_FULL_SCREEN_FRACTION) return null
        return signal.fromIndex
    }

    fun videoKeyFor(position: Int): String = "pos:$position"
}
