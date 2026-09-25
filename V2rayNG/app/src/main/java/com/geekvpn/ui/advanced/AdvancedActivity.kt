package com.geekvpn.ui.advanced

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.launch

/**
 * GeekVPN's "advanced settings": v2rayNG's settings in GeekVPN's own design,
 * instead of v2rayNG's whole app. A change reaches a running connection when
 * Home next comes to the front (v2rayNG's restart-on-change signal).
 */
class AdvancedActivity : BaseComponentActivity() {

    private val viewModel: AdvancedViewModel by viewModels { AdvancedViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is AdvancedEvent.Message -> Toast.makeText(this@AdvancedActivity, event.text, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        GeekTheme {
            GeekBackdrop {
                AdvancedScreen(
                    state = state,
                    onBack = ::finish,
                    onToggle = viewModel::setToggle,
                    onChoice = viewModel::setChoice,
                    onText = viewModel::setText,
                    onLink = ::openLink,
                )
            }
        }
    }

    private fun openLink(key: String) {
        when (key) {
            AdvancedSettings.LINK_MODE_HELP -> Utils.openUri(this, AppConfig.APP_WIKI_MODE)
            AdvancedSettings.LINK_SYSTEM_VPN -> try {
                startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                LogUtil.e(AppConfig.TAG, "Advanced: no system VPN settings page", e)
                Toast.makeText(this, R.string.toast_system_vpn_settings_unavailable, Toast.LENGTH_LONG).show()
            } catch (e: SecurityException) {
                LogUtil.e(AppConfig.TAG, "Advanced: system VPN settings refused", e)
                Toast.makeText(this, R.string.toast_system_vpn_settings_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }
}
