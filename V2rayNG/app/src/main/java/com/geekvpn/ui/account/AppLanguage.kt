package com.geekvpn.ui.account

/**
 * «زبان»: the languages GeekVPN ships (`androidResources.localeFilters`), as
 * v2rayNG's language codes (`com.v2ray.ang.enums.Language`), which own the
 * stored setting and apply it.
 */
enum class AppLanguage(val code: String) {
    Persian("fa"),
    English("en"),

    /** The phone's language; Persian when it is Persian, English otherwise. */
    System("auto");

    companion object {
        /** v2rayNG's stored code; nothing stored is Persian, its default here, and a language not shipped follows the phone. */
        fun of(code: String?): AppLanguage = when (code) {
            null, "" -> Persian
            else -> entries.firstOrNull { it.code == code } ?: System
        }
    }
}
