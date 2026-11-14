package com.geekvpn.diagnose

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The checks "عیب‌یاب اتصال" runs, in this order. */
enum class Check(val key: String) {
    /** The phone has a network that says it reaches the internet. */
    Network("network"),

    /** An Iranian site answers directly. */
    Domestic("domestic"),

    /** A site abroad answers directly. */
    International("international"),

    /** The selected server's name resolves and its port accepts a connection. */
    Server("server"),

    /** Only while connected: a page loads through the tunnel. */
    Tunnel("tunnel"),

    /** The GeekVPN backend answers (sign-in, the shop, sync). */
    Api("api"),
}

/** How one check came out. */
enum class Outcome {
    Ok,
    Failed,

    /** Did not apply: no server selected, or not connected. */
    Skipped,

    /** Server only: its name resolves to a private address, the filter's usual answer. */
    Filtered,
}

/** What the checks add up to, most important first; see [Diagnosis.verdict]. */
enum class Verdict(val key: String) {
    NoNetwork("no_network"),
    NoInternet("no_internet"),
    NoServer("no_server"),
    ServerFiltered("server_filtered"),

    /** The world is cut off but Iran is not: a tunnel service still works then. */
    InternationalDown("international_down"),
    ServerDown("server_down"),
    TunnelBroken("tunnel_broken"),

    /** The connection is fine; only the app's own backend is out of reach. */
    ApiDown("api_down"),
    AllGood("all_good"),
}

/** Each check, done for real by [AndroidDiagnosePorts]; apart so [Diagnosis] runs in tests. */
interface DiagnosePorts {
    val connected: Boolean
    suspend fun network(): Outcome
    suspend fun domestic(): Outcome
    suspend fun international(): Outcome
    suspend fun server(): Outcome
    suspend fun tunnel(): Outcome
    suspend fun api(): Outcome
}

object Diagnosis {
    /**
     * Runs every check in [Check] order, reporting each result as it comes
     * (null while it runs). A check that throws counts as failed.
     */
    suspend fun run(ports: DiagnosePorts, onResult: (Check, Outcome?) -> Unit): Map<Check, Outcome> {
        val results = linkedMapOf<Check, Outcome>()
        for (check in Check.entries) {
            currentCoroutineContext().ensureActive()
            onResult(check, null)
            val outcome = try {
                when (check) {
                    Check.Network -> ports.network()
                    Check.Domestic -> ports.domestic()
                    Check.International -> ports.international()
                    Check.Server -> ports.server()
                    Check.Tunnel -> if (ports.connected) ports.tunnel() else Outcome.Skipped
                    Check.Api -> ports.api()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Outcome.Failed
            }
            results[check] = outcome
            onResult(check, outcome)
        }
        return results
    }

    /** The one thing to tell the customer, from what failed first and matters most. */
    fun verdict(results: Map<Check, Outcome>): Verdict {
        fun failed(check: Check) = results[check] == Outcome.Failed
        return when {
            failed(Check.Network) -> Verdict.NoNetwork
            failed(Check.Domestic) && failed(Check.International) -> Verdict.NoInternet
            results[Check.Server] == Outcome.Skipped -> Verdict.NoServer
            results[Check.Server] == Outcome.Filtered -> Verdict.ServerFiltered
            failed(Check.Server) && failed(Check.International) -> Verdict.InternationalDown
            failed(Check.Server) -> Verdict.ServerDown
            failed(Check.Tunnel) -> Verdict.TunnelBroken
            failed(Check.International) -> Verdict.InternationalDown
            failed(Check.Api) -> Verdict.ApiDown
            else -> Verdict.AllGood
        }
    }

    /** One line for the support ticket: no addresses, only outcomes. */
    fun summary(results: Map<Check, Outcome>): String =
        "diagnosis: " + verdict(results).key + " (" +
            results.entries.joinToString(" ") { (check, outcome) -> "${check.key}=${outcome.name.lowercase()}" } + ")"
}
