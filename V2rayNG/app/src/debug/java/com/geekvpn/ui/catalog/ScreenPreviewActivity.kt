package com.geekvpn.ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.geekvpn.ui.login.LinkPurpose
import com.geekvpn.ui.login.SyncingScreen
import com.geekvpn.ui.login.UsernameScreen
import com.geekvpn.ui.login.WaitingScreen
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.ui.base.BaseComponentActivity

/**
 * Debug-only: one screen with sample data, for design review and the CI
 * screenshots of states the emulator cannot reach on its own (the waiting
 * screen needs the API, which GitHub's runners may not reach). Buttons do
 * nothing here.
 *
 *   am start -n <pkg>/com.geekvpn.ui.catalog.ScreenPreviewActivity --es screen waiting --ez dark false
 */
class ScreenPreviewActivity : BaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        val dark = intent.getBooleanExtra(CatalogActivity.EXTRA_DARK, false)
        // A fixed 4:32 left, so screenshots are comparable run to run.
        val expiresAt = remember { System.currentTimeMillis() + 272_000L }
        GeekTheme(darkTheme = dark) {
            when (intent.getStringExtra(EXTRA_SCREEN)) {
                SCREEN_SYNCING -> SyncingScreen()
                SCREEN_USERNAME -> UsernameScreen(busy = false, onSubmit = { _, _ -> }, onBack = {})
                SCREEN_CREATE -> WaitingScreen(LinkPurpose.CreateAccount, expiresAt, onReopen = {}, onCancel = {})
                else -> WaitingScreen(LinkPurpose.SignIn, expiresAt, onReopen = {}, onCancel = {})
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
        const val SCREEN_CREATE = "create"
        const val SCREEN_SYNCING = "syncing"
        const val SCREEN_USERNAME = "username"
    }
}
