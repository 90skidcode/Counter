package com.shortvideotracker.permissions

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.shortvideotracker.detection.ShortVideoAccessibilityService
import com.shortvideotracker.usage.UsageStatsReader
import org.json.JSONObject

/**
 * Reports what the user has granted, checked fresh on every call so revocations are noticed.
 * Every settings screen is opened only in response to a user action.
 */
class PermissionStatusProvider(private val context: Context) {

    fun usageAccessGranted(): Boolean = UsageStatsReader(context).hasAccess()

    /** Enabled in system settings (the user switched it on). */
    fun accessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val ours = ComponentName(context, ShortVideoAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    /** Enabled and actually connected; tracking is only reported as active in this case. */
    fun accessibilityRunning(): Boolean = accessibilityEnabled() && ShortVideoAccessibilityService.isConnected

    fun notificationsGranted(): Boolean =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()

    fun toJson(): JSONObject = JSONObject().apply {
        put("usageAccess", status(usageAccessGranted()))
        put("accessibility", when {
            accessibilityRunning() -> "granted"
            accessibilityEnabled() -> "enabled_not_running"
            else -> "not_granted"
        })
        put("notifications", status(notificationsGranted()))
        // Website blocking uses the browser address bar; no VPN is used in this version.
        put("vpn", "not_used")
        put("overlay", if (accessibilityRunning()) "granted" else "requires_accessibility")
    }

    fun settingsIntent(kind: String): Intent? = when (kind) {
        "usageAccess" -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        "appDetails" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        else -> null
    }?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun status(granted: Boolean) = if (granted) "granted" else "not_granted"
}
