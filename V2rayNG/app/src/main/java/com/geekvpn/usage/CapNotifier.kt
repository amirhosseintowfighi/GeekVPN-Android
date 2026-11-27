package com.geekvpn.usage

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.geekvpn.ui.usage.UsageActivity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil
import java.time.LocalDate

/**
 * Checks «سقف مصرف» ([DataCap]) after each usage sample in the VPN process
 * and tells the customer when it is near or reached. Tapping opens the usage
 * screen, where the limit is changed.
 */
class CapNotifier(private val context: Context, private val usage: DailyUsage) {
    private var usedAtStart: Long? = null

    /** Remembers what was used when the connection started; see [DataCapPolicy.decide]. */
    fun begin(today: LocalDate = LocalDate.now()) {
        usedAtStart = usage.cap?.let { usage.usedInPeriod(it, today) } ?: 0L
    }

    /** @return true when the VPN should disconnect now. */
    fun check(today: LocalDate = LocalDate.now()): Boolean {
        val cap = usage.cap ?: return false
        val used = usage.usedInPeriod(cap, today)
        val periodKey = DataCapPolicy.periodKey(cap.period, today)
        val sent = DataCapPolicy.prune(usage.capSent, periodKey)
        val decision = DataCapPolicy.decide(cap, used, usedAtStart ?: used, sent, periodKey)
        if (decision.sentKeys.isNotEmpty() || sent != usage.capSent) usage.capSent = sent + decision.sentKeys
        decision.events.forEach { post(it, cap, decision.disconnect) }
        if (decision.disconnect) {
            LogUtil.i(AppConfig.TAG, "DataCap: ${cap.period} limit reached, disconnecting")
        }
        return decision.disconnect
    }

    private fun post(event: DataCapPolicy.Event, cap: DataCap, disconnecting: Boolean) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            LogUtil.i(AppConfig.TAG, "DataCap: notifications not allowed, $event skipped")
            return
        }
        ensureChannel()
        val period = context.getString(
            if (cap.period == DataCap.Period.Day) R.string.geek_cap_period_day_lower else R.string.geek_cap_period_month_lower,
        )
        val title = when (event) {
            DataCapPolicy.Event.Warn -> context.getString(R.string.geek_cap_warn_title, period)
            DataCapPolicy.Event.Reached -> context.getString(R.string.geek_cap_reached_title, period)
        }
        val text = context.getString(
            when {
                event == DataCapPolicy.Event.Warn -> R.string.geek_cap_warn_text
                disconnecting -> R.string.geek_cap_reached_disconnected
                else -> R.string.geek_cap_reached_text
            },
        )
        val open = Intent(context, UsageActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Revoked between the check and the post.
            LogUtil.w(AppConfig.TAG, "DataCap: posting $event refused", e)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.geek_cap_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "geek_usage_cap"
        const val NOTIFICATION_ID = 0x6E0CA9
    }
}
