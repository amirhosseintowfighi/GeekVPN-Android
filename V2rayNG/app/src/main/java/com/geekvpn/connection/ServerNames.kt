package com.geekvpn.connection

import java.util.Locale

/** What a server row shows, derived from its v2rayNG remarks. */
object ServerNames {
    private const val REGIONAL_A = 0x1F1E6
    private const val REGIONAL_Z = 0x1F1FF
    private val LEADING_CODE = Regex("^([A-Za-z]{2})(?:[\\s\\-_|.·]|$)")

    /**
     * The ISO country code a panel put in the remarks: a flag emoji
     * ("🇩🇪 Germany") or a leading two-letter code ("DE-1", "de | fast").
     * Null when neither is there.
     */
    fun countryCode(remarks: String): String? {
        val codePoints = remarks.trim().codePoints().toArray()
        for (i in 0 until codePoints.size - 1) {
            val a = codePoints[i]
            val b = codePoints[i + 1]
            if (a in REGIONAL_A..REGIONAL_Z && b in REGIONAL_A..REGIONAL_Z) {
                return String(charArrayOf('A' + (a - REGIONAL_A), 'A' + (b - REGIONAL_A)))
            }
        }
        return LEADING_CODE.find(remarks.trim())?.groupValues?.get(1)?.uppercase(Locale.ROOT)
    }

    /** The remarks without their flag emoji, which the badge already shows. */
    fun title(remarks: String): String {
        val builder = StringBuilder()
        remarks.codePoints().forEach { cp ->
            if (cp !in REGIONAL_A..REGIONAL_Z) builder.appendCodePoint(cp)
        }
        return builder.toString().trim().ifEmpty { remarks.trim() }
    }

    /** "آلمان" for "DE" in Persian; the code itself when the platform has no name for it. */
    fun countryName(code: String, locale: Locale): String {
        val region = try {
            Locale.Builder().setRegion(code).build()
        } catch (_: java.util.IllformedLocaleException) {
            return code
        }
        val name = region.getDisplayCountry(locale)
        return if (name.isBlank() || name.equals(code, ignoreCase = true)) code else name
    }
}
