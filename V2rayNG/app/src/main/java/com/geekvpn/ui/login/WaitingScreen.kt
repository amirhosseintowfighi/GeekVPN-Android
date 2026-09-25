package com.geekvpn.ui.login

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.v2ray.ang.R
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * After the app has sent the customer to Telegram: what to do there, how long
 * the link lasts, and a way back. Built from Main.html's parts; the design has
 * no screen of its own for this step.
 */
@Composable
fun WaitingScreen(
    purpose: LinkPurpose,
    expiresAt: Long,
    onReopen: () -> Unit,
    onCancel: () -> Unit,
) {
    LoginScaffold(
        hero = {
            PulsingLogo()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        if (purpose == LinkPurpose.CreateAccount) R.string.geek_wait_title_create else R.string.geek_wait_title
                    ),
                    style = Geek.type.pageTitle,
                    color = Geek.colors.onBackground,
                )
                Countdown(expiresAt)
            }
        },
        sheet = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                Step(1, stringResource(R.string.geek_wait_step_open))
                Step(
                    2,
                    stringResource(
                        if (purpose == LinkPurpose.CreateAccount) R.string.geek_wait_step_create else R.string.geek_wait_step_start
                    ),
                )
                Step(3, stringResource(R.string.geek_wait_step_approve))
            }
            GeekPrimaryButton(
                text = stringResource(R.string.geek_wait_reopen),
                icon = GeekIcons.Telegram,
                onClick = onReopen,
            )
            GeekSecondaryButton(
                text = stringResource(R.string.geek_wait_cancel),
                icon = GeekIcons.Close,
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

/** Shown between approval and leaving the login screens, while the services download. */
@Composable
fun SyncingScreen() {
    LoginScaffold(
        hero = { PulsingLogo() },
        sheet = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                CircularProgressIndicator(
                    color = Geek.colors.onGlass,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.geek_wait_syncing),
                    style = Geek.type.row,
                    color = Geek.colors.onGlass,
                )
            }
        },
    )
}

@Composable
private fun PulsingLogo() {
    val transition = rememberInfiniteTransition(label = "waiting")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    HeroLogo(frame = 220.dp, logo = 130.dp, modifier = Modifier.scale(pulse))
}

/** "اعتبار لینک: ۴:۳۲", ticking once a second, in the app language's digits. */
@Composable
private fun Countdown(expiresAt: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (now < expiresAt) {
            delay(1_000L - (System.currentTimeMillis() % 1_000L))
            now = System.currentTimeMillis()
        }
    }
    val seconds = ((expiresAt - now).coerceAtLeast(0L) + 999L) / 1_000L
    val locale = currentLocale()
    val clock = String.format(locale, "%d:%02d", seconds / 60, seconds % 60)
    GlassSurface(kind = GlassKind.Clear, shape = Geek.shapes.pill) {
        Row(
            modifier = Modifier
                .height(36.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(GeekIcons.Clock, contentDescription = null, tint = Geek.colors.onBackground, modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.geek_wait_expires, clock),
                style = Geek.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = Geek.colors.onBackground,
                modifier = Modifier.alpha(if (seconds > 0) 1f else 0.6f),
            )
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Geek.colors.soft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = String.format(currentLocale(), "%d", number),
                style = Geek.type.label,
                color = Geek.colors.onGlass,
            )
        }
        Text(
            text = text,
            style = Geek.type.body,
            color = Geek.colors.onGlass,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The app language (per-app locale), which picks Persian or Latin digits. */
@Composable
private fun currentLocale(): Locale =
    ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
