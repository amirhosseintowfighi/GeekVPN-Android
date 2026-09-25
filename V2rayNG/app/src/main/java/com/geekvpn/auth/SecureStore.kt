package com.geekvpn.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small secrets (the session's tokens) encrypted with an AES-GCM key that
 * never leaves the Android Keystore, stored as ciphertext in MMKV.
 *
 * Its own MMKV file rather than `MmkvManager`'s, which holds v2rayNG's
 * settings and servers in plaintext (see `GeekGraph.storage` for where it lives).
 */
class SecureStore(private val storage: MMKV) {

    fun put(key: String, value: String?): Boolean {
        if (value == null) {
            storage.removeValueForKey(key)
            return true
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            storage.encode(key, Base64.encodeToString(sealed, Base64.NO_WRAP))
        } catch (e: GeneralSecurityException) {
            LogUtil.e(AppConfig.TAG, "SecureStore: encrypt failed for $key", e)
            false
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "SecureStore: keystore unavailable for $key", e)
            false
        } catch (e: ProviderException) {
            // Some vendor keystores throw this instead of a checked exception.
            LogUtil.e(AppConfig.TAG, "SecureStore: keystore provider failed for $key", e)
            false
        }
    }

    fun get(key: String): String? {
        val stored = storage.decodeString(key) ?: return null
        return try {
            val sealed = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // The key is gone (restored to a new device, keystore reset): the
            // value is unreadable for good, so drop it instead of failing forever.
            LogUtil.w(AppConfig.TAG, "SecureStore: decrypt failed for $key, dropping it", e)
            storage.removeValueForKey(key)
            null
        } catch (e: IllegalArgumentException) {
            LogUtil.w(AppConfig.TAG, "SecureStore: corrupt value for $key, dropping it", e)
            storage.removeValueForKey(key)
            null
        } catch (e: IOException) {
            // Transient: keep the value and try again next time.
            LogUtil.e(AppConfig.TAG, "SecureStore: keystore unavailable for $key", e)
            null
        } catch (e: ProviderException) {
            LogUtil.e(AppConfig.TAG, "SecureStore: keystore provider failed for $key", e)
            null
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "geekvpn_session"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
