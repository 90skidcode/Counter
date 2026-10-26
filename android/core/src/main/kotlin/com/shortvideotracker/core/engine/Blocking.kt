package com.shortvideotracker.core.engine

enum class BlockTargetKind(val code: String) { APP("app"), DOMAIN("domain") }

/**
 * Something the user chose to block once a limit is reached.
 *
 * @property platformId For app targets, the platform whose per-platform limit also applies.
 *   Domain targets follow the global limit only, unless the user links a platform.
 */
data class BlockTarget(
    val kind: BlockTargetKind,
    val value: String,
    val platformId: String?,
)

enum class BlockSuspension(val code: String) {
    /** Blocking is turned off in settings. */
    DISABLED("disabled"),

    /** A temporary override is running. */
    OVERRIDE("override"),

    /** The user used the emergency escape for the rest of the tracking day. */
    PAUSED_FOR_DAY("paused_for_day"),
}

data class ActiveBlock(
    val target: BlockTarget,
    /** Scopes whose limit triggered the block, global first. */
    val reasons: List<LimitScope>,
)

data class BlockState(
    val dayKey: String,
    /** Targets that would be blocked by limits, before any suspension is applied. */
    val triggered: List<ActiveBlock>,
    val suspension: BlockSuspension?,
    val overrideUntil: Long?,
    val resetsAt: Long,
) {
    /** Targets that are enforced right now. */
    val enforced: List<ActiveBlock> get() = if (suspension == null) triggered else emptyList()

    fun blocksPackage(packageName: String): ActiveBlock? =
        enforced.firstOrNull { it.target.kind == BlockTargetKind.APP && it.target.value == packageName }

    fun blocksHost(host: String): ActiveBlock? =
        enforced.firstOrNull { it.target.kind == BlockTargetKind.DOMAIN && UrlClassifier.domainMatches(host, it.target.value) }
}

/**
 * Packages that must never be blocked, whatever the configuration says. The Android layer adds
 * the device's current launcher, dialer and SMS app at runtime.
 */
object EssentialApps {
    val STATIC: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastreceiver",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.inputmethod.latin",
        "com.android.inputmethod.latin",
    )

    fun isEssential(packageName: String, runtimeEssential: Set<String>, ownPackage: String): Boolean =
        packageName == ownPackage || packageName in STATIC || packageName in runtimeEssential
}

/**
 * Deterministic block-state calculation.
 *
 * Precedence (documented in docs/ARCHITECTURE.md):
 * 1. Essential packages are never blocked.
 * 2. A target is triggered when the global limit is reached, or when the limit of its linked
 *    platform is reached. Any reached applicable limit triggers it ("most restrictive wins").
 * 3. Suspensions are checked in order: blocking disabled, paused for the day, active override.
 *    A suspension never changes counts; it only stops enforcement.
 */
object BlockStateCalculator {

    fun calculate(
        now: Long,
        dayKey: String,
        resetsAt: Long,
        blockingEnabled: Boolean,
        targets: List<BlockTarget>,
        statuses: List<LimitStatus>,
        overrideUntil: Long?,
        pausedForDayKey: String?,
        isEssential: (String) -> Boolean,
    ): BlockState {
        val reached = statuses.filter { it.reached }.map { it.scope }
        val globalReached = LimitScope.Global in reached

        val triggered = targets
            .filterNot { it.kind == BlockTargetKind.APP && isEssential(it.value) }
            .distinctBy { it.kind to it.value }
            .mapNotNull { target ->
                val reasons = buildList {
                    if (globalReached) add(LimitScope.Global)
                    target.platformId?.let { pid ->
                        val scope = LimitScope.Platform(pid)
                        if (scope in reached) add(scope)
                    }
                }
                if (reasons.isEmpty()) null else ActiveBlock(target, reasons)
            }

        val activeOverride = overrideUntil?.takeIf { it > now }
        val suspension = when {
            !blockingEnabled -> BlockSuspension.DISABLED
            pausedForDayKey == dayKey -> BlockSuspension.PAUSED_FOR_DAY
            activeOverride != null -> BlockSuspension.OVERRIDE
            else -> null
        }
        return BlockState(dayKey, triggered, suspension, activeOverride, resetsAt)
    }
}
