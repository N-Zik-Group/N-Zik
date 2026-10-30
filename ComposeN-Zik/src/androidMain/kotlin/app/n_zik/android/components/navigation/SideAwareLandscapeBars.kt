package app.n_zik.android.components.navigation

import androidx.compose.runtime.Composable
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.utils.LandscapeBarsToggleButton
import app.it.fast4x.rimusic.utils.landscapeBarsToggleButton

/** A side rail remains visible in landscape, so it never needs a reveal control. */
@Composable
internal fun sideAwareLandscapeBarsToggleButton(): LandscapeBarsToggleButton? =
    if (sideNavigationOwnsHeader(NavigationBarPosition.current())) null else landscapeBarsToggleButton()
