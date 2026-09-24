package com.geekvpn.scanner

import cfscan.Cfscan
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

/**
 * The Kotlin face of the Go `cfscan` package bound into libv2ray.aar.
 *
 * Kept as the only file that imports `cfscan.*`, so the rest of the app does
 * not depend on gomobile's generated names.
 */
object CfScanNative {

    fun version(): String {
        return try {
            Cfscan.version()
        } catch (e: Throwable) {
            // Throwable, not Exception: a missing native symbol surfaces as an Error.
            LogUtil.e(AppConfig.TAG, "cfscan is missing from libv2ray.aar", e)
            "Unknown"
        }
    }
}
