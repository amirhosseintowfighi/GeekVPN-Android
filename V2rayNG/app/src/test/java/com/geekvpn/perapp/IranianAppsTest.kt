package com.geekvpn.perapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IranianAppsTest {

    @Test
    fun an_ir_prefix_or_a_known_package_is_iranian() {
        assertTrue(IranianApps.matches("ir.nasim"))
        assertTrue(IranianApps.matches("ir.divar"))
        assertTrue(IranianApps.matches("cab.snapp.passenger"))
        assertTrue(IranianApps.matches("com.digikala"))
        assertFalse(IranianApps.matches("org.telegram.messenger"))
        assertFalse(IranianApps.matches("com.instagram.android"))
        // A prefix is a whole segment, not any "ir" at the start.
        assertFalse(IranianApps.matches("irc.client"))
        assertFalse(IranianApps.matches("com.digikalax"))
    }

    @Test
    fun only_installed_iranian_apps_are_selected_and_never_this_app() {
        val installed = listOf("ir.nasim", "org.telegram.messenger", "com.farsitel.bazaar", "ir.geek.self")
        assertEquals(setOf("ir.nasim", "com.farsitel.bazaar"), IranianApps.select(installed, self = "ir.geek.self"))
    }
}
