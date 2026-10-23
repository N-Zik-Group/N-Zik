package app.it.fast4x.rimusic.utils

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import app.n_zik.android.R
import app.n_zik.android.extensions.discord.DiscordUiState
import app.n_zik.android.utils.coroutines.NzikDispatchers

// Below this smallest width (dp) a device is a phone; tablets and unfolded foldables are above it
private const val TABLET_MIN_SMALLEST_WIDTH_DP = 600

/** Duration of the slide that puts the app header and navigation bar in and out. */
const val LANDSCAPE_BARS_ANIMATION_MS = 250

// Home tabs (ids as used by HomeScreen) whose lists get the full landscape height:
// songs, artists, albums and library (playlists). Quick Picks keeps its bars.
private val BARLESS_LANDSCAPE_HOME_TABS = setOf("songs", "artists", "albums", "playlists")

/**
 * State of the app header + navigation bar on the screens where a landscape phone
 * hides them (see [isLandscapeBarlessScreen]). They stay hidden until the user taps
 * the floating toggle; a tap on it again, or sliding the list, puts them away, and
 * nothing else brings them back.
 */
object LandscapeBars {
    /** The user asked for the bars: they are shown despite the landscape-phone hiding. */
    var revealed by mutableStateOf(false)
        private set

    fun toggle() {
        revealed = !revealed
    }

    fun hide() {
        revealed = false
    }
}

/**
 * Whether a screen is one where a landscape phone hides the app header and the
 * navigation bar by default: a phone (smallest width under [TABLET_MIN_SMALLEST_WIDTH_DP])
 * held in landscape has too little vertical room for them, but only on the Songs,
 * Album, Artist and Library home tabs. Every other screen keeps its bars.
 *
 * @param isLandscape Whether the device is currently in landscape
 * @param smallestScreenWidthDp Smallest screen width of the device, in dp
 * @param homeTab Id of the current Home tab
 */
internal fun hidesBarsInLandscape(
    isLandscape: Boolean,
    smallestScreenWidthDp: Int,
    route: String?,
    homeTab: String?
): Boolean =
    isLandscape &&
            smallestScreenWidthDp < TABLET_MIN_SMALLEST_WIDTH_DP &&
            route == "home" &&
            homeTab in BARLESS_LANDSCAPE_HOME_TABS

/** [hidesBarsInLandscape] for the current configuration and screen. */
@Composable
fun isLandscapeBarlessScreen(): Boolean {
    val configuration = LocalConfiguration.current
    val route by DiscordUiState.currentRoute.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
    val homeTab by DiscordUiState.currentHomeTab.collectAsStateWithLifecycle(initialValue = null, context = NzikDispatchers.DATA)
    return hidesBarsInLandscape(
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        smallestScreenWidthDp = configuration.smallestScreenWidthDp,
        route = route,
        homeTab = homeTab
    )
}

/** Whether the app header and the navigation bar are hidden right now. */
@Composable
fun hideBarsInLandscapeMobile(): Boolean = isLandscapeBarlessScreen() && !LandscapeBars.revealed

/** The floating button that toggles the bars: what it shows and what a tap does. */
class LandscapeBarsToggleButton(@DrawableRes val iconId: Int, val onClick: () -> Unit)

/**
 * The toggle for the floating actions group (the one holding the scroll-to-top arrow),
 * or null when the current screen does not hide the bars. Shown next to the arrow, it
 * slides in like it and moves with it. Menu while the bars are hidden, close while shown.
 */
@Composable
fun landscapeBarsToggleButton(): LandscapeBarsToggleButton? {
    if (!isLandscapeBarlessScreen()) return null
    return LandscapeBarsToggleButton(
        iconId = if (LandscapeBars.revealed) R.drawable.close else R.drawable.menu,
        onClick = LandscapeBars::toggle
    )
}
