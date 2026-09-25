package com.geekvpn.ui.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.SettingsDivider
import com.geekvpn.ui.components.GeekCheckbox
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSheet
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R

/** v2rayNG's settings as GeekVPN cards: one collapsible card per group, sheets to edit. */
@Composable
fun BoxScope.AdvancedScreen(
    state: AdvancedUiState,
    onBack: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onChoice: (String, String) -> Unit,
    onText: (String, String) -> Boolean,
    onLink: (String) -> Unit,
) {
    val colors = Geek.colors
    // Only presentation state here: which card is open and which setting is being edited.
    var open by rememberSaveable { mutableStateOf(AdvancedSettings.sections.first().title) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), onBack)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.geek_account_advanced), style = Geek.type.pageTitle.copy(fontSize = 22.sp), color = colors.onBackground)
                Text(stringResource(R.string.geek_advanced_subtitle), style = Geek.type.caption, color = colors.onBackgroundMuted, maxLines = 2)
            }
            if (state.busy) {
                CircularProgressIndicator(color = colors.onBackground, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
        }
        GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.sheet, modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                if (!state.loaded) return@LazyColumn
                AdvancedSettings.sections.forEach { section ->
                    item(key = section.title) {
                        SectionCard(
                            section = section,
                            expanded = open == section.title,
                            state = state,
                            onExpand = { open = if (open == section.title) 0 else section.title },
                            onToggle = onToggle,
                            onEdit = { editing = it },
                            onLink = onLink,
                        )
                    }
                }
            }
        }
    }

    when (val item = editing?.let { AdvancedSettings.items[it] }) {
        is AdvancedItem.Text -> TextSheet(
            item = item,
            current = state.values[item.key] as? String ?: item.default,
            onSave = { if (onText(item.key, it)) editing = null },
            onDismiss = { editing = null },
        )
        is AdvancedItem.Choice -> ChoiceSheet(
            item = item,
            current = state.values[item.key] as? String ?: item.default,
            onSelect = {
                onChoice(item.key, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
        else -> Unit
    }
}

@Composable
private fun SectionCard(
    section: AdvancedSection,
    expanded: Boolean,
    state: AdvancedUiState,
    onExpand: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onLink: (String) -> Unit,
) {
    val colors = Geek.colors
    val stateText = stringResource(if (expanded) R.string.geek_advanced_expanded else R.string.geek_advanced_collapsed)
    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = onExpand)
                    .semantics(mergeDescendants = true) { stateDescription = stateText }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(section.title), style = Geek.type.row, color = colors.onGlass, modifier = Modifier.weight(1f))
                Icon(
                    GeekIcons.ChevronStart,
                    contentDescription = null,
                    tint = colors.onGlassMuted,
                    modifier = Modifier.size(18.dp).rotate(if (expanded) 90f else -90f),
                )
            }
            if (expanded) {
                section.items.forEach { item ->
                    if (item is AdvancedItem.Link && item.key == AdvancedSettings.LINK_SYSTEM_VPN && !state.systemVpnSettings) return@forEach
                    SettingsDivider()
                    ItemRow(item, state, onToggle, onEdit, onLink)
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: AdvancedItem,
    state: AdvancedUiState,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit,
    onLink: (String) -> Unit,
) {
    val enabled = state.enabled(item.key)
    val title = stringResource(item.title)
    when (item) {
        is AdvancedItem.Toggle -> {
            val on = state.values[item.key] as? Boolean ?: item.default
            Row(
                modifier = rowModifier(enabled)
                    .clickable(enabled = enabled, role = Role.Switch) { onToggle(item.key, !on) }
                    .padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Labels(title, item.summary?.let { stringResource(it) }, Modifier.weight(1f))
                GeekSwitch(checked = on, onCheckedChange = null, enabled = enabled)
            }
        }
        is AdvancedItem.Text -> {
            val value = state.values[item.key] as? String ?: item.default
            val shown = when {
                value.isEmpty() -> stringResource(R.string.geek_advanced_default)
                item.password -> "••••••"
                else -> value
            }
            NavRow(title, shown, enabled) { onEdit(item.key) }
        }
        is AdvancedItem.Choice -> {
            val value = state.values[item.key] as? String ?: item.default
            val entries = stringArrayResource(item.entries)
            val values = stringArrayResource(item.values)
            val shown = entries.getOrNull(values.indexOf(value)) ?: value
            NavRow(title, shown, enabled) { onEdit(item.key) }
        }
        is AdvancedItem.Link -> NavRow(title, item.summary?.let { stringResource(it) }, enabled) { onLink(item.key) }
    }
}

private fun rowModifier(enabled: Boolean): Modifier = Modifier
    .fillMaxWidth()
    .heightIn(min = 56.dp)
    .alpha(if (enabled) 1f else 0.45f)

@Composable
private fun NavRow(title: String, value: String?, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = rowModifier(enabled).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Labels(title, value, Modifier.weight(1f))
        Icon(GeekIcons.ChevronStart, contentDescription = null, tint = Geek.colors.onGlassMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Labels(title: String, hint: String?, modifier: Modifier) {
    val colors = Geek.colors
    Column(modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp)) {
        Text(title, style = Geek.type.row.copy(fontSize = 14.sp), color = colors.onGlass)
        if (!hint.isNullOrEmpty()) {
            Text(
                hint,
                style = Geek.type.caption.copy(fontSize = 12.sp),
                color = colors.onGlassMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BoxScope.TextSheet(item: AdvancedItem.Text, current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = Geek.colors
    var input by rememberSaveable(item.key) { mutableStateOf(current) }
    val title = stringResource(item.title)
    GeekSheet(
        title = title,
        subtitle = stringResource(R.string.geek_advanced_empty_default),
        onDismiss = onDismiss,
        closeLabel = stringResource(R.string.geek_sheet_close),
    ) {
        GlassSurface(kind = GlassKind.Milk, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp)) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = item.key != com.v2ray.ang.AppConfig.PREF_DNS_HOSTS,
                    textStyle = Geek.type.body.copy(color = colors.onGlass),
                    cursorBrush = SolidColor(colors.action),
                    visualTransformation = if (item.password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (item.number) KeyboardType.Number else KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = title },
                )
            }
        }
        GeekPrimaryButton(
            text = stringResource(R.string.geek_advanced_save),
            icon = GeekIcons.Check,
            onClick = { onSave(input) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BoxScope.ChoiceSheet(item: AdvancedItem.Choice, current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val colors = Geek.colors
    val entries = stringArrayResource(item.entries)
    val values = stringArrayResource(item.values)
    GeekSheet(title = stringResource(item.title), onDismiss = onDismiss, closeLabel = stringResource(R.string.geek_sheet_close)) {
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEachIndexed { index, value ->
                val selected = value == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) colors.soft else colors.chip)
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(value) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(entries.getOrElse(index) { value }, style = Geek.type.row.copy(fontSize = 14.sp), color = colors.onGlass, modifier = Modifier.weight(1f))
                    GeekCheckbox(checked = selected, onCheckedChange = null)
                }
            }
        }
    }
}
