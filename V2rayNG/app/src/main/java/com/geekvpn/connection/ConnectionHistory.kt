package com.geekvpn.connection

import com.geekvpn.GeekStorage
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV

/**
 * One connection in «تاریخچه‌ی اتصال»: from the core starting to it stopping.
 * Every field has a default so Gson builds it through the no-argument
 * constructor; the field names are the stored format.
 */
data class ConnectionRecord(
    val startedAt: Long = 0,
    /** Null while the connection is still up. */
    val endedAt: Long? = null,
    /** Last time the VPN process was seen alive with this connection; the end of an interrupted one. */
    val lastSeenAt: Long = 0,
    /** The server's remarks when the connection started. */
    val server: String? = null,
    /** The server it ended on, when failover moved it. */
    val lastServer: String? = null,
    val failovers: Int = 0,
    /** Traffic through the VPN during the connection. */
    val bytes: Long = 0,
    /** [End]'s name; null while up. */
    val end: String? = null,
) {
    enum class End {
        /** Stopped by the customer, a tile, a schedule or the system. */
        Stopped,

        /** «سقف مصرف» disconnected it. */
        DataCap,

        /** The VPN process died without stopping the core (killed, crashed, rebooted). */
        Interrupted,
    }

    val endReason: End? get() = End.entries.firstOrNull { it.name == end }
    val open: Boolean get() = endedAt == null

    /** Milliseconds, up to [now] while open. */
    fun duration(now: Long): Long = ((endedAt ?: now) - startedAt).coerceAtLeast(0)
}

/** The list operations, newest first; pure, for the tests. */
object HistoryLog {
    const val MAX_RECORDS = 200
    const val KEEP_MS = 30L * 24 * 60 * 60 * 1000

    /** A new connection; one left open by a dead process is closed as interrupted first. */
    fun begin(records: List<ConnectionRecord>, now: Long, server: String?): List<ConnectionRecord> {
        val closed = records.map { record ->
            if (record.open) record.copy(endedAt = record.lastSeenAt.coerceAtLeast(record.startedAt), end = ConnectionRecord.End.Interrupted.name) else record
        }
        val fresh = ConnectionRecord(startedAt = now, lastSeenAt = now, server = server)
        return (listOf(fresh) + closed).filter { now - it.startedAt <= KEEP_MS }.take(MAX_RECORDS)
    }

    fun touch(records: List<ConnectionRecord>, now: Long, bytes: Long): List<ConnectionRecord> =
        updateOpen(records) { it.copy(lastSeenAt = now, bytes = bytes) }

    fun failover(records: List<ConnectionRecord>, server: String?): List<ConnectionRecord> =
        updateOpen(records) { it.copy(failovers = it.failovers + 1, lastServer = server) }

    fun end(records: List<ConnectionRecord>, now: Long, bytes: Long, reason: ConnectionRecord.End): List<ConnectionRecord> =
        updateOpen(records) { it.copy(endedAt = now, lastSeenAt = now, bytes = bytes, end = reason.name) }

    /** Only the newest record can be open; nothing changes when it is not (cleared meanwhile). */
    private fun updateOpen(records: List<ConnectionRecord>, change: (ConnectionRecord) -> ConnectionRecord): List<ConnectionRecord> {
        val first = records.firstOrNull() ?: return records
        if (!first.open) return records
        return listOf(change(first)) + records.drop(1)
    }
}

/**
 * «تاریخچه‌ی اتصال»: written by the VPN process ([com.geekvpn.smartconnect.FailoverMonitor],
 * whose life is the core's), read and cleared by the app. Its own MMKV file,
 * multi-process like the rest of GeekVPN's.
 */
class ConnectionHistory(private val storage: MMKV) {
    private val gson = Gson()

    fun records(): List<ConnectionRecord> {
        val json = storage.decodeString(KEY_RECORDS) ?: return emptyList()
        return try {
            gson.fromJson<List<ConnectionRecord>>(json, object : TypeToken<List<ConnectionRecord>>() {}.type).orEmpty()
        } catch (_: JsonParseException) {
            emptyList()
        }
    }

    @Synchronized
    fun update(change: (List<ConnectionRecord>) -> List<ConnectionRecord>) {
        val before = records()
        val after = change(before)
        if (after != before) storage.encode(KEY_RECORDS, gson.toJson(after))
    }

    fun clear() {
        storage.removeValueForKey(KEY_RECORDS)
    }

    companion object {
        private const val STORE_ID = "GEEK_HISTORY"
        private const val KEY_RECORDS = "records"

        fun open(): ConnectionHistory = ConnectionHistory(GeekStorage.open(STORE_ID))
    }
}
