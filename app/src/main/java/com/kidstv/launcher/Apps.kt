package com.kidstv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import java.net.Inet4Address
import java.net.NetworkInterface

data class AppInfo(val pkg: String, val label: String)

object Apps {
    private fun launchIntent(ctx: Context, pkg: String): Intent? =
        ctx.packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: ctx.packageManager.getLaunchIntentForPackage(pkg)

    /** Every app with a TV or regular launcher entry, except this launcher. */
    fun launchable(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, String>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || pkg in found) continue
                found[pkg] = ri.activityInfo.applicationInfo.loadLabel(pm).toString()
            }
        }
        return found.map { AppInfo(it.key, it.value) }.sortedBy { it.label.lowercase() }
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val intent = launchIntent(ctx, pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent) }.isSuccess
    }

    /** The wide TV banner when the app ships one; null otherwise. */
    fun banner(ctx: Context, pkg: String): Bitmap? {
        val pm = ctx.packageManager
        val d: Drawable? = runCatching {
            launchIntent(ctx, pkg)?.component?.let { pm.getActivityBanner(it) }
                ?: pm.getApplicationBanner(pkg)
        }.getOrNull()
        return d?.let { runCatching { it.toBitmap(480, 270) }.getOrNull() }
    }

    fun icon(ctx: Context, pkg: String, size: Int = 144): Bitmap? =
        runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(size, size) }.getOrNull()

    /** The box's address on the home network, for the web admin link. */
    fun lanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
