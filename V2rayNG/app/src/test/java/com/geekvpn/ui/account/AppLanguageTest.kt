package com.geekvpn.ui.account

import com.v2ray.ang.enums.Language
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun nothing_stored_is_persian() {
        assertEquals(AppLanguage.Persian, AppLanguage.of(null))
        assertEquals(AppLanguage.Persian, AppLanguage.of(""))
    }

    @Test
    fun shipped_codes_map_back_and_others_follow_the_phone() {
        assertEquals(AppLanguage.English, AppLanguage.of("en"))
        assertEquals(AppLanguage.System, AppLanguage.of("auto"))
        assertEquals(AppLanguage.System, AppLanguage.of("zh-rCN"))
    }

    @Test
    fun every_choice_is_a_v2rayng_language_code() {
        AppLanguage.entries.forEach { choice ->
            assertEquals(choice.code, Language.fromCode(choice.code).code)
        }
    }
}
