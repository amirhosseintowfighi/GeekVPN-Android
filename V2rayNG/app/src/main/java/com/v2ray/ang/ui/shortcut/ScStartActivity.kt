package com.v2ray.ang.ui.shortcut

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.geekvpn.quick.QuickConnect
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.ui.base.BaseComponentActivity

class ScStartActivity : BaseComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ScreenContent() {
        LaunchedEffect(Unit) {
            moveTaskToBack(true)
            if (!CoreServiceManager.isRunning()) {
                // GeekVPN: with "server: auto" the app's smart connect picks the server.
                if (QuickConnect.viaApp()) startActivity(QuickConnect.intent(this@ScStartActivity)) else LauncherManager.startServiceFromToggle(this@ScStartActivity)
            }
            finish()
        }
    }
}
