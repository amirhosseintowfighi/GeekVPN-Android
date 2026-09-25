package com.geekvpn.connection

/** Where the connection is, as the Home screen tells it. */
enum class ConnectionPhase {
    Off,

    /** Real-delay test before connecting, to pick the server ("سرور: خودکار"). */
    Testing,
    Connecting,
    On,
    Stopping,
}

/** What the daemon reported, stripped of v2rayNG's transport. */
enum class ServiceSignal { Running, NotRunning, StartSuccess, StartFailure, StopSuccess }

object ConnectionLogic {
    /**
     * The next phase after a daemon signal.
     *
     * `NotRunning` is also the daemon's answer to "are you there?" when the
     * screen registers, which can land after the user already tapped connect;
     * it must not throw a start in progress back to Off.
     */
    fun next(phase: ConnectionPhase, signal: ServiceSignal): ConnectionPhase = when (signal) {
        ServiceSignal.Running, ServiceSignal.StartSuccess ->
            if (phase == ConnectionPhase.Stopping) phase else ConnectionPhase.On
        ServiceSignal.NotRunning ->
            if (phase == ConnectionPhase.Testing || phase == ConnectionPhase.Connecting) phase else ConnectionPhase.Off
        ServiceSignal.StartFailure, ServiceSignal.StopSuccess -> ConnectionPhase.Off
    }

    /** One server's real-delay result: > 0 milliseconds, 0 untested, < 0 failed. */
    data class Delay(val guid: String, val millis: Long)

    /** The fastest server that answered, or null when none did. Ties keep list order. */
    fun best(delays: List<Delay>): String? =
        delays.filter { it.millis > 0 }.minByOrNull { it.millis }?.guid
}
