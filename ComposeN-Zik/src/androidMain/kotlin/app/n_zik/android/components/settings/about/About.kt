package app.n_zik.android.components.settings.about

import app.n_zik.android.components.settings.common.settingsTopContentPadding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.settings.common.SettingsCardSlot
import app.n_zik.android.components.settings.common.SettingsDescription

/** About page with autonomous information, support and contributor cards. */
@Suppress("UNUSED_PARAMETER") // Retained navigation signature for existing callers.
@Composable
fun About(navController: NavController) {

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .settingsTopContentPadding()
    ) {
        HeaderWithIcon(
             title = stringResource(R.string.about),
             iconId = R.drawable.information,
             enabled = false,
             showIcon = true,
             modifier = Modifier,
             onClick = {}
         )
         SettingsDescription(
            text = stringResource(R.string.about_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        AboutSettingsContent()
    }
}

/** Content-only host shared by the legacy About page and thematic category shell. */
@Composable
internal fun AboutSettingsContent(
    selectedCards: Set<Int>? = null,
    cardModifier: @Composable (Int) -> Modifier = { Modifier },
) {
    if (selectedCards == null || R.string.about in selectedCards) {
        SettingsCardSlot(R.string.about, modifier = { cardModifier(R.string.about) }) { AboutAppCard() }
    }
    if (selectedCards == null || R.string.troubleshooting in selectedCards) {
        SettingsCardSlot(R.string.troubleshooting, modifier = { cardModifier(R.string.troubleshooting) }) { AboutSupportCard() }
    }
    if (selectedCards == null || R.string.contributors in selectedCards) {
        SettingsCardSlot(R.string.contributors, modifier = { cardModifier(R.string.contributors) }) { AboutContributorsCard() }
    }
}
