package com.geekvpn.scanner

import cfscan.Cfscan
import cfscan.Listener
import cfscan.Scanner
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

/**
 * The Kotlin face of the Go `cfscan` package bound into libv2ray.aar.
 *
 * Kept as the only file that imports `cfscan.*`, so the rest of the app does
 * not depend on gomobile's generated names.
 */
object CfScanNative {

    /** What a running scan reports. Called on Go threads. */
    interface Events {
        fun onResult(json: String)
        fun onProgress(tested: Long, total: Long, found: Long)
        fun onFinish(error: String?)
    }

    private val scanner: Scanner by lazy { Cfscan.newScanner() }

    fun version(): String {
        return try {
            Cfscan.version()
        } catch (e: Throwable) {
            // Throwable, not Exception: a missing native symbol surfaces as an Error.
            LogUtil.e(AppConfig.TAG, "cfscan is missing from libv2ray.aar", e)
            "Unknown"
        }
    }

    /** Starts a scan; throws when it cannot (bad config, one already running). */
    fun start(configJson: String, ranges: String, events: Events) {
        scanner.start(
            configJson,
            ranges,
            object : Listener {
                override fun onResult(resultJSON: String?) {
                    if (resultJSON != null) events.onResult(resultJSON)
                }

                override fun onProgress(tested: Long, total: Long, found: Long) = events.onProgress(tested, total, found)

                override fun onFinish(errorMessage: String?) = events.onFinish(errorMessage?.takeIf { it.isNotEmpty() })
            },
        )
    }

    fun stop() = scanner.stop()

    fun isRunning(): Boolean = scanner.isRunning
}
