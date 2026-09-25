package com.geekvpn.ui.login

import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geekvpn.GeekGraph
import com.geekvpn.api.ApiException
import com.geekvpn.api.LinkStartRequest
import com.geekvpn.api.PasswordLoginRequest
import com.geekvpn.auth.LinkOutcome
import com.geekvpn.auth.LinkStarted
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** Why the Telegram flow was started; only the waiting screen's wording differs. */
enum class LinkPurpose { SignIn, CreateAccount }

sealed interface LoginUiState {
    data object Choose : LoginUiState
    data object Starting : LoginUiState
    data class Waiting(
        val purpose: LinkPurpose,
        val deepLink: String,
        /** Wall-clock millis when the server forgets the request. */
        val expiresAt: Long,
    ) : LoginUiState
    data object Syncing : LoginUiState

    /** The username and password form; [busy] while the server checks them. */
    data class Username(val busy: Boolean = false) : LoginUiState
}

sealed interface LoginEvent {
    data class OpenTelegram(val deepLink: String) : LoginEvent
    data class Message(@param:StringRes val text: Int) : LoginEvent

    /** Signed in or chose guest: leave the login screens. */
    data object Done : LoginEvent
}

/** What the login screens need from the account layer; faked in tests. */
interface LoginBackend {
    suspend fun start(): LinkStarted
    suspend fun await(pollToken: String, deadlineMillis: Long): LinkOutcome

    /** Throws `ApiException`: 401 wrong username or password, 429 too many tries. */
    suspend fun passwordLogin(username: String, password: String): LinkOutcome.Approved

    /** False when the session could not be stored. */
    fun signIn(approved: LinkOutcome.Approved): Boolean

    /** Throws `IOException` when the services cannot be fetched. */
    suspend fun sync()
    fun continueAsGuest()
    fun now(): Long
    fun logFailure(message: String, e: Throwable)
}

/** The real account layer, from [GeekGraph]. */
object GeekLoginBackend : LoginBackend {
    override suspend fun start(): LinkStarted = GeekGraph.linkLogin.start(
        LinkStartRequest(
            deviceId = GeekGraph.session.deviceId,
            deviceName = deviceName(),
            platform = "android",
            appVersion = BuildConfig.VERSION_NAME,
        )
    )

    override suspend fun await(pollToken: String, deadlineMillis: Long) =
        GeekGraph.linkLogin.await(pollToken, deadlineMillis)

    override suspend fun passwordLogin(username: String, password: String) =
        GeekGraph.linkLogin.password(
            PasswordLoginRequest(
                username = username,
                password = password,
                deviceName = deviceName(),
                platform = "android",
                appVersion = BuildConfig.VERSION_NAME,
            )
        )

    override fun signIn(approved: LinkOutcome.Approved) =
        GeekGraph.session.signIn(approved.tokens, approved.user)

    override suspend fun sync() {
        GeekGraph.accountSync.sync()
    }

    override fun continueAsGuest() = GeekGraph.session.continueAsGuest()

    override fun now() = System.currentTimeMillis()

    override fun logFailure(message: String, e: Throwable) {
        LogUtil.w(AppConfig.TAG, message, e)
    }

    /** "Samsung SM-S918B": what the bot shows in its approval prompt and device list. */
    private fun deviceName(): String {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        val name = if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
        return name.take(64).ifEmpty { "Android" }
    }
}

/**
 * The login screen and the "waiting for Telegram" screen. The poll runs in
 * [viewModelScope], so it survives rotation and the trip to Telegram and
 * stops when the screen is left or cancelled.
 */
class LoginViewModel : ViewModel {
    private val backend: LoginBackend

    /** What `viewModels()` calls. */
    constructor() : super() {
        backend = GeekLoginBackend
    }

    /** For tests: a fake backend on a test dispatcher instead of the Android main thread. */
    constructor(backend: LoginBackend, scope: CoroutineScope) : super(scope) {
        this.backend = backend
    }

    private val state = MutableStateFlow<LoginUiState>(LoginUiState.Choose)
    val uiState: StateFlow<LoginUiState> = state.asStateFlow()

    private val eventChannel = Channel<LoginEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private var flow: Job? = null

    fun startTelegram(purpose: LinkPurpose) {
        if (flow?.isActive == true) return
        flow = viewModelScope.launch {
            state.value = LoginUiState.Starting
            val started = try {
                backend.start()
            } catch (e: ApiException) {
                backend.logFailure("Login: link/start failed (status=${e.status})", e)
                fail(messageFor(e))
                return@launch
            }
            val expiresAt = backend.now() + started.expiresInSeconds * 1000L
            state.value = LoginUiState.Waiting(purpose, started.deepLink, expiresAt)
            eventChannel.send(LoginEvent.OpenTelegram(started.deepLink))

            when (val outcome = backend.await(started.pollToken, expiresAt)) {
                is LinkOutcome.Approved -> finishSignIn(outcome)
                LinkOutcome.Denied -> fail(R.string.geek_login_err_denied)
                LinkOutcome.Expired -> fail(R.string.geek_login_err_expired)
            }
        }
    }

    fun reopenTelegram() {
        val waiting = state.value as? LoginUiState.Waiting ?: return
        eventChannel.trySend(LoginEvent.OpenTelegram(waiting.deepLink))
    }

    /**
     * Back to the choice screen, from the waiting screen or the username form.
     * A waiting request simply expires server-side.
     */
    fun cancel() {
        flow?.cancel()
        flow = null
        state.value = LoginUiState.Choose
    }

    fun continueAsGuest() {
        backend.continueAsGuest()
        eventChannel.trySend(LoginEvent.Done)
    }

    /** "نام کاربری" on the login screen: the form for the login set in the bot. */
    fun username() {
        if (flow?.isActive == true) return
        state.value = LoginUiState.Username()
    }

    fun submitPassword(username: String, password: String) {
        if (flow?.isActive == true) return
        if (username.isBlank() || password.isEmpty()) {
            eventChannel.trySend(LoginEvent.Message(R.string.geek_login_err_fields))
            return
        }
        flow = viewModelScope.launch {
            state.value = LoginUiState.Username(busy = true)
            val approved = try {
                backend.passwordLogin(username.trim(), password)
            } catch (e: ApiException) {
                backend.logFailure("Login: password sign-in failed (status=${e.status})", e)
                state.value = LoginUiState.Username()
                eventChannel.send(LoginEvent.Message(passwordMessageFor(e)))
                return@launch
            }
            finishSignIn(approved)
        }
    }

    private suspend fun finishSignIn(approved: LinkOutcome.Approved) {
        if (!backend.signIn(approved)) {
            fail(R.string.geek_login_err_storage)
            return
        }
        state.value = LoginUiState.Syncing
        try {
            backend.sync()
        } catch (e: IOException) {
            // Signed in all the same; the next launch syncs again.
            backend.logFailure("Login: first account sync failed", e)
            eventChannel.send(LoginEvent.Message(R.string.geek_login_sync_failed))
        }
        eventChannel.send(LoginEvent.Done)
    }

    private suspend fun fail(@StringRes message: Int) {
        state.value = LoginUiState.Choose
        eventChannel.send(LoginEvent.Message(message))
    }

    @StringRes
    private fun passwordMessageFor(e: ApiException): Int = when (e.status) {
        401 -> R.string.geek_login_err_wrong
        else -> messageFor(e)
    }

    @StringRes
    private fun messageFor(e: ApiException): Int = when (e.status) {
        null -> R.string.geek_login_err_network
        503 -> R.string.geek_login_err_disabled
        429 -> R.string.geek_login_err_rate
        else -> R.string.geek_login_err_network
    }
}
