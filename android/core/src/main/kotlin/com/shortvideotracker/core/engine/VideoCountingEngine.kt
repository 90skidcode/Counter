package com.shortvideotracker.core.engine

import com.shortvideotracker.core.model.ConfidenceLevel
import com.shortvideotracker.core.model.EvidenceType
import com.shortvideotracker.core.model.VideoEventType

/**
 * A detector's claim that the user is currently looking at a particular short video.
 *
 * Detectors may emit the same observation many times (Android delivers repeated callbacks);
 * the engine is responsible for turning that noisy stream into distinct views.
 *
 * @property videoKey A key that is stable for one video within one session. Either a real
 *   identifier (`id:<value>`, only from [EvidenceType.URL_VIDEO_ID]) or a pager position
 *   (`pos:<n>`). Raw keys stay in memory; only salted hashes are persisted.
 */
data class VideoObservation(
    val platformId: String,
    val sourceDetector: String,
    val detectorVersion: Int,
    val timestamp: Long,
    val videoKey: String,
    val confidence: ConfidenceLevel,
    val evidence: EvidenceType,
) {
    init {
        require(confidence.producesVideoCounts) { "only A/B observations can produce video counts" }
    }

    val hasStableIdentity: Boolean get() = evidence == EvidenceType.URL_VIDEO_ID
}

/** A distinct, normalized video view ready to be persisted idempotently. */
data class CountedVideoEvent(
    val platformId: String,
    val sourceDetector: String,
    val detectorVersion: Int,
    val sessionId: String,
    /** When the view started (the moment the video was first observed). */
    val eventTimestamp: Long,
    val videoIdentifierHash: String?,
    val deduplicationKey: String,
    val eventType: VideoEventType,
    val confidence: ConfidenceLevel,
    val evidence: EvidenceType,
)

enum class DiagnosticMetric(val code: String) {
    DUPLICATE_CALLBACK_SUPPRESSED("duplicate_callback_suppressed"),
    SHORT_VIEW_DISCARDED("short_view_discarded"),
    REPLAY_NOT_COUNTED("replay_not_counted"),
    DUPLICATE_EVENT_REJECTED("duplicate_event_rejected"),
    EVENT_DROPPED("event_dropped"),
    VIDEO_COUNTED("video_counted"),
}

interface CountingListener {
    fun onCounted(event: CountedVideoEvent)
    fun onDiagnostic(platformId: String, metric: DiagnosticMetric)
}

data class CountingConfig(
    /** A video counts only after it has been on screen this long. 0 counts immediately. */
    val minViewMillis: Long = 3_000,
    /** Leaving a platform for longer than this starts a new viewing session. */
    val sessionGapMillis: Long = 5 * 60_000,
)

/**
 * Single, framework-independent counting engine shared by every detector.
 *
 * Rules (documented in docs/ARCHITECTURE.md):
 * 1. Repeated observations of the pending video are duplicate callbacks and never count.
 * 2. A new video counts once it stays on screen for [CountingConfig.minViewMillis].
 * 3. Returning to a video already counted in the same session (scrolling back, replay) does
 *    not count again. A new session starts after [CountingConfig.sessionGapMillis] away from
 *    the platform, or when the tracking day rolls over.
 * 4. The deduplication key is derived from (platform, session, video key), so a persisted
 *    unique index rejects any event that is emitted twice.
 *
 * Not thread-safe; callers serialize access (the Android layer uses a single thread).
 */
class VideoCountingEngine(
    private var config: CountingConfig,
    private val hasher: IdentifierHasher,
    private val dayKeyOf: (Long) -> String,
    private val newSessionId: () -> String,
    private val listener: CountingListener,
) {
    private class Pending(val observation: VideoObservation, val startedAt: Long) {
        var resolved = false
    }

    private class Session(val id: String, val dayKey: String) {
        var lastActivityAt: Long = 0
        val seenKeys = HashSet<String>()
        var pending: Pending? = null
    }

    private val sessions = HashMap<String, Session>()

    fun updateConfig(newConfig: CountingConfig) {
        config = newConfig
    }

    fun observe(observation: VideoObservation) {
        val session = sessionFor(observation.platformId, observation.timestamp)
        session.lastActivityAt = observation.timestamp

        val pending = session.pending
        if (pending != null && pending.observation.videoKey == observation.videoKey) {
            listener.onDiagnostic(observation.platformId, DiagnosticMetric.DUPLICATE_CALLBACK_SUPPRESSED)
            resolveIfDue(session, observation.timestamp)
            return
        }

        if (pending != null) finish(session, pending, observation.timestamp)

        val next = Pending(observation, observation.timestamp)
        session.pending = next
        if (observation.videoKey in session.seenKeys) {
            next.resolved = true
            listener.onDiagnostic(observation.platformId, DiagnosticMetric.REPLAY_NOT_COUNTED)
            return
        }
        resolveIfDue(session, observation.timestamp)
    }

    /** The platform left the foreground (or its feed is no longer visible). */
    fun platformLeft(platformId: String, timestamp: Long) {
        val session = sessions[platformId] ?: return
        session.pending?.let { finish(session, it, timestamp) }
        session.pending = null
        session.lastActivityAt = timestamp
    }

    /** Commits pending views whose minimum viewing time has elapsed. */
    fun tick(now: Long) {
        for (session in sessions.values) resolveIfDue(session, now)
    }

    /** The earliest time a pending view becomes countable, for scheduling a [tick]. */
    fun nextDueAt(): Long? = sessions.values
        .mapNotNull { s -> s.pending?.takeIf { !it.resolved }?.let { it.startedAt + config.minViewMillis } }
        .minOrNull()

    /** Drops all in-memory state, e.g. after the user clears today's data. */
    fun reset() {
        sessions.clear()
    }

    private fun sessionFor(platformId: String, timestamp: Long): Session {
        val dayKey = dayKeyOf(timestamp)
        val existing = sessions[platformId]
        val expired = existing == null ||
            existing.dayKey != dayKey ||
            (existing.pending == null && timestamp - existing.lastActivityAt > config.sessionGapMillis)
        if (!expired) return existing!!
        existing?.pending?.let { finish(existing, it, timestamp) }
        return Session(newSessionId(), dayKey).also { sessions[platformId] = it }
    }

    private fun resolveIfDue(session: Session, now: Long) {
        val pending = session.pending ?: return
        if (pending.resolved) return
        if (now - pending.startedAt >= config.minViewMillis) commit(session, pending)
    }

    private fun finish(session: Session, pending: Pending, endedAt: Long) {
        if (pending.resolved) return
        if (endedAt - pending.startedAt >= config.minViewMillis) {
            commit(session, pending)
        } else {
            pending.resolved = true
            listener.onDiagnostic(pending.observation.platformId, DiagnosticMetric.SHORT_VIEW_DISCARDED)
        }
    }

    private fun commit(session: Session, pending: Pending) {
        pending.resolved = true
        val o = pending.observation
        session.seenKeys += o.videoKey
        listener.onCounted(
            CountedVideoEvent(
                platformId = o.platformId,
                sourceDetector = o.sourceDetector,
                detectorVersion = o.detectorVersion,
                sessionId = session.id,
                eventTimestamp = pending.startedAt,
                videoIdentifierHash = if (o.hasStableIdentity) hasher.hash("video", o.platformId, o.videoKey) else null,
                deduplicationKey = hasher.hash("dedup-v1", o.platformId, session.id, o.videoKey),
                eventType = VideoEventType.VIDEO_VIEW,
                confidence = o.confidence,
                evidence = o.evidence,
            ),
        )
        listener.onDiagnostic(o.platformId, DiagnosticMetric.VIDEO_COUNTED)
    }
}
