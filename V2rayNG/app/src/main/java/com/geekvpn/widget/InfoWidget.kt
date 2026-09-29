package com.geekvpn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.geekvpn.GeekGraph
import com.geekvpn.GeekStorage
import com.geekvpn.account.AccountStore
import com.geekvpn.quick.QuickConnect
import com.geekvpn.ui.common.formatGib
import com.geekvpn.ui.login.LaunchActivity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import java.time.Instant

/**
 * Home-screen widget: the service in use (remaining traffic and days) and a
 * connect button. Lives in the VPN process, like v2rayNG's own switch widget,
 * so it hears the core's state broadcasts and can stop the core directly.
 * Starting goes through [QuickConnect] with «سرور: خودکار» on, as the tile does.
 *
 * The account data is the copy the app keeps in multi-process MMKV; the app
 * asks for a redraw after each account sync ([refresh]).
 */
class InfoWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        draw(context, appWidgetManager, appWidgetIds, CoreServiceManager.isRunning())
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                if (CoreServiceManager.isRunning()) {
                    LauncherManager.stopService(context)
                } else {
                    LauncherManager.startServiceFromToggle(context)
                }
            }
            AppConfig.BROADCAST_ACTION_ACTIVITY -> {
                val running = when (intent.getIntExtra("key", 0)) {
                    AppConfig.MSG_STATE_RUNNING, AppConfig.MSG_STATE_START_SUCCESS -> true
                    AppConfig.MSG_STATE_NOT_RUNNING, AppConfig.MSG_STATE_START_FAILURE, AppConfig.MSG_STATE_STOP_SUCCESS -> false
                    else -> return
                }
                val manager = AppWidgetManager.getInstance(context) ?: return
                draw(context, manager, manager.getAppWidgetIds(ComponentName(context, InfoWidget::class.java)), running)
            }
        }
    }

    private fun draw(context: Context, manager: AppWidgetManager, ids: IntArray, running: Boolean) {
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_geek_info)
        val status = try {
            val selected = MmkvManager.getSelectServer()?.let { MmkvManager.decodeServerConfig(it)?.subscriptionId }
            WidgetInfo.pick(AccountStore(GeekStorage.open(GeekGraph.ID_ACCOUNT)).services.value, selected, Instant.now())
        } catch (e: RuntimeException) {
            // A widget that cannot read the account still has to show the switch.
            LogUtil.w(AppConfig.TAG, "InfoWidget: reading the account failed", e)
            null
        }
        val locale = context.resources.configuration.locales[0]
        val connection = context.getString(if (running) R.string.geek_widget_connected else R.string.geek_widget_disconnected)
        if (status == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.geek_brand))
            views.setTextViewText(R.id.widget_detail, connection)
            views.setViewVisibility(R.id.widget_progress, android.view.View.GONE)
        } else {
            views.setTextViewText(R.id.widget_title, status.title)
            val parts = buildList {
                add(connection)
                add(
                    status.remainingGib?.let { context.getString(R.string.geek_widget_remaining, formatGib(it, locale)) }
                        ?: context.getString(R.string.geek_widget_unlimited),
                )
                status.daysLeft?.let { add(context.resources.getQuantityString(R.plurals.geek_days_left, it, it)) }
            }
            views.setTextViewText(R.id.widget_detail, parts.joinToString(" · "))
            views.setViewVisibility(R.id.widget_progress, android.view.View.VISIBLE)
            views.setProgressBar(R.id.widget_progress, PROGRESS_MAX, (status.remainingFraction * PROGRESS_MAX).toInt(), false)
        }
        views.setImageViewResource(R.id.widget_button_icon, if (running) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp)
        views.setInt(R.id.widget_button, "setBackgroundResource", if (running) R.drawable.geek_widget_button_on else R.drawable.geek_widget_button_off)
        views.setContentDescription(
            R.id.widget_button,
            context.getString(if (running) R.string.geek_widget_disconnect else R.string.geek_widget_connect),
        )
        val toggle = if (!running && QuickConnect.viaApp()) {
            QuickConnect.pendingIntent(context)
        } else {
            PendingIntent.getBroadcast(
                context,
                REQUEST_TOGGLE,
                Intent(context, InfoWidget::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        views.setOnClickPendingIntent(R.id.widget_button, toggle)
        views.setOnClickPendingIntent(
            R.id.widget_root,
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN,
                Intent(context, LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        manager.updateAppWidget(ids, views)
    }

    companion object {
        private const val ACTION_TOGGLE = "com.geekvpn.widget.TOGGLE"
        private const val REQUEST_TOGGLE = 0x6977
        private const val REQUEST_OPEN = 0x6978
        private const val PROGRESS_MAX = 1000

        /** Redraw after the account changed (called from the app process; the widget draws in its own). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, InfoWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, InfoWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}
