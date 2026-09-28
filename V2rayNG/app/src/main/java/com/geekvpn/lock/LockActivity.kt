package com.geekvpn.lock

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil

/** Covers GeekVPN's screens until the phone's own lock is passed ([AppLock]). */
class LockActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Back leaves the app instead of uncovering the screen underneath.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        if (savedInstanceState == null) prompt()
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            GeekBackdrop {
                LockScreen(onUnlock = ::prompt)
            }
        }
    }

    private fun prompt() {
        if (!AppLock.available(this)) {
            // The screen lock was removed since the lock was turned on: nothing to ask for.
            LogUtil.w(AppConfig.TAG, "AppLock: no screen lock or biometrics any more, lock skipped")
            AppLock.onUnlocked()
            finish()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    AppLock.onUnlocked()
                    finish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancelled or locked out: the lock screen stays with its button.
                    LogUtil.i(AppConfig.TAG, "AppLock: prompt ended ($errorCode)")
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.geek_lock_title))
                .setSubtitle(getString(R.string.geek_lock_subtitle))
                .setAllowedAuthenticators(AppLock.authenticators)
                .build(),
        )
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val colors = Geek.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Image(painterResource(R.drawable.ic_geek_logo), contentDescription = null, modifier = Modifier.size(72.dp))
        Text(stringResource(R.string.geek_lock_title), style = Geek.type.pageTitle, color = colors.onBackground, textAlign = TextAlign.Center)
        Text(stringResource(R.string.geek_lock_text), style = Geek.type.body, color = colors.onBackgroundMuted, textAlign = TextAlign.Center)
        GeekPrimaryButton(stringResource(R.string.geek_lock_unlock), GeekIcons.Lock, onUnlock)
    }
}
