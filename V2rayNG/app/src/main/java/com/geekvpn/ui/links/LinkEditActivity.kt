package com.geekvpn.ui.links

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.geekvpn.ui.common.GlassIconButton
import com.geekvpn.ui.common.SettingsDivider
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSwitch
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity
import kotlinx.coroutines.launch

/**
 * Add or edit a subscription link the customer brought themselves, in
 * GeekVPN's design; v2rayNG's subscription store underneath (the same checks
 * as its `SubEditActivity`).
 */
class LinkEditActivity : BaseComponentActivity() {

    private val viewModel: LinkEditViewModel by viewModels {
        LinkEditViewModel.factory(intent.getStringExtra(EXTRA_ID).orEmpty())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is LinkEditEvent.Done -> {
                            Toast.makeText(this@LinkEditActivity, event.message, Toast.LENGTH_LONG).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        GeekTheme {
            GeekBackdrop {
                LinkEditScreen(state, viewModel::edit, viewModel::save, viewModel::delete, ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "subId"

        /** [id] null for a new link. */
        fun intent(context: Context, id: String? = null): Intent =
            Intent(context, LinkEditActivity::class.java).apply { if (id != null) putExtra(EXTRA_ID, id) }
    }
}

@Composable
fun LinkEditScreen(
    state: LinkEditUiState,
    onEdit: ((LinkForm) -> LinkForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = Geek.colors
    val form = state.form
    var more by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(GeekIcons.ArrowForward, stringResource(R.string.geek_servers_back_description), onBack)
            Text(
                stringResource(if (state.isNew) R.string.geek_links_add_title else R.string.geek_links_edit_title),
                style = Geek.type.pageTitle.copy(fontSize = 22.sp),
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (!state.isNew) {
                GlassIconButton(GeekIcons.Unlink, stringResource(R.string.geek_links_delete), { confirmDelete = true })
            }
        }
        GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.sheet, modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.loaded) return@Column
                Field(
                    label = stringResource(R.string.geek_links_name),
                    value = form.name,
                    error = errorText(state, LinkForm.Error.NameMissing),
                ) { v -> onEdit { it.copy(name = v) } }
                Field(
                    label = stringResource(R.string.geek_links_url),
                    value = form.url,
                    keyboard = KeyboardType.Uri,
                    singleLine = false,
                    error = errorText(state, LinkForm.Error.UrlInvalid) ?: errorText(state, LinkForm.Error.UrlInsecure),
                ) { v -> onEdit { it.copy(url = v) } }

                GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ToggleRow(stringResource(R.string.geek_links_enabled), form.enabled) { v -> onEdit { it.copy(enabled = v) } }
                        SettingsDivider()
                        ToggleRow(stringResource(R.string.geek_links_auto_update), form.autoUpdate) { v -> onEdit { it.copy(autoUpdate = v) } }
                    }
                }
                if (form.autoUpdate) {
                    Field(
                        label = stringResource(R.string.geek_links_interval),
                        value = form.interval,
                        keyboard = KeyboardType.Number,
                        error = errorText(state, LinkForm.Error.IntervalTooShort),
                    ) { v -> onEdit { it.copy(interval = v) } }
                }

                MoreHeader(more) { more = !more }
                if (more) {
                    Field(stringResource(R.string.sub_setting_filter), form.filter) { v -> onEdit { it.copy(filter = v) } }
                    Field(stringResource(R.string.sub_setting_user_agent), form.userAgent) { v -> onEdit { it.copy(userAgent = v) } }
                    Field(stringResource(R.string.sub_setting_request_headers), form.headers, singleLine = false) { v -> onEdit { it.copy(headers = v) } }
                    Field(stringResource(R.string.sub_setting_pre_profile), form.prevProfile) { v -> onEdit { it.copy(prevProfile = v) } }
                    Field(stringResource(R.string.sub_setting_next_profile), form.nextProfile) { v -> onEdit { it.copy(nextProfile = v) } }
                    GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.card, modifier = Modifier.fillMaxWidth()) {
                        ToggleRow(stringResource(R.string.sub_allow_insecure_url), form.allowInsecure) { v -> onEdit { it.copy(allowInsecure = v) } }
                    }
                }

                GeekPrimaryButton(
                    text = stringResource(R.string.geek_advanced_save),
                    icon = GeekIcons.Check,
                    onClick = onSave,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.geek_links_delete), style = Geek.type.sectionTitle) },
            text = { Text(stringResource(R.string.geek_links_delete_text), style = Geek.type.body) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.geek_links_delete), color = colors.danger, style = Geek.type.button) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.geek_account_cancel), style = Geek.type.button) }
            },
        )
    }
}

@Composable
private fun errorText(state: LinkEditUiState, error: LinkForm.Error): String? {
    if (error !in state.errors) return null
    return stringResource(
        when (error) {
            LinkForm.Error.NameMissing -> R.string.geek_links_err_name
            LinkForm.Error.UrlInvalid -> R.string.geek_links_err_url
            LinkForm.Error.UrlInsecure -> R.string.geek_links_err_insecure
            LinkForm.Error.IntervalTooShort -> R.string.geek_links_err_interval
        }
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    error: String? = null,
    onChange: (String) -> Unit,
) {
    val colors = Geek.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = Geek.type.label, color = colors.onGlass, modifier = Modifier.padding(horizontal = 4.dp))
        GlassSurface(kind = GlassKind.Milk, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp)) {
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = singleLine,
                    textStyle = Geek.type.body.copy(color = colors.onGlass),
                    cursorBrush = SolidColor(colors.action),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
            }
        }
        if (error != null) {
            Text(error, style = Geek.type.caption.copy(fontSize = 12.sp), color = colors.danger, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onChange(!on) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = Geek.type.row.copy(fontSize = 14.sp), color = Geek.colors.onGlass, modifier = Modifier.weight(1f))
        GeekSwitch(checked = on, onCheckedChange = null)
    }
}

@Composable
private fun MoreHeader(open: Boolean, onClick: () -> Unit) {
    val colors = Geek.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.geek_links_more), style = Geek.type.label, color = colors.link, modifier = Modifier.weight(1f))
        Icon(GeekIcons.ChevronStart, contentDescription = null, tint = colors.link, modifier = Modifier.size(16.dp).rotate(if (open) 90f else -90f))
    }
}
