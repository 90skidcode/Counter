package com.shortvideotracker.detection

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.shortvideotracker.core.engine.AddressBarClassification
import com.shortvideotracker.core.engine.PagerScrollSignal
import com.shortvideotracker.core.engine.PagerTransitionInterpreter
import com.shortvideotracker.core.engine.UrlClassifier
import com.shortvideotracker.core.engine.VideoObservation
import com.shortvideotracker.core.model.ConfidenceLevel
import com.shortvideotracker.core.model.DetectorType
import com.shortvideotracker.core.model.EvidenceType
import com.shortvideotracker.core.model.PlatformDefinition

/** What a detector extracted from one accessibility event. Contains no third-party content. */
sealed interface DetectorSignal {
    data class Video(val observation: VideoObservation) : DetectorSignal

    /** A browser address bar was classified (used for web timing and domain blocking). */
    data class Address(val platformId: String, val classification: AddressBarClassification) : DetectorSignal
}

data class ScreenSize(val width: Int, val height: Int)

/**
 * One detector per platform. Detectors read only the minimum needed: scroll indices and the
 * bounds/resource id of the scrolled view, or the address-bar text of a browser (classified in
 * memory and immediately discarded).
 */
interface PlatformDetector {
    val definition: PlatformDefinition
    fun onEvent(event: AccessibilityEvent, rootProvider: () -> AccessibilityNodeInfo?, screen: ScreenSize): DetectorSignal?
}

/** Scroll events from a pager whose resource id is known for this app version. */
class KnownPagerDetector(override val definition: PlatformDefinition) : PlatformDetector {
    override fun onEvent(event: AccessibilityEvent, rootProvider: () -> AccessibilityNodeInfo?, screen: ScreenSize): DetectorSignal? {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return null
        val source = event.source ?: return null
        try {
            if (source.viewIdResourceName !in definition.pagerViewIds) return null
            val position = PagerTransitionInterpreter.settledPosition(signal(event, source, screen), requireFullScreen = true)
                ?: return null
            return DetectorSignal.Video(observation(definition, position, EvidenceType.PAGER_POSITION_KNOWN_VIEW))
        } finally {
            recycle(source)
        }
    }
}

/**
 * For apps without stable view ids: any full-screen, full-width pager (RecyclerView or
 * ViewPager) that settles on a new position. Less precise; counts are always estimates.
 */
class GenericVerticalPagerDetector(override val definition: PlatformDefinition) : PlatformDetector {
    override fun onEvent(event: AccessibilityEvent, rootProvider: () -> AccessibilityNodeInfo?, screen: ScreenSize): DetectorSignal? {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return null
        val className = event.className?.toString() ?: return null
        if (PAGER_CLASS_HINTS.none { className.contains(it) }) return null
        val source = event.source ?: return null
        try {
            val bounds = Rect().also { source.getBoundsInScreen(it) }
            if (screen.width <= 0 || bounds.width() < screen.width * 0.9f) return null
            val position = PagerTransitionInterpreter.settledPosition(signal(event, source, screen), requireFullScreen = true)
                ?: return null
            return DetectorSignal.Video(observation(definition, position, EvidenceType.PAGER_POSITION_GENERIC))
        } finally {
            recycle(source)
        }
    }

    private companion object {
        val PAGER_CLASS_HINTS = listOf("RecyclerView", "ViewPager")
    }
}

/**
 * Reads the address bar of a supported browser. Brave for Android has no extension support, so
 * this is the only supported integration available on-device.
 */
class BrowserAddressBarDetector(override val definition: PlatformDefinition) : PlatformDetector {
    private var lastReadAt = 0L

    override fun onEvent(event: AccessibilityEvent, rootProvider: () -> AccessibilityNodeInfo?, screen: ScreenSize): DetectorSignal? {
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return null
        val now = System.currentTimeMillis()
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - lastReadAt < THROTTLE_MILLIS) return null
        lastReadAt = now

        val root = rootProvider() ?: return null
        try {
            for (id in definition.addressBarViewIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                try {
                    val bar = nodes.firstOrNull() ?: continue
                    // While the user is typing, the bar holds a search query, not a page address.
                    if (bar.isFocused) return null
                    val classification = UrlClassifier.classify(bar.text?.toString()) ?: return null
                    return DetectorSignal.Address(definition.id, classification)
                } finally {
                    nodes.forEach(::recycle)
                }
            }
            return null
        } finally {
            recycle(root)
        }
    }

    private companion object {
        const val THROTTLE_MILLIS = 750L
    }
}

object DetectorFactory {
    fun create(definition: PlatformDefinition): PlatformDetector? = when (definition.detectorType) {
        DetectorType.KNOWN_PAGER_VIEW -> KnownPagerDetector(definition)
        DetectorType.GENERIC_VERTICAL_PAGER -> GenericVerticalPagerDetector(definition)
        DetectorType.BROWSER_ADDRESS_BAR -> BrowserAddressBarDetector(definition)
        DetectorType.USAGE_ONLY -> null
    }
}

private fun signal(event: AccessibilityEvent, source: AccessibilityNodeInfo, screen: ScreenSize): PagerScrollSignal {
    val bounds = Rect().also { source.getBoundsInScreen(it) }
    val fraction = if (screen.height > 0) bounds.height().toFloat() / screen.height else 0f
    return PagerScrollSignal(event.fromIndex, event.toIndex, event.itemCount, fraction)
}

private fun observation(definition: PlatformDefinition, position: Int, evidence: EvidenceType) =
    VideoObservation(
        platformId = definition.id,
        sourceDetector = definition.detectorType.code,
        detectorVersion = definition.detectorVersion,
        timestamp = System.currentTimeMillis(),
        videoKey = PagerTransitionInterpreter.videoKeyFor(position),
        confidence = ConfidenceLevel.B_INFERRED,
        evidence = evidence,
    )

@Suppress("DEPRECATION")
private fun recycle(node: AccessibilityNodeInfo) {
    // Nodes are recycled automatically from API 33; recycling is still required below that.
    if (android.os.Build.VERSION.SDK_INT < 33) node.recycle()
}
