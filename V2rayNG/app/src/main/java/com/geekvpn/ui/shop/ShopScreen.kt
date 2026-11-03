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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.api.StorePlan
import com.geekvpn.api.TrialOffer
import com.geekvpn.shop.ServiceKind
import com.geekvpn.shop.ShopCatalog
import com.geekvpn.ui.common.Eyebrow
import com.geekvpn.ui.common.Hairline
import com.geekvpn.ui.common.PageTitle
import com.geekvpn.ui.common.appLocale
import com.geekvpn.ui.common.formatNumber
import com.geekvpn.ui.components.ChoicePill
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GeekSegmented
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
    fun onKind(kind: ServiceKind)
    fun onDuration(days: Int)
    fun onPlan(planId: String)
    fun onCustom()
    fun onCustomGib(value: Int?)
    fun onApplyCoupon(code: String)
    fun onClearCoupon()
    fun onCancelRenew()
    fun onPay()
    fun onTrial()
}

/** Shop.dc.html: the free trial, then service type, length and volume, and the price with «پرداخت». */
@Composable
fun ShopScreen(state: ShopUiState, actions: ShopActions) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageTitle(stringResource(R.string.geek_shop_title), eyebrow = stringResource(R.string.geek_eyebrow_shop))
        when {
            !state.signedIn -> GuestCard(actions::onLogin)
            state.kinds.isEmpty() && state.loading -> NoticeCard(stringResource(R.string.geek_shop_loading), null, null)
            state.kinds.isEmpty() && state.loadFailed ->
                NoticeCard(stringResource(R.string.geek_shop_err_load), stringResource(R.string.geek_shop_retry), actions::onRetry)
            state.kinds.isEmpty() -> NoticeCard(stringResource(R.string.geek_shop_empty), stringResource(R.string.geek_shop_retry), actions::onRetry)
            else -> {
                state.trial?.takeIf { it.available == true }?.let { TrialCard(it, state.busy, actions::onTrial) }
                state.renewing?.let { RenewBanner(it.title, actions::onCancelRenew) }
                PlanChoices(state, actions)
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
    GlassSurface(kind = GlassKind.Clear, shape = Geek.shapes.row, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(colors.softButton).border(1.dp, colors.checkboxBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GeekIcons.Gift, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                    .clip(Geek.shapes.pill)
                    .background(colors.action)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onClaim)
                    .alpha(if (busy) 0.5f else 1f)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.geek_trial_claim), style = Geek.type.label.copy(fontSize = 13.sp), color = colors.onAction)
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
            .clip(Geek.shapes.pill)
            .background(colors.softButton)
            .border(1.5.dp, colors.clearGlassBorder, Geek.shapes.pill)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(GeekIcons.Refresh, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.geek_shop_renewing, title),
            style = Geek.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.onBackground,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp),
        )
        val label = stringResource(R.string.geek_shop_renew_cancel)
        Box(
            Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = label, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GeekIcons.Close, contentDescription = label, tint = colors.onBackground, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PlanChoices(state: ShopUiState, actions: ShopActions) {
    val colors = Geek.colors
    val locale = appLocale()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (state.kinds.size > 1) {
            // The category's own name, as the bot's shop shows it.
            GeekSegmented(options = state.kinds, selected = state.kind, label = { it.name }, onSelect = actions::onKind, height = 44.dp)
        }
        Eyebrow(stringResource(R.string.geek_eyebrow_duration))
        ChoiceRows(state.durations, columns = 4) { days, modifier ->
            DurationChip(days, selected = days == state.duration, locale = locale, onClick = { actions.onDuration(days) }, modifier = modifier)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(stringResource(R.string.geek_eyebrow_volume), Modifier.weight(1f))
            ShopCatalog.discountPercent(state.plan)?.let { percent ->
                Text(
                    stringResource(R.string.geek_shop_discount, formatNumber(percent.toLong(), locale)),
                    style = Geek.type.micro.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.logoBlue,
                )
            }
        }
        val custom = state.customFloor != null
        ChoiceRows(if (custom) state.volumes.map<StorePlan, StorePlan?> { it } + null else state.volumes, columns = 4) { plan, modifier ->
            if (plan == null) {
                ChoicePill(selected = state.customMode, onClick = actions::onCustom, dashed = true, modifier = modifier) { color ->
                    Text(stringResource(R.string.geek_shop_custom), style = Geek.type.label, color = color)
                }
            } else {
                VolumeChip(plan, selected = !state.customMode && plan.planId == state.plan?.planId, locale = locale, onClick = { plan.planId?.let(actions::onPlan) }, modifier = modifier)
            }
        }
        state.customFloor?.let { floor -> CustomVolume(state, floor, actions) }
        CouponRow(state.coupon, actions)
    }
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

/** "۱ ماهه", "۴۵ روزه". */
@Composable
private fun DurationChip(days: Int, selected: Boolean, locale: Locale, onClick: () -> Unit, modifier: Modifier) {
    val label = ShopCatalog.durationLabel(days)
    val text = stringResource(
        if (label.months) R.string.geek_shop_months_chip else R.string.geek_shop_days_chip,
        formatNumber(label.count.toLong(), locale),
    )
    ChoicePill(selected = selected, onClick = onClick, modifier = modifier) { color ->
        Text(text, style = Geek.type.body.copy(fontSize = 15.sp), color = color, maxLines = 1)
    }
}

@Composable
private fun VolumeChip(plan: StorePlan, selected: Boolean, locale: Locale, onClick: () -> Unit, modifier: Modifier) {
    ChoicePill(selected = selected, onClick = onClick, modifier = modifier) { color ->
        val quota = plan.quotaGib
        Text(
            if (quota == null) stringResource(R.string.geek_services_unlimited) else formatNumber(quota.toLong(), locale),
            style = if (quota == null) Geek.type.label else Geek.type.numberMedium.copy(fontSize = 16.sp),
            color = color,
            maxLines = 1,
        )
    }
}

/** Once «دلخواه» is chosen: a field for the monthly volume. */
@Composable
private fun CustomVolume(state: ShopUiState, floor: Int, actions: ShopActions) {
    val colors = Geek.colors
    val locale = appLocale()
    if (!state.customMode) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var text by rememberSaveable(state.duration, state.kind?.id) { mutableStateOf(floor.toString()) }
        Text(stringResource(R.string.geek_shop_custom_ask), style = Geek.type.caption, color = colors.onGlassMuted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(Geek.shapes.pill)
                .background(colors.soft)
                .border(1.dp, colors.checkboxBorder, Geek.shapes.pill)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { typed ->
                    // Persian digits too: the keyboard may be in either.
                    text = typed.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else c }.filter(Char::isDigit).joinToString("").take(5)
                    actions.onCustomGib(text.toIntOrNull())
                },
                singleLine = true,
                textStyle = Geek.type.body.copy(color = colors.onGlass, textDirection = TextDirection.Ltr, textAlign = TextAlign.Start),
                cursorBrush = SolidColor(colors.link),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val gib = state.customGib
        val days = state.duration ?: 30
        val months = days / 30
        Text(
            when {
                gib == null -> stringResource(R.string.geek_shop_custom_floor, formatNumber(floor.toLong(), locale))
                months >= 2 -> stringResource(
                    R.string.geek_shop_custom_total,
                    formatNumber(gib.toLong(), locale),
                    formatNumber(months.toLong(), locale),
                    formatNumber(ShopCatalog.termGib(gib, days).toLong(), locale),
                )
                else -> stringResource(R.string.geek_shop_custom_priced)
            },
            style = Geek.type.caption,
            color = if (gib == null) colors.danger else colors.onGlassMuted,
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
            modifier = Modifier.fillMaxWidth().clip(Geek.shapes.pill).background(colors.softButton).border(1.5.dp, colors.clearGlassBorder, Geek.shapes.pill).padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(GeekIcons.Tag, contentDescription = null, tint = colors.logoBlue, modifier = Modifier.size(16.dp))
            Text(
                coupon.message ?: coupon.code,
                style = Geek.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.onBackground,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            val label = stringResource(R.string.geek_shop_coupon_remove)
            Box(Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = label, onClick = actions::onClearCoupon), contentAlignment = Alignment.Center) {
                Icon(GeekIcons.Close, contentDescription = label, tint = colors.onBackground, modifier = Modifier.size(16.dp))
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
        ) {
            Text(
                stringResource(R.string.geek_shop_coupon_ask),
                style = Geek.type.caption.copy(fontSize = 13.sp, textDecoration = TextDecoration.Underline),
                color = colors.onBackgroundMuted,
            )
        }
    }
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    val colors = Geek.colors
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(Geek.shapes.pill)
            .background(colors.soft)
            .border(1.dp, colors.checkboxBorder, Geek.shapes.pill)
            .padding(horizontal = 18.dp),
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

/** Shop.dc.html's foot: a hairline, the plan and its price (the old price struck through), and «پرداخت». */
@Composable
private fun PayBar(state: ShopUiState, onPay: () -> Unit) {
    val colors = Geek.colors
    val locale = appLocale()
    val plan = state.plan
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hairline()
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (plan != null) {
                    val shown = if (state.customMode) {
                        plan.copy(quotaGib = state.customGib?.let { ShopCatalog.termGib(it, plan.durationDays ?: 30) })
                    } else {
                        plan
                    }
                    if (!state.customMode || state.customGib != null) {
                        Text(planSummary(shown, locale), style = Geek.type.caption, color = colors.onBackgroundMuted)
                    }
                }
                state.total?.let {
                    Text(
                        stringResource(R.string.geek_home_balance, formatNumber(it, locale)),
                        style = Geek.type.sectionTitle.copy(fontSize = 22.sp, fontWeight = FontWeight.Black),
                        color = colors.onBackground,
                    )
                }
            }
            state.wasPrice?.let {
                Text(
                    stringResource(R.string.geek_home_balance, formatNumber(it, locale)),
                    style = Geek.type.caption.copy(fontSize = 13.sp, textDecoration = TextDecoration.LineThrough),
                    color = colors.onBackgroundMuted,
                )
            }
        }
        GeekPrimaryButton(
            text = stringResource(R.string.geek_shop_pay),
            icon = GeekIcons.ArrowAction,
            onClick = onPay,
            enabled = plan != null && state.total != null && !state.busy,
        )
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
