package app.n_zik.android.components.menu

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.components.song.TrackMetadataSearch
import app.n_zik.android.components.song.openTrackMetadataSearch
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon

internal fun artistSearchMenuAction(name: String, navController: NavController, menuState: MenuState): MenuIcon =
    object : MenuIcon, Descriptive {
        override val iconId: Int = R.drawable.people
        override val messageId: Int = R.string.library_artists_title

        @get:Composable
        override val menuIconTitle: String get() = artistMoreFromTitle(name)

        override fun onShortClick() {
            if (openTrackMetadataSearch(navController, name, TrackMetadataSearch.Artist)) menuState.hide()
        }
    }
