package com.geekvpn.ui.links

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.handler.AngConfigManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsChangeManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SubscriptionUpdater
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The subscription link form, as the customer types it. */
data class LinkForm(
    val name: String = "",
    val url: String = "",
    val enabled: Boolean = true,
    val autoUpdate: Boolean = false,
    val interval: String = "1440",
    val userAgent: String = "",
    val headers: String = "",
    val filter: String = "",
    val allowInsecure: Boolean = false,
    val prevProfile: String = "",
    val nextProfile: String = "",
) {
    enum class Error { NameMissing, UrlInvalid, UrlInsecure, IntervalTooShort }

    /**
     * v2rayNG's `SubEditActivity` checks, in order: a name; a URL that is a URL
     * and, unless allowed, https (or local http); an auto-update interval of at
     * least [AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES] minutes.
     */
    fun errors(isUrl: (String) -> Boolean, isSecureUrl: (String) -> Boolean): Set<Error> = buildSet {
        if (name.isBlank()) add(Error.NameMissing)
        val link = url.trim()
        if (link.isNotEmpty()) {
            if (!isUrl(link)) add(Error.UrlInvalid)
            else if (!isSecureUrl(link) && !allowInsecure) add(Error.UrlInsecure)
        }
        if (autoUpdate && (interval.trim().toLongOrNull() ?: 0L) < AppConfig.SUBSCRIPTION_MIN_INTERVAL_MINUTES) {
            add(Error.IntervalTooShort)
        }
    }

    fun applyTo(item: SubscriptionItem): SubscriptionItem = item.apply {
        remarks = name.trim()
        url = this@LinkForm.url.trim()
        enabled = this@LinkForm.enabled
        autoUpdate = this@LinkForm.autoUpdate
        updateInterval = interval.trim().toLongOrNull() ?: updateInterval
        userAgent = this@LinkForm.userAgent.trim().ifEmpty { null }
        requestHeaders = headers.trim().ifEmpty { null }
        filter = this@LinkForm.filter.trim().ifEmpty { null }
        allowInsecureUrl = allowInsecure
        prevProfile = this@LinkForm.prevProfile.trim().ifEmpty { null }
        nextProfile = this@LinkForm.nextProfile.trim().ifEmpty { null }
    }

    companion object {
        fun of(item: SubscriptionItem) = LinkForm(
            name = item.remarks,
            url = item.url,
            enabled = item.enabled,
            autoUpdate = item.autoUpdate,
            interval = item.updateInterval.toString(),
            userAgent = item.userAgent.orEmpty(),
            headers = item.requestHeaders.orEmpty(),
            filter = item.filter.orEmpty(),
            allowInsecure = item.allowInsecureUrl,
            prevProfile = item.prevProfile.orEmpty(),
            nextProfile = item.nextProfile.orEmpty(),
        )
    }
}

data class LinkEditUiState(
    /** v2rayNG subscription GUID; empty for a new link. */
    val id: String = "",
    val form: LinkForm = LinkForm(),
    val errors: Set<LinkForm.Error> = emptySet(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
) {
    val isNew: Boolean get() = id.isEmpty()
}

sealed interface LinkEditEvent {
    data class Done(@param:StringRes val message: Int) : LinkEditEvent
}

/** Where subscription links are kept: v2rayNG's own subscription store. */
interface LinkStore {
    fun load(id: String): SubscriptionItem?
    /** Saves and returns the id (a new one for a new link). */
    fun save(id: String, item: SubscriptionItem): String
    /** Fetches the link's servers now; false when the fetch failed. */
    fun fetch(id: String): Boolean
    fun delete(id: String)
    fun isUrl(value: String): Boolean
    fun isSecureUrl(value: String): Boolean
}

object V2rayLinkStore : LinkStore {
    override fun load(id: String) = MmkvManager.decodeSubscription(id)

    override fun save(id: String, item: SubscriptionItem): String {
        val key = id.ifEmpty { Utils.getUuid() }
        MmkvManager.encodeSubscription(key, item)
        SubscriptionUpdater.syncOne(subId = key)
        SettingsChangeManager.makeSetupGroupTab()
        return key
    }

    override fun fetch(id: String): Boolean {
        val cache = MmkvManager.decodeSubscriptions().firstOrNull { it.guid == id } ?: return false
        return try {
            AngConfigManager.updateConfigViaSub(cache).configCount > 0
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "Links: fetching $id failed", e)
            false
        }
    }

    override fun delete(id: String) {
        SettingsManager.removeSubscriptionWithDefault(id)
        SettingsChangeManager.makeSetupGroupTab()
    }

    override fun isUrl(value: String) = Utils.isValidUrl(value)
    override fun isSecureUrl(value: String) = Utils.isValidSubUrl(value)
}

class LinkEditViewModel(
    private val id: String,
    private val store: LinkStore,
    private val io: CoroutineDispatcher,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {

    private val state = MutableStateFlow(LinkEditUiState(id = id))
    val uiState: StateFlow<LinkEditUiState> = state.asStateFlow()

    private val eventChannel = Channel<LinkEditEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val item = if (id.isEmpty()) null else withContext(io) { store.load(id) }
            state.update { it.copy(form = item?.let(LinkForm::of) ?: LinkForm(), loaded = true) }
        }
    }

    fun edit(change: (LinkForm) -> LinkForm) {
        state.update { it.copy(form = change(it.form), errors = emptySet()) }
    }

    fun save() {
        val current = state.value
        if (!current.loaded || current.saving) return
        val errors = current.form.errors(store::isUrl, store::isSecureUrl)
        if (errors.isNotEmpty()) {
            state.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            state.update { it.copy(saving = true) }
            val message = withContext(io) {
                val item = current.form.applyTo(store.load(id) ?: SubscriptionItem())
                val saved = store.save(id, item)
                // A new link with an address: fetch its servers now, as the import does.
                if (item.url.isNotEmpty() && item.enabled && !store.fetch(saved)) R.string.geek_links_saved_not_fetched
                else R.string.geek_links_saved
            }
            state.update { it.copy(saving = false) }
            eventChannel.send(LinkEditEvent.Done(message))
        }
    }

    fun delete() {
        if (id.isEmpty()) return
        viewModelScope.launch {
            withContext(io) { store.delete(id) }
            eventChannel.send(LinkEditEvent.Done(R.string.geek_links_deleted))
        }
    }

    companion object {
        fun factory(id: String) = viewModelFactory {
            initializer { LinkEditViewModel(id, V2rayLinkStore, Dispatchers.IO) }
        }
    }
}
