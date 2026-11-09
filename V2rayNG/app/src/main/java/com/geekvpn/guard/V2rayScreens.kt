package com.geekvpn.guard

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.geekvpn.ui.login.LaunchActivity

/**
 * No v2rayNG screen is ever shown. Whatever opens one (the connection
 * notification, a long press on the Quick Settings tile, a `v2rayng://` link
 * after its import, Tasker, a future upstream entry point), it is closed before
 * its first frame and GeekVPN's own entry ([LaunchActivity]) opens instead.
 *
 * The shortcut trampolines and the link import have no UI and do their work
 * in onCreate, so they run; the screen they hand over to is caught here.
 */
object V2rayScreens {
    private const val V2RAY_UI = "com.v2ray.ang.ui."

    /** v2rayNG activities without a UI of their own, which must keep working. */
    private val ALLOWED = setOf(
        "com.v2ray.ang.ui.shortcut.ScStartActivity",
        "com.v2ray.ang.ui.shortcut.ScStopActivity",
        "com.v2ray.ang.ui.shortcut.ScSwitchActivity",
        "com.v2ray.ang.ui.UrlSchemeActivity",
    )

    /** Pure, for the tests: whether an activity of this class is replaced. */
    fun isBlocked(className: String): Boolean = className.startsWith(V2RAY_UI) && className !in ALLOWED

    @Volatile
    private var installed = false

    /** From [com.v2ray.ang.AngApplication.onCreate], so it holds in a process no GeekVPN screen started. */
    fun install(application: Application) {
        if (installed) return
        installed = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (!isBlocked(activity.javaClass.name)) return
                activity.startActivity(
                    Intent(activity, LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                activity.finish()
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}
