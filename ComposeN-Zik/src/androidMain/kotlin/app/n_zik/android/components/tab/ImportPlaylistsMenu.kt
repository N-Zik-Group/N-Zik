package app.n_zik.android.components.tab

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

import app.it.fast4x.rimusic.enums.MenuStyle
import app.it.fast4x.rimusic.ui.components.LocalMenuState
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Button
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.menuStyleKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.menu.GridMenu
import app.n_zik.android.components.menu.ListMenu

class ImportPlaylistsMenu(
    private val onImportNzik: () -> Unit,
    private val onImportSpotify: () -> Unit,
    private val onImportRiplay: () -> Unit,
    private val onImportYoutubeLink: () -> Unit,
    // Mixed action (spec-full-offline-mode): when false (full offline mode on),
    // the dialog only offers the local NZik/CSV import — network sources are pruned.
    private val networkOptionsEnabled: () -> Boolean = { true }
) : Descriptive, MenuIcon {
    override val messageId: Int = R.string.playlist_action_import
    override val iconId: Int = R.drawable.import_outline
    override val menuIconTitle: String
        @Composable get() = stringResource(messageId)

    var showDialog = mutableStateOf(false)

    override fun onShortClick() {
        showDialog.value = true
    }

    @Composable
    fun Render() {
        val menuState = LocalMenuState.current
        
        LaunchedEffect(showDialog.value) {
            if (showDialog.value) {
                menuState.display {
                    ImportOptionsContent(menuState)
                }
                showDialog.value = false
            }
        }
    }

    @Composable
    private fun ImportOptionsContent(menuState: MenuState) {
        val styleState = rememberPreference(
            menuStyleKey, 
            MenuStyle.List
        )
        
        val menu = remember {
            object : Menu {
                override val menuState = menuState
                override var menuStyle: MenuStyle by styleState
                
                // The local NZik/CSV import is always offered; the network sources
                // (Spotify, RiPlay, YouTube link) are pruned in full offline mode.
                private val buttons: List<Button> = buildList {
                    add(
                        object : MenuIcon, Descriptive, Clickable {
                            override val iconId: Int = R.drawable.ic_launcher
                            override val messageId: Int = R.string.playlist_import_format_nzik_csv
                            @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                            @get:Composable override val color: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color.Unspecified
                            override fun onShortClick() { menuState.hide(); onImportNzik() }
                            override fun onLongClick() {}
                        }
                    )
                    if (networkOptionsEnabled()) {
                        add(
                            object : MenuIcon, Descriptive, Clickable {
                                override val iconId: Int = R.drawable.spotify
                                override val messageId: Int = R.string.playlist_import_format_exportify_csv
                                @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                                @get:Composable override val color: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color.Unspecified
                                override fun onShortClick() { menuState.hide(); onImportSpotify() }
                                override fun onLongClick() {}
                            }
                        )
                        add(
                            object : MenuIcon, Descriptive, Clickable {
                                override val iconId: Int = R.drawable.riplay
                                override val messageId: Int = R.string.playlist_import_format_riplay_csv
                                @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                                @get:Composable override val color: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color.Unspecified
                                override fun onShortClick() { menuState.hide(); onImportRiplay() }
                                override fun onLongClick() {}
                            }
                        )
                        add(
                            object : MenuIcon, Descriptive, Clickable {
                                override val iconId: Int = R.drawable.ytmusic
                                override val messageId: Int = R.string.settings_youtube_music_title
                                @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                                @get:Composable override val color: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color.Unspecified
                                override fun onShortClick() { menuState.hide(); onImportYoutubeLink() }
                                override fun onLongClick() {}
                            }
                        )
                    }
                }

                @Composable
                override fun ListMenu() = ListMenu.Menu(title = stringResource(messageId)) {
                    buttons.forEach { 
                        if (it is MenuIcon) it.ListMenuItem() 
                    }
                }

                @Composable
                override fun GridMenu() = GridMenu.Menu(title = stringResource(messageId)) {
                    items(buttons, key = Button::hashCode) {
                        if (it is MenuIcon) it.GridMenuItem()
                    }
                }

                @Composable
                override fun MenuComponent() {
                    if (menuStyle == MenuStyle.List) {
                        ListMenu()
                    } else {
                        GridMenu()
                    }
                }
            }
        }
        
        menu.MenuComponent()
    }
}
