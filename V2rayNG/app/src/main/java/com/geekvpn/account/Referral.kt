package com.geekvpn.account

/** The invite link and the terms, as the bot and the Mini App state them. */
object Referral {
    /** The bot's deep link (`/start ref_<code>`); just the code when there is no bot configured. */
    fun link(botUsername: String, code: String): String =
        if (botUsername.isBlank()) code else "https://t.me/$botUsername?start=ref_$code"

    /** Basis points as a percentage: 500 -> 5.0, 250 -> 2.5. */
    fun percent(bps: Int): Double = bps / 100.0
}
