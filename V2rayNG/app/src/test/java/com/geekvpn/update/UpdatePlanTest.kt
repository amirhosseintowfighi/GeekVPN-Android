package com.geekvpn.update

import com.geekvpn.api.AppApk
import com.geekvpn.api.AppLatest
import com.geekvpn.api.AppVersionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePlanTest {
    private val sha = "ab".repeat(32)

    private fun apk(abi: String, url: String = "https://github.com/o/r/releases/download/v1.3.0/GeekVPN_1.3.0_$abi.apk") =
        AppApk(abi = abi, fileName = "GeekVPN_1.3.0_$abi.apk", url = url, sha256 = sha, sizeBytes = 30_000_000)

    private fun response(version: String = "v1.3.0", min: String = "", apks: List<AppApk> = listOf(apk("arm64-v8a"), apk("universal"))) =
        AppVersionResponse(AppLatest(version, "notes", null, apks), min)

    @Test
    fun versions_compare_by_number_not_by_text() {
        assertTrue(UpdatePlan.compare("1.10.0", "1.9.2") > 0)
        assertEquals(0, UpdatePlan.compare("1.2", "1.2.0"))
        assertEquals(0, UpdatePlan.compare("v1.2.0", "1.2.0-staging"))
        assertTrue(UpdatePlan.compare("0.9.9", "1.0.0") < 0)
    }

    @Test
    fun a_newer_release_is_offered_with_the_apk_for_this_phone() {
        val offer = UpdatePlan.offer("1.2.0", response(), listOf("arm64-v8a", "armeabi-v7a"))!!

        assertEquals("1.3.0", offer.versionName)
        assertEquals("arm64-v8a", offer.apk.abi)
        assertEquals(sha, offer.apk.sha256)
        assertFalse(offer.required)
    }

    @Test
    fun the_same_or_an_older_release_is_not_offered() {
        assertNull(UpdatePlan.offer("1.3.0", response(), listOf("arm64-v8a")))
        assertNull(UpdatePlan.offer("1.4.0", response(), listOf("arm64-v8a")))
        assertNull(UpdatePlan.offer("1.2.0", AppVersionResponse(null, ""), listOf("arm64-v8a")))
    }

    @Test
    fun a_phone_without_a_matching_build_gets_the_universal_one() {
        assertEquals("universal", UpdatePlan.offer("1.2.0", response(), listOf("x86_64"))!!.apk.abi)
        assertNull(UpdatePlan.offer("1.2.0", response(apks = listOf(apk("arm64-v8a"))), listOf("x86_64")))
    }

    @Test
    fun below_the_minimum_version_the_update_is_required() {
        assertTrue(UpdatePlan.offer("1.1.0", response(min = "1.2.0"), listOf("arm64-v8a"))!!.required)
        assertFalse(UpdatePlan.offer("1.2.0", response(min = "1.2.0"), listOf("arm64-v8a"))!!.required)
    }

    @Test
    fun only_https_downloads_and_well_formed_digests_are_trusted() {
        val plain = response(apks = listOf(apk("arm64-v8a", url = "http://mirror.example/x.apk"), apk("universal")))
        assertEquals("universal", UpdatePlan.offer("1.2.0", plain, listOf("arm64-v8a"))!!.apk.abi)

        val badDigest = response(apks = listOf(apk("arm64-v8a").copy(sha256 = "not-a-digest")))
        assertNull(UpdatePlan.offer("1.2.0", badDigest, listOf("arm64-v8a"))!!.apk.sha256)
    }
}
