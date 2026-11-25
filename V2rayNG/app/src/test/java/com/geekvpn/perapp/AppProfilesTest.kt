package com.geekvpn.perapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppProfilesTest {
    private var next = 0
    private val ids = { "id${next++}" }

    @Test
    fun names_are_trimmed_and_empty_ones_refused() {
        assertEquals("Work apps", AppProfileList.cleanName("  Work   apps "))
        assertNull(AppProfileList.save(emptyList(), "   ", true, setOf("a"), ids))
    }

    @Test
    fun saving_adds_and_the_same_name_replaces() {
        val one = AppProfileList.save(emptyList(), "Banks", true, setOf("b", "a"), ids)!!
        assertEquals(listOf(AppProfile("id0", "Banks", true, listOf("a", "b"))), one)
        val two = AppProfileList.save(one, "banks", false, setOf("c"), ids)!!
        assertEquals(listOf(AppProfile("id0", "banks", false, listOf("c"))), two)
        assertEquals(emptyList<AppProfile>(), AppProfileList.remove(two, "id0"))
    }

    @Test
    fun a_full_list_takes_no_new_name() {
        var list = emptyList<AppProfile>()
        repeat(AppProfileList.MAX_PROFILES) { i -> list = AppProfileList.save(list, "P$i", true, emptySet(), ids)!! }
        assertNull(AppProfileList.save(list, "one more", true, emptySet(), ids))
        // Replacing an existing one still works.
        assertEquals(AppProfileList.MAX_PROFILES, AppProfileList.save(list, "P0", false, emptySet(), ids)!!.size)
    }

    @Test
    fun the_active_profile_matches_mode_and_list_exactly() {
        val list = listOf(AppProfile("1", "A", true, listOf("x", "y")), AppProfile("2", "B", false, listOf("x", "y")))
        assertEquals("1", AppProfileList.active(list, true, setOf("y", "x"))?.id)
        assertEquals("2", AppProfileList.active(list, false, setOf("x", "y"))?.id)
        assertNull(AppProfileList.active(list, true, setOf("x")))
    }

    @Test
    fun toggles_turn_one_selection_into_another() {
        assertEquals(setOf("a", "c"), AppProfileList.toggles(setOf("a", "b"), setOf("b", "c")))
        assertEquals(emptySet<String>(), AppProfileList.toggles(setOf("a"), setOf("a")))
    }
}
