package app.n_zik.android.components.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.enums.ImportPlaylistType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colorPalette().background0)
                .padding(bottom = app.it.fast4x.rimusic.ui.styling.Dimensions.bottomSpacer)
        ) {
            ImportPlaylistType.entries.forEach { type ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            menuState.hide()
                            when(type) {
                                ImportPlaylistType.RiMusic -> onImportNzik()
                                ImportPlaylistType.Exportify -> onImportSpotify()
                                ImportPlaylistType.RiPlay -> onImportSpotify()
                            }
                        }
                        .padding(16.dp)
                ) {
                    val iconId = when(type) {
                        ImportPlaylistType.RiMusic -> R.drawable.ic_launcher_monochrome
                        ImportPlaylistType.Exportify -> R.drawable.spotify
                        ImportPlaylistType.RiPlay -> R.drawable.riplay
                    }
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(iconId),
                        contentDescription = null,
                        tint = colorPalette().text,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    BasicText(
                        text = stringResource(type.titleId),
                        style = typography().m.copy(color = colorPalette().text)
                    )
                }
            }
        }
    }
}
