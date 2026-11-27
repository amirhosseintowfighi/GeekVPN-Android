package com.geekvpn.account

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.geekvpn.GeekGraph
import com.geekvpn.api.ApiException
import com.geekvpn.api.RefundOffer
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * What the customer typed, read the way the bot reads it
 * (`ServiceOwnership.rename`, `fa.telegram_id_from`), so the app refuses
 * before sending exactly what the server would refuse.
 */
object OwnerInput {
    /** The bot's limit, after whitespace is squeezed. */
    const val NAME_MAX = 32

    /** The bot's "back to the plan's name". */
    private const val CLEAR_NAME = "-"

    /** Whitespace squeezed; null when the name is to be cleared (blank or a lone `-`). */
    fun name(raw: String): String? {
        val cleaned = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")
        return cleaned.takeUnless { it.isEmpty() || it == CLEAR_NAME }
    }

    fun nameTooLong(raw: String): Boolean = (name(raw)?.length ?: 0) > NAME_MAX

    /** A numeric Telegram id, Persian or Arabic-Indic digits too; null for anything else. */
    fun telegramId(raw: String): Long? {
        val digits = buildString {
            raw.trim().forEach { c ->
                append(
                    when (c) {
                        in '۰'..'۹' -> '0' + (c - '۰')
                        in '٠'..'٩' -> '0' + (c - '٠')
                        else -> c
                    },
                )
            }
        }
        if (digits.isEmpty() || digits.length > 19 || !digits.all { it in '0'..'9' }) return null
        return digits.toLongOrNull()?.takeIf { it > 0 }
    }
}

/** A sentence for the screen: the server's own, or one of ours. */
data class OwnerText(val text: String? = null, @param:StringRes val res: Int = 0)

/**
 * Which refusal it was. A code we know gets our own sentence (in the app's
 * language; the server's `message_fa` is its generic line for codes it has no
 * copy for, `feature_disabled` among them). An unknown code gets the server's.
 */
fun ownerFailure(e: IOException): OwnerText {
    val api = e as? ApiException
    if (api == null || api.isNetwork) return OwnerText(res = R.string.geek_owner_err_network)
    val res = when (api.code) {
        "feature_disabled" -> R.string.geek_owner_err_disabled
        "subscription_not_found", "device_not_found" -> R.string.geek_owner_err_not_found
        "subscription_revoked" -> R.string.geek_owner_err_revoked
        "service_name_too_long" -> R.string.geek_owner_name_too_long
        "transfer_recipient_invalid" -> R.string.geek_owner_transfer_bad_id
        "transfer_to_self" -> R.string.geek_owner_transfer_self
        "transfer_recipient_not_found" -> R.string.geek_owner_transfer_no_user
        "refund_disabled" -> R.string.geek_owner_refund_disabled
        "refund_service_not_active" -> R.string.geek_owner_refund_not_active
        "refund_window_closed" -> R.string.geek_owner_refund_window_closed
        "refund_not_purchased" -> R.string.geek_owner_refund_not_purchased
        "refund_service_used" -> R.string.geek_owner_refund_used
        "unused_refund_refused" -> R.string.geek_owner_refund_refused
        else -> null
    }
    return when {
        res != null -> OwnerText(res = res)
        api.messageFa != null -> OwnerText(api.messageFa)
        else -> OwnerText(res = R.string.geek_owner_err_generic)
    }
}

/** Why a refund offer says no, in the app's language; the server's sentence for a reason it added later. */
fun refundReason(offer: RefundOffer): OwnerText =
    ownerFailure(ApiException(409, "refund offer", code = offer.reasonCode ?: "unused_refund_refused", messageFa = offer.reasonFa))

/** The bot's owner controls on one service (`/api/miniapp/subscriptions/{id}/…`). */
interface OwnerPort {
    suspend fun rename(subscriptionId: String, name: String?)
    suspend fun transfer(subscriptionId: String, recipient: Long)
    suspend fun refundOffer(subscriptionId: String): RefundOffer

    /** The toman returned and the server's sentence for it. */
    suspend fun refund(subscriptionId: String): Pair<Long?, String?>
}

/** The dialog open over the Services tab, if any. */
sealed interface OwnerDialog {
    val subscriptionId: String
    val title: String

    data class Rename(override val subscriptionId: String, override val title: String, val current: String) : OwnerDialog

    data class Transfer(override val subscriptionId: String, override val title: String) : OwnerDialog

    /** [offer] is null while it loads. */
    data class Refund(override val subscriptionId: String, override val title: String, val offer: RefundOffer? = null) : OwnerDialog
}

data class OwnerUiState(
    val dialog: OwnerDialog? = null,
    val busy: Boolean = false,
    /** Why the last step was refused; shown inside the dialog. */
    val error: OwnerText? = null,
)

/** A change the server made. [refreshWallet]: money moved. */
data class OwnerDone(val message: OwnerText, val refreshWallet: Boolean = false)

/**
 * Rename, transfer and refund from a service card. Each opens a dialog, and
 * the dialog stays open with the server's reason when it refuses; on success
 * it closes and [done] tells Home to sync the account (the name, or the
 * service gone from this list).
 */
class ServiceOwnerViewModel(
    private val port: OwnerPort,
    /** LogUtil in the app; tests run without MMKV. */
    private val log: (String, Throwable) -> Unit = { message, e -> LogUtil.w(AppConfig.TAG, message, e) },
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val state = MutableStateFlow(OwnerUiState())
    val uiState: StateFlow<OwnerUiState> = state.asStateFlow()

    private val doneChannel = Channel<OwnerDone>(Channel.BUFFERED)
    val done: Flow<OwnerDone> = doneChannel.receiveAsFlow()

    private var job: Job? = null

    fun openRename(subscriptionId: String, title: String, current: String?) =
        open(OwnerDialog.Rename(subscriptionId, title, current.orEmpty()))

    fun openTransfer(subscriptionId: String, title: String) = open(OwnerDialog.Transfer(subscriptionId, title))

    /** Asks the server first whether this service can go back, and for how much. */
    fun openRefund(subscriptionId: String, title: String) {
        open(OwnerDialog.Refund(subscriptionId, title))
        loadOffer()
    }

    fun retryOffer() = loadOffer()

    fun dismiss() {
        job?.cancel()
        state.value = OwnerUiState()
    }

    fun clearError() = state.update { it.copy(error = null) }

    fun rename(raw: String) {
        val dialog = state.value.dialog as? OwnerDialog.Rename ?: return
        if (OwnerInput.nameTooLong(raw)) {
            state.update { it.copy(error = OwnerText(res = R.string.geek_owner_name_too_long)) }
            return
        }
        run("rename") {
            port.rename(dialog.subscriptionId, OwnerInput.name(raw))
            OwnerDone(OwnerText(res = R.string.geek_owner_rename_done))
        }
    }

    /** Called after the confirmation; the move is immediate and cannot be undone. */
    fun transfer(raw: String) {
        val dialog = state.value.dialog as? OwnerDialog.Transfer ?: return
        val recipient = OwnerInput.telegramId(raw)
        if (recipient == null) {
            state.update { it.copy(error = OwnerText(res = R.string.geek_owner_transfer_bad_id)) }
            return
        }
        run("transfer") {
            port.transfer(dialog.subscriptionId, recipient)
            OwnerDone(OwnerText(res = R.string.geek_owner_transfer_done))
        }
    }

    fun refund() {
        val dialog = state.value.dialog as? OwnerDialog.Refund ?: return
        if (dialog.offer?.eligible != true) return
        run("refund") {
            val (_, message) = port.refund(dialog.subscriptionId)
            OwnerDone(message?.takeIf { it.isNotBlank() }?.let { OwnerText(it) } ?: OwnerText(res = R.string.geek_owner_refund_done), refreshWallet = true)
        }
    }

    private fun open(dialog: OwnerDialog) {
        job?.cancel()
        state.value = OwnerUiState(dialog = dialog)
    }

    private fun loadOffer() {
        val dialog = state.value.dialog as? OwnerDialog.Refund ?: return
        job?.cancel()
        state.update { it.copy(busy = true, error = null) }
        job = viewModelScope.launch {
            try {
                val offer = port.refundOffer(dialog.subscriptionId)
                state.update { current ->
                    if (current.dialog != dialog) current else current.copy(dialog = dialog.copy(offer = offer), busy = false)
                }
            } catch (e: IOException) {
                log("ServiceOwner: refund offer for ${dialog.subscriptionId} failed (${(e as? ApiException)?.status})", e)
                state.update { if (it.dialog != dialog) it else it.copy(busy = false, error = ownerFailure(e)) }
            }
        }
    }

    private fun run(what: String, call: suspend () -> OwnerDone) {
        if (state.value.busy) return
        val dialog = state.value.dialog ?: return
        state.update { it.copy(busy = true, error = null) }
        job = viewModelScope.launch {
            try {
                val outcome = call()
                state.value = OwnerUiState()
                doneChannel.send(outcome)
            } catch (e: IOException) {
                log("ServiceOwner: $what of ${dialog.subscriptionId} failed (${(e as? ApiException)?.status} ${(e as? ApiException)?.code})", e)
                state.update { if (it.dialog != dialog) it else it.copy(busy = false, error = ownerFailure(e)) }
            }
        }
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer {
                ServiceOwnerViewModel(
                    object : OwnerPort {
                        override suspend fun rename(subscriptionId: String, name: String?) {
                            GeekGraph.api.renameSubscription(subscriptionId, name)
                        }

                        override suspend fun transfer(subscriptionId: String, recipient: Long) {
                            GeekGraph.api.transferSubscription(subscriptionId, recipient)
                        }

                        override suspend fun refundOffer(subscriptionId: String) = GeekGraph.api.refundOffer(subscriptionId)

                        override suspend fun refund(subscriptionId: String) =
                            GeekGraph.api.refundSubscription(subscriptionId).let { it.amount to it.messageFa }
                    },
                )
            }
        }
    }
}
