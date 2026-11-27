package com.geekvpn.ui.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.geekvpn.account.OwnerDialog
import com.geekvpn.account.OwnerInput
import com.geekvpn.account.OwnerText
import com.geekvpn.account.OwnerUiState
import com.geekvpn.account.refundReason
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatDateTime
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R

/** What the owner dialogs can ask of the screen. */
interface OwnerDialogActions {
    fun onRename(name: String)
    fun onTransfer(recipient: String)
    fun onRefund()
    fun onRetryOffer()
    fun onClearError()
    fun onDismiss()
}

/**
 * The dialog for the bot's «تغییر نام»، «انتقال به کاربر دیگر» and «برگشت وجه»
 * on one service, whichever [state] has open. The server's refusal stays in
 * the dialog so the customer can correct what they typed.
 */
@Composable
fun ServiceOwnerDialog(state: OwnerUiState, actions: OwnerDialogActions) {
    when (val dialog = state.dialog) {
        is OwnerDialog.Rename -> RenameDialog(dialog, state, actions)
        is OwnerDialog.Transfer -> TransferDialog(dialog, state, actions)
        is OwnerDialog.Refund -> RefundDialog(dialog, state, actions)
        null -> Unit
    }
}

@Composable
private fun RenameDialog(dialog: OwnerDialog.Rename, state: OwnerUiState, actions: OwnerDialogActions) {
    var name by rememberSaveable(dialog.subscriptionId) { mutableStateOf(dialog.current) }
    AlertDialog(
        onDismissRequest = actions::onDismiss,
        title = { Text(stringResource(R.string.geek_owner_rename_title), style = Geek.type.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.geek_owner_rename_text, OwnerInput.NAME_MAX), style = Geek.type.body)
                OwnerField(
                    value = name,
                    onValueChange = {
                        name = it
                        actions.onClearError()
                    },
                    label = stringResource(R.string.geek_owner_rename_field),
                    enabled = !state.busy,
                    keyboard = KeyboardOptions(imeAction = ImeAction.Done),
                )
                ErrorLine(state.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.onRename(name) }, enabled = !state.busy) {
                Text(stringResource(R.string.geek_owner_save), style = Geek.type.button)
            }
        },
        dismissButton = {
            TextButton(onClick = { actions.onRename("") }, enabled = !state.busy && dialog.current.isNotBlank()) {
                Text(stringResource(R.string.geek_owner_rename_clear), style = Geek.type.button)
            }
        },
    )
}

/** Two steps, as in the bot: the id, then «مطمئنی؟» naming the service and the id. */
@Composable
private fun TransferDialog(dialog: OwnerDialog.Transfer, state: OwnerUiState, actions: OwnerDialogActions) {
    val colors = Geek.colors
    var recipient by rememberSaveable(dialog.subscriptionId) { mutableStateOf("") }
    var confirming by rememberSaveable(dialog.subscriptionId) { mutableStateOf(false) }
    var badId by rememberSaveable(dialog.subscriptionId) { mutableStateOf(false) }
    // A refusal (no such customer, their own id) is fixed by editing the id.
    LaunchedEffect(state.error) { if (state.error != null) confirming = false }
    val id = OwnerInput.telegramId(recipient)

    if (confirming && id != null) {
        AlertDialog(
            onDismissRequest = actions::onDismiss,
            title = { Text(stringResource(R.string.geek_owner_transfer_confirm_title), style = Geek.type.sectionTitle) },
            text = {
                Text(
                    stringResource(R.string.geek_owner_transfer_confirm_text, dialog.title, id.toString()),
                    style = Geek.type.body,
                )
            },
            confirmButton = {
                TextButton(onClick = { actions.onTransfer(recipient) }, enabled = !state.busy) {
                    Text(stringResource(R.string.geek_owner_transfer_confirm), style = Geek.type.button, color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }, enabled = !state.busy) {
                    Text(stringResource(R.string.geek_owner_back), style = Geek.type.button)
                }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = actions::onDismiss,
        title = { Text(stringResource(R.string.geek_owner_transfer_title), style = Geek.type.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.geek_owner_transfer_text), style = Geek.type.body)
                OwnerField(
                    value = recipient,
                    onValueChange = {
                        recipient = it
                        badId = false
                        actions.onClearError()
                    },
                    label = stringResource(R.string.geek_owner_transfer_field),
                    enabled = !state.busy,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    ltr = true,
                )
                Text(stringResource(R.string.geek_owner_transfer_note), style = Geek.type.caption, color = colors.warning)
                ErrorLine(if (badId) OwnerText(res = R.string.geek_owner_transfer_bad_id) else state.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (id == null) badId = true else confirming = true },
                enabled = !state.busy && recipient.isNotBlank(),
            ) { Text(stringResource(R.string.geek_owner_next), style = Geek.type.button) }
        },
        dismissButton = {
            TextButton(onClick = actions::onDismiss) { Text(stringResource(R.string.geek_account_cancel), style = Geek.type.button) }
        },
    )
}

@Composable
private fun RefundDialog(dialog: OwnerDialog.Refund, state: OwnerUiState, actions: OwnerDialogActions) {
    val colors = Geek.colors
    val locale = appLocale()
    val offer = dialog.offer
    AlertDialog(
        onDismissRequest = actions::onDismiss,
        title = { Text(stringResource(R.string.geek_owner_refund_title), style = Geek.type.sectionTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    offer == null && state.busy ->
                        Text(stringResource(R.string.geek_owner_refund_checking), style = Geek.type.body, color = colors.onGlassMuted)
                    offer == null -> ErrorLine(state.error)
                    offer.eligible == true -> {
                        Text(
                            stringResource(R.string.geek_owner_refund_text, dialog.title, formatNumber(offer.amount ?: 0L, locale)),
                            style = Geek.type.body,
                        )
                        formatDateTime(offer.deadlineAt, locale)?.let { deadline ->
                            Text(stringResource(R.string.geek_owner_refund_deadline, deadline), style = Geek.type.caption, color = colors.onGlassMuted)
                        }
                        Text(stringResource(R.string.geek_owner_refund_warning), style = Geek.type.caption, color = colors.warning)
                        ErrorLine(state.error)
                    }
                    else -> {
                        val reason = refundReason(offer)
                        Text(reason.text ?: stringResource(reason.res), style = Geek.type.body)
                        val hours = offer.windowHours ?: 0
                        if (hours > 0) {
                            Text(
                                stringResource(R.string.geek_owner_refund_policy, formatNumber(hours.toLong(), locale)),
                                style = Geek.type.caption,
                                color = colors.onGlassMuted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                offer?.eligible == true -> TextButton(onClick = actions::onRefund, enabled = !state.busy) {
                    Text(stringResource(R.string.geek_owner_refund_confirm), style = Geek.type.button, color = colors.danger)
                }
                offer == null && !state.busy -> TextButton(onClick = actions::onRetryOffer) {
                    Text(stringResource(R.string.geek_tickets_retry), style = Geek.type.button)
                }
                else -> TextButton(onClick = actions::onDismiss) {
                    Text(stringResource(R.string.geek_owner_close), style = Geek.type.button)
                }
            }
        },
        dismissButton = if (offer?.eligible == true || (offer == null && !state.busy)) {
            { TextButton(onClick = actions::onDismiss) { Text(stringResource(R.string.geek_account_cancel), style = Geek.type.button) } }
        } else {
            null
        },
    )
}

@Composable
private fun ErrorLine(error: OwnerText?) {
    if (error == null) return
    Text(error.text ?: stringResource(error.res), style = Geek.type.caption, color = Geek.colors.danger)
}

@Composable
private fun OwnerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboard: KeyboardOptions,
    ltr: Boolean = false,
) {
    val colors = Geek.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = Geek.type.caption) },
        enabled = enabled,
        singleLine = true,
        // A Telegram id is a number; typed right to left its digits would show reversed while typing.
        textStyle = Geek.type.row.copy(textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content, color = colors.onGlass),
        keyboardOptions = keyboard,
        shape = Geek.shapes.button,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.action,
            unfocusedBorderColor = colors.checkboxBorder,
            focusedLabelColor = colors.action,
            unfocusedLabelColor = colors.onGlassMuted,
            cursorColor = colors.action,
            focusedContainerColor = colors.softButton,
            unfocusedContainerColor = colors.softButton,
            disabledContainerColor = colors.softButton,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
