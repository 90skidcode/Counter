package com.shortvideotracker.core

import com.shortvideotracker.core.engine.PagerScrollSignal
import com.shortvideotracker.core.engine.PagerTransitionInterpreter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PagerTransitionInterpreterTest {
    @Test
    fun settledPagerReportsPosition() {
        assertEquals(4, PagerTransitionInterpreter.settledPosition(PagerScrollSignal(4, 4, 20, 1f), requireFullScreen = true))
    }

    @Test
    fun midSwipeIsIgnored() {
        assertNull(PagerTransitionInterpreter.settledPosition(PagerScrollSignal(4, 5, 20, 1f), requireFullScreen = true))
    }

    @Test
    fun smallCarouselIsIgnoredWhenFullScreenRequired() {
        val signal = PagerScrollSignal(2, 2, 10, 0.3f)
        assertNull(PagerTransitionInterpreter.settledPosition(signal, requireFullScreen = true))
        assertEquals(2, PagerTransitionInterpreter.settledPosition(signal, requireFullScreen = false))
    }

    @Test
    fun invalidIndicesAreIgnored() {
        assertNull(PagerTransitionInterpreter.settledPosition(PagerScrollSignal(-1, -1, 10, 1f), true))
        assertNull(PagerTransitionInterpreter.settledPosition(PagerScrollSignal(0, 0, 0, 1f), true))
    }
}
