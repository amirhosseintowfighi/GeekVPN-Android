package com.geekvpn.scanner

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import androidx.annotation.StringRes
import com.v2ray.ang.R
import java.security.MessageDigest

/**
 * The network a clean address was found on. Which Cloudflare addresses are
 * reachable depends on the operator, so results and overrides are kept per
 * network and an address found on Irancell is not used on Hamrah-e Avval.
 *
 * Mobile networks are told apart by MCC+MNC, which needs no permission. Wi-Fi
 * networks are told apart by what DHCP handed out (DNS servers and search
 * domain): the SSID would need the location permission, which a VPN app has
 * no business asking for.
 */
data class NetworkIdentity(
    val key: String,
    val kind: Kind,
    /** Our name for a known Iranian operator. */
    @param:StringRes val operatorLabel: Int? = null,
    /** What the phone calls the operator, for one we do not know. */
    val operatorName: String? = null,
) {
    enum class Kind { Wifi, Mobile, Other }

    companion object {
        val UNKNOWN = NetworkIdentity("other", Kind.Other)

        /** The network underneath: the app itself is excluded from the VPN, so this is not the tunnel. */
        fun current(context: Context): NetworkIdentity {
            val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return UNKNOWN
            val network = connectivity.activeNetwork ?: return UNKNOWN
            val caps = connectivity.getNetworkCapabilities(network) ?: return UNKNOWN
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    val telephony = context.getSystemService(TelephonyManager::class.java)
                    val mccMnc = telephony?.networkOperator.orEmpty()
                    mobile(mccMnc, telephony?.networkOperatorName)
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    val link = connectivity.getLinkProperties(network)
                    wifi(link?.dnsServers?.map { it.hostAddress.orEmpty() }.orEmpty(), link?.domains)
                }
                else -> UNKNOWN
            }
        }

        fun mobile(mccMnc: String, name: String?): NetworkIdentity =
            if (mccMnc.isBlank()) {
                UNKNOWN
            } else {
                NetworkIdentity("mobile:$mccMnc", Kind.Mobile, IranOperators.labelOf(mccMnc), name?.takeIf { it.isNotBlank() })
            }

        fun wifi(dnsServers: List<String>, domains: String?): NetworkIdentity {
            val fingerprint = (dnsServers.sorted() + listOfNotNull(domains)).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray())
            return NetworkIdentity("wifi:" + digest.take(6).joinToString("") { "%02x".format(it) }, Kind.Wifi)
        }
    }
}

/** Iran's mobile operators by MCC+MNC (432-xx). */
object IranOperators {
    private val labels = mapOf(
        "43211" to R.string.geek_operator_mci,
        "43235" to R.string.geek_operator_irancell,
        "43220" to R.string.geek_operator_rightel,
        "43232" to R.string.geek_operator_taliya,
    )

    @StringRes
    fun labelOf(mccMnc: String): Int? = labels[mccMnc]
}
