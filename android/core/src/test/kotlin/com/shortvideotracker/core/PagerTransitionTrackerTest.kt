package com.shortvideotracker.core

import com.shortvideotracker.core.engine.PagerTransitionTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PagerTransitionTrackerTest {
    @Test
    fun eachSwipeIsOneTransition() {
        val t = PagerTransitionTracker()
        // Timings observed on YouTube 21.42: one two-page event per swipe.
        val keys = listOf(20_177L, 43_006L, 47_119L, 49_379L, 58_438L, 130_783L).map { t.onPagerChanged(2, it) }
        assertEquals(listOf("seq:1", "seq:2", "seq:3", "seq:4", "seq:5", "seq:6"), keys)
    }

    @Test
    fun burstsDuringOneSwipeAreMerged() {
        val t = PagerTransitionTracker()
        assertEquals("seq:1", t.onPagerChanged(2, 0))
        assertNull(t.onPagerChanged(2, 300))
        assertNull(t.onPagerChanged(2, 900)) // still within 800 ms of the previous event
        assertEquals("seq:2", t.onPagerChanged(2, 2_000))
    }

    @Test
    fun singlePageIsNotATransition() {
        assertNull(PagerTransitionTracker().onPagerChanged(1, 0))
    }
}
