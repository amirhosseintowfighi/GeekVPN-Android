package com.geekvpn.support

/** What the report says about the phone and the connection; nothing that identifies a server. */
data class ReportFacts(
    val appVersion: String,
    val versionCode: Int,
    val android: String,
    val sdk: Int,
    val device: String,
    /** `wifi`, `mobile 432-11` (MCC-MNC names the operator), or `none`. */
    val network: String,
    /** VPN, proxy only or root. */
    val mode: String,
    val route: String,
    val autoServer: Boolean,
    val connected: Boolean,
    val core: String,
    /** `vless ws tls :443 (tunnel)`: shape and port only, never the address. */
    val config: String?,
    val lastFailure: String?,
    /** How long ago [lastFailure] came, in minutes. */
    val lastFailureMinutesAgo: Long?,
)

/**
 * The technical half of "گزارش مشکل": the customer's own words, then facts
 * and the end of the app's log, with everything that could point at a server
 * or a person taken out. Pure, so what leaves the phone is tested.
 *
 * The ticket API accepts 4000 characters; the log gets whatever the rest
 * leaves, newest lines kept.
 */
object ProblemReport {
    const val MAX_CHARS = 3_900

    fun compose(description: String, facts: ReportFacts, log: List<String>, limit: Int = MAX_CHARS): String {
        val head = buildString {
            appendLine(description.trim())
            appendLine()
            appendLine("--- report ---")
            appendLine("app: ${facts.appVersion} (${facts.versionCode})")
            appendLine("android: ${facts.android} (sdk ${facts.sdk}), ${facts.device}")
            appendLine("network: ${facts.network}")
            appendLine("mode: ${facts.mode}, route: ${facts.route}, auto server: ${if (facts.autoServer) "on" else "off"}")
            appendLine("connected: ${if (facts.connected) "yes" else "no"}")
            appendLine("core: ${facts.core}")
            facts.config?.let { appendLine("config: ${redact(it)}") }
            facts.lastFailure?.let { failure ->
                val ago = facts.lastFailureMinutesAgo?.let { " ($it min ago)" }.orEmpty()
                appendLine("last failure$ago: ${redact(failure)}")
            }
        }
        val room = limit - head.length - LOG_HEADER.length
        if (room <= 0) return head.take(limit)
        val kept = ArrayDeque<String>()
        var used = 0
        for (line in log.asReversed()) {
            val clean = redact(line).take(MAX_LINE)
            if (used + clean.length + 1 > room) break
            kept.addFirst(clean)
            used += clean.length + 1
        }
        return if (kept.isEmpty()) head.trimEnd() else head + LOG_HEADER + kept.joinToString("\n")
    }

    /**
     * Links, UUIDs, IP addresses, host names, e-mail addresses and long
     * key-like strings become placeholders. Package and class names are
     * dotted too, so the platform's and the app's own are left readable.
     */
    fun redact(text: String): String {
        var out = URL.replace(text, "<link>")
        out = EMAIL.replace(out, "<email>")
        out = UUID.replace(out, "<uuid>")
        out = BEARER.replace(out, "Bearer <token>")
        out = IPV6.replace(out, "<ip>")
        out = IPV4.replace(out, "<ip>")
        out = HOST.replace(out) { match ->
            // Host names in configs and the core's messages are lower case;
            // `Refresh.authenticate` in a stack frame is code.
            val code = CODE_PREFIXES.any { match.value.startsWith(it) } ||
                match.value.substringAfterLast('.') in CODE_SUFFIXES ||
                match.value.any { it.isUpperCase() }
            if (code) match.value else "<host>"
        }
        out = SECRET.replace(out, "<secret>")
        return out
    }

    private const val LOG_HEADER = "--- log ---\n"
    private const val MAX_LINE = 300

    private val URL = Regex("""\b[a-zA-Z][a-zA-Z0-9+.\-]*://\S+""")
    private val EMAIL = Regex("""\b[\w.+\-]+@[\w\-]+(\.[\w\-]+)+\b""")
    private val UUID = Regex("""\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b""")
    private val BEARER = Regex("""Bearer\s+\S+""")

    // Four or more groups, or a "::": a clock time ("12:34:56") has three.
    private val IPV6 = Regex("""\b(?:[0-9a-fA-F]{1,4}:){3,7}[0-9a-fA-F]{1,4}\b|[0-9a-fA-F:]*::[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*""")
    private val IPV4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val HOST = Regex("""\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}\b""")

    // Base64 or hex runs of 32+ characters: keys, short IDs, tokens.
    private val SECRET = Regex("""[A-Za-z0-9+/_\-]{32,}={0,2}""")

    /** File names in stack traces and the core's messages: `GeekApi.kt:230`, `geoip.dat`. */
    private val CODE_SUFFIXES = setOf("kt", "java", "go", "so", "dat", "json", "apk")

    private val CODE_PREFIXES = listOf(
        "java.", "javax.", "kotlin.", "kotlinx.", "android.", "androidx.", "dalvik.", "com.android.",
        "com.v2ray.", "com.geekvpn.", "com.google.", "com.tencent.", "okhttp3.", "libv2ray.", "cfscan.", "go.",
    )
}
