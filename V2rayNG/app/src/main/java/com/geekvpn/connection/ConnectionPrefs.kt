package com.geekvpn.connection

import com.geekvpn.GeekStorage
import com.geekvpn.smartconnect.FailoverThreshold
import com.tencent.mmkv.MMKV

/**
 * GeekVPN's own connection choices, beside v2rayNG's settings: whether the
 * server is picked automatically, the route mode, and when the running
 * connection started (the daemon does not say, and the timer must survive
 * the app being closed and reopened).
 *
 * Read by the VPN process too (the failover monitor); the file is multi-process.
 */
class ConnectionPrefs(private val storage: MMKV) {
    var autoServer: Boolean
        get() = storage.decodeBool(KEY_AUTO_SERVER, true)
        set(value) {
            storage.encode(KEY_AUTO_SERVER, value)
        }

    /** "بروزرسانی خودکار سرویس‌ها": sync the account each time the app opens. */
    var autoUpdate: Boolean
        get() = storage.decodeBool(KEY_AUTO_UPDATE, true)
        set(value) {
            storage.encode(KEY_AUTO_UPDATE, value)
        }

    /** Null until the user (or first launch) picks one. */
    var routeMode: RouteMode?
        get() = RouteMode.of(storage.decodeString(KEY_ROUTE))
        set(value) {
            if (value == null) storage.removeValueForKey(KEY_ROUTE) else storage.encode(KEY_ROUTE, value.key)
        }

    /** Wall-clock millis the running connection started; 0 when not connected. */
    var connectedSince: Long
        get() = storage.decodeLong(KEY_CONNECTED_SINCE, 0L)
        set(value) {
            storage.encode(KEY_CONNECTED_SINCE, value)
        }

    /** When the running connection moves to another server; only with [autoServer] on. */
    var failoverThreshold: FailoverThreshold
        get() = FailoverThreshold.of(storage.decodeString(KEY_FAILOVER))
        set(value) {
            storage.encode(KEY_FAILOVER, value.key)
        }

    companion object {
        private const val STORE_ID = "GEEK_CONNECTION"

        fun open(): ConnectionPrefs = ConnectionPrefs(GeekStorage.open(STORE_ID))

        private const val KEY_FAILOVER = "failover_threshold"
        private const val KEY_AUTO_SERVER = "auto_server"
        private const val KEY_AUTO_UPDATE = "auto_update"
        private const val KEY_ROUTE = "route_mode"
        private const val KEY_CONNECTED_SINCE = "connected_since"
    }
}
