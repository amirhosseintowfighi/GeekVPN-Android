package com.geekvpn.update

import com.geekvpn.api.AppApk
import com.geekvpn.api.AppVersionResponse

/** The APK this phone would install. */
data class UpdateApk(
    val abi: String,
    val fileName: String,
    val url: String,
    /** Lowercase hex; checked before installing when the server gave one. */
    val sha256: String?,
    val sizeBytes: Long,
)

/** A newer version, and whether this one may still be used. */
data class UpdateOffer(
    val versionName: String,
    val notes: String,
    val apk: UpdateApk,
    /** This version is below the server's minimum: no "later". */
    val required: Boolean,
)

/**
 * Deciding whether to offer an update. Pure, so the rules are tested without
 * a phone: which version is newer, which APK fits the phone, and when the
 * offer is mandatory.
 */
object UpdatePlan {
    /**
     * @param current this build's `versionName`, e.g. `1.1.0`.
     * @param abis the phone's ABIs, preferred first (`Build.SUPPORTED_ABIS`).
     */
    fun offer(current: String, response: AppVersionResponse, abis: List<String>): UpdateOffer? {
        val latest = response.latest ?: return null
        val version = latest.versionName?.trim()?.removePrefix("v").orEmpty()
        if (version.isEmpty() || compare(version, current) <= 0) return null
        val apk = pick(latest.apks.orEmpty(), abis) ?: return null
        val minimum = response.minVersion?.trim()?.removePrefix("v").orEmpty()
        return UpdateOffer(
            versionName = version,
            notes = latest.notes?.trim().orEmpty(),
            apk = apk,
            required = minimum.isNotEmpty() && compare(current, minimum) < 0,
        )
    }

    /** The first APK built for one of [abis], in the phone's order; else the universal one. */
    fun pick(apks: List<AppApk>, abis: List<String>): UpdateApk? {
        val usable = apks.mapNotNull { apk ->
            val abi = apk.abi ?: return@mapNotNull null
            val url = apk.url?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            UpdateApk(
                abi = abi,
                fileName = apk.fileName?.takeIf { it.isNotBlank() } ?: "GeekVPN_$abi.apk",
                url = url,
                sha256 = apk.sha256?.lowercase()?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } },
                sizeBytes = apk.sizeBytes ?: 0,
            )
        }
        return abis.firstNotNullOfOrNull { abi -> usable.firstOrNull { it.abi == abi } }
            ?: usable.firstOrNull { it.abi == UNIVERSAL }
    }

    /**
     * Compares `1.10.0` with `1.9.2` by number, segment by segment; a missing
     * segment counts as 0 and anything after `-` (`-staging`) is ignored.
     */
    fun compare(a: String, b: String): Int {
        val left = segments(a)
        val right = segments(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private fun segments(version: String): List<Int> =
        version.trim().removePrefix("v").substringBefore('-').split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }

    private const val UNIVERSAL = "universal"
}
