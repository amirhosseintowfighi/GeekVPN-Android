package com.geekvpn.auth

import com.geekvpn.api.AppUser
import com.geekvpn.api.TokenPair
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Who is using the app. */
sealed interface Session {
    /** Nobody yet: the login screen is shown. */
    data object SignedOut : Session

    /** "شروع سریع بدون ثبت‌نام": manual links only, no account. */
    data object Guest : Session

    data class SignedIn(val user: AppUser) : Session
}

/**
 * The GeekVPN account on this device: the token pair (encrypted, in
 * [SecureStore]), the signed-in user, the guest choice and a stable device ID.
 *
 * Its own MMKV file, not `MmkvManager`'s: none of this is v2rayNG data, and
 * it must not travel in v2rayNG's backup export (see `GeekGraph.storage`).
 * Main process only, like `GeekApi`.
 */
class SessionStore(
    private val plain: MMKV,
    private val secure: SecureStore,
    private val gson: Gson = Gson(),
) {
    private val lock = Any()

    @Volatile
    private var access: String? = null

    @Volatile
    private var refresh: String? = null

    private val state = MutableStateFlow(load())
    val session: StateFlow<Session> = state.asStateFlow()

    /** Random, made once per install. Lets the server tell this phone's sessions apart. */
    val deviceId: String
        get() = synchronized(lock) {
            plain.decodeString(KEY_DEVICE_ID) ?: UUID.randomUUID().toString().also { id ->
                plain.encode(KEY_DEVICE_ID, id)
            }
        }

    fun accessToken(): String? = access

    fun refreshToken(): String? = refresh

    /** Returns false when the tokens could not be stored; the session is then not started. */
    fun signIn(tokens: TokenPair, user: AppUser): Boolean {
        val accessToken = tokens.accessToken ?: return false
        val refreshToken = tokens.refreshToken ?: return false
        synchronized(lock) {
            if (!secure.put(KEY_ACCESS, accessToken) || !secure.put(KEY_REFRESH, refreshToken)) {
                // Never leave half a pair behind.
                secure.put(KEY_ACCESS, null)
                secure.put(KEY_REFRESH, null)
                return false
            }
            plain.encode(KEY_USER, gson.toJson(user))
            plain.removeValueForKey(KEY_GUEST)
            access = accessToken
            refresh = refreshToken
            state.value = Session.SignedIn(user)
        }
        return true
    }

    fun updateTokens(tokens: TokenPair) {
        val accessToken = tokens.accessToken ?: return
        val refreshToken = tokens.refreshToken ?: return
        synchronized(lock) {
            // Memory first: even if storage fails, this process keeps working
            // until it dies, and the next start simply signs in again.
            access = accessToken
            refresh = refreshToken
            secure.put(KEY_ACCESS, accessToken)
            secure.put(KEY_REFRESH, refreshToken)
        }
    }

    fun continueAsGuest() {
        synchronized(lock) {
            plain.encode(KEY_GUEST, true)
            state.value = Session.Guest
        }
    }

    fun signOut() {
        synchronized(lock) {
            secure.put(KEY_ACCESS, null)
            secure.put(KEY_REFRESH, null)
            plain.removeValueForKey(KEY_USER)
            plain.removeValueForKey(KEY_GUEST)
            access = null
            refresh = null
            state.value = Session.SignedOut
        }
    }

    private fun load(): Session {
        val user = plain.decodeString(KEY_USER)?.let {
            try {
                gson.fromJson(it, AppUser::class.java)
            } catch (_: JsonParseException) {
                null
            }
        }
        val accessToken = secure.get(KEY_ACCESS)
        val refreshToken = secure.get(KEY_REFRESH)
        if (user != null && accessToken != null && refreshToken != null) {
            access = accessToken
            refresh = refreshToken
            return Session.SignedIn(user)
        }
        return if (plain.decodeBool(KEY_GUEST, false)) Session.Guest else Session.SignedOut
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER = "user"
        const val KEY_GUEST = "guest"
        const val KEY_DEVICE_ID = "device_id"
    }
}
