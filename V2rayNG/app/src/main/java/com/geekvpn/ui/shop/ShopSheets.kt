package com.geekvpn.ui.shop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.api.PaymentMethodOption
import com.geekvpn.api.PaymentView
import com.geekvpn.api.WalletTransaction
import com.geekvpn.shop.PaymentKeys
import com.geekvpn.shop.TopupLimits
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatDate
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSheet
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.SpaceGrotesk
import com.v2ray.ang.R

/** How to pay: the wallet (purchases only), card to card, and each online gateway. */
@Composable
fun BoxScope.CheckoutSheet(
    sheet: ShopSheet.Checkout,
    options: List<PaymentMethodOption>,
    balance: Long?,
    busy: Boolean,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = appLocale()
    GeekSheet(
        title = stringResource(R.string.geek_checkout_title),
        subtitle = stringResource(R.string.geek_checkout_amount, formatNumber(sheet.total, locale)),
        closeLabel = stringResource(R.string.geek_sheet_close),
        onDismiss = onDismiss,
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { option ->
                val key = option.key ?: return@forEach
                when (key) {
                    PaymentKeys.WALLET -> {
                        val short = balance != null && balance < sheet.total
                        MethodRow(
                            icon = GeekIcons.Wallet,
                            title = stringResource(R.string.geek_checkout_wallet),
                            hint = if (short) {
                                stringResource(R.string.geek_checkout_wallet_short, formatNumber(balance ?: 0, locale))
                            } else {
                                stringResource(R.string.geek_checkout_wallet_hint, formatNumber(balance ?: 0, locale))
                            },
                            enabled = !busy && !short,
                            onClick = { onChoose(key) },
                        )
                    }
                    PaymentKeys.CARD -> MethodRow(
                        icon = GeekIcons.Card,
                        title = stringResource(R.string.geek_checkout_card),
                        hint = stringResource(R.string.geek_checkout_card_hint),
                        enabled = !busy,
                        onClick = { onChoose(key) },
                    )
                    else -> MethodRow(
                        icon = GeekIcons.Bank,
                        title = option.labelFa?.takeIf { it.isNotBlank() } ?: stringResource(R.string.geek_checkout_gateway),
                        hint = stringResource(R.string.geek_checkout_gateway_hint),
                        enabled = !busy,
                        onClick = { onChoose(key) },
                    )
                }
            }
        }
        if (busy) {
            Text(stringResource(R.string.geek_checkout_busy), style = Geek.type.caption, color = Geek.colors.onGlassMuted)
        }
    }
}

@Composable
private fun MethodRow(icon: ImageVector, title: String, hint: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Geek.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.chip)
            .border(1.dp, colors.track, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(colors.soft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.link, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Geek.type.row, color = colors.onGlass)
            Text(hint, style = Geek.type.caption, color = colors.onGlassMuted)
        }
        Icon(GeekIcons.ChevronStart, contentDescription = null, tint = colors.onGlassMuted, modifier = Modifier.size(18.dp))
    }
}

/** Wallet.html: presets or a custom amount, the top-up button, recent activity. */
@Composable
fun BoxScope.WalletSheet(
    balance: Long?,
    wallet: WalletUiState,
    busy: Boolean,
    onTopup: (Long) -> Unit,
    onPending: (PaymentView) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Geek.colors
    val locale = appLocale()
    var preset by rememberSaveable { mutableLongStateOf(TopupLimits.PRESETS[1]) }
    var custom by rememberSaveable { mutableStateOf("") }
    val amount = TopupLimits.parse(custom) ?: preset

    GeekSheet(
        title = stringResource(R.string.geek_wallet_title),
        subtitle = stringResource(R.string.geek_wallet_balance, formatNumber(balance ?: 0, locale)),
        closeLabel = stringResource(R.string.geek_sheet_close),
        onDismiss = onDismiss,
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TopupLimits.PRESETS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { value ->
                        val selected = custom.isBlank() && value == preset
                        val shape = RoundedCornerShape(14.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(shape)
                                .background(if (selected) colors.soft else colors.chip)
                                .border(if (selected) 2.dp else 1.dp, if (selected) colors.link else colors.track, shape)
                                .selectable(selected = selected, role = Role.RadioButton) {
                                    preset = value
                                    custom = ""
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(formatNumber(value, locale), style = Geek.type.row.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = colors.onGlass)
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val customLabel = stringResource(R.string.geek_wallet_custom)
            Text(customLabel, style = Geek.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.onGlassMuted)
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(colors.softButton).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(GeekIcons.Pencil, contentDescription = null, tint = colors.onGlassMuted, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (custom.isEmpty()) {
                        Text(stringResource(R.string.geek_wallet_custom_hint, formatNumber(150_000, locale)), style = Geek.type.body, color = colors.onGlassMuted)
                    }
                    BasicTextField(
                        value = custom,
                        onValueChange = { text -> custom = text.filter { Character.isDigit(it) }.take(9) },
                        singleLine = true,
                        textStyle = Geek.type.body.copy(color = colors.onGlass),
                        cursorBrush = SolidColor(colors.link),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = customLabel },
                    )
                }
            }
        }
        GeekPrimaryButton(
            text = stringResource(R.string.geek_wallet_charge, formatNumber(amount, locale)),
            icon = GeekIcons.Card,
            onClick = { onTopup(amount) },
            enabled = !busy,
        )
        Text(stringResource(R.string.geek_wallet_recent), style = Geek.type.label, color = colors.onGlass)
        val pending = wallet.pending
        val transactions = wallet.transactions
        if (pending.isEmpty() && transactions.isEmpty()) {
            Text(
                stringResource(if (wallet.loading) R.string.geek_wallet_loading else R.string.geek_wallet_empty),
                style = Geek.type.caption,
                color = colors.onGlassMuted,
            )
        }
        pending.forEach { payment -> PendingRow(payment, onPending) }
        transactions.forEach { TransactionRow(it) }
    }
}

@Composable
private fun PendingRow(payment: PaymentView, onOpen: (PaymentView) -> Unit) {
    val colors = Geek.colors
    val locale = appLocale()
    val awaiting = payment.state == "awaiting_proof" && payment.card != null
    ActivityRow(
        icon = GeekIcons.Clock,
        tint = colors.warning,
        tile = colors.warningSoft,
        amount = stringResource(R.string.geek_home_balance, formatNumber(payment.amount ?: 0, locale)),
        detail = formatDate(payment.createdAt, locale),
        badge = stringResource(if (awaiting) R.string.geek_wallet_state_awaiting else R.string.geek_wallet_state_review),
        onClick = if (awaiting) ({ onOpen(payment) }) else null,
    )
}

@Composable
private fun TransactionRow(item: WalletTransaction) {
    val colors = Geek.colors
    val locale = appLocale()
    val credit = item.kind in setOf("topup", "cashback", "referral", "refund")
    val amount = formatNumber(item.amount ?: 0, locale)
    val date = formatDate(item.createdAt, locale)
    ActivityRow(
        icon = if (credit) GeekIcons.ArrowDown else GeekIcons.Bag,
        tint = if (credit) colors.success else colors.link,
        tile = if (credit) colors.successSoft else colors.soft,
        amount = stringResource(if (credit) R.string.geek_wallet_credit else R.string.geek_wallet_debit, amount),
        detail = listOfNotNull(item.descriptionFa?.takeIf { it.isNotBlank() }, date).joinToString(" · ").ifEmpty { null },
        badge = null,
        onClick = null,
    )
}

@Composable
private fun ActivityRow(
    icon: ImageVector,
    tint: Color,
    tile: Color,
    amount: String,
    detail: String?,
    badge: String?,
    onClick: (() -> Unit)?,
) {
    val colors = Geek.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, colors.track, RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tile), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(amount, style = Geek.type.row.copy(fontWeight = FontWeight.ExtraBold), color = colors.onGlass)
            if (detail != null) Text(detail, style = Geek.type.micro, color = colors.onGlassMuted)
        }
        if (badge != null) {
            Text(
                badge,
                style = Geek.type.micro.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.warning,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.warningSoft).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/** Deposit.html: the card, the exact amount, and the receipt button. */
@Composable
fun BoxScope.DepositSheet(
    info: DepositInfo,
    uploading: Boolean,
    onCopy: (text: String, label: Int) -> Unit,
    onSendReceipt: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Geek.colors
    val locale = appLocale()
    GeekSheet(
        title = stringResource(R.string.geek_deposit_title),
        subtitle = stringResource(R.string.geek_deposit_subtitle),
        onDismiss = onDismiss,
    ) {
        BankCard(info, onCopy)
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.softButton).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.geek_deposit_amount), style = Geek.type.caption, color = colors.onGlassMuted)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(formatNumber(info.amount, locale), style = Geek.type.pageTitle.copy(fontSize = 24.sp), color = colors.onGlass)
                    Text(stringResource(R.string.geek_account_toman), style = Geek.type.caption, color = colors.onGlassMuted, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(stringResource(R.string.geek_deposit_rials, formatNumber(info.amount * 10, locale)), style = Geek.type.micro, color = colors.onGlassMuted)
            }
            CopyButton(stringResource(R.string.geek_deposit_copy_amount), background = colors.chip, tint = colors.onGlass) {
                onCopy(info.amount.toString(), R.string.geek_deposit_amount_copied)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.warningSoft).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(GeekIcons.Clock, contentDescription = null, tint = colors.warning, modifier = Modifier.padding(top = 3.dp).size(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.geek_deposit_exact), style = Geek.type.caption, color = colors.warning)
                if (info.reviewNote.isNotBlank()) {
                    Text(info.reviewNote, style = Geek.type.caption, color = colors.warning)
                }
            }
        }
        GeekPrimaryButton(
            text = stringResource(if (uploading) R.string.geek_deposit_sending else R.string.geek_deposit_send),
            icon = GeekIcons.Receipt,
            onClick = onSendReceipt,
            enabled = !uploading,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .heightIn(min = 44.dp)
                .clickable(enabled = !uploading, role = Role.Button, onClick = onDismiss)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.geek_sheet_close), style = Geek.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.link)
        }
    }
}

@Composable
private fun BankCard(info: DepositInfo, onCopy: (String, Int) -> Unit) {
    val colors = Geek.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 190.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.bankCard)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(GeekIcons.Bank, contentDescription = null, tint = colors.onBankCard, modifier = Modifier.size(18.dp))
            Text(
                info.bank.ifBlank { stringResource(R.string.geek_deposit_bank) },
                style = Geek.type.row.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.onBankCard,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Image(painterResource(R.drawable.ic_geek_logo), contentDescription = null, modifier = Modifier.size(40.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                groupCardNumber(info.cardNumber),
                style = Geek.type.body.copy(
                    fontFamily = SpaceGrotesk,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    textDirection = TextDirection.Ltr,
                    textAlign = TextAlign.End,
                ),
                color = colors.onBankCard,
                modifier = Modifier.weight(1f),
            )
            CopyButton(stringResource(R.string.geek_deposit_copy_card), background = colors.onBankCard, tint = colors.bankCard) {
                onCopy(info.cardNumber.filter { it.isDigit() }, R.string.geek_deposit_card_copied)
            }
        }
        if (info.cardHolder.isNotBlank()) {
            Text(stringResource(R.string.geek_deposit_holder, info.cardHolder), style = Geek.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.onBankCard)
        }
    }
}

@Composable
private fun CopyButton(label: String, background: Color, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(GeekIcons.Copy, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** "6037991122334455" as "6037 9911 2233 4455"; anything else as sent. */
fun groupCardNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    if (digits.length != 16) return number
    return digits.chunked(4).joinToString(" ")
}
