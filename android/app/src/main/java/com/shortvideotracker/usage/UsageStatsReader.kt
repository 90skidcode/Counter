package com.shortvideotracker.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Reads foreground time for monitored packages through Android Usage Access.
 *
 * Foreground time is whole-app time (all of YouTube, not only Shorts) and is never used as a
 * substitute for a video count. Queries are made on demand; there is no background polling.
 */
class UsageStatsReader(private val context: Context) {

    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Foreground milliseconds per package between [start] and [end], or null when Usage Access
     * is not granted (or was revoked) so callers can show "unavailable" instead of zero.
     */
    fun foregroundMillis(packages: Set<String>, start: Long, end: Long): Map<String, Long>? {
        if (!hasAccess() || end <= start) return null
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = try {
            manager.queryEvents(start, end)
        } catch (e: SecurityException) {
            return null
        } ?: return null

        val totals = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()
        val seen = HashSet<String>()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg !in packages) continue
            when (event.eventType) {
                RESUMED -> resumedAt.putIfAbsent(pkg, event.timeStamp)
                PAUSED, STOPPED -> {
                    // A pause with no earlier resume means the app was already open at [start].
                    val from = resumedAt.remove(pkg) ?: if (pkg !in seen) start else null
                    if (from != null) totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - from).coerceAtLeast(0)
                }
            }
            seen += pkg
        }
        // Still in the foreground at the end of the window.
        for ((pkg, from) in resumedAt) totals[pkg] = (totals[pkg] ?: 0L) + (end - from).coerceAtLeast(0)
        return packages.associateWith { totals[it] ?: 0L }
    }

    private companion object {
        val RESUMED = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) UsageEvents.Event.ACTIVITY_RESUMED else 1
        val PAUSED = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) UsageEvents.Event.ACTIVITY_PAUSED else 2
        const val STOPPED = 23 // UsageEvents.Event.ACTIVITY_STOPPED (API 29)
    }
}
