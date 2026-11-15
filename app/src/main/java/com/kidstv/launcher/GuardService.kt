package com.kidstv.launcher

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which app is in front. Anything the active profile may not use right
 * now is sent home and covered by the lock screen. Also counts viewing time and
 * hosts the web admin, because the system keeps this service alive.
 */
class GuardService : AccessibilityService() {
    companion object {
        private const val TICK_SEC = 15
        private const val TAG = "KidsTV"
        const val YOUTUBE_PKG = "com.google.android.youtube.tv"
        // Boot-time placeholder home inside the Settings package; never user-initiated.
        private const val FALLBACK_HOME = "com.android.tv.settings.system.FallbackHome"

        @Volatile var running = false
            private set

        /** The live service, for actions the web admin triggers on the TV. */
        @Volatile var instance: GuardService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var web: WebServer? = null

    private val tick = object : Runnable {
        override fun run() {
            if (Store.takeDueSleep()) sleepNow()
            val pkg = Store.currentPkg
            val power = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (power.isInteractive && pkg.isNotEmpty() && pkg != packageName && !Store.parentActive()) {
                Store.addUsage(pkg, TICK_SEC)
                enforce(pkg)
            }
            handler.postDelayed(this, TICK_SEC * 1000L)
        }
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Store.endParent()
            Store.flushUsage()
        }
    }

    override fun onServiceConnected() {
        running = true
        instance = this
        web = WebServer(applicationContext).also { runCatching { it.start() } }
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        handler.postDelayed(tick, TICK_SEC * 1000L)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (Store.isSystemSurface(pkg) || event.className?.toString() == FALLBACK_HOME) return
        Store.currentPkg = pkg
        enforce(pkg)
    }

    private fun enforce(pkg: String) {
        val verdict = Store.decide(pkg)
        if (verdict !is Verdict.Block) return
        Log.i(TAG, "blocking $pkg: ${verdict.kind}")
        startActivity(LockActivity.intent(this, verdict.kind, verdict.reason))
        // A time lock ends the session: stop playback and free memory once the app is covered.
        // An app that merely needs a parent stays alive so the parent lands in it after the PIN.
        if (verdict.kind != "app") {
            handler.postDelayed({
                runCatching { (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg) }
            }, 1500)
        }
    }

    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun open(pkg: String): Boolean = Apps.launch(this, pkg)

    /** Plays one video in the YouTube app. The id is validated by the caller. */
    fun openYoutubeVideo(videoId: String): Boolean = runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                .setPackage(YOUTUBE_PKG)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess

    /**
     * Wakes the box from sleep. The wake lock alone is ignored by this firmware, so the
     * home screen is also started with a request to turn the screen on, which the system honours.
     */
    fun wake() {
        Log.i(TAG, "wake requested")
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        runCatching {
            power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "kidstv:wake",
            ).acquire(3000)
        }
        startActivity(
            Intent(this, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(LauncherActivity.EXTRA_WAKE, true)
        )
    }

    /** Applies the current policy to the app in front right now, without waiting for the next tick. */
    fun recheck() {
        val pkg = Store.currentPkg
        if (pkg.isNotEmpty() && pkg != packageName) enforce(pkg)
    }

    /**
     * Sends the TV home and stops the app that was in front, so playback ends and memory is freed.
     * [then] runs once the app has been stopped.
     */
    fun closeFront(then: () -> Unit = {}) {
        val pkg = Store.currentPkg
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({
            if (pkg.isNotEmpty() && pkg != packageName) {
                runCatching { (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg) }
            }
            then()
        }, 1500)
    }

    /** Ends whatever is playing and puts the box to sleep. */
    private fun sleepNow() = closeFront { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        instance = null
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenOff) }
        web?.stop()
        Store.flushUsage()
        super.onDestroy()
    }
}
