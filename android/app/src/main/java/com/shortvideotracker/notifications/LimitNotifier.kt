package com.shortvideotracker.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shortvideotracker.MainActivity
import com.shortvideotracker.R
import com.shortvideotracker.core.engine.LimitScope
import com.shortvideotracker.core.engine.PlannedWarning
import com.shortvideotracker.core.engine.WarningKind
import com.shortvideotracker.core.model.PlatformRegistry

/** Posts nonjudgmental threshold notifications, if the user allows notifications. */
class LimitNotifier(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun canNotify(): Boolean = manager.areNotificationsEnabled()

    fun show(w: PlannedWarning) {
        if (!canNotify()) return
        ensureChannel()
        val title = titleFor(w)
        val text = buildString {
            append(bodyFor(w))
            if (w.includesEstimates) append(' ').append(context.getString(R.string.warning_includes_estimates))
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_stat_counter)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        // One notification slot per scope, so updates replace rather than stack.
        manager.notify(w.scope.key.hashCode(), notification)
    }

    private fun scopeName(scope: LimitScope): String = when (scope) {
        LimitScope.Global -> context.getString(R.string.scope_all_platforms)
        is LimitScope.Platform -> PlatformRegistry.byId(scope.platformId)?.displayName ?: scope.platformId
    }

    private fun titleFor(w: PlannedWarning): String = when (w.kind) {
        WarningKind.MILESTONE -> context.getString(R.string.warning_title_milestone)
        WarningKind.APPROACHING_VIDEO_LIMIT, WarningKind.APPROACHING_TIME_LIMIT ->
            context.getString(R.string.warning_title_approaching, scopeName(w.scope))
        WarningKind.VIDEO_LIMIT_REACHED, WarningKind.TIME_LIMIT_REACHED, WarningKind.AFTER_LIMIT_REPEAT ->
            context.getString(R.string.warning_title_reached, scopeName(w.scope))
    }

    private fun bodyFor(w: PlannedWarning): String = when (w.kind) {
        WarningKind.MILESTONE -> context.getString(R.string.warning_body_milestone, w.currentVideos)
        WarningKind.APPROACHING_VIDEO_LIMIT -> context.getString(R.string.warning_body_approaching, w.currentVideos, w.limit ?: 0)
        WarningKind.VIDEO_LIMIT_REACHED -> context.getString(R.string.warning_body_reached, w.limit ?: 0)
        WarningKind.AFTER_LIMIT_REPEAT -> context.getString(R.string.warning_body_after_limit, w.currentVideos, w.limit ?: 0)
        WarningKind.APPROACHING_TIME_LIMIT -> context.getString(R.string.warning_body_time_approaching, w.currentMinutes ?: 0L, w.limit ?: 0)
        WarningKind.TIME_LIMIT_REACHED -> context.getString(R.string.warning_body_time_reached, w.limit ?: 0)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_limits_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_limits_description)
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "limits"
    }
}
