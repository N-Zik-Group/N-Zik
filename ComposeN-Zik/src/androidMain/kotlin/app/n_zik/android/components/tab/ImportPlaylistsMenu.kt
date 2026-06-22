package app.n_zik.android.components.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.enums.ImportPlaylistType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Clickable
import app.n_zik.android.typography
import app.n_zik.android.colorPalette

class ImportPlaylistsMenu(
    private val onImportNzik: () -> Unit,
    private val onImportSpotify: () -> Unit
) : Descriptive, MenuIcon {
    override val messageId: Int = R.string.import_playlist
    override val iconId: Int = R.drawable.import_outline
    override val menuIconTitle: String
        @Composable get() = stringResource(messageId)

    var showDialog = mutableStateOf(false)

    override fun onShortClick() {
        showDialog.value = true
    }

    @Composable
    fun Render() {
        val menuState = app.it.fast4x.rimusic.ui.components.LocalMenuState.current
        
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
    private fun ImportOptionsContent(menuState: app.it.fast4x.rimusic.ui.components.MenuState) {
        val styleState = app.it.fast4x.rimusic.utils.rememberPreference(
            app.it.fast4x.rimusic.utils.menuStyleKey, 
            app.it.fast4x.rimusic.enums.MenuStyle.List
        )
        
        val menu = androidx.compose.runtime.remember {
            object : app.it.fast4x.rimusic.ui.components.tab.toolbar.Menu {
                override val menuState = menuState
                override var menuStyle: app.it.fast4x.rimusic.enums.MenuStyle by styleState
                
                private val buttons: List<app.it.fast4x.rimusic.ui.components.tab.toolbar.Button> = listOf(
                    object : MenuIcon, Descriptive, Clickable {
                        override val iconId: Int = R.drawable.ic_launcher
                        override val messageId: Int = R.string.import_playlist_nzik
                        @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                        @get:Composable override val color: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color.Unspecified
                        override fun onShortClick() { menuState.hide(); onImportNzik() }
                        override fun onLongClick() {}
                    },
                    object : MenuIcon, Descriptive, Clickable {
                        override val iconId: Int = R.drawable.spotify
                        override val messageId: Int = R.string.import_playlist_exportify_net
                        @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                        @get:Composable override val color: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color.Unspecified
                        override fun onShortClick() { menuState.hide(); onImportSpotify() }
                        override fun onLongClick() {}
                    },
                    object : MenuIcon, Descriptive, Clickable {
                        override val iconId: Int = R.drawable.riplay
                        override val messageId: Int = R.string.import_playlist_riplay
                        @get:Composable override val menuIconTitle: String get() = stringResource(messageId)
                        @get:Composable override val color: androidx.compose.ui.graphics.Color get() = androidx.compose.ui.graphics.Color.Unspecified
                        override fun onShortClick() { menuState.hide(); onImportSpotify() }
                        override fun onLongClick() {}
                    }
                )

                @Composable
                override fun ListMenu() = app.n_zik.android.components.menu.ListMenu.Menu {
                    buttons.forEach { 
                        if (it is MenuIcon) it.ListMenuItem() 
                    }
                }

                @Composable
                override fun GridMenu() = app.n_zik.android.components.menu.GridMenu.Menu {
                    items(buttons, key = app.it.fast4x.rimusic.ui.components.tab.toolbar.Button::hashCode) {
                        if (it is MenuIcon) it.GridMenuItem()
                    }
                }

                @Composable
                override fun MenuComponent() {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorPalette().background0)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.background( colorPalette().background1 )
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.cd_arrow_down),
                                tint = colorPalette().textSecondary,
                                modifier = Modifier.size( 24.dp )
                            )

                            BasicText(
                                text = stringResource(R.string.import_playlist),
                                style = typography().m.copy(color = colorPalette().text),
                                modifier = Modifier.padding(
                                    top = 5.dp,
                                    bottom = 10.dp
                                )
                            )

                            androidx.compose.material3.HorizontalDivider( Modifier.height(1.dp) )
                        }

                        if( menuStyle == app.it.fast4x.rimusic.enums.MenuStyle.List )
                            ListMenu()
                        else
                            GridMenu()
                    }
                }
            }
        }
        
        menu.MenuComponent()
    }
}
