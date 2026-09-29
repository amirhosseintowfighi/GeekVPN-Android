package com.geekvpn.lock

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import com.geekvpn.connection.ConnectionPrefs

/** When the app asks to be unlocked again; pure, for the tests. */
class LockClock(private val graceMs: Long = GRACE_MS) {
    private var unlocked = false
    private var backgroundAt = 0L

    fun onUnlocked() {
        unlocked = true
    }

    fun onBackground(now: Long) {
        backgroundAt = now
    }

    /** Locked until the first unlock, and again after [graceMs] in the background. */
    fun needsUnlock(enabled: Boolean, now: Long): Boolean {
        if (!enabled) return false
        if (!unlocked) return true
        if (backgroundAt > 0 && now - backgroundAt > graceMs) {
            unlocked = false
            return true
        }
        return false
    }

    companion object {
        /** Switching to Telegram to confirm a login, or to the bank to pay, does not lock. */
        const val GRACE_MS = 60_000L
    }
}

/**
 * "قفل برنامه": the phone's own fingerprint, face or screen lock before the
 * app shows anything, and again after a minute in the background. Guards
 * GeekVPN's screens in the main process; the VPN keeps running whatever the
 * lock says, and the tile and widget stay usable.
 */
object AppLock {
    private val clock = LockClock()
    private var installed = false
    private var started = 0

    private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun enabled(): Boolean = ConnectionPrefs.open().appLock

    /** The phone has a screen lock or biometrics the app can ask for. */
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    internal val authenticators: Int get() = AUTHENTICATORS

    /** Also when the lock is turned on: the person turning it on is already in. */
    fun onUnlocked() = clock.onUnlocked()

    fun setEnabled(enabled: Boolean) {
        ConnectionPrefs.open().appLock = enabled
        if (enabled) onUnlocked()
    }

    /** Watches GeekVPN's activities; idempotent, called from the entry activities. */
    fun install(application: Application) {
        if (installed) return
        installed = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
            }

            override fun onActivityStopped(activity: Activity) {
                started--
                if (started <= 0) clock.onBackground(System.currentTimeMillis())
            }

            override fun onActivityResumed(activity: Activity) {
                if (activity is LockActivity || !activity.javaClass.name.startsWith("com.geekvpn.")) return
                if (clock.needsUnlock(enabled(), System.currentTimeMillis())) {
                    activity.startActivity(Intent(activity, LockActivity::class.java))
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
