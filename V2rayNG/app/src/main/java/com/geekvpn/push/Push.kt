package com.geekvpn.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil

/**
 * Firebase Cloud Messaging, for announcements sent from the Firebase console.
 *
 * Configured from build values (`GEEK_FIREBASE_*`, see docs/geekvpn.md) rather
 * than google-services.json, so no part of the Firebase project lives in the
 * repository; a build without them has push off and never touches Firebase.
 * Every install joins [TOPIC_ALL], so the console can reach everyone by topic
 * as well as by app.
 *
 * Started when the app opens (`LaunchActivity`, `HomeActivity`) and by
 * [GeekMessagingService] when a push is what woke the app; after
 * `AngApplication.onCreate` either way, which `LogUtil` (MMKV) needs.
 */
object Push {
    const val TOPIC_ALL = "all"
    const val CHANNEL_ID = "geek_announcements"

    val configured: Boolean
        get() = listOf(
            BuildConfig.FIREBASE_APP_ID,
            BuildConfig.FIREBASE_API_KEY,
            BuildConfig.FIREBASE_PROJECT_ID,
            BuildConfig.FIREBASE_SENDER_ID,
        ).all { it.isNotBlank() }

    fun init(context: Context) {
        if (!configured) return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            ensureChannel(context)
            FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_ALL)
                .addOnFailureListener { LogUtil.w(AppConfig.TAG, "Push: joining topic $TOPIC_ALL failed", it) }
        } catch (e: IllegalStateException) {
            // A malformed option (e.g. a wrong app id) must not stop the app starting.
            LogUtil.e(AppConfig.TAG, "Push: Firebase could not start", e)
        } catch (e: IllegalArgumentException) {
            LogUtil.e(AppConfig.TAG, "Push: Firebase could not start", e)
        }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.geek_push_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}

