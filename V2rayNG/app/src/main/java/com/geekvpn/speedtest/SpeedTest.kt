package com.geekvpn.speedtest

import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** Bits per second from bytes over nanoseconds; pure, for the tests and the gauge. */
object SpeedMath {
    fun mbps(bytes: Long, nanos: Long): Double =
        if (bytes <= 0 || nanos <= 0) 0.0 else bytes * 8.0 / (nanos / 1_000_000_000.0) / 1_000_000.0

    /** The middle of a few pings, so one slow handshake does not set the number. */
    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}

/** What the test reports while it runs: the phase and the speed so far. */
fun interface SpeedProgress {
    fun onProgress(phase: SpeedPhase, mbps: Double)
}

enum class SpeedPhase { Ping, Download, Upload }

data class SpeedResult(val pingMs: Long?, val downloadMbps: Double?, val uploadMbps: Double?)

/** The measurements, apart so the screen's logic runs in a JVM test. */
interface SpeedProbe {
    suspend fun run(throughVpn: Boolean, progress: SpeedProgress): SpeedResult
}

/**
 * Cloudflare's speed test endpoints (speed.cloudflare.com), time-boxed:
 * download and upload each stop at [PHASE_NANOS] or their byte budget.
 *
 * The app is excluded from its own VPN, so a plain request measures the
 * phone's network; while connected the test goes through the core's local
 * HTTP proxy, and so measures the VPN, like the exit address on Home.
 */
class CloudflareSpeedProbe : SpeedProbe {

    override suspend fun run(throughVpn: Boolean, progress: SpeedProgress): SpeedResult = withContext(Dispatchers.IO) {
        val client = client(throughVpn)
        val ping = ping(client)
        progress.onProgress(SpeedPhase.Ping, 0.0)
        val down = try {
            download(client, progress)
        } catch (e: IOException) {
            LogUtil.w(AppConfig.TAG, "SpeedTest: download failed (vpn=$throughVpn)", e)
            null
        }
        val up = try {
            upload(client, progress)
        } catch (e: IOException) {
            LogUtil.w(AppConfig.TAG, "SpeedTest: upload failed (vpn=$throughVpn)", e)
            null
        }
        SpeedResult(ping, down, up)
    }

    private fun client(throughVpn: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
        if (throughVpn) {
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", SettingsManager.getHttpPort())))
            val user = SettingsManager.getSocksUsername()
            val pass = SettingsManager.getSocksPassword()
            if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                builder.proxyAuthenticator { _, response ->
                    if (response.request.header("Proxy-Authorization") != null) null
                    else response.request.newBuilder().header("Proxy-Authorization", Credentials.basic(user, pass)).build()
                }
            }
        }
        return builder.build()
    }

    private suspend fun ping(client: OkHttpClient): Long? {
        val samples = mutableListOf<Long>()
        repeat(PINGS) {
            currentCoroutineContext().ensureActive()
            val start = System.nanoTime()
            try {
                client.newCall(Request.Builder().url("$BASE/__down?bytes=0").build()).execute().use { response ->
                    if (response.isSuccessful) samples += (System.nanoTime() - start) / 1_000_000
                }
            } catch (e: IOException) {
                // One lost ping; the median of the rest still stands.
                LogUtil.d(AppConfig.TAG, "SpeedTest: a ping failed", e)
            }
        }
        return SpeedMath.median(samples)
    }

    private suspend fun download(client: OkHttpClient, progress: SpeedProgress): Double {
        val start = System.nanoTime()
        var bytes = 0L
        client.newCall(Request.Builder().url("$BASE/__down?bytes=$DOWNLOAD_BYTES").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("download answered ${response.code}")
            val input = response.body.byteStream()
            val buffer = ByteArray(64 * 1024)
            var lastReport = start
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                bytes += read
                val now = System.nanoTime()
                if (now - lastReport > REPORT_NANOS) {
                    progress.onProgress(SpeedPhase.Download, SpeedMath.mbps(bytes, now - start))
                    lastReport = now
                }
                if (now - start > PHASE_NANOS) break
            }
        }
        return SpeedMath.mbps(bytes, System.nanoTime() - start)
    }

    private fun upload(client: OkHttpClient, progress: SpeedProgress): Double {
        val chunk = ByteArray(64 * 1024)
        var sent = 0L
        var start = 0L
        var end = 0L
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()

            // Unknown length: the body ends when the time box does.
            override fun contentLength() = -1L

            override fun writeTo(sink: BufferedSink) {
                start = System.nanoTime()
                var lastReport = start
                while (sent < UPLOAD_BYTES) {
                    sink.write(chunk)
                    sink.flush()
                    sent += chunk.size
                    val now = System.nanoTime()
                    if (now - lastReport > REPORT_NANOS) {
                        progress.onProgress(SpeedPhase.Upload, SpeedMath.mbps(sent, now - start))
                        lastReport = now
                    }
                    if (now - start > PHASE_NANOS) break
                }
                end = System.nanoTime()
            }
        }
        client.newCall(Request.Builder().url("$BASE/__up").post(body).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("upload answered ${response.code}")
        }
        return SpeedMath.mbps(sent, end - start)
    }

    private companion object {
        const val BASE = "https://speed.cloudflare.com"
        const val PINGS = 5
        const val DOWNLOAD_BYTES = 50_000_000L
        const val UPLOAD_BYTES = 20_000_000L
        const val PHASE_NANOS = 10_000_000_000L
        const val REPORT_NANOS = 250_000_000L
    }
}
