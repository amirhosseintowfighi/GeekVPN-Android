package com.geekvpn.push

import com.geekvpn.GeekStorage
import com.geekvpn.api.GeekApi
import com.google.firebase.messaging.FirebaseMessaging
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Tells the backend which FCM token belongs to the signed-in account, so a
 * support reply reaches this phone (`POST /api/miniapp/push-token`).
 *
 * The token sent and the account it was sent for are remembered; a new token
 * (Firebase rotates them) or another account sends again, anything else sends
 * nothing. Main process only, like every API call (see [GeekApi]).
 */
class PushRegistration(private val store: MMKV, private val api: GeekApi) {

    /** Sends the current token when the server does not have it yet for [account]. */
    suspend fun sync(account: String) {
        val token = currentToken() ?: return
        if (!needsSend(sent(), account, token)) return
        try {
            api.registerPushToken(token)
            store.encode(KEY_SENT, entry(account, token))
        } catch (e: java.io.IOException) {
            // Retried the next time the app opens.
            LogUtil.w(AppConfig.TAG, "Push: registering the token failed", e)
        }
    }

    /** Sign-out: the server stops pushing to this install. Best effort; the entry goes either way. */
    suspend fun forget() {
        val token = sent()?.substringAfter(SEPARATOR, "")?.takeIf { it.isNotEmpty() }
        store.removeValueForKey(KEY_SENT)
        if (token == null) return
        try {
            api.forgetPushToken(token)
        } catch (e: java.io.IOException) {
            LogUtil.w(AppConfig.TAG, "Push: forgetting the token failed", e)
        }
    }

    private fun sent(): String? = store.decodeString(KEY_SENT)

    private suspend fun currentToken(): String? {
        if (!Push.configured) return null
        return try {
            suspendCancellableCoroutine { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    val token = if (task.isSuccessful) task.result else null
                    if (!task.isSuccessful) LogUtil.w(AppConfig.TAG, "Push: no FCM token", task.exception)
                    continuation.resume(token?.takeIf { it.isNotBlank() })
                }
            }
        } catch (e: IllegalStateException) {
            // Firebase not started (a malformed option): push is off, nothing to register.
            LogUtil.w(AppConfig.TAG, "Push: Firebase is not running", e)
            null
        }
    }

    companion object {
        private const val KEY_SENT = "sent"
        private const val SEPARATOR = '|'

        fun open(api: GeekApi) = PushRegistration(GeekStorage.open("GEEK_PUSH"), api)

        fun entry(account: String, token: String) = "$account$SEPARATOR$token"

        /** Pure, for the tests: whether the server still lacks this token for this account. */
        fun needsSend(sent: String?, account: String, token: String): Boolean = sent != entry(account, token)
    }
}
