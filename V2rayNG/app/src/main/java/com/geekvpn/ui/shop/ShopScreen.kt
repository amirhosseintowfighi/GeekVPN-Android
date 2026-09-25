package com.geekvpn.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.api.StorePlan
import com.geekvpn.api.TrialOffer
import com.geekvpn.shop.ShopCatalog
import com.geekvpn.shop.Tier
import com.geekvpn.ui.common.PageTitle
import com.geekvpn.ui.common.WalletChip
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R
import java.util.Locale

interface ShopActions {
    fun onWallet()
    fun onLogin()
    fun onRetry()
    fun onTier(tier: Tier)
    fun onDuration(days: Int)
    fun onPlan(planId: String)
    fun onApplyCoupon(code: String)
    fun onClearCoupon()
    fun onCancelRenew()
    fun onPay()
    fun onTrial()
}

/** Shop.html: the free trial, then tier, length and volume, and the pay bar. */
@Composable
fun ShopScreen(state: ShopUiState, actions: ShopActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PageTitle(stringResource(R.string.geek_shop_title), Modifier.weight(1f))
            if (state.signedIn) WalletChip(state.balance, actions::onWallet)
        }
        when {
            !state.signedIn -> GuestCard(actions::onLogin)
            state.tiers.isEmpty() && state.loading -> NoticeCard(stringResource(R.string.geek_shop_loading), null, null)
            state.tiers.isEmpty() && state.loadFailed ->
                NoticeCard(stringResource(R.string.geek_shop_err_load), stringResource(R.string.geek_shop_retry), actions::onRetry)
            state.tiers.isEmpty() -> NoticeCard(stringResource(R.string.geek_shop_empty), stringResource(R.string.geek_shop_retry), actions::onRetry)
            else -> {
                state.trial?.takeIf { it.available == true }?.let { TrialCard(it, state.busy, actions::onTrial) }
                state.renewing?.let { RenewBanner(it.title, actions::onCancelRenew) }
                PlanCard(state, actions)
                PayBar(state, actions::onPay)
            }
        }
    }
}

@Composable
private fun GuestCard(onLogin: () -> Unit) {
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.geek_shop_guest), style = Geek.type.body, color = Geek.colors.onGlass)
            GeekPrimaryButton(text = stringResource(R.string.geek_shop_login), icon = GeekIcons.Telegram, onClick = onLogin)
        }
    }
}

@Composable
private fun NoticeCard(text: String, action: String?, onAction: (() -> Unit)?) {
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(text, style = Geek.type.body, color = Geek.colors.onGlass)
            if (action != null && onAction != null) {
                GeekSecondaryButton(text = action, icon = GeekIcons.Refresh, onClick = onAction, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TrialCard(offer: TrialOffer, busy: Boolean, onClaim: () -> Unit) {
    val colors = Geek.colors
    val locale = appLocale()
    GlassSurface(kind = GlassKind.Clear, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(GeekIcons.Gift, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.geek_trial_title), style = Geek.type.row.copy(fontWeight = FontWeight.ExtraBold), color = colors.onBackground)
                Text(
                    stringResource(
                        R.string.geek_trial_subtitle,
                        formatNumber((offer.trafficMib ?: 0).toLong(), locale),
                        formatNumber((offer.durationDays ?: 0).toLong(), locale),
                    ),
                    style = Geek.type.caption,
                    color = colors.onBackgroundMuted,
                )
            }
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.onBackground)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onClaim)
                    .alpha(if (busy) 0.5f else 1f)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.geek_trial_claim), style = Geek.type.caption.copy(fontWeight = FontWeight.ExtraBold), color = colors.action)
            }
        }
    }
}

@Composable
private fun RenewBanner(title: String, onCancel: () -> Unit) {
    val colors = Geek.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.successSoft)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GeekIcons.Refresh, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.geek_shop_renewing, title),
            style = Geek.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.success,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp),
        )
        val label = stringResource(R.string.geek_shop_renew_cancel)
        Box(
            Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GeekIcons.Close, contentDescription = label, tint = colors.success, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PlanCard(state: ShopUiState, actions: ShopActions) {
    val colors = Geek.colors
    val locale = appLocale()
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.tiers.size > 1) {
                TierSwitch(state.tiers, state.tier, actions::onTier)
            }
            Text(stringResource(R.string.geek_shop_duration), style = Geek.type.label, color = colors.onGlass)
            ChoiceRows(state.durations, columns = 4) { days, modifier ->
                DurationChip(days, selected = days == state.duration, locale = locale, onClick = { actions.onDuration(days) }, modifier = modifier)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.geek_shop_volume), style = Geek.type.label, color = colors.onGlass, modifier = Modifier.weight(1f))
                ShopCatalog.discountPercent(state.plan)?.let { percent ->
                    Text(
                        stringResource(R.string.geek_shop_discount, formatNumber(percent.toLong(), locale)),
                        style = Geek.type.micro.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.success,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.successSoft).padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            ChoiceRows(state.volumes, columns = 4) { plan, modifier ->
                VolumeChip(plan, selected = plan.planId == state.plan?.planId, locale = locale, onClick = { plan.planId?.let(actions::onPlan) }, modifier = modifier)
            }
            CouponRow(state.coupon, actions)
        }
    }
}

@Composable
private fun TierSwitch(tiers: List<Tier>, selected: Tier?, onSelect: (Tier) -> Unit) {
    val colors = Geek.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(colors.soft)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tiers.forEach { tier ->
            val isSelected = tier == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) colors.chip else colors.soft)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tier) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(tier.label),
                    style = Geek.type.caption.copy(fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium),
                    color = if (isSelected) colors.onGlass else colors.onGlassMuted,
                )
            }
        }
    }
}

val Tier.label: Int
    get() = when (this) {
        Tier.Direct -> R.string.geek_tier_direct
        Tier.Tunnel -> R.string.geek_tier_tunnel
        Tier.Elite -> R.string.geek_tier_elite
    }

/** [items] in rows of [columns] equal cells; a short last row keeps the cell width. */
@Composable
private fun <T> ChoiceRows(items: List<T>, columns: Int, cell: @Composable (T, Modifier) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DurationChip(days: Int, selected: Boolean, locale: Locale, onClick: () -> Unit, modifier: Modifier) {
    val colors = Geek.colors
    val label = ShopCatalog.durationLabel(days)
    val unit = if (label.months) {
        stringResource(R.string.geek_shop_months_unit)
    } else {
        stringResource(R.string.geek_shop_days_unit)
    }
    Column(
        modifier = modifier
            .height(62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.action else colors.chip)
            .then(if (selected) Modifier else Modifier.border(1.dp, colors.track, RoundedCornerShape(16.dp)))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val content = if (selected) colors.onAction else colors.onGlass
        Text(formatNumber(label.count.toLong(), locale), style = Geek.type.row.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = content)
        Text(unit, style = Geek.type.micro, color = content.copy(alpha = 0.75f))
    }
}

@Composable
private fun VolumeChip(plan: StorePlan, selected: Boolean, locale: Locale, onClick: () -> Unit, modifier: Modifier) {
    val colors = Geek.colors
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(if (selected) colors.soft else colors.chip)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.link else colors.track, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val quota = plan.quotaGib
        Text(
            if (quota == null) stringResource(R.string.geek_services_unlimited) else formatNumber(quota.toLong(), locale),
            style = if (quota == null) Geek.type.caption.copy(fontWeight = FontWeight.Bold) else Geek.type.numberSmall.copy(fontSize = 15.sp),
            color = colors.onGlass,
        )
    }
}

@Composable
private fun CouponRow(coupon: CouponState?, actions: ShopActions) {
    val colors = Geek.colors
    var open by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    when {
        coupon != null && coupon.valid -> Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(colors.successSoft).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(GeekIcons.Tag, contentDescription = null, tint = colors.success, modifier = Modifier.size(16.dp))
            Text(
                coupon.message ?: coupon.code,
                style = Geek.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.success,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            val label = stringResource(R.string.geek_shop_coupon_remove)
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = label, onClick = actions::onClearCoupon), contentAlignment = Alignment.Center) {
                Icon(GeekIcons.Close, contentDescription = label, tint = colors.success, modifier = Modifier.size(16.dp))
            }
        }
        open -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CodeField(code, onChange = { code = it }, onDone = { actions.onApplyCoupon(code) }, modifier = Modifier.weight(1f))
                GeekSecondaryButton(text = stringResource(R.string.geek_shop_coupon_apply), onClick = { actions.onApplyCoupon(code) }, enabled = code.isNotBlank())
            }
            if (coupon != null && !coupon.valid && coupon.message != null) {
                Text(coupon.message, style = Geek.type.caption, color = colors.danger)
            }
        }
        else -> Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button) { open = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(GeekIcons.Tag, contentDescription = null, tint = colors.link, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.geek_shop_coupon_ask), style = Geek.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.link)
        }
    }
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    val colors = Geek.colors
    Box(
        modifier = modifier.height(52.dp).clip(RoundedCornerShape(14.dp)).background(colors.softButton).padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(stringResource(R.string.geek_shop_coupon_hint), style = Geek.type.body, color = colors.onGlassMuted)
        }
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(64)) },
            singleLine = true,
            textStyle = Geek.type.body.copy(color = colors.onGlass, textDirection = TextDirection.Ltr, textAlign = TextAlign.Start),
            cursorBrush = SolidColor(colors.link),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PayBar(state: ShopUiState, onPay: () -> Unit) {
    val colors = Geek.colors
    val locale = appLocale()
    val plan = state.plan
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Geek.shapes.card)
            .background(colors.payBar)
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            state.wasPrice?.let {
                Text(
                    stringResource(R.string.geek_home_balance, formatNumber(it, locale)),
                    style = Geek.type.micro.copy(textDecoration = TextDecoration.LineThrough),
                    color = colors.onPayBar.copy(alpha = 0.7f),
                )
            }
            state.total?.let {
                Text(
                    stringResource(R.string.geek_home_balance, formatNumber(it, locale)),
                    style = Geek.type.row.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                    color = colors.onPayBar,
                )
            }
            if (plan != null) {
                Text(planSummary(plan, locale), style = Geek.type.micro, color = colors.payButton)
            }
        }
        PayButton(enabled = plan != null && state.total != null && !state.busy, onClick = onPay)
    }
}

@Composable
private fun PayButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = Geek.colors
    Row(
        modifier = Modifier
            .height(50.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(colors.payButton)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(GeekIcons.Lock, contentDescription = null, tint = colors.onPayButton, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.geek_shop_pay), style = Geek.type.button, color = colors.onPayButton)
    }
}

/** "۴۰ گیگ · ۳۰ روز". */
@Composable
fun planSummary(plan: StorePlan, locale: Locale): String {
    val volume = plan.quotaGib?.let { stringResource(R.string.geek_shop_gib, formatNumber(it.toLong(), locale)) }
        ?: stringResource(R.string.geek_services_unlimited)
    val days = plan.durationDays ?: 0
    return stringResource(R.string.geek_shop_summary, volume, pluralStringResource(R.plurals.geek_shop_days, days, formatNumber(days.toLong(), locale)))
}
