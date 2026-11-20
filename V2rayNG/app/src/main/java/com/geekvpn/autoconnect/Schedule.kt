package com.geekvpn.autoconnect

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.geekvpn.connection.ConnectionPrefs
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * "زمان‌بندی روزانه": connect at one time of day and disconnect at another,
 * every day. Inexact alarms (a window of a few minutes), which need no
 * exact-alarm permission; the receiver runs in the VPN process, like the
 * Wi-Fi trigger, and starts the core the same way ([AutoConnect]).
 */
object Schedule {
    /** The next thing to do: connect ([connect] true) or disconnect, at [at]. */
    data class Event(val at: LocalDateTime, val connect: Boolean)

    /**
     * After [now], the sooner of today's or tomorrow's start and end. Minutes
     * of the day; start == end is no schedule. Pure, for the tests.
     */
    fun next(now: LocalDateTime, startMinute: Int, endMinute: Int): Event? {
        if (startMinute == endMinute) return null
        fun upcoming(minute: Int): LocalDateTime {
            val today = now.toLocalDate().atTime(LocalTime.of(minute / 60, minute % 60))
            return if (today.isAfter(now)) today else today.plusDays(1)
        }
        val start = upcoming(startMinute)
        val end = upcoming(endMinute)
        return if (start.isBefore(end)) Event(start, true) else Event(end, false)
    }

    /** Whether [now] falls in the connected part of the day (it may cross midnight). */
    fun inWindow(now: LocalTime, startMinute: Int, endMinute: Int): Boolean {
        val minute = now.hour * 60 + now.minute
        return if (startMinute < endMinute) minute in startMinute until endMinute else minute >= startMinute || minute < endMinute
    }

    /** Sets (or clears) the next alarm to match the settings; after a change, at boot, and after each alarm. */
    fun arm(context: Context, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        val prefs = ConnectionPrefs.open()
        if (!prefs.scheduleOn) return
        val event = next(now, prefs.scheduleStart, prefs.scheduleEnd) ?: return
        val at = event.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, pending)
        LogUtil.i(AppConfig.TAG, "Schedule: next ${if (event.connect) "connect" else "disconnect"} at ${event.at}")
    }

    /** Runs in the VPN process when an alarm fires: does what the time of day says, then arms the next. */
    internal suspend fun onAlarm(context: Context) {
        val prefs = ConnectionPrefs.open()
        if (prefs.scheduleOn) {
            val connect = inWindow(LocalTime.now(), prefs.scheduleStart, prefs.scheduleEnd)
            val running = CoreServiceManager.isRunning()
            when {
                connect && !running -> AutoConnect.startOrAsk(context)
                !connect && running -> LauncherManager.stopService(context)
            }
            LogUtil.i(AppConfig.TAG, "Schedule: alarm, connect=$connect running=$running")
        }
        arm(context)
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ScheduleReceiver::class.java).setAction(ACTION_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    internal const val ACTION_ALARM = "com.geekvpn.action.SCHEDULE"
    private const val REQUEST_CODE = 0x6e6e
    private const val WINDOW_MS = 5 * 60_000L
}

/** The schedule's alarm; not exported, in `:daemon`. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Schedule.ACTION_ALARM) return
        val result = goAsync()
        AutoConnect.scope.launch {
            try {
                withTimeoutOrNull(RECEIVER_BUDGET_MS) { Schedule.onAlarm(context.applicationContext) }
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        const val RECEIVER_BUDGET_MS = 8_000L
    }
}
