package com.geekvpn

import com.geekvpn.account.AccountStore
import com.geekvpn.account.AccountSync
import com.geekvpn.api.GeekApi
import com.geekvpn.api.TokenHolder
import com.geekvpn.api.TokenPair
import com.geekvpn.auth.LinkLogin
import com.geekvpn.connection.ConnectionPrefs
import com.geekvpn.auth.SecureStore
import com.geekvpn.auth.Session
import com.geekvpn.auth.SessionStore
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The GeekVPN account objects, one per process, built on first use (after
 * `AngApplication` has initialised MMKV). Only the main app process touches
 * them; see [GeekApi] for why.
 */
object GeekGraph {
    val session: SessionStore by lazy {
        SessionStore(
            plain = storage(ID_ACCOUNT),
            secure = SecureStore(storage(ID_SECURE)),
        )
    }

    private fun storage(id: String): MMKV = GeekStorage.open(id)

    val api: GeekApi by lazy {
        GeekApi(
            baseUrl = BuildConfig.API_BASE,
            tokens = object : TokenHolder {
                override fun accessToken() = session.accessToken()
                override fun refreshToken() = session.refreshToken()
                override fun onRefreshed(tokens: TokenPair) = session.updateTokens(tokens)
                override fun onSessionLost() {
                    // Logged out elsewhere, or the device was disconnected from the bot.
                    LogUtil.i(AppConfig.TAG, "GeekGraph: session rejected by the server, signing out")
                    accountSync.removeAllNow()
                    session.signOut()
                }
            },
        )
    }

    val linkLogin: LinkLogin by lazy { LinkLogin(api) }

    val accountStore: AccountStore by lazy { AccountStore(storage(ID_ACCOUNT)) }

    val connectionPrefs: ConnectionPrefs by lazy { ConnectionPrefs.open() }

    val accountSync: AccountSync by lazy { AccountSync(api, accountStore) }

    /**
     * Owns the background refresh started when the app opens. Process-wide
     * because the launcher activity that asks for it finishes at once; a newer
     * request while one runs is dropped rather than stacked.
     */
    private val launchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineName("geek-launch-sync"))
    private var launchJob: Job? = null
    private var lastLaunchSyncAt = 0L

    fun syncOnLaunch() {
        if (session.session.value !is Session.SignedIn || !connectionPrefs.autoUpdate) return
        if (launchJob?.isActive == true) return
        if (System.currentTimeMillis() - lastLaunchSyncAt < LAUNCH_SYNC_INTERVAL_MS) return
        launchJob = launchScope.launch {
            try {
                accountSync.sync()
                lastLaunchSyncAt = System.currentTimeMillis()
            } catch (e: java.io.IOException) {
                // Offline or server trouble: the services already on the device keep working.
                LogUtil.w(AppConfig.TAG, "GeekGraph: launch sync failed", e)
            }
        }
    }

    /**
     * Sign out on this device: tell the server (best effort, in the
     * background), drop the account's services, then the session.
     */
    fun signOut() {
        if (session.session.value is Session.SignedIn) {
            launchScope.launch {
                // Bounded: a phone offline must still be able to sign out.
                withTimeoutOrNull(LOGOUT_TIMEOUT_MS) {
                    try {
                        api.logout()
                    } catch (e: java.io.IOException) {
                        LogUtil.w(AppConfig.TAG, "GeekGraph: server logout failed; the session expires on its own", e)
                    }
                }
                withContext(Dispatchers.IO) { accountSync.removeAllNow() }
                session.signOut()
            }
        } else {
            session.signOut()
        }
    }

    private const val LOGOUT_TIMEOUT_MS = 5_000L
    private const val ID_ACCOUNT = "GEEK_ACCOUNT"
    private const val ID_SECURE = "GEEK_SECURE"
    private const val LAUNCH_SYNC_INTERVAL_MS = 10 * 60 * 1000L
}
