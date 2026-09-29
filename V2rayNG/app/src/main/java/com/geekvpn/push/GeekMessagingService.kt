package com.geekvpn.push

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.geekvpn.GeekGraph
import com.geekvpn.ui.home.HomeActivity
import com.geekvpn.ui.login.LaunchActivity
import com.geekvpn.ui.support.TicketsActivity
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
 *
 * A data message with `type=ticket` is a support reply from the backend
 * (always data-only, so it lands here in the foreground and the background
 * alike); its tap opens that ticket.
 */
class GeekMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        // Woken by a push with the app closed: Firebase needs its app before handling it.
        Push.init(applicationContext)
        super.onCreate()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (message.data["type"] == TYPE_TICKET) {
            showTicketReply(message.data)
            return
        }
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
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

    /**
     * A support answer, sent by the backend as a data message so the tap can
     * open that ticket's conversation (behind Home, so back leads into the app).
     */
    private fun showTicketReply(data: Map<String, String>) {
        val ticketId = data["ticket_id"]?.takeIf { it.isNotBlank() } ?: return
        val reference = data["reference"].orEmpty()
        val body = data["body"].orEmpty()
        Push.ensureSupportChannel(this)
        val open = TaskStackBuilder.create(this)
            .addNextIntent(Intent(this, HomeActivity::class.java))
            .addNextIntent(Intent(this, TicketsActivity::class.java).putExtra(TicketsActivity.EXTRA_TICKET_ID, ticketId))
            .getPendingIntent(ticketId.hashCode(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = if (reference.isNotBlank()) getString(R.string.geek_push_ticket_title_ref, reference) else getString(R.string.geek_push_ticket_title)
        val notification = NotificationCompat.Builder(this, Push.SUPPORT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            // One notification per ticket: a second answer replaces the first.
            NotificationManagerCompat.from(this).notify(TAG_TICKET, ticketId.hashCode(), notification)
        } catch (e: SecurityException) {
            LogUtil.w(AppConfig.TAG, "Push: showing the support reply was refused", e)
        }
    }

    override fun onNewToken(token: String) {
        // This service runs in the main process, where the API may be called.
        LogUtil.i(AppConfig.TAG, "Push: new FCM token")
        GeekGraph.syncPushToken()
    }

    private companion object {
        const val TYPE_TICKET = "ticket"
        const val TAG_TICKET = "geek_ticket"
    }
}
