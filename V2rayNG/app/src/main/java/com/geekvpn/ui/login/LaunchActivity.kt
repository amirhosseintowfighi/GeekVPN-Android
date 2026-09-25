package com.geekvpn.ui.login

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.geekvpn.GeekGraph
import com.geekvpn.auth.Session
import com.geekvpn.auth.TelegramLink
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.launch

/**
 * The launcher entry. Signed in or guest: straight on to the main screen (and
 * a background refresh of the account's services). Otherwise the login
 * screens, until the customer signs in or picks guest mode.
 */
class LaunchActivity : BaseComponentActivity() {
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && GeekGraph.session.session.value != Session.SignedOut) {
            GeekGraph.syncOnLaunch()
            openMain()
            return
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { handle(it) }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            BackHandler(enabled = state is LoginUiState.Waiting) { viewModel.cancel() }
            when (val current = state) {
                LoginUiState.Choose, LoginUiState.Starting -> LoginScreen(
                    busy = current == LoginUiState.Starting,
                    onTelegram = { viewModel.startTelegram(LinkPurpose.SignIn) },
                    onUsername = viewModel::username,
                    onCreateAccount = { viewModel.startTelegram(LinkPurpose.CreateAccount) },
                    onGuest = viewModel::continueAsGuest,
                )
                is LoginUiState.Waiting -> WaitingScreen(
                    purpose = current.purpose,
                    expiresAt = current.expiresAt,
                    onReopen = viewModel::reopenTelegram,
                    onCancel = viewModel::cancel,
                )
                LoginUiState.Syncing -> SyncingScreen()
            }
        }
    }

    private fun handle(event: LoginEvent) {
        when (event) {
            is LoginEvent.OpenTelegram -> openTelegram(event.deepLink)
            is LoginEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
            LoginEvent.Done -> openMain()
        }
    }

    /** The Telegram app directly when it is installed, else the https link (browser, then Telegram). */
    private fun openTelegram(deepLink: String) {
        val candidates = listOfNotNull(TelegramLink.appUri(deepLink), deepLink)
        for (uri in candidates) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
                return
            } catch (e: ActivityNotFoundException) {
                LogUtil.i(AppConfig.TAG, "Login: no app for ${uri.substringBefore(':')} links", e)
            }
        }
        Toast.makeText(this, R.string.geek_login_err_no_telegram, Toast.LENGTH_LONG).show()
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
