package com.geekvpn.account

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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.geekvpn.GeekGraph
import com.geekvpn.GeekStorage
import com.geekvpn.ui.home.HomeActivity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Tells the customer a service is running out ([UsageAlerts]), with a tap
 * that opens its renewal in the shop.
 *
 * Checked after every account sync (the app is open, the numbers are fresh)
 * and twice a day by [Check], which runs in WorkManager's `:bg` process and
 * only reads what the last sync stored: the API is the main process's alone
 * (see `GeekApi`). The expiry warning is exact either way; the traffic one is
 * as fresh as the last sync.
 */
object UsageNotifier {
    fun check(context: Context, now: Instant = Instant.now()) {
        val cards = GeekGraph.accountStore.services.value
        val store = GeekStorage.open(STORE_ID)
        val sent = store.decodeStringSet(KEY_SENT).orEmpty()
        val due = UsageAlerts.due(cards, sent, now)
        // Keys of services no longer on the account are dropped, so the set stays small.
        val live = cards.mapNotNull { it.subscriptionId }.toSet()
        val kept = sent.filterTo(mutableSetOf()) { key -> key.split('|').getOrNull(1) in live }
        if (due.isEmpty()) {
            if (kept != sent) store.encode(KEY_SENT, kept)
            return
        }
        if (!canNotify(context)) return
        ensureChannel(context)
        due.forEach { post(context, it) }
        store.encode(KEY_SENT, kept + due.map { it.key })
    }

    /** Schedules [Check]; kept if already scheduled. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<Check>(CHECK_INTERVAL_HOURS, TimeUnit.HOURS).build()
        RemoteWorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    class Check(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            check(applicationContext)
            return Result.success()
        }
    }

    private fun canNotify(context: Context): Boolean {
        if (!allowed(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            LogUtil.i(AppConfig.TAG, "UsageNotifier: notifications not allowed, alert skipped")
            return false
        }
        return true
    }

    private fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.geek_alert_channel), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun post(context: Context, alert: UsageAlert) {
        val title = when (alert.kind) {
            UsageAlert.Kind.Quota -> context.getString(R.string.geek_alert_quota_title, alert.serviceName)
            UsageAlert.Kind.Expiry -> context.resources.getQuantityString(R.plurals.geek_alert_expiry_title, alert.amount, alert.serviceName, alert.amount)
        }
        val text = when (alert.kind) {
            UsageAlert.Kind.Quota -> context.getString(R.string.geek_alert_quota_text, alert.amount)
            UsageAlert.Kind.Expiry -> context.getString(R.string.geek_alert_expiry_text)
        }
        val open = Intent(context, HomeActivity::class.java)
            .putExtra(HomeActivity.EXTRA_RENEW, alert.subscriptionId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            alert.key.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .addAction(0, context.getString(R.string.geek_alert_renew), pending)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(alert.key.hashCode(), notification)
        } catch (e: SecurityException) {
            // Revoked between the check and the post.
            LogUtil.w(AppConfig.TAG, "UsageNotifier: posting ${alert.kind} alert refused", e)
        }
    }

    private const val STORE_ID = "GEEK_ALERTS"
    private const val KEY_SENT = "sent"
    private const val CHANNEL_ID = "geek_account_alerts"
    private const val WORK_NAME = "geek-usage-alerts"
    private const val CHECK_INTERVAL_HOURS = 12L
}
