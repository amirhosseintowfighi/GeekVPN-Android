package com.geekvpn.ui.links

import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkEditTest {
    private val isUrl: (String) -> Boolean = { it.startsWith("http://") || it.startsWith("https://") }
    private val isSecure: (String) -> Boolean = { it.startsWith("https://") }

    @Test
    fun a_named_https_link_is_valid() {
        assertTrue(LinkForm(name = "My link", url = "https://sub.example.com/abc").errors(isUrl, isSecure).isEmpty())
    }

    @Test
    fun the_name_is_required() {
        assertEquals(setOf(LinkForm.Error.NameMissing), LinkForm(name = " ", url = "https://a.b/c").errors(isUrl, isSecure))
    }

    @Test
    fun plain_http_needs_the_insecure_switch() {
        val form = LinkForm(name = "x", url = "http://sub.example.com/abc")
        assertEquals(setOf(LinkForm.Error.UrlInsecure), form.errors(isUrl, isSecure))
        assertTrue(form.copy(allowInsecure = true).errors(isUrl, isSecure).isEmpty())
    }

    @Test
    fun not_a_url_is_refused() {
        assertEquals(setOf(LinkForm.Error.UrlInvalid), LinkForm(name = "x", url = "vless://abc").errors(isUrl, isSecure))
    }

    @Test
    fun auto_update_needs_fifteen_minutes() {
        val form = LinkForm(name = "x", autoUpdate = true, interval = "10")
        assertEquals(setOf(LinkForm.Error.IntervalTooShort), form.errors(isUrl, isSecure))
        assertTrue(form.copy(interval = "15").errors(isUrl, isSecure).isEmpty())
        // Without auto update the interval is not checked.
        assertTrue(form.copy(autoUpdate = false).errors(isUrl, isSecure).isEmpty())
    }

    @Test
    fun the_form_round_trips_a_stored_link() {
        val item = SubscriptionItem(remarks = "A", url = "https://a.b/c", autoUpdate = true, updateInterval = 60, filter = "IR")
        val back = LinkForm.of(item).applyTo(SubscriptionItem())
        assertEquals("A", back.remarks)
        assertEquals(60L, back.updateInterval)
        assertEquals("IR", back.filter)
        assertEquals(null, back.userAgent)
    }

    private class FakeStore : LinkStore {
        val saved = mutableMapOf<String, SubscriptionItem>()
        var fetchOk = true
        val deleted = mutableListOf<String>()
        override fun load(id: String) = saved[id]
        override fun save(id: String, item: SubscriptionItem): String {
            val key = id.ifEmpty { "new-id" }
            saved[key] = item
            return key
        }
        override fun fetch(id: String) = fetchOk
        override fun delete(id: String) {
            deleted += id
        }
        override fun isUrl(value: String) = value.startsWith("http")
        override fun isSecureUrl(value: String) = value.startsWith("https://")
    }

    private fun TestScope.viewModel(id: String, store: FakeStore): Pair<LinkEditViewModel, MutableList<LinkEditEvent>> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = LinkEditViewModel(id, store, dispatcher, CoroutineScope(SupervisorJob() + dispatcher))
        val events = mutableListOf<LinkEditEvent>()
        CoroutineScope(SupervisorJob() + dispatcher).launch { vm.events.toList(events) }
        testScheduler.advanceUntilIdle()
        return vm to events
    }

    @Test
    fun an_invalid_form_shows_errors_and_saves_nothing() = runTest {
        val store = FakeStore()
        val (vm, events) = viewModel("", store)
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals(setOf(LinkForm.Error.NameMissing), vm.uiState.value.errors)
        assertTrue(store.saved.isEmpty())
        assertTrue(events.isEmpty())
    }

    @Test
    fun a_new_link_is_saved_and_fetched() = runTest {
        val store = FakeStore()
        val (vm, events) = viewModel("", store)
        vm.edit { it.copy(name = "Mine", url = "https://sub.example.com/x") }
        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals("https://sub.example.com/x", store.saved.getValue("new-id").url)
        assertEquals(listOf(LinkEditEvent.Done(R.string.geek_links_saved)), events)
    }

    @Test
    fun a_failed_fetch_still_saves_and_says_so() = runTest {
        val store = FakeStore().apply { fetchOk = false }
        val (vm, events) = viewModel("", store)
        vm.edit { it.copy(name = "Mine", url = "https://sub.example.com/x") }
        vm.save()
        testScheduler.advanceUntilIdle()
        assertTrue("new-id" in store.saved)
        assertEquals(listOf(LinkEditEvent.Done(R.string.geek_links_saved_not_fetched)), events)
    }

    @Test
    fun editing_loads_the_stored_link_and_delete_removes_it() = runTest {
        val store = FakeStore().apply { saved["g1"] = SubscriptionItem(remarks = "Old", url = "https://a.b/c") }
        val (vm, events) = viewModel("g1", store)
        assertEquals("Old", vm.uiState.value.form.name)
        vm.delete()
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("g1"), store.deleted)
        assertEquals(listOf(LinkEditEvent.Done(R.string.geek_links_deleted)), events)
    }
}
