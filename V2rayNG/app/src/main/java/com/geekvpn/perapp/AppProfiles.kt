package com.geekvpn.perapp

import com.geekvpn.GeekStorage
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.tencent.mmkv.MMKV
import java.util.UUID

/**
 * A saved per-app selection («پروفایل برنامه‌ها»): v2rayNG's mode (bypass or
 * proxy) and its list, under a name. Applying one puts both back through
 * v2rayNG's own toggles. Defaults on every field so Gson uses the
 * no-argument constructor; the field names are the stored format.
 */
data class AppProfile(
    val id: String = "",
    val name: String = "",
    val bypass: Boolean = false,
    val packages: List<String> = emptyList(),
)

/** Pure list operations, for the tests. */
object AppProfileList {
    const val MAX_NAME = 24
    const val MAX_PROFILES = 12

    fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)

    /**
     * Saves the current selection under [name]; a profile already called that
     * is replaced in place. Null when the name is empty or the list is full.
     */
    fun save(
        profiles: List<AppProfile>,
        name: String,
        bypass: Boolean,
        packages: Set<String>,
        newId: () -> String = { UUID.randomUUID().toString() },
    ): List<AppProfile>? {
        val clean = cleanName(name)
        if (clean.isEmpty()) return null
        val existing = profiles.indexOfFirst { it.name.equals(clean, ignoreCase = true) }
        if (existing >= 0) {
            return profiles.toMutableList().also {
                it[existing] = it[existing].copy(name = clean, bypass = bypass, packages = packages.sorted())
            }
        }
        if (profiles.size >= MAX_PROFILES) return null
        return profiles + AppProfile(newId(), clean, bypass, packages.sorted())
    }

    fun remove(profiles: List<AppProfile>, id: String): List<AppProfile> = profiles.filterNot { it.id == id }

    /** The profile the current selection is exactly, if any. */
    fun active(profiles: List<AppProfile>, bypass: Boolean, selected: Set<String>): AppProfile? =
        profiles.firstOrNull { it.bypass == bypass && it.packages.toSet() == selected }

    /** Packages to toggle so that [current] becomes [wanted]. */
    fun toggles(current: Set<String>, wanted: Set<String>): Set<String> = (current - wanted) + (wanted - current)
}

/** «پروفایل برنامه‌ها» in its own MMKV file. */
class AppProfiles(private val storage: MMKV) {
    private val gson = Gson()

    fun load(): List<AppProfile> {
        val json = storage.decodeString(KEY_PROFILES) ?: return emptyList()
        return try {
            gson.fromJson<List<AppProfile>>(json, object : TypeToken<List<AppProfile>>() {}.type).orEmpty()
                .filter { it.id.isNotEmpty() }
        } catch (_: JsonParseException) {
            emptyList()
        }
    }

    fun store(profiles: List<AppProfile>) {
        storage.encode(KEY_PROFILES, gson.toJson(profiles))
    }

    companion object {
        private const val STORE_ID = "GEEK_PERAPP"
        private const val KEY_PROFILES = "profiles"

        fun open(): AppProfiles = AppProfiles(GeekStorage.open(STORE_ID))
    }
}
