package com.geekvpn.account

import com.geekvpn.api.ApiException

/** Why a backend call failed, in the terms the refresh message uses. Pure, for the tests. */
enum class ApiFailureKind {
    /** No answer at all: the server or the path to it is down. */
    Network,

    /** 401/403: the sign-in is no longer accepted. */
    Session,

    /** 429: too many requests just now. */
    Busy,

    /** 5xx. */
    Server,

    /** Something answered, but not the API (a captive portal, an HTML error page). */
    BadResponse,

    /** Any other status. */
    Other,
    ;

    companion object {
        fun of(e: ApiException): ApiFailureKind {
            val status = e.status
            return when {
                e.badBody -> BadResponse
                status == null -> Network
                status == 401 || status == 403 -> Session
                status == 429 -> Busy
                status >= 500 -> Server
                else -> Other
            }
        }
    }
}

/**
 * What the services refresh did: whether the service list was read, and how
 * many service links were downloaded. The refresh downloads the servers even
 * when the list cannot be read, so the two are reported separately.
 */
data class RefreshOutcome(
    /** Null when the service list was read. */
    val failure: ApiFailureKind?,
    val status: Int? = null,
    /** The server's own sentence for the failure, when it sent one. */
    val serverText: String? = null,
    val fetched: Int = 0,
    val fetchFailures: Int = 0,
) {
    /** A refresh the customer did not ask for only speaks up when something went wrong. */
    val worthSaying: Boolean get() = failure != null || fetchFailures > 0
}
