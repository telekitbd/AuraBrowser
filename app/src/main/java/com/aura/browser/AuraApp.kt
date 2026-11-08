package com.aura.browser

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle

/** State of the app lock (PIN / fingerprint). Kept in memory only, the PIN hash lives in [Store]. */
object AppLock {
    @Volatile
    var locked = false

    @Volatile
    var leftAt = 0L

    @Volatile
    var started = 0

    fun hash(pin: String): String = sha256("aura-lock:$pin")

    fun enabled(store: Store): Boolean =
        store.getBool("applock", false) && store.getStr("lockpin", "").isNotEmpty()

    fun timeoutMs(store: Store): Long = when (store.getInt("locktime", 1)) {
        0 -> 3000L
        1 -> 60000L
        2 -> 300000L
        else -> 900000L
    }

    val TIME_NAMES = listOf("Immediately", "After 1 minute", "After 5 minutes", "After 15 minutes")
}

class AuraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLock.locked = AppLock.enabled(Store(this))
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                if (AppLock.started == 0 && activity !is LockActivity) {
                    val s = Store(activity)
                    if (AppLock.enabled(s) && AppLock.leftAt > 0L &&
                        System.currentTimeMillis() - AppLock.leftAt >= AppLock.timeoutMs(s)
                    ) {
                        AppLock.locked = true
                    }
                }
                AppLock.started = AppLock.started + 1
            }

            override fun onActivityStopped(activity: Activity) {
                AppLock.started = AppLock.started - 1
                if (AppLock.started <= 0) {
                    AppLock.started = 0
                    AppLock.leftAt = System.currentTimeMillis()
                }
            }

            override fun onActivityResumed(activity: Activity) {
                if (AppLock.locked && activity !is LockActivity && AppLock.enabled(Store(activity))) {
                    activity.startActivity(Intent(activity, LockActivity::class.java))
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
