package com.shortvideotracker.overlay

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.text.format.DateFormat
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.shortvideotracker.MainActivity
import com.shortvideotracker.R
import com.shortvideotracker.core.engine.ActiveBlock
import com.shortvideotracker.core.engine.BlockState
import com.shortvideotracker.core.engine.BlockTargetKind
import com.shortvideotracker.core.engine.FloatingCounterMode
import com.shortvideotracker.core.engine.LimitScope
import com.shortvideotracker.core.model.PlatformRegistry
import com.shortvideotracker.tracking.TrackingCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs

/**
 * Draws the floating counter and the block screen on top of other apps using
 * TYPE_ACCESSIBILITY_OVERLAY windows, which belong to the accessibility service and need no
 * extra "display over other apps" permission. Main thread only.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val coordinator: TrackingCoordinator,
    private val goHome: () -> Unit,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = service.getSharedPreferences("overlay_position", Context.MODE_PRIVATE)
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var counterView: TextView? = null
    private var counterExpanded = false
    private var counterPackage: String? = null

    private var blockView: View? = null
    private var blockKey: String? = null
    private var confirmingPause = false

    // ---- Floating counter ------------------------------------------------------------------

    fun updateCounter(foregroundPackage: String?, visibleBlock: Boolean) {
        counterPackage = foregroundPackage
        val settings = coordinator.settings
        val platform = foregroundPackage?.let { PlatformRegistry.byPackage(it) }
        val show = !visibleBlock && settings.monitoringEnabled && foregroundPackage != null &&
            foregroundPackage != service.packageName &&
            when (settings.floatingCounter) {
                FloatingCounterMode.OFF -> false
                FloatingCounterMode.MONITORED_APPS -> platform != null && coordinator.isMonitored(platform.id)
                FloatingCounterMode.ALL_APPS -> true
            }
        if (!show) {
            removeCounter()
            return
        }
        val view = counterView ?: createCounter()
        view.text = counterText(platform?.id)
        view.contentDescription = view.text
    }

    private fun counterText(platformId: String?): String {
        val today = coordinator.today ?: return service.getString(R.string.counter_loading)
        val global = today.global ?: return service.getString(R.string.counter_loading)
        val estimateMark = if (global.estimatedVideos > 0) "~" else ""
        val compact = buildString {
            append("▶ ").append(estimateMark).append(global.totalVideos)
            global.rule.maxVideos?.let { append(" / ").append(it) }
        }
        if (!counterExpanded) return compact
        return buildString {
            append(compact)
            platformId?.let { id ->
                val usage = today.platform(id)
                val name = PlatformRegistry.byId(id)?.displayName ?: id
                val value = usage?.let { (it.verifiedVideos + it.estimatedVideos).toString() } ?: "—"
                append('\n').append(service.getString(R.string.counter_platform_line, name, value))
            }
            append('\n').append(service.getString(R.string.counter_breakdown, global.verifiedVideos, global.estimatedVideos))
            global.remainingVideos?.let {
                val basis = if (global.enforcedIncludesEstimates) R.string.counter_basis_all else R.string.counter_basis_verified
                append('\n').append(service.getString(R.string.counter_remaining, it, service.getString(basis)))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCounter(): TextView {
        val density = service.resources.displayMetrics.density
        val view = TextView(service).apply {
            setBackgroundResource(R.drawable.overlay_counter_background)
            setTextColor(service.getColor(R.color.overlay_counter_text))
            textSize = 14f
            val pad = (10 * density).toInt()
            setPadding(pad + pad / 2, pad, pad + pad / 2, pad)
            elevation = 6 * density
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", (16 * density).toInt())
            y = prefs.getInt("y", (120 * density).toInt())
        }

        // Drag to move; tap to expand or collapse.
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragged = false
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > 8 * density || abs(dy) > 8 * density) dragged = true
                    if (dragged) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { windowManager.updateViewLayout(v, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) {
                        prefs.edit().putInt("x", params.x).putInt("y", params.y).apply()
                    } else {
                        counterExpanded = !counterExpanded
                        updateCounter(counterPackage, visibleBlock = false)
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
        windowManager.addView(view, params)
        counterView = view
        return view
    }

    private fun removeCounter() {
        counterView?.let { runCatching { windowManager.removeView(it) } }
        counterView = null
    }

    // ---- Block screen ----------------------------------------------------------------------

    /** Shows (or refreshes) the block screen. Returns true when it was newly shown. */
    fun showBlock(block: ActiveBlock, state: BlockState): Boolean {
        val key = "${block.target.kind.code}:${block.target.value}"
        val existing = blockView
        if (existing != null && blockKey == key) {
            bindBlock(existing, block, state)
            return false
        }
        hideBlock()
        val view = LayoutInflater.from(service).inflate(R.layout.overlay_block, null)
        bindBlock(view, block, state)
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                goHome()
                true
            } else {
                false
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT,
        )
        windowManager.addView(view, params)
        view.requestFocus()
        blockView = view
        blockKey = key
        confirmingPause = false
        return true
    }

    private fun bindBlock(view: View, block: ActiveBlock, state: BlockState) {
        val targetName = when (block.target.kind) {
            BlockTargetKind.APP -> PlatformRegistry.byPackage(block.target.value)?.displayName ?: block.target.value
            BlockTargetKind.DOMAIN -> block.target.value
        }
        val reason = block.reasons.joinToString(" · ") { scope ->
            when (scope) {
                LimitScope.Global -> service.getString(R.string.block_reason_global)
                is LimitScope.Platform -> service.getString(
                    R.string.block_reason_platform,
                    PlatformRegistry.byId(scope.platformId)?.displayName ?: scope.platformId,
                )
            }
        }
        val resetTime = DateFormat.getTimeFormat(service).format(Date(state.resetsAt))
        view.findViewById<TextView>(R.id.block_title).text = service.getString(R.string.block_title, targetName)
        view.findViewById<TextView>(R.id.block_reason).text = reason
        view.findViewById<TextView>(R.id.block_reset).text = service.getString(R.string.block_resets_at, resetTime)
        if (block.target.kind == BlockTargetKind.DOMAIN) {
            view.findViewById<TextView>(R.id.block_scope_note).text = service.getString(R.string.block_domain_note)
        }

        view.findViewById<Button>(R.id.block_home).setOnClickListener { goHome() }
        view.findViewById<Button>(R.id.block_open_app).setOnClickListener {
            service.startActivity(Intent(service, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        val overrideMinutes = coordinator.settings.overrideMinutes
        val overrideButton = view.findViewById<Button>(R.id.block_override)
        overrideButton.visibility = if (overrideMinutes > 0) View.VISIBLE else View.GONE
        overrideButton.text = service.getString(R.string.block_override, overrideMinutes)
        overrideButton.setOnClickListener {
            uiScope.launch {
                when (coordinator.startOverride()) {
                    null -> Unit // Block state changes; the service hides this screen.
                    "override_limit_reached" -> view.findViewById<TextView>(R.id.block_status).text =
                        service.getString(R.string.block_override_limit)
                    else -> view.findViewById<TextView>(R.id.block_status).text =
                        service.getString(R.string.block_override_unavailable)
                }
            }
        }

        val pauseButton = view.findViewById<Button>(R.id.block_pause)
        pauseButton.text = service.getString(if (confirmingPause) R.string.block_pause_confirm else R.string.block_pause)
        pauseButton.setOnClickListener {
            if (!confirmingPause) {
                confirmingPause = true
                pauseButton.text = service.getString(R.string.block_pause_confirm)
            } else {
                uiScope.launch { coordinator.pauseBlockingForToday() }
            }
        }
    }

    fun hideBlock() {
        blockView?.let { runCatching { windowManager.removeView(it) } }
        blockView = null
        blockKey = null
        confirmingPause = false
    }

    fun removeAll() {
        hideBlock()
        removeCounter()
    }
}
