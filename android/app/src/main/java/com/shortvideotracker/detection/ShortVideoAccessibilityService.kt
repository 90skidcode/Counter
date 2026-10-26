package com.shortvideotracker.detection

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.shortvideotracker.core.engine.BlockTargetKind
import com.shortvideotracker.core.model.PlatformRegistry
import com.shortvideotracker.core.model.PlatformType
import com.shortvideotracker.overlay.OverlayController
import com.shortvideotracker.tracking.TrackingCoordinator

/**
 * Observes configured short-video apps and browsers to infer video transitions, and shows the
 * floating counter and block screen on top of other apps.
 *
 * Privacy constraints (see docs/PRIVACY.md):
 * - For packages that are not monitored, only the package name of window-change events is read,
 *   to know when a monitored app leaves the foreground. Nothing else is inspected.
 * - For monitored apps only scroll indices, view ids and bounds of the scrolled view are read.
 * - For supported browsers only the address-bar text is read, classified in memory, and dropped.
 * - The service never clicks, scrolls or otherwise acts on another app. The only global action
 *   is "Home", and only when the user taps "Go to home screen" on the block screen.
 */
class ShortVideoAccessibilityService : AccessibilityService(), TrackingCoordinator.Listener {

    private lateinit var coordinator: TrackingCoordinator
    private lateinit var overlays: OverlayController
    private val detectors = HashMap<String, PlatformDetector>()
    private val main = Handler(Looper.getMainLooper())
    private var foregroundPackage: String? = null
    private var currentHost: String? = null
    private var transientPackages: Set<String> = emptySet()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> setForeground(null)
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON ->
                    rootInActiveWindow?.packageName?.toString()?.let(::setForeground)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        coordinator = TrackingCoordinator.get(this)
        for (definition in PlatformRegistry.ALL) {
            val detector = DetectorFactory.create(definition) ?: continue
            definition.packageNames.forEach { detectors[it] = detector }
        }
        transientPackages = buildSet {
            add("com.android.systemui")
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .enabledInputMethodList.forEach { add(it.packageName) }
        }
        overlays = OverlayController(this, coordinator) { performGlobalAction(GLOBAL_ACTION_HOME) }
        coordinator.addListener(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        isConnected = true
        rootInActiveWindow?.packageName?.toString()?.let(::setForeground)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) onWindowChanged(pkg, event)
        if (pkg != foregroundPackage) return

        val detector = detectors[pkg] ?: return
        val definition = detector.definition
        val needsAddressForBlocking = definition.platformType == PlatformType.BROWSER &&
            coordinator.blockState?.enforced?.any { it.target.kind == BlockTargetKind.DOMAIN } == true
        if (!coordinator.isMonitored(definition.id) && !needsAddressForBlocking) return

        val screen = resources.displayMetrics.let { ScreenSize(it.widthPixels, it.heightPixels) }
        when (val signal = detector.onEvent(event, { rootInActiveWindow }, screen)) {
            is DetectorSignal.Video -> coordinator.onVideoObserved(signal.observation)
            is DetectorSignal.Address -> {
                currentHost = signal.classification.host
                updateOverlays()
                coordinator.onBrowserAddress(signal.platformId, signal.classification, System.currentTimeMillis())
            }
            null -> Unit
        }
    }

    private fun onWindowChanged(pkg: String, event: AccessibilityEvent) {
        if (pkg in transientPackages) return
        // Our own overlay windows are not a foreground change; our activity is.
        if (pkg == packageName && event.className?.toString()?.endsWith("MainActivity") != true) return
        setForeground(pkg)
    }

    private fun setForeground(pkg: String?) {
        if (pkg == foregroundPackage) return
        foregroundPackage = pkg
        currentHost = null
        coordinator.onForegroundChanged(pkg, System.currentTimeMillis())
        updateOverlays()
    }

    override fun onTrackerChanged(reason: String) {
        main.post { updateOverlays() }
    }

    /** Decides which overlays are visible for the current foreground app. Main thread only. */
    private fun updateOverlays() {
        if (!::overlays.isInitialized) return
        val pkg = foregroundPackage
        val state = coordinator.blockState
        val block = when {
            pkg == null || coordinator.essentialApps.isEssential(pkg) -> null
            else -> state?.blocksPackage(pkg) ?: currentHost?.takeIf { detectors[pkg]?.definition?.platformType == PlatformType.BROWSER }
                ?.let { state?.blocksHost(it) }
        }
        if (block != null) {
            if (overlays.showBlock(block, state!!)) coordinator.recordBlockShown(block.target)
        } else {
            overlays.hideBlock()
        }
        overlays.updateCounter(foregroundPackage = pkg, visibleBlock = block != null)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun shutdown() {
        if (!isConnected) return
        isConnected = false
        runCatching { unregisterReceiver(screenReceiver) }
        coordinator.removeListener(this)
        coordinator.onForegroundChanged(null, System.currentTimeMillis())
        if (::overlays.isInitialized) overlays.removeAll()
    }

    companion object {
        /** True while the system has the service bound and connected. */
        @Volatile var isConnected: Boolean = false
            private set
    }
}
