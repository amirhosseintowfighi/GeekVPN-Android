package com.geekvpn.ui.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R

/**
 * "نام کاربری" on the login screen: the username and password the customer
 * set in the bot. Built from Main.html's parts; the design has no screen of
 * its own for it.
 *
 * The typed username survives rotation; the password deliberately does not,
 * so it never lands in saved instance state.
 */
@Composable
fun UsernameScreen(
    busy: Boolean,
    onSubmit: (username: String, password: String) -> Unit,
    onBack: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val passwordFocus = remember { FocusRequester() }
    val submit = { if (!busy) onSubmit(username, password) }

    LoginScaffold(
        hero = {
            HeroLogo(frame = 160.dp, logo = 96.dp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.geek_username_title),
                    style = Geek.type.pageTitle,
                    color = Geek.colors.onBackground,
                )
                Text(
                    text = stringResource(R.string.geek_username_hint),
                    style = Geek.type.body,
                    color = Geek.colors.onBackgroundMuted,
                )
            }
        },
        sheet = {
            GeekTextField(
                value = username,
                onValueChange = { username = it.filterNot(Char::isWhitespace) },
                label = stringResource(R.string.geek_username_field),
                enabled = !busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            )
            GeekTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.geek_password_field),
                enabled = !busy,
                modifier = Modifier.focusRequester(passwordFocus),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailing = {
                    val description = stringResource(
                        if (visible) R.string.geek_password_hide_description else R.string.geek_password_show_description
                    )
                    Text(
                        text = stringResource(if (visible) R.string.geek_password_hide else R.string.geek_password_show),
                        style = Geek.type.caption.copy(fontWeight = FontWeight.Bold),
                        color = Geek.colors.link,
                        modifier = Modifier
                            .clip(Geek.shapes.checkbox)
                            .clickable(role = Role.Button) { visible = !visible }
                            .semantics { contentDescription = description }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                },
            )
            GeekPrimaryButton(
                text = stringResource(R.string.geek_username_submit),
                icon = GeekIcons.ArrowForward,
                onClick = submit,
                enabled = !busy,
            )
            GeekSecondaryButton(
                text = stringResource(R.string.geek_username_back),
                onClick = onBack,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun GeekTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Geek.colors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = Geek.type.caption) },
        enabled = enabled,
        singleLine = true,
        // Usernames and passwords are Latin; typed right to left they would
        // show their characters in reverse order while the cursor moves.
        textStyle = Geek.type.row.copy(textDirection = TextDirection.Ltr, color = colors.onGlass),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailing,
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
        modifier = modifier.fillMaxWidth(),
    )
}
