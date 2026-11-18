package com.geekvpn.ui.onboarding

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geekvpn.GeekGraph
import com.geekvpn.ui.components.GeekBackdrop
import com.geekvpn.ui.components.GeekPrimaryButton
import com.geekvpn.ui.components.GeekSecondaryButton
import com.geekvpn.ui.components.GlassKind
import com.geekvpn.ui.components.GlassSurface
import com.geekvpn.ui.icons.GeekIcons
import com.geekvpn.ui.theme.Geek
import com.geekvpn.ui.theme.GeekTheme
import com.v2ray.ang.R
import com.v2ray.ang.ui.base.BaseComponentActivity

/** One page of the first-run guide. */
data class OnboardingPage(@param:StringRes val title: Int, @param:StringRes val body: Int)

/** The three things a new customer would not guess, in the order they meet them. */
val ONBOARDING_PAGES = listOf(
    OnboardingPage(R.string.geek_onboard_auto_title, R.string.geek_onboard_auto_body),
    OnboardingPage(R.string.geek_onboard_route_title, R.string.geek_onboard_route_body),
    OnboardingPage(R.string.geek_onboard_help_title, R.string.geek_onboard_help_body),
)

/**
 * The first-run guide, opened once by [com.geekvpn.ui.home.HomeActivity].
 * It counts as seen as soon as it opens, so back, skip or a crash never
 * bring it back.
 */
class OnboardingActivity : BaseComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GeekGraph.connectionPrefs.onboardingSeen = true
    }

    @Composable
    override fun ScreenContent() {
        GeekTheme {
            GeekBackdrop {
                var page by rememberSaveable { mutableIntStateOf(0) }
                OnboardingScreen(page, onNext = { if (page < ONBOARDING_PAGES.lastIndex) page++ else finish() }, onSkip = ::finish)
            }
        }
    }
}

@Composable
fun OnboardingScreen(page: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    val colors = Geek.colors
    val last = page == ONBOARDING_PAGES.lastIndex
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            stringResource(R.string.geek_onboard_step, page + 1, ONBOARDING_PAGES.size),
            style = Geek.type.label,
            color = colors.onBackgroundMuted,
        )
        Spacer(Modifier.padding(top = 40.dp))
        AnimatedContent(targetState = page, label = "onboarding") { shown ->
            val content = ONBOARDING_PAGES[shown]
            GlassSurface(kind = GlassKind.Milk, shape = Geek.shapes.tileLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(content.title),
                        style = Geek.type.pageTitle.copy(fontSize = 24.sp),
                        color = colors.onGlass,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(content.body), style = Geek.type.body, color = colors.onGlassMuted)
                }
            }
        }
        Spacer(Modifier.padding(top = 12.dp))
        GeekPrimaryButton(
            text = stringResource(if (last) R.string.geek_onboard_start else R.string.geek_onboard_next),
            icon = if (last) GeekIcons.Check else GeekIcons.ArrowForward,
            onClick = onNext,
        )
        if (!last) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                GeekSecondaryButton(text = stringResource(R.string.geek_onboard_skip), onClick = onSkip)
            }
        }
    }
}
