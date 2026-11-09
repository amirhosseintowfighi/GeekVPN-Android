package com.geekvpn.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2rayScreensTest {
    @Test
    fun v2rayng_screens_are_replaced() {
        assertTrue(V2rayScreens.isBlocked("com.v2ray.ang.ui.main.MainActivity"))
        assertTrue(V2rayScreens.isBlocked("com.v2ray.ang.ui.settings.SettingsActivity"))
        assertTrue(V2rayScreens.isBlocked("com.v2ray.ang.ui.shortcut.TaskerActivity"))
        assertTrue(V2rayScreens.isBlocked("com.v2ray.ang.ui.shortcut.ScScannerActivity"))
    }

    @Test
    fun trampolines_without_ui_and_geekvpn_screens_run() {
        assertFalse(V2rayScreens.isBlocked("com.v2ray.ang.ui.shortcut.ScStartActivity"))
        assertFalse(V2rayScreens.isBlocked("com.v2ray.ang.ui.shortcut.ScSwitchActivity"))
        assertFalse(V2rayScreens.isBlocked("com.v2ray.ang.ui.UrlSchemeActivity"))
        assertFalse(V2rayScreens.isBlocked("com.geekvpn.ui.home.HomeActivity"))
    }
}
