package com.geekvpn.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.geekvpn.ui.login.LaunchActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil

/**
 * Shows a push that arrives while the app is in front; in the background
 * Firebase shows it itself, on [Push.CHANNEL_ID] with the app's status icon
 * (set in the manifest). A `url` data field (https only) opens that page;
 * otherwise the tap opens the app.
 */
class GeekMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        // Woken by a push with the app closed: Firebase needs its app before handling it.
        Push.init(applicationContext)
        super.onCreate()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Push.ensureChannel(this)
        val url = message.data["url"]?.takeIf { it.startsWith("https://") }
        val open = if (url != null) {
            Intent(Intent.ACTION_VIEW, url.toUri())
        } else {
            Intent(this, LaunchActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val id = message.messageId?.hashCode() ?: System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(this, Push.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(PendingIntent.getActivity(this, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (e: SecurityException) {
            LogUtil.w(AppConfig.TAG, "Push: showing the message was refused", e)
        }
    }

    override fun onNewToken(token: String) {
        // Topic delivery needs no token on our side; kept for a later per-user send.
        LogUtil.i(AppConfig.TAG, "Push: new FCM token")
    }
}
