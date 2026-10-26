package com.shortvideotracker.tracking

import android.content.Context
import com.shortvideotracker.core.engine.AddressBarClassification
import com.shortvideotracker.core.engine.BlockState
import com.shortvideotracker.core.engine.BlockStateCalculator
import com.shortvideotracker.core.engine.BlockTarget
import com.shortvideotracker.core.engine.BlockTargetKind
import com.shortvideotracker.core.engine.CountedVideoEvent
import com.shortvideotracker.core.engine.CountingListener
import com.shortvideotracker.core.engine.DayClock
import com.shortvideotracker.core.engine.DiagnosticMetric
import com.shortvideotracker.core.engine.IdentifierHasher
import com.shortvideotracker.core.engine.LimitEvaluator
import com.shortvideotracker.core.engine.LimitScope
import com.shortvideotracker.core.engine.LimitStatus
import com.shortvideotracker.core.engine.PlatformUsage
import com.shortvideotracker.core.engine.SettingsValidator
import com.shortvideotracker.core.engine.TrackerSettings
import com.shortvideotracker.core.engine.ValidatedSettings
import com.shortvideotracker.core.engine.VideoCountingEngine
import com.shortvideotracker.core.engine.VideoObservation
import com.shortvideotracker.core.engine.WarningPlanner
import com.shortvideotracker.core.model.ConfidenceLevel
import com.shortvideotracker.core.model.EvidenceType
import com.shortvideotracker.core.model.PlatformDefinition
import com.shortvideotracker.core.model.PlatformRegistry
import com.shortvideotracker.core.model.PlatformType
import com.shortvideotracker.data.ActivityDailyEntity
import com.shortvideotracker.data.OverrideEntity
import com.shortvideotracker.data.TrackerRepository
import com.shortvideotracker.notifications.LimitNotifier
import com.shortvideotracker.usage.UsageStatsReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Wires detectors → counting engine → repository → limits → warnings and block state.
 *
 * All mutable state is confined to one single-threaded dispatcher, so concurrent detector
 * callbacks can never interleave half-applied updates. Block state is always recomputed from
 * the database (events, limits, overrides), which is what lets it survive process death and
 * reboots without a separate persisted flag drifting out of sync.
 */
class TrackingCoordinator private constructor(context: Context) {

    fun interface Listener {
        fun onTrackerChanged(reason: String)
    }

    /** Snapshot of today used by the floating counter and the block screen. */
    data class Today(
        val dayKey: String,
        val statuses: List<LimitStatus>,
        val usage: List<PlatformUsage>,
        val resetsAt: Long,
    ) {
        val global: LimitStatus? get() = statuses.firstOrNull { it.scope == LimitScope.Global }
        fun platform(id: String): PlatformUsage? = usage.firstOrNull { it.platformId == id }
    }

    private val app = context.applicationContext
    val repository = TrackerRepository.get(app)
    private val usageReader = UsageStatsReader(app)
    private val notifier = LimitNotifier(app)
    val essentialApps = EssentialAppsResolver(app)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val ready = CompletableDeferred<Unit>()
    private val listeners = CopyOnWriteArraySet<Listener>()

    @Volatile var settings: TrackerSettings = TrackerSettings()
        private set

    @Volatile var enabledPlatforms: Set<String> = emptySet()
        private set

    @Volatile var blockState: BlockState? = null
        private set

    @Volatile var today: Today? = null
        private set

    /** Last time each detector produced a usable signal (in memory, for diagnostics). */
    val lastSignalAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private lateinit var dayClock: DayClock
    private lateinit var clockZone: ZoneId
    private lateinit var engine: VideoCountingEngine

    private val counted = ArrayList<CountedVideoEvent>()
    private val diagnosticBuffer = HashMap<Pair<String, String>, Long>()
    private var diagnosticFlushJob: Job? = null
    private var tickJob: Job? = null
    private var timerJob: Job? = null
    private var usageCache: Triple<String, Long, Map<String, Long>?>? = null

    private var foregroundPlatform: String? = null
    private var webSession: Pair<String, Long>? = null

    private val engineListener = object : CountingListener {
        override fun onCounted(event: CountedVideoEvent) {
            counted += event
        }

        override fun onDiagnostic(platformId: String, metric: DiagnosticMetric) = diagnostic(platformId, metric)
    }

    init {
        scope.launch { initialize() }
    }

    // ---- Lifecycle ---------------------------------------------------------------------------

    private suspend fun initialize() {
        val now = System.currentTimeMillis()
        repository.seedPlatforms(now)
        settings = repository.loadSettings()
        enabledPlatforms = repository.platforms().filter { it.enabled }.map { it.id }.toSet()
        rebuildClockAndEngine()
        repository.applyRetention(dayClock.dayOf(now).minusDays(settings.retentionDays.toLong()).toString())
        ready.complete(Unit)
        recompute("startup")
    }

    private suspend fun rebuildClockAndEngine() {
        clockZone = ZoneId.systemDefault()
        dayClock = DayClock(settings.resetMinuteOfDay, clockZone)
        engine = VideoCountingEngine(
            config = settings.countingConfig,
            hasher = IdentifierHasher(repository.installSalt()),
            dayKeyOf = { dayClock.dayKey(it) },
            newSessionId = { UUID.randomUUID().toString() },
            listener = engineListener,
        )
    }

    private fun launchReady(block: suspend () -> Unit) {
        scope.launch {
            ready.await()
            block()
        }
    }

    suspend fun <T> call(block: suspend TrackingCoordinator.() -> T): T = withContext(dispatcher) {
        ready.await()
        block()
    }

    fun addListener(listener: Listener) = listeners.add(listener)

    fun removeListener(listener: Listener) = listeners.remove(listener)

    // ---- Detector input ----------------------------------------------------------------------

    fun isMonitored(platformId: String): Boolean = settings.monitoringEnabled && platformId in enabledPlatforms

    fun onVideoObserved(observation: VideoObservation) = launchReady {
        lastSignalAt[observation.platformId] = observation.timestamp
        if (!isMonitored(observation.platformId)) return@launchReady
        engine.observe(observation)
        persistCounted()
    }

    fun onBrowserAddress(platformId: String, classification: AddressBarClassification, at: Long) = launchReady {
        lastSignalAt[platformId] = at
        if (!isMonitored(platformId)) return@launchReady
        when (classification) {
            is AddressBarClassification.ShortVideo -> {
                startWebSession(platformId, at)
                engine.observe(
                    VideoObservation(
                        platformId = platformId,
                        sourceDetector = PlatformRegistry.byId(platformId)?.detectorType?.code ?: "browser_address_bar",
                        detectorVersion = PlatformRegistry.byId(platformId)?.detectorVersion ?: 1,
                        timestamp = at,
                        videoKey = "id:${classification.site.code}:${classification.videoId}",
                        confidence = ConfidenceLevel.A_VERIFIED,
                        evidence = EvidenceType.URL_VIDEO_ID,
                    ),
                )
            }
            is AddressBarClassification.SiteActivity -> {
                startWebSession(platformId, at)
                engine.platformLeft(platformId, at)
            }
            is AddressBarClassification.OtherSite -> {
                endWebSession(at)
                engine.platformLeft(platformId, at)
            }
        }
        persistCounted()
    }

    /** The foreground package changed; null means the screen turned off. */
    fun onForegroundChanged(packageName: String?, at: Long) = launchReady {
        val platform = packageName?.let { PlatformRegistry.byPackage(it)?.id }
        val previous = foregroundPlatform
        if (previous != null && previous != platform) {
            engine.platformLeft(previous, at)
            if (webSession?.first == previous) endWebSession(at)
        }
        foregroundPlatform = platform
        persistCounted()
        if (platform != previous) recompute("foreground")
    }

    private suspend fun persistCounted() {
        val batch = counted.toList()
        counted.clear()
        var changed = false
        val now = System.currentTimeMillis()
        for (event in batch) {
            if (repository.insertCountedEvent(event, dayClock.dayKey(event.eventTimestamp), now)) {
                changed = true
            } else {
                diagnostic(event.platformId, DiagnosticMetric.DUPLICATE_EVENT_REJECTED)
            }
        }
        scheduleTick()
        if (changed) recompute("video_counted")
    }

    private fun scheduleTick() {
        tickJob?.cancel()
        val due = engine.nextDueAt() ?: return
        tickJob = scope.launch {
            delay((due - System.currentTimeMillis()).coerceAtLeast(0))
            engine.tick(System.currentTimeMillis())
            persistCounted()
        }
    }

    private fun diagnostic(platformId: String, metric: DiagnosticMetric) {
        val key = platformId to metric.code
        diagnosticBuffer[key] = (diagnosticBuffer[key] ?: 0L) + 1
        if (diagnosticFlushJob?.isActive == true) return
        diagnosticFlushJob = scope.launch {
            delay(DIAGNOSTIC_FLUSH_MILLIS)
            flushDiagnostics()
        }
    }

    private suspend fun flushDiagnostics() {
        if (diagnosticBuffer.isEmpty()) return
        val now = System.currentTimeMillis()
        val day = dayClock.dayKey(now)
        val snapshot = diagnosticBuffer.toMap()
        diagnosticBuffer.clear()
        for ((key, delta) in snapshot) repository.addDiagnostic(day, key.first, key.second, delta, now)
    }

    private suspend fun startWebSession(platformId: String, at: Long) {
        val current = webSession
        if (current != null && current.first == platformId) {
            // Flush periodically so a long session is not lost on process death.
            if (at - current.second >= WEB_FLUSH_MILLIS) {
                endWebSession(at)
                webSession = platformId to at
            }
            return
        }
        endWebSession(at)
        webSession = platformId to at
    }

    private suspend fun endWebSession(at: Long) {
        val (platformId, since) = webSession ?: return
        webSession = null
        val delta = (at - since).coerceIn(0, MAX_WEB_SEGMENT_MILLIS)
        if (delta > 0) repository.addWebActivity(dayClock.dayKey(since), platformId, delta, System.currentTimeMillis())
    }

    // ---- Evaluation --------------------------------------------------------------------------

    /**
     * Recomputes limits, warnings and block state from the database, then notifies listeners.
     * UI-initiated reads pass [notify] = false so they do not echo back as change events.
     */
    suspend fun recompute(reason: String, notify: Boolean = true) {
        if (ZoneId.systemDefault() != clockZone) rebuildClockAndEngine()
        val now = System.currentTimeMillis()
        val day = dayClock.dayOf(now)
        val dayKey = day.toString()
        val usage = usageFor(dayKey, dayClock.dayStart(day), now)
        val statuses = LimitEvaluator.evaluate(settings.limits, usage)

        if (settings.warningsEnabled) {
            val plan = WarningPlanner.plan(dayKey, settings.limits, statuses, repository.sentWarningIds(dayKey))
            val notifyIds = plan.toNotify.map { it.id }.toSet()
            for (w in plan.toRecord) {
                val first = repository.recordWarning(dayKey, w, w.id in notifyIds, now)
                if (first && w.id in notifyIds) notifier.show(w)
            }
        }

        val overrides = repository.overridesForDay(dayKey).filter { it.cancelledAt == null }
        val overrideUntil = overrides.filter { it.kind == OverrideEntity.KIND_TEMPORARY }.mapNotNull { it.endsAt }.maxOrNull()
        val paused = if (overrides.any { it.kind == OverrideEntity.KIND_PAUSE_FOR_DAY }) dayKey else null
        val state = BlockStateCalculator.calculate(
            now = now,
            dayKey = dayKey,
            resetsAt = dayClock.dayEnd(day),
            blockingEnabled = settings.blockingEnabled,
            targets = repository.blockTargets(),
            statuses = statuses,
            overrideUntil = overrideUntil,
            pausedForDayKey = paused,
            isEssential = essentialApps::isEssential,
        )
        for (block in state.enforced) {
            repository.recordBlockEvent(dayKey, block.target, "activated", block.reasons.joinToString(",") { it.key }, now, countRepeats = false)
        }

        blockState = state
        today = Today(dayKey, statuses, usage, dayClock.dayEnd(day))
        scheduleTimers(now, state)
        if (notify) listeners.forEach { runCatching { it.onTrackerChanged(reason) } }
    }

    private suspend fun usageFor(dayKey: String, dayStart: Long, now: Long): List<PlatformUsage> {
        val counts = repository.countsByDay(dayKey, dayKey)
        val web = repository.activityByDay(dayKey, dayKey).filter { it.source == ActivityDailyEntity.SOURCE_WEB_TIMER }
        val appForeground = appForegroundMillis(dayKey, dayStart, now)
        return PlatformRegistry.ALL.filter { it.id in enabledPlatforms }.map { p ->
            val rows = counts.filter { it.platformId == p.id }
            PlatformUsage(
                platformId = p.id,
                verifiedVideos = rows.filter { it.confidenceLevel == ConfidenceLevel.A_VERIFIED.code }.sumOf { it.count },
                estimatedVideos = rows.filter { it.confidenceLevel == ConfidenceLevel.B_INFERRED.code }.sumOf { it.count },
                foregroundMillis = when (p.platformType) {
                    PlatformType.APP -> appForeground?.let { m -> p.packageNames.sumOf { m[it] ?: 0L } }
                    PlatformType.BROWSER -> web.filter { it.platformId == p.id }.sumOf { it.activeMillis }
                },
            )
        }
    }

    /** Whole-app foreground time from Usage Access, cached briefly to avoid repeated queries. */
    private suspend fun appForegroundMillis(dayKey: String, dayStart: Long, now: Long): Map<String, Long>? {
        usageCache?.let { (day, at, value) -> if (day == dayKey && now - at < USAGE_CACHE_MILLIS) return value }
        val apps = PlatformRegistry.ALL.filter { it.platformType == PlatformType.APP && it.id in enabledPlatforms }
        val value = usageReader.foregroundMillis(apps.flatMap { it.packageNames }.toSet(), dayStart, now)
        if (value != null) {
            for (p in apps) repository.setUsageMillis(dayKey, p.id, p.packageNames.sumOf { value[it] ?: 0L }, now)
        }
        usageCache = Triple(dayKey, now, value)
        return value
    }

    /** Re-evaluates at the next reset and when an override expires. */
    private fun scheduleTimers(now: Long, state: BlockState) {
        timerJob?.cancel()
        // Time limits need fresh Usage Access data, but only while a monitored app is open.
        val refresh = if (foregroundPlatform != null) FOREGROUND_REFRESH_MILLIS else IDLE_REFRESH_MILLIS
        val next = listOfNotNull(state.resetsAt, state.overrideUntil, now + refresh).min()
        timerJob = scope.launch {
            delay((next - System.currentTimeMillis()).coerceAtLeast(1_000))
            usageCache = null
            recompute("timer")
        }
    }

    // ---- Commands from the UI / block screen -------------------------------------------------

    suspend fun saveSettings(input: TrackerSettings): Pair<ValidatedSettings, Boolean> = call {
        val validated = validate(input)
        if (validated.hasErrors) return@call validated to false
        val resetChanged = validated.settings.resetMinuteOfDay != settings.resetMinuteOfDay
        settings = validated.settings
        repository.saveSettings(settings)
        if (resetChanged) rebuildClockAndEngine() else engine.updateConfig(settings.countingConfig)
        usageCache = null
        recompute("settings")
        validated to true
    }

    suspend fun validate(input: TrackerSettings): ValidatedSettings {
        val levels = PlatformRegistry.ALL.filter { it.id in enabledPlatforms }.associate { it.id to it.capabilityLevel }
        return SettingsValidator.validate(input, repository.blockTargets().size, levels)
    }

    suspend fun setPlatformEnabled(platformId: String, enabled: Boolean) = call {
        repository.setPlatformEnabled(platformId, enabled, System.currentTimeMillis())
        enabledPlatforms = repository.platforms().filter { it.enabled }.map { it.id }.toSet()
        if (!enabled) engine.platformLeft(platformId, System.currentTimeMillis())
        persistCounted()
        usageCache = null
        recompute("platforms")
    }

    suspend fun addBlockTarget(target: BlockTarget): Boolean = call {
        if (target.kind == BlockTargetKind.APP && essentialApps.isEssential(target.value)) return@call false
        repository.addBlockTarget(target, System.currentTimeMillis())
        recompute("block_targets")
        true
    }

    suspend fun removeBlockTarget(kind: BlockTargetKind, value: String) = call {
        repository.removeBlockTarget(kind, value)
        recompute("block_targets")
    }

    /** Starts a temporary override if allowed. Returns null on success or a reason code. */
    suspend fun startOverride(): String? = call {
        val now = System.currentTimeMillis()
        val dayKey = dayClock.dayKey(now)
        if (settings.overrideMinutes <= 0) return@call "overrides_disabled"
        val used = repository.overridesForDay(dayKey).count { it.kind == OverrideEntity.KIND_TEMPORARY }
        if (used >= settings.maxOverridesPerDay) return@call "override_limit_reached"
        repository.insertOverride(
            OverrideEntity(dayKey = dayKey, kind = OverrideEntity.KIND_TEMPORARY, startedAt = now, endsAt = now + settings.overrideMinutes * 60_000L, cancelledAt = null),
        )
        recompute("override")
        null
    }

    /** Emergency escape: stop enforcing blocks until the next reset. Always available. */
    suspend fun pauseBlockingForToday() = call {
        val now = System.currentTimeMillis()
        repository.insertOverride(
            OverrideEntity(dayKey = dayClock.dayKey(now), kind = OverrideEntity.KIND_PAUSE_FOR_DAY, startedAt = now, endsAt = dayClock.nextResetAfter(now), cancelledAt = null),
        )
        recompute("override")
    }

    suspend fun cancelOverrides() = call {
        val now = System.currentTimeMillis()
        repository.cancelOverrides(dayClock.dayKey(now), now)
        recompute("override")
    }

    fun recordBlockShown(target: BlockTarget) = launchReady {
        val now = System.currentTimeMillis()
        repository.recordBlockEvent(dayClock.dayKey(now), target, "shown", "", now, countRepeats = true)
    }

    suspend fun clearToday() = call {
        flushDiagnostics()
        repository.clearDay(dayClock.dayKey(System.currentTimeMillis()))
        engine.reset()
        counted.clear()
        usageCache = null
        recompute("data_cleared")
    }

    suspend fun clearAllHistory() = call {
        diagnosticBuffer.clear()
        repository.clearAllHistory()
        engine.reset()
        counted.clear()
        webSession = null
        usageCache = null
        lastSignalAt.clear()
        recompute("data_cleared")
    }

    suspend fun deleteAllData() = call {
        diagnosticBuffer.clear()
        repository.deleteAllData()
        settings = repository.loadSettings()
        enabledPlatforms = repository.platforms().filter { it.enabled }.map { it.id }.toSet()
        counted.clear()
        webSession = null
        usageCache = null
        lastSignalAt.clear()
        rebuildClockAndEngine() // new install salt, so old hashes cannot be linked to new ones
        recompute("data_deleted")
    }

    fun dayKeyNow(): String = dayClock.dayKey(System.currentTimeMillis())

    fun dayClock(): DayClock = dayClock

    fun definition(platformId: String): PlatformDefinition? = PlatformRegistry.byId(platformId)

    companion object {
        private const val DIAGNOSTIC_FLUSH_MILLIS = 10_000L
        private const val USAGE_CACHE_MILLIS = 60_000L
        private const val WEB_FLUSH_MILLIS = 60_000L
        private const val MAX_WEB_SEGMENT_MILLIS = 30 * 60_000L
        private const val FOREGROUND_REFRESH_MILLIS = 60_000L
        private const val IDLE_REFRESH_MILLIS = 30 * 60_000L

        @Volatile private var instance: TrackingCoordinator? = null

        fun get(context: Context): TrackingCoordinator = instance ?: synchronized(this) {
            instance ?: TrackingCoordinator(context).also { instance = it }
        }
    }
}
