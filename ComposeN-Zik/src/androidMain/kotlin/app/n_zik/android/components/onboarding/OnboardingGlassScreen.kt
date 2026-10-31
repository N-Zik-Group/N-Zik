package app.n_zik.android.components.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.settings.ui.GlassSettingsContent

/** Full Glass customization after restore; continuing never completes onboarding. */
@Composable
fun OnboardingGlassScreen(modifier: Modifier = Modifier, onComplete: () -> Unit) {
    Box(modifier.fillMaxSize().statusBarsPadding().testTag("onboarding.glass")) {
        GlassSettingsContent(onboardingFooter = {
            OnboardingPrimaryButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
                    .testTag("onboarding.glass.continue"),
            ) {
                Text(stringResource(R.string.onboard_profile_continue))
            }
        })
    }
}
