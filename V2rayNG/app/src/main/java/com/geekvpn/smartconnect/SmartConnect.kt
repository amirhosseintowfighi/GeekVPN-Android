package com.geekvpn.smartconnect

/** One way to connect: a config, and the clean address it uses instead of its own (null: its own). */
data class Choice(val guid: String, val ip: String? = null)

/** A [Choice] and its real delay: > 0 milliseconds, anything else failed. */
data class Measured(val choice: Choice, val delayMs: Long)

/** What smart connect is doing, for the Home screen to say. */
sealed interface SmartStage {
    /** A short clean-IP scan, since this network has no fresh results. */
    data object FindingIp : SmartStage

    /** v2rayNG's real-delay test over the service's configs (and clean addresses). */
    data object Testing : SmartStage

    /** Connecting with the [attempt]th best choice of [of]. */
    data class Connecting(val attempt: Int, val of: Int) : SmartStage
}

sealed interface SmartResult {
    /** [untested]: nothing answered the delay test, so the selected config was tried as is. */
    data class Connected(val choice: Choice, val delayMs: Long, val untested: Boolean = false) : SmartResult

    data object NoServers : SmartResult

    /** Every attempt started a connection that did not carry traffic. */
    data class Failed(val attempts: Int) : SmartResult
}

/**
 * What smart connect needs from the rest of the app. The Home screen's
 * implementation drives v2rayNG's test service and `LauncherManager`; the
 * VPN process's failover monitor uses the running core directly.
 */
interface SmartConnectPorts {
    /** The GUIDs of [groupId]'s configs, in list order. */
    fun servers(groupId: String): List<String>

    /**
     * The clean-IP scanner applies to this config: a direct service or manual
     * link whose domain is verified to be on Cloudflare. May resolve DNS.
     */
    suspend fun scannable(guid: String): Boolean

    /** Clean addresses already found for [guid] on this network and still fresh, best first. */
    fun freshIps(guid: String): List<String>

    /** A short scan for [guid]'s domain; the addresses it found, best first. May find none. */
    suspend fun quickScan(guid: String): List<String>

    /** Make [guid] use [ip] on this network from now on (connection and delay test alike); null: its own address. */
    fun useIp(guid: String, ip: String?, delayMs: Long)

    /** v2rayNG's real-delay test of [guids], each with whatever address it now uses. */
    suspend fun measure(guids: List<String>): Map<String, Long>

    /** Select [guid], connect (or reconnect) with it and report whether traffic got through. */
    suspend fun connect(guid: String): Boolean
}

/**
 * GeekVPN's smart connect (spec §3.6), apart from the UI and the VPN service:
 *
 * 1. For CDN-fronted configs, clean addresses for this network: the fresh
 *    cached ones, else a short scan.
 * 2. v2rayNG's real-delay test over every config on its own address, then
 *    every config × clean address for the CDN-fronted ones, so a clean
 *    address is only used where it beats the config's own.
 * 3. Connect with the best, then the next, [maxAttempts] at most.
 */
class SmartConnectUseCase(
    private val ports: SmartConnectPorts,
    private val maxIps: Int = MAX_IPS,
    private val maxAttempts: Int = MAX_ATTEMPTS,
) {
    /**
     * Picks and connects. [fallback] is the config to try as it stands when
     * nothing answers the test (the delay-test URL itself can be what fails).
     */
    suspend fun run(groupId: String, fallback: String?, onStage: (SmartStage) -> Unit): SmartResult {
        val guids = ports.servers(groupId)
        if (guids.isEmpty()) return SmartResult.NoServers
        val ranked = rank(guids, onStage)
        if (ranked.isEmpty()) {
            val guid = fallback?.takeIf { it in guids } ?: guids.first()
            onStage(SmartStage.Connecting(1, 1))
            return if (ports.connect(guid)) SmartResult.Connected(Choice(guid), 0, untested = true) else SmartResult.Failed(1)
        }
        val attempts = ranked.take(maxAttempts)
        attempts.forEachIndexed { index, measured ->
            onStage(SmartStage.Connecting(index + 1, attempts.size))
            val choice = measured.choice
            if (choice.guid in lastScannable) ports.useIp(choice.guid, choice.ip, measured.delayMs)
            if (ports.connect(choice.guid)) return SmartResult.Connected(choice, measured.delayMs)
        }
        return SmartResult.Failed(attempts.size)
    }

    /**
     * Every choice that answered, fastest first (ties keep list order).
     * Leaves each CDN-fronted config on its own best address, so a later
     * manual pick or the failover starts from there.
     */
    suspend fun rank(guids: List<String>, onStage: (SmartStage) -> Unit = {}): List<Measured> {
        val scannable = guids.filter { ports.scannable(it) }
        lastScannable = scannable.toSet()
        var ips = scannable.flatMap { ports.freshIps(it) }.distinct().take(maxIps)
        if (scannable.isNotEmpty() && ips.isEmpty()) {
            onStage(SmartStage.FindingIp)
            ips = ports.quickScan(scannable.first()).distinct().take(maxIps)
        }
        onStage(SmartStage.Testing)
        val measured = mutableListOf<Measured>()
        // Round 0: every config on its own address.
        scannable.forEach { ports.useIp(it, null, 0) }
        val own = ports.measure(guids)
        guids.forEach { measured += Measured(Choice(it), own[it] ?: 0) }
        // Then one round per clean address: the override is per config, so a
        // config can only be tested with one address at a time.
        ips.forEach { ip ->
            scannable.forEach { ports.useIp(it, ip, 0) }
            val delays = ports.measure(scannable)
            scannable.forEach { measured += Measured(Choice(it, ip), delays[it] ?: 0) }
        }
        val answered = measured.filter { it.delayMs > 0 }
        scannable.forEach { guid ->
            val best = answered.filter { it.choice.guid == guid }.minByOrNull { it.delayMs }
            ports.useIp(guid, best?.choice?.ip, best?.delayMs ?: 0)
        }
        return best(measured)
    }

    /** The configs the last [rank] treated as CDN-fronted; only their address is ever changed. */
    private var lastScannable: Set<String> = emptySet()

    companion object {
        /** Clean addresses tried per config: each is one more round of the delay test. */
        const val MAX_IPS = 3

        /** Spec §3.6: at most three tries before telling the customer. */
        const val MAX_ATTEMPTS = 3

        /** The choices that answered, fastest first; a stable sort keeps list order on ties. */
        fun best(measured: List<Measured>): List<Measured> =
            measured.filter { it.delayMs > 0 }.sortedBy { it.delayMs }
    }
}
