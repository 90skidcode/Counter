package com.shortvideotracker.tracking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import android.telecom.TelecomManager
import com.shortvideotracker.core.engine.EssentialApps

/** Adds the device's current launcher, dialer and SMS app to the never-block list. */
class EssentialAppsResolver(private val context: Context) {

    @Volatile private var cached: Set<String>? = null

    fun runtimeEssential(): Set<String> = cached ?: resolve().also { cached = it }

    fun refresh() {
        cached = null
    }

    fun isEssential(packageName: String): Boolean =
        EssentialApps.isEssential(packageName, runtimeEssential(), context.packageName)

    private fun resolve(): Set<String> = buildSet {
        val pm = context.packageManager
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.let(::add)
        }
        runCatching {
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage?.let(::add)
        }
        runCatching { Telephony.Sms.getDefaultSmsPackage(context)?.let(::add) }
    }
}
